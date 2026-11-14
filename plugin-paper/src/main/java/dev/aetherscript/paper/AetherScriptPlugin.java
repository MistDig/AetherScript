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
    private FeatureCommands featureCommands;
    private AuditLog audit;
    private PairedStatusBridge bridge;
    private volatile CloudRelayConnector cloud;
    private volatile RemoteScripts remote;
    private volatile SharedRelayConnector shared;
    private ConnectionStore connections;
    private final Map<String, RemoteScripts> connectionServices = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        running = true; busy.set(true);
        featureCommands = new FeatureCommands(getServer().getCommandMap(), registry, () -> ready);
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
                    getLogger().info("Ready. Restored " + registry.snapshot().features().size() + " feature(s); saved-version limit=" + settings.retainVersions() + "; remote manage requires explicit console grant.");
                    return null;
                });
                try {
                    connections = new ConnectionStore(data.resolve("connections.json"));
                    Path serverDirectory = data.getParent() == null ? null : data.getParent().getParent();
                    String serverName = serverDirectory == null || serverDirectory.getFileName() == null ? "Minecraft server" : serverDirectory.getFileName().toString();
                    shared = new SharedRelayConnector(connections, this::connectionRequest, this::connectionGrant, this::connectionNotice, serverName);
                    for (var grant : connections.state().grants().values()) if (!grant.revoked()) connectionGrant(grant);
                    shared.start();
                } catch (Exception connectionFailure) {
                    getLogger().warning("Connections unavailable: inspect protected connections.json; scripts remain available locally.");
                }
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
        if (featureCommands != null) featureCommands.close();
        connectionServices.values().forEach(RemoteScripts::revoke); connectionServices.clear();
        if (shared != null) { shared.close(); shared = null; }
        revokeRemote();
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
        if (args.length < 1) { say(sender, "/ais <operation> [feature]; /ais run <feature> [arguments...]"); return true; }
        String op = args[0].toLowerCase(Locale.ROOT), selected = args.length >= 2 ? args[1] : null;
        if ((!op.equals("run") && args.length > 2) || (op.equals("run") && args.length > InvocationArguments.MAX_COMMAND_LENGTH + 2)) {
            say(sender, "Use /ais run <feature> with at most 16 arguments; other operations accept at most one value."); return true;
        }
        if (Set.of("connect", "connections", "disconnect").contains(op)) {
            if (sender != getServer().getConsoleSender()) { say(sender, "CONNECTIONS_CONSOLE_ONLY"); return true; }
            if (!ready || shared == null || connections == null) { say(sender, "CONNECTIONS_NOT_READY"); return true; }
            if (op.equals("connect")) {
                try {
                    Set<String> targets = selected == null ? Set.of() : Set.copyOf(Arrays.asList(selected.split(",", -1)));
                    if (targets.size() > 128) throw new IllegalArgumentException(); targets.forEach(FeatureParser::requireId);
                    shared.enroll(targets); say(sender, "Creating a private browser approval link; no relay configuration is required.");
                } catch (RuntimeException invalid) { say(sender, "Use /ais connect [feature-id[,feature-id]]; feature IDs must be exact."); }
            } else if (op.equals("connections")) {
                if (selected != null) { say(sender, "Use /ais connections"); return true; }
                say(sender, "Named connections (revoked entries stay revoked across restart):");
                connections.state().grants().values().stream().sorted(Comparator.comparing(ConnectionStore.Grant::name)).forEach(grant ->
                    say(sender, grant.name() + " | " + grant.connectionId() + " | " + (grant.revoked() ? "REVOKED" : "APPROVED") + " | " + (grant.allScripts() ? "ALL NATIVE .aether SCRIPTS (including new IDs)" : grant.allowedScriptIds().stream().sorted().toList())));
                if (connections.state().grants().isEmpty()) say(sender, "No approved connections. Use /ais connect.");
            } else {
                String id = null;
                if (selected != null) {
                    String choice = selected;
                    var matches = connections.state().grants().values().stream().filter(g -> g.connectionId().equals(choice) || g.name().equalsIgnoreCase(choice)).toList();
                    if (matches.size() != 1) { say(sender, "Select an unambiguous connection ID from /ais connections, or use /ais disconnect to revoke all."); return true; }
                    id = matches.getFirst().connectionId();
                }
                if (id == null) { connectionServices.values().forEach(RemoteScripts::revoke); connectionServices.clear(); }
                else { var service = connectionServices.remove(id); if (service != null) service.revoke(); }
                shared.disconnect(id);
                say(sender, "Access blocked immediately; waiting for durable revocation confirmation.");
            }
            return true;
        }
        if (op.equals("remote") || op.equals("remote-off")) {
            if (sender != getServer().getConsoleSender()) { say(sender, "REMOTE_CONSOLE_ONLY"); return true; }
            if (op.equals("remote-off")) { revokeRemote(); say(sender, "Remote manage grant revoked."); return true; }
            if (!ready || selected == null) { say(sender, "Use /ais remote <feature-id[,feature-id]> after startup. Requires owner relay.json."); return true; }
            try {
                Set<String> targets = Set.copyOf(Arrays.asList(selected.split(",", -1)));
                targets.forEach(FeatureParser::requireId);
                revokeRemote();
                RemoteScripts service = new RemoteScripts(manager, registry, targets, new RemoteScripts.Main() {
                    public <T> T call(Callable<T> action) throws Exception { return onMain(action); }
                }, (before, change, authorized) -> apply(null, "remote-agent", "remote-apply", UUID.randomUUID().toString(), before, change, authorized));
                remote = service;
                cloud = new CloudRelayConnector(getDataFolder().toPath().resolve("relay.json"), (operation, arguments) -> {
                    if (!running || !ready || remote != service || cloud == null || !cloud.live()) throw new SecurityException("REVOKED");
                    if (!busy.compareAndSet(false, true)) throw new IllegalStateException("BUSY");
                    CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
                    try { io.execute(() -> {
                        try {
                            if (remote != service || cloud == null || !cloud.live()) throw new SecurityException("REVOKED");
                            var value = service.execute(operation, arguments);
                            audit.append(UUID.randomUUID().toString(), "remote-agent", operation, "SUCCEEDED", registry.snapshot().generation(), Map.of());
                            result.complete(value);
                        } catch (Exception e) {
                            try { audit.append(UUID.randomUUID().toString(), "remote-agent", operation, "REJECTED", registry.snapshot().generation(), Map.of()); } catch (IOException ignored) { }
                            result.completeExceptionally(e);
                        } finally { busy.set(false); }
                    }); } catch (RejectedExecutionException e) { busy.set(false); throw e; }
                    try { return result.get(); }
                    catch (ExecutionException e) { if (e.getCause() instanceof Exception cause) throw cause; throw e; }
                }, text -> { if (running && remote == service) getServer().getScheduler().runTask(this, () -> { if (remote == service) say(getServer().getConsoleSender(), text); }); });
                cloud.start(); say(sender, "Remote manage grant enabled for " + targets + "; connecting outbound. /ais remote-off revokes.");
            } catch (Exception e) { revokeRemote(); say(sender, "REMOTE_UNAVAILABLE: check relay.json and feature IDs."); }
            return true;
        }
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
            try {
                List<String> invocation = InvocationArguments.fromCommandTokens(Arrays.copyOfRange(args, Math.min(2, args.length), args.length));
                if (!ready || selected == null || !registry.run(selected, invocation, sender instanceof Player, sender::hasPermission, text -> say(sender, text)))
                    say(sender, "Feature unavailable or permission denied.");
            } catch (ValidationException error) { say(sender, safeError(error)); }
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
                + "; features=" + registry.snapshot().features().size() + "; busy=" + busy.get() + "; remoteManage=" + ((cloud != null && cloud.live()) || !connectionServices.isEmpty()) + "; recovery=ENABLED"
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
        apply(sender, who, operation, request, before, change, () -> { });
    }
    private void apply(CommandSender sender, String who, String operation, String request, Registry.Snapshot before, FeatureManager.Change change, Runnable authorized) throws Exception {
        authorized.run();
        if (!change.durable().changed() && before.features().equals(change.target())) {
            audit.append(request, who, operation, "UNCHANGED", before.generation(), before.features());
            List<String> messages = new ArrayList<>(change.messages());
            messages.add("UNCHANGED: no new revision; generation=" + before.generation());
            reply(sender, messages); return;
        }
        audit.append(request, who, operation, "INTENT", before.generation(), change.target());
        Registry.Snapshot activated = onMain(() -> {
            authorized.run();
            if (sender != null && !sender.hasPermission("aetherscript.admin")) throw new SecurityException("PERMISSION_DENIED: permission changed before activation");
            return featureCommands.activate(before.generation(), change.target());
        });
        try {
            authorized.run();
            manager.store().commit(change.durable());
            audit.append(request, who, operation, "ACTIVE", activated.generation(), change.target());
        } catch (Exception failure) {
            boolean restored = true;
            try { manager.store().restore(change.durable().before()); }
            catch (Exception restoreFailure) { restored = false; getLogger().severe("Storage recovery uncertain; operations quarantined. Restore an owner backup before restarting."); }
            boolean safe = restored;
            onMain(() -> {
                featureCommands.activate(activated.generation(), safe ? before.features() : Map.of());
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
    private void revokeRemote() {
        RemoteScripts service = remote; remote = null; if (service != null) service.revoke();
        CloudRelayConnector connector = cloud; cloud = null; if (connector != null) connector.close();
    }
    private void connectionNotice(String text) {
        if (running) getServer().getScheduler().runTask(this, () -> { if (running) say(getServer().getConsoleSender(), text); });
    }
    private void connectionGrant(ConnectionStore.Grant grant) {
        if (grant.revoked() || shared == null || shared.blocked(grant.connectionId())) {
            var previous = connectionServices.remove(grant.connectionId()); if (previous != null) previous.revoke(); return;
        }
        connectionServices.computeIfAbsent(grant.connectionId(), id -> new RemoteScripts(manager, registry, grant.allowedScriptIds(), grant.allScripts(), new RemoteScripts.Main() {
            public <T> T call(Callable<T> action) throws Exception { return onMain(action); }
        }, (before, change, authorized) -> apply(null, "connection:" + id, "remote-apply", UUID.randomUUID().toString(), before, change, authorized)));
    }
    private Map<String, Object> connectionRequest(ConnectionStore.Grant grant, String operation, Map<String, Object> arguments) throws Exception {
        String id = grant.connectionId(); var service = connectionServices.get(id);
        if (!running || !ready || shared == null || shared.blocked(id) || service == null || grant.revoked()) throw new SecurityException("CONNECTION_REVOKED");
        if (!busy.compareAndSet(false, true)) throw new IllegalStateException("BUSY");
        CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        try { io.execute(() -> {
            try {
                if (shared == null || shared.blocked(id) || connectionServices.get(id) != service) throw new SecurityException("CONNECTION_REVOKED");
                var value = service.execute(operation, arguments);
                audit.append(UUID.randomUUID().toString(), "connection:" + id, operation, "SUCCEEDED", registry.snapshot().generation(), Map.of()); result.complete(value);
            } catch (Exception rejected) {
                try { audit.append(UUID.randomUUID().toString(), "connection:" + id, operation, "REJECTED", registry.snapshot().generation(), Map.of()); } catch (IOException ignored) { }
                result.completeExceptionally(rejected);
            } finally { busy.set(false); }
        }); } catch (RejectedExecutionException rejected) { busy.set(false); throw rejected; }
        try { return result.get(); }
        catch (ExecutionException failure) { if (failure.getCause() instanceof Exception cause) throw cause; throw failure; }
    }
    private static String safeError(Exception e) {
        if (e instanceof ValidationException || e instanceof SecurityException || e instanceof IOException)
            return FeatureManager.display(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        return "OPERATION_FAILED: " + e.getClass().getSimpleName();
    }
}
