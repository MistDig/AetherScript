package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AetherScriptPlugin extends JavaPlugin implements CommandExecutor {
    private final Registry registry = new Registry();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "AetherScript-IO"); t.setDaemon(true); return t; });
    private volatile boolean running, ready;
    private volatile String startupError = "";
    private FeatureManager manager;
    private AuditLog audit;
    private PairedStatusBridge bridge;

    @Override public void onEnable() {
        running = true; busy.set(true);
        Objects.requireNonNull(getCommand("ais")).setExecutor(this);
        Objects.requireNonNull(getCommand("ais")).setTabCompleter((s,c,a,args) -> List.of());
        io.execute(() -> {
            try {
                Path data = getDataFolder().toPath().toAbsolutePath().normalize();
                Files.createDirectories(data);
                if (!data.toRealPath().equals(data)) throw new IOException("Unsafe plugin data directory");
                Files.deleteIfExists(data.resolve("connection-details.json"));
                for (String directory : List.of("scripts", "audit")) {
                    Path path = data.resolve(directory); Files.createDirectories(path);
                    if (!path.toRealPath().equals(path) || Files.isSymbolicLink(path)) throw new IOException("Unsafe " + directory + " directory");
                }
                copyDefault("config.yml", data.resolve("config.yml"));
                if (!Files.exists(data.resolve("storage"), LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(data.resolve("scripts/hello-ai.json"), LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(data.resolve("scripts/hello-ai.aether"), LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(data.resolve("scripts/hello-ai.yaml"), LinkOption.NOFOLLOW_LINKS))
                    copyDefault("hello-ai.yml", data.resolve("scripts/hello-ai.yml"));
                EngineSettings settings = EngineSettings.load(data.resolve("config.yml"));
                audit = new AuditLog(data.resolve("audit/local.jsonl"));
                manager = new FeatureManager(new ScriptStore(data.resolve("scripts")), new RevisionStore(data.resolve("storage"), settings.retainVersions()));
                var startup = manager.startup();
                apply(null, "server", "startup", UUID.randomUUID().toString(), registry.snapshot(), startup);
                onMain(() -> {
                    ready = true;
                    getLogger().info("Ready. Restored " + registry.snapshot().features().size() + " feature(s); saved-version limit=" + settings.retainVersions() + "; AI deployment absent.");
                    return null;
                });
            } catch (Exception e) {
                startupError = safeError(e);
                getLogger().severe("Startup blocked: " + startupError);
            } finally { busy.set(false); }
        });
    }
    private void copyDefault(String resource, Path destination) throws IOException {
        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            try (InputStream input = getResource(resource)) { Files.copy(Objects.requireNonNull(input), destination); }
    }
    @Override public void onDisable() {
        running = false; ready = false;
        if (bridge != null) { bridge.close(); bridge = null; }
        getServer().getScheduler().cancelTasks(this); io.shutdownNow();
        registry.replace(registry.snapshot().generation(), Map.of());
    }
    /** The worker waits for a main-thread boundary; the main thread never waits for disk I/O. */
    private <T> T onMain(Callable<T> action) throws Exception {
        if (!running) throw new InterruptedException("Plugin stopping");
        CompletableFuture<T> completion = new CompletableFuture<>();
        getServer().getScheduler().runTask(this, () -> {
            try {
                if (!running) throw new IllegalStateException("Plugin stopping");
                completion.complete(action.call());
            } catch (Throwable e) { completion.completeExceptionally(e); }
        });
        try { return completion.get(); }
        catch (ExecutionException e) { if (e.getCause() instanceof Exception cause) throw cause; throw e; }
    }
    private void say(CommandSender sender, String text) { sender.sendMessage(Component.text(text)); }
    private void reply(CommandSender sender, List<String> lines) throws Exception {
        onMain(() -> {
            int limit = sender instanceof Player ? 30 : 400;
            lines.stream().limit(limit).forEach(line -> { if (sender == null) getLogger().info(line); else say(sender, line); });
            if (lines.size() > limit && sender != null) say(sender, "More entries omitted. Inspect /ais status <feature> or use the server console.");
            return null;
        });
    }
    private String principal(CommandSender sender) { return sender instanceof Player p ? "player:" + p.getUniqueId() : "local:" + sender.getName(); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) { say(sender, "/ais <status|list|validate|reload|run|history|diff|rollback|pair|unpair> [feature]"); return true; }
        String op = args[0].toLowerCase(Locale.ROOT), selected = args.length == 2 ? args[1] : null;
        if (op.equals("pair") || op.equals("unpair")) {
            if (sender != getServer().getConsoleSender() || args.length != 1) { say(sender, "PAIRING_CONSOLE_ONLY: use the server console /ais " + op); return true; }
            if (op.equals("unpair")) {
                if (bridge != null) { bridge.close(); bridge = null; }
                Path details = getDataFolder().toPath().resolve("connection-details.json");
                try { io.execute(() -> { try { Files.deleteIfExists(details); } catch (IOException ignored) { } }); }
                catch (RejectedExecutionException ignored) { }
                say(sender, "Pairing revoked; status bridge stopped."); return true;
            }
            if (!ready) { say(sender, "NOT_READY: pairing requires completed startup recovery"); return true; }
            try {
                if (bridge == null) bridge = new PairedStatusBridge(() -> {
                    Registry.Snapshot snapshot = registry.snapshot();
                    return Map.of("version", BuildInfo.VERSION, "ready", ready, "busy", busy.get(),
                        "generation", snapshot.generation(), "features", snapshot.features().size(),
                        "aiDeployment", "ABSENT", "recovery", "ENABLED");
                });
                String code = bridge.pair();
                say(sender, "AetherScript pairing endpoint: " + bridge.endpoint());
                say(sender, "Pairing code (expires in 5 minutes, single use): " + code);
                say(sender, "Scope: status only; session expires in 1 hour. Use an SSH loopback tunnel for remote access. /ais unpair revokes access.");
                String endpoint = bridge.endpoint();
                Path details = getDataFolder().toPath().resolve("connection-details.json");
                try { io.execute(() -> {
                    try { Files.writeString(details,
                        "{\"endpoint\":\"" + endpoint + "\",\"scope\":\"status\",\"transport\":\"loopback-http\"}\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); }
                    catch (IOException e) { getLogger().warning("Could not write non-secret connection details"); }
                }); } catch (RejectedExecutionException ignored) { say(sender, "Connection details file pending; use endpoint above."); }
            } catch (IOException e) { say(sender, "PAIRING_UNAVAILABLE"); }
            return true;
        }
        if (op.equals("run")) {
            if (!ready || selected == null || !registry.run(selected, sender instanceof Player, sender::hasPermission, text -> say(sender, text)))
                say(sender, "Feature unavailable or permission denied.");
            return true;
        }
        if (!sender.hasPermission("aetherscript.admin")) {
            say(sender, "PERMISSION_DENIED");
            if (ready && !busy.get()) {
                String who = principal(sender);
                try { io.execute(() -> { try { audit.append(UUID.randomUUID().toString(), who, "admin", "DENIED", registry.snapshot().generation(), Map.of()); }
                    catch (IOException ignored) { getLogger().warning("Denied-request audit unavailable"); } }); }
                catch (RejectedExecutionException ignored) { }
            }
            return true;
        }
        if (op.equals("status") && selected == null) {
            say(sender, "AetherScript " + BuildInfo.VERSION + "; " + getServer().getVersion() + "; ready=" + ready + "; generation=" + registry.snapshot().generation()
                + "; features=" + registry.snapshot().features().size() + "; busy=" + busy.get() + "; AI deployment=ABSENT; recovery=ENABLED"
                + (startupError.isEmpty() ? "" : "; " + startupError));
            return true;
        }
        if (!Set.of("status", "list", "validate", "reload", "history", "diff", "rollback").contains(op)) { say(sender, "Unknown operation."); return true; }
        if (Set.of("history", "diff", "rollback").contains(op) && selected == null) { say(sender, "/ais " + op + " <feature>"); return true; }
        if (!ready) { say(sender, "NOT_READY: " + (startupError.isEmpty() ? "startup recovery in progress" : startupError)); return true; }
        if (!busy.compareAndSet(false, true)) { say(sender, "BUSY: another inspection/reload is pending"); return true; }
        Registry.Snapshot before = registry.snapshot(); String who = principal(sender), request = UUID.randomUUID().toString();
        try {
            io.execute(() -> {
                try {
                    switch (op) {
                        case "status" -> reply(sender, manager.status(selected, before));
                        case "list" -> reply(sender, manager.list(before));
                        case "history" -> reply(sender, manager.history(selected));
                        case "diff" -> reply(sender, manager.diff(selected));
                        case "validate" -> {
                            int count = manager.validate(selected, before);
                            audit.append(request, who, op, "VALIDATED", before.generation(), before.features());
                            reply(sender, List.of("VALIDATED " + count + " feature(s); active generation unchanged"));
                        }
                        case "reload", "rollback" -> apply(sender, who, op, request, before,
                            op.equals("reload") ? manager.reload(selected, before) : manager.rollback(selected, before));
                    }
                } catch (Exception e) {
                    try { audit.append(request, who, op, "REJECTED", registry.snapshot().generation(), Map.of()); }
                    catch (IOException ignored) { getLogger().warning("Rejected-operation audit unavailable"); }
                    List<String> errors = new ArrayList<>();
                    if (e instanceof FeatureManager.Rejected rejected) errors.addAll(rejected.diagnostics()); else errors.add(safeError(e));
                    errors.add(registry.snapshot() == before ? "Rejected; active generation unchanged. Last working scripts remain active." : "Operation failed; inspect /ais status before retrying.");
                    try { reply(sender, errors); } catch (Exception ignored) { }
                } finally { busy.set(false); }
            });
        } catch (RejectedExecutionException e) { busy.set(false); say(sender, "BUSY"); }
        return true;
    }
    private void apply(CommandSender sender, String who, String operation, String request, Registry.Snapshot before, FeatureManager.Change change) throws Exception {
        if (!change.durable().changed() && before.features().equals(change.target())) {
            audit.append(request, who, operation, "UNCHANGED", before.generation(), before.features());
            List<String> messages = new ArrayList<>(change.messages());
            messages.add("UNCHANGED: no new revision; generation=" + before.generation());
            reply(sender, messages); return;
        }
        audit.append(request, who, operation, "INTENT", before.generation(), change.target());
        Registry.Snapshot activated = onMain(() -> {
            if (sender != null && !sender.hasPermission("aetherscript.admin")) throw new SecurityException("PERMISSION_DENIED: permission changed before activation");
            return registry.replace(before.generation(), change.target());
        });
        try {
            manager.store().commit(change.durable());
            audit.append(request, who, operation, "ACTIVE", activated.generation(), change.target());
        } catch (Exception failure) {
            boolean restored = true;
            try { manager.store().restore(change.durable().before()); }
            catch (Exception restoreFailure) { restored = false; getLogger().severe("Storage recovery uncertain; operations quarantined. Restore an owner backup before restarting."); }
            boolean safe = restored;
            onMain(() -> {
                registry.replace(activated.generation(), safe ? before.features() : Map.of());
                if (!safe) { ready = false; startupError = "STORAGE_QUARANTINED"; } return null;
            });
            throw new IOException(safe ? "SAVE_FAILED: previous working registry restored as a new generation; editable files untouched" : "STORAGE_QUARANTINED: restore owner backup", failure);
        }
        List<String> messages = new ArrayList<>(change.messages());
        messages.add("ACTIVE generation=" + activated.generation() + " features=" + change.target().size() + "; saved successfully");
        reply(sender, messages);
        try { manager.store().prune(); }
        catch (IOException e) { getLogger().warning("Old revision cleanup incomplete; active saved versions remain protected."); }
    }
    private static String safeError(Exception e) {
        if (e instanceof ValidationException || e instanceof SecurityException || e instanceof IOException)
            return FeatureManager.display(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        return "OPERATION_FAILED: " + e.getClass().getSimpleName();
    }
}
