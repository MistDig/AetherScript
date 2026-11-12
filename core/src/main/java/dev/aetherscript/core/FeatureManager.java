package dev.aetherscript.core;

import java.io.*;
import java.util.*;

/** Preparation/inspection runs on the I/O worker; activation stays on the platform thread. */
public final class FeatureManager {
    public record Change(RevisionStore.Plan durable, Map<String, CompiledFeature> target, List<String> messages) {
        public Change { target = Map.copyOf(target); messages = List.copyOf(messages); }
    }
    public static final class Rejected extends RuntimeException {
        private final List<String> diagnostics;
        public Rejected(List<String> diagnostics) { super("Reload rejected"); this.diagnostics = List.copyOf(diagnostics); }
        public List<String> diagnostics() { return diagnostics; }
    }
    private final ScriptStore scripts;
    private final RevisionStore store;
    public FeatureManager(ScriptStore scripts, RevisionStore store) { this.scripts = scripts; this.store = store; }
    public RevisionStore store() { return store; }
    public ScriptStore scripts() { return scripts; }
    public Change prepareText(String id, String text, Registry.Snapshot current) throws IOException {
        CompiledFeature compiled = validateText(id, text, current);
        var source = new SourceFile(id + ".aether", "aether", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, CompiledFeature> target = new TreeMap<>(current.features()); target.put(id, compiled);
        return new Change(store.prepare(Map.of(id, source), Set.of()), target, List.of());
    }
    public CompiledFeature validateText(String id, String text, Registry.Snapshot current) {
        FeatureParser.requireId(id);
        var source = new SourceFile(id + ".aether", "aether", text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var compiled = source.compile();
        if (!id.equals(compiled.definition().id())) throw new ValidationException("$.id", "ID must match target");
        Map<String, CompiledFeature> target = new TreeMap<>(current.features()); target.put(id, compiled); validateTarget(target);
        return compiled;
    }

    public Change startup() throws IOException {
        scripts.recoverRemoteIntents(store);
        Map<String, ScriptStore.Inspection> files = scripts.inspect(null);
        Map<String, CompiledFeature> target = new TreeMap<>();
        Map<String, SourceFile> updates = new TreeMap<>();
        Map<String, Long> recovered = new TreeMap<>();
        Set<String> deactivate = new HashSet<>();
        List<String> messages = new ArrayList<>();
        Set<String> ids = new TreeSet<>(files.keySet()); ids.addAll(store.state().features().keySet());
        Map<String, Integer> names = new HashMap<>();
        files.values().stream().filter(ScriptStore.Inspection::valid).forEach(f -> names.merge(f.compiled().definition().command().name(), 1, Integer::sum));
        for (String id : ids) {
            var file = files.get(id); var history = store.state().features().get(id);
            boolean held = history != null && history.rollbackHeld() && history.active() != 0;
            if (file != null && file.valid() && !held && names.get(file.compiled().definition().command().name()) == 1) {
                Map<String, CompiledFeature> candidate = new TreeMap<>(target); candidate.put(id, file.compiled());
                try { validateTarget(candidate); target.put(id, file.compiled()); updates.put(id, file.source()); continue; }
                catch (ValidationException e) { messages.add(id + ": editable command collides with an already restored feature"); }
            } else if (file != null && !file.valid()) messages.addAll(diagnostic(file));
            else if (file != null && file.valid() && !held) messages.add(id + ": duplicate command name in editable files; trying saved version");
            try {
                RevisionStore.Saved saved = store.recover(id);
                if (saved != null) {
                    Map<String, CompiledFeature> candidate = new TreeMap<>(target); candidate.put(id, saved.compiled()); validateTarget(candidate);
                    target.put(id, saved.compiled()); recovered.put(id, saved.revision().number());
                    messages.add(id + ": running saved revision #" + saved.revision().number() + (held ? " (rollback held until /ais reload " + id + ")" : "; edit scripts/" + (file == null ? saved.source().fileName() : file.fileName())));
                } else if (file != null) messages.add(id + ": DISABLED - no valid working revision");
            } catch (IOException | ValidationException e) {
                // Invalid filenames are never passed into the persistent store.
                if (history != null) deactivate.add(id);
                messages.add(display(id) + ": DISABLED - no readable, valid, nonconflicting saved revision");
            }
        }
        return new Change(store.prepare(updates, deactivate, recovered), target, messages);
    }
    public Change reload(String selected, Registry.Snapshot current) throws IOException {
        var files = validatedFiles(selected);
        Map<String, CompiledFeature> target = candidate(files, selected, current);
        Map<String, SourceFile> updates = new TreeMap<>(); files.forEach((id, file) -> updates.put(id, file.source()));
        Set<String> deactivate = new HashSet<>();
        if (selected == null) { deactivate.addAll(current.features().keySet()); deactivate.removeAll(target.keySet()); }
        return new Change(store.prepare(updates, deactivate), target, List.of());
    }
    public int validate(String selected, Registry.Snapshot current) throws IOException {
        var files = validatedFiles(selected); candidate(files, selected, current); return files.size();
    }
    public Change rollback(String id, Registry.Snapshot current) throws IOException {
        FeatureParser.requireId(id); var plan = store.rollback(id); var history = plan.after().features().get(id);
        var saved = store.load(id, history.activeRevision());
        Map<String, CompiledFeature> target = new TreeMap<>(current.features()); target.put(id, saved.compiled()); validateTarget(target);
        return new Change(plan, target, List.of("Rolled back " + id + " to saved revision #" + history.active() + ".",
            "Editable file was not overwritten. /ais diff " + id + " shows the difference.",
            "Rollback stays active across restarts until you explicitly reload this feature."));
    }
    private Map<String, ScriptStore.Inspection> validatedFiles(String selected) throws IOException {
        var files = scripts.inspect(selected); List<String> diagnostics = new ArrayList<>();
        files.values().stream().filter(f -> !f.valid()).forEach(f -> diagnostics.addAll(diagnostic(f)));
        if (!diagnostics.isEmpty()) throw new Rejected(diagnostics);
        return files;
    }
    private Map<String, CompiledFeature> candidate(Map<String, ScriptStore.Inspection> files, String selected, Registry.Snapshot current) {
        Map<String, CompiledFeature> target = new TreeMap<>(selected == null ? Map.of() : current.features());
        files.forEach((id, file) -> target.put(id, file.compiled())); validateTarget(target); return target;
    }
    private static void validateTarget(Map<String, CompiledFeature> target) { new Registry().replace(0, target); }
    public static List<String> diagnostic(ScriptStore.Inspection file) {
        var e = file.error();
        return List.of("Edit scripts/" + display(file.fileName()) + ": " + (e.line() > 0 ? "line " + e.line() + ", column " + e.column() : "location unavailable") + "; " + display(e.path()),
            display(e.reason()), "Fix that editable file, then run /ais reload " + display(file.id()) + ".");
    }
    public List<String> status(String id, Registry.Snapshot current) throws IOException {
        FeatureParser.requireId(id); var file = scripts.inspect(id).get(id); var active = current.features().get(id);
        var history = store.state().features().get(id); long number = history == null ? 0 : history.active();
        List<String> lines = new ArrayList<>();
        lines.add(id + " | Running: " + (active == null ? "DISABLED" : "saved revision #" + number + " (" + active.sourceHash().substring(0, 12) + ")"));
        lines.add("Editable file: scripts/" + display(file.fileName()));
        lines.add("File status: " + fileStatus(file, active));
        if (history != null && history.rollbackHeld()) lines.add("Rollback is held across restart. /ais reload " + id + " opts into the editable file.");
        if (!file.valid()) lines.addAll(diagnostic(file));
        else if (active != null && !file.compiled().sourceHash().equals(active.sourceHash())) lines.add("Changes are not active. Compare with /ais diff " + id + ", then /ais reload " + id + ".");
        return lines;
    }
    public List<String> list(Registry.Snapshot current) throws IOException {
        var files = scripts.inspect(null); Set<String> ids = new TreeSet<>(files.keySet()); ids.addAll(current.features().keySet());
        List<String> lines = new ArrayList<>(); lines.add("Features: " + ids.size() + " | saved-version limit: " + store.retainVersions());
        for (String id : ids) lines.add(display(id) + " | " + fileStatus(files.get(id), current.features().get(id)));
        return lines;
    }
    private static String fileStatus(ScriptStore.Inspection file, CompiledFeature active) {
        if (file == null || (file.source() == null && file.error() != null && file.error().reason().startsWith("Editable file is missing")))
            return active == null ? "DISABLED - editable file missing" : "RUNNING SAVED VERSION - editable file missing";
        if (!file.valid()) return active == null ? "DISABLED - file has errors" : "RUNNING SAVED VERSION - file has errors";
        if (active == null) return "NOT ACTIVE - valid file; reload to activate";
        return file.compiled().sourceHash().equals(active.sourceHash()) ? "UP TO DATE" : "RUNNING SAVED VERSION - file has unapplied changes";
    }
    public List<String> history(String id) {
        FeatureParser.requireId(id); var history = store.state().features().get(id);
        if (history == null) return List.of(id + ": no saved versions");
        List<String> lines = new ArrayList<>(); lines.add(id + ": " + history.revisions().size() + "/" + store.retainVersions() + " saved versions (internal copies; edit scripts/ only)");
        history.revisions().stream().sorted(Comparator.comparingLong(RevisionStore.Revision::number).reversed()).forEach(r ->
            lines.add("#" + r.number() + (r.number() == history.active() ? " [ACTIVE] " : " ") + r.hash().substring(0, 12) + " | " + r.savedAt()));
        return lines;
    }
    public List<String> diff(String id) throws IOException {
        FeatureParser.requireId(id); var history = store.state().features().get(id);
        if (history == null || history.activeRevision() == null) return List.of(id + ": no running saved revision to compare");
        var file = scripts.inspect(id).get(id);
        if (file.source() == null) return List.of("Cannot read scripts/" + display(file.fileName()) + " for comparison. Fix the editable file location first.");
        if (file.source().bytes().length > FeatureParser.MAX_BYTES) return List.of("Editable file exceeds 64 KiB; shorten it before comparing.");
        var saved = store.load(id, history.activeRevision());
        String[] oldLines = saved.source().text().split("\\R", -1), newLines = file.source().text().split("\\R", -1);
        if (saved.source().hash().equals(file.source().hash())) return List.of(id + ": editable file matches running revision #" + history.active());
        List<String> output = new ArrayList<>(); output.add("--- running saved revision #" + history.active()); output.add("+++ scripts/" + display(file.fileName()) + " (editable, not active)");
        int start = 0; while (start < oldLines.length && start < newLines.length && oldLines[start].equals(newLines[start])) start++;
        int oldEnd = oldLines.length, newEnd = newLines.length;
        while (oldEnd > start && newEnd > start && oldLines[oldEnd - 1].equals(newLines[newEnd - 1])) { oldEnd--; newEnd--; }
        if (start == oldEnd && start == newEnd) { output.add("Only encoding/line-ending bytes differ."); return output; }
        output.add("@@ old line " + (start + 1) + ", editable line " + (start + 1) + " @@");
        for (int i = start; i < oldEnd && output.size() < 14; i++) output.add("- " + (i + 1) + ": " + display(oldLines[i]));
        for (int i = start; i < newEnd && output.size() < 26; i++) output.add("+ " + (i + 1) + ": " + display(newLines[i]));
        if (oldEnd - start > 11 || newEnd - start > 12) output.add("Diff shortened for chat. The full editable file remains in scripts/.");
        return output;
    }
    public static String display(String value) {
        String cleaned = value.replaceAll("[\\p{Cntrl}\\u00a7]", " ");
        return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 237) + "...";
    }
}
