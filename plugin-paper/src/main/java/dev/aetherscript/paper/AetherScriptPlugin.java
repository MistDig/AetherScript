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
    private ScriptStore scripts;
    private AuditLog audit;

    @Override public void onEnable() {
        running = true;
        Objects.requireNonNull(getCommand("ais")).setExecutor(this);
        Objects.requireNonNull(getCommand("ais")).setTabCompleter((s,c,a,args) -> List.of());
        io.execute(() -> {
            try {
                Path data = getDataFolder().toPath().toAbsolutePath().normalize();
                Files.createDirectories(data.resolve("scripts"));
                Files.createDirectories(data.resolve("audit"));
                if (!data.toRealPath().equals(data) || !data.resolve("audit").toRealPath().equals(data.resolve("audit"))) throw new IOException("Unsafe data directory");
                scripts = new ScriptStore(data.resolve("scripts"));
                audit = new AuditLog(data.resolve("audit/local.jsonl"));
                Path example = data.resolve("scripts/hello-ai.yml");
                if (!Files.exists(example, LinkOption.NOFOLLOW_LINKS)) {
                    try (InputStream input = getResource("hello-ai.yml")) { Files.copy(Objects.requireNonNull(input), example); }
                }
                audit.append(UUID.randomUUID().toString(), "server", "startup", "EMPTY_REGISTRY", 0, Map.of());
                ready = true;
                getLogger().info("Ready. AI deployment absent. Use /ais reload to activate owner-reviewed local scripts.");
            } catch (Exception e) { getLogger().severe("Initialization failed; mutations disabled: " + e.getClass().getSimpleName()); }
        });
    }
    @Override public void onDisable() {
        running = false; ready = false;
        getServer().getScheduler().cancelTasks(this);
        io.shutdownNow();
        registry.replace(registry.snapshot().generation(), Map.of());
    }
    private void main(Runnable action) {
        if (!running) return;
        try { getServer().getScheduler().runTask(this, () -> { if (running) action.run(); }); }
        catch (org.bukkit.plugin.IllegalPluginAccessException ignored) { /* disabling */ }
    }
    private void say(CommandSender sender, String text) { sender.sendMessage(Component.text(text)); }
    private String principal(CommandSender sender) {
        return sender instanceof Player p ? "player:" + p.getUniqueId() : "local:" + sender.getName();
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) { say(sender, "/ais <status|list|validate|reload|run> [feature]"); return true; }
        String op = args[0].toLowerCase(Locale.ROOT);
        if (op.equals("run")) {
            if (args.length != 2 || !registry.run(args[1], sender instanceof Player, sender::hasPermission, text -> say(sender, text))) {
                say(sender, "Feature unavailable or permission denied.");
            }
            return true;
        }
        if (!sender.hasPermission("aetherscript.admin")) {
            say(sender, "PERMISSION_DENIED");
            if (ready && !busy.get()) {
                String who = principal(sender);
                try { io.execute(() -> { try { audit.append(UUID.randomUUID().toString(), who, "admin", "DENIED", registry.snapshot().generation(), Map.of()); } catch (IOException ignored) { getLogger().warning("Denied-request audit unavailable"); } }); }
                catch (RejectedExecutionException ignored) { /* bounded denial queue */ }
            }
            return true;
        }
        switch (op) {
            case "status" -> say(sender, "AetherScript " + BuildInfo.VERSION + "; " + getServer().getVersion() + "; ready=" + ready + "; generation=" + registry.snapshot().generation() + "; features=" + registry.snapshot().features().size() + "; busy=" + busy.get() + "; AI deployment=ABSENT; restart recovery=NOT IMPLEMENTED");
            case "list" -> say(sender, "Active: " + String.join(", ", new TreeSet<>(registry.snapshot().features().keySet())));
            case "reload", "validate" -> process(sender, op, args.length == 2 ? args[1] : null);
            default -> say(sender, "Unknown operation. Use status, list, validate, reload, run.");
        }
        return true;
    }
    private void process(CommandSender sender, String op, String selected) {
        if (!ready) { say(sender, "NOT_READY"); return; }
        if (!busy.compareAndSet(false, true)) { say(sender, "BUSY: another validation/reload is pending"); return; }
        String who = principal(sender), request = UUID.randomUUID().toString();
        Registry.Snapshot before = registry.snapshot();
        try {
            io.execute(() -> {
                try {
                    Map<String, CompiledFeature> loaded = scripts.read(selected);
                    Map<String, CompiledFeature> target = new TreeMap<>(selected == null ? Map.of() : before.features());
                    target.putAll(loaded);
                    // Validate whole candidate, including collisions, without touching the active registry.
                    new Registry().replace(0, target);
                    audit.append(request, who, op, op.equals("reload") ? "INTENT" : "VALIDATED", before.generation(), target);
                    main(() -> {
                        if (!sender.hasPermission("aetherscript.admin")) { busy.set(false); say(sender, "PERMISSION_DENIED: permission changed"); return; }
                        if (op.equals("validate")) { busy.set(false); say(sender, "VALIDATED " + loaded.size() + " feature(s); active generation unchanged"); return; }
                        try {
                            Registry.Snapshot activated = registry.replace(before.generation(), target);
                            io.execute(() -> {
                                try {
                                    audit.append(request, who, op, "ACTIVE", activated.generation(), target);
                                    main(() -> { busy.set(false); say(sender, "ACTIVE generation=" + activated.generation() + " features=" + target.size()); });
                                } catch (IOException e) {
                                    main(() -> {
                                        registry.replace(activated.generation(), before.features());
                                        busy.set(false); say(sender, "AUDIT_FAILED: previous registry restored as a new generation");
                                    });
                                }
                            });
                        } catch (RuntimeException e) { busy.set(false); say(sender, "RELOAD_FAILED: active registry unchanged"); }
                    });
                } catch (Exception e) {
                    try { audit.append(request, who, op, "REJECTED", before.generation(), Map.of()); } catch (IOException ignored) { /* mutation has not happened */ }
                    String diagnostic = e instanceof ValidationException ? e.getMessage() : "SOURCE_OR_AUDIT_IO_FAILED";
                    main(() -> { busy.set(false); say(sender, diagnostic + "; active generation unchanged"); });
                }
            });
        } catch (RejectedExecutionException e) { busy.set(false); say(sender, "BUSY"); }
    }
}
