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
    private volatile ScriptCapabilityPolicy scriptPolicy = ScriptCapabilityPolicy.messageOnly();
    private volatile GameplayRuntime gameplay;
    private AuditLog audit;
    private PairedStatusBridge bridge;
    private volatile CloudRelayConnector cloud;
    private volatile RemoteScripts remote;
    private volatile SharedRelayConnector shared;
    private ConnectionStore connections;
    private final Map<String, RemoteScripts> connectionServices = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        running = true; busy.set(true);
        featureCommands = new FeatureCommands(getServer().getCommandMap(), registry, () -> ready, this::runFeature);
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
                try { scriptPolicy = new ScriptCapabilityPolicy(data.resolve("script-capabilities.json"), executionCapabilities()); }
                catch (IOException failure) { getLogger().warning("Gameplay capability policy unavailable; message-only scripts remain enabled. Repair script-capabilities.json from an owner backup."); }
                gameplay = onMain(() -> new GameplayRuntime(this, registry, io, new RemoteScripts.Main() {
                    public <T> T call(Callable<T> action) throws Exception { return onMain(action); }
                }, () -> scriptPolicy, () -> ready, data.resolve("player-state")));
                manager = new FeatureManager(new ScriptStore(data.resolve("scripts")), new RevisionStore(data.resolve("storage"), settings.retainVersions()), feature -> scriptPolicy.validate(feature));
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
        if (gameplay != null) gameplay.close();
        if (featureCommands != null) featureCommands.close();
        connectionServices.values().forEach(RemoteScripts::revoke); connectionServices.clear();
        if (shared != null) { shared.close(); shared = null; }
        revokeRemote();
        if (bridge != null) { bridge.close(); bridge = null; }
        getServer().getScheduler().cancelTasks(this); io.shutdownNow();
        if (gameplay != null) gameplay.closeStorageAfterShutdown();
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
    private static Set<String> executionCapabilities() {
        Set<String> capabilities = new TreeSet<>(Set.of(ScriptCapabilityPolicy.MESSAGE));
        StandardCallables.create().descriptors().forEach(call -> capabilities.addAll(call.requiredCapabilities()));
        return Set.copyOf(capabilities);
    }
    private boolean runFeature(String id, List<String> arguments, CommandSender sender) {
        if (!ready) return false;
        CompiledFeature feature = registry.snapshot().features().get(id);
        if (feature == null) return false;
        Feature.Command command = feature.definition().command();
        if (!sender.hasPermission(command.permission()) || command.sender().equals("player") && !(sender instanceof Player)) return false;
        scriptPolicy.validate(feature);
        if (feature.definition().requires().stream().anyMatch(capability -> !capability.equals(ScriptCapabilityPolicy.MESSAGE))) {
            if (!(sender instanceof Player player)) throw new ValidationException("$", "PLAYER_REQUIRED: this gameplay program needs the invoking player");
            if (busy.get()) throw new ValidationException("$", "RUNTIME_BUSY: wait for the pending reload or inspection");
            if (gameplay == null) throw new ValidationException("$", "GAMEPLAY_NOT_READY");
            gameplay.start(feature, player, arguments); return true;
        }
        return registry.run(id, arguments, sender instanceof Player, sender::hasPermission, text -> say(sender, text));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) { say(sender, "/ais <operation> [feature]; /ais run <feature> [arguments...]"); return true; }
        String op = args[0].toLowerCase(Locale.ROOT), selected = args.length >= 2 ? args[1] : null;
        if ((!op.equals("run") && args.length > (op.equals("permit") ? 3 : 2)) || (op.equals("run") && args.length > InvocationArguments.MAX_COMMAND_LENGTH + 2)) {
            say(sender, "Use /ais run <feature> [arguments], ais permit <feature> <comma-separated-capabilities>; other operations accept at most one value."); return true;
        }
        if (Set.of("permit", "restrict", "capabilities").contains(op)) {
            if (sender != getServer().getConsoleSender()) { say(sender, "CAPABILITIES_CONSOLE_ONLY"); return true; }
            if (!ready || selected == null) { say(sender, "Use ais capabilities <feature>, ais permit <feature> <comma-separated-capabilities>, or ais restrict <feature> after startup"); return true; }
            try { FeatureParser.requireId(selected); }
            catch (ValidationException failure) { say(sender, safeError(failure)); return true; }
            if (op.equals("capabilities")) {
                say(sender, selected + " allowed execution capabilities: " + new TreeSet<>(scriptPolicy.allowed(selected))); return true;
            }
            Set<String> approved = new TreeSet<>(Set.of(ScriptCapabilityPolicy.MESSAGE));
            if (op.equals("permit")) {
                if (args.length != 3) { say(sender, "Use ais permit <feature> <comma-separated-capabilities>; known: " + new TreeSet<>(executionCapabilities())); return true; }
                approved.addAll(Arrays.asList(args[2].split(",", -1)));
                if (!executionCapabilities().containsAll(approved)) { say(sender, "Unknown capability; known: " + new TreeSet<>(executionCapabilities())); return true; }
            }
            if (!busy.compareAndSet(false, true)) { say(sender, "BUSY"); return true; }
            String id = selected;
            try { io.execute(() -> {
                try {
                    scriptPolicy.grant(id, approved);
                    try { audit.append(UUID.randomUUID().toString(), "local:console", "capabilities", "SAVED", registry.snapshot().generation(), Map.of()); }
                    catch (IOException failure) { getLogger().warning("Capability grant saved; audit append unavailable."); }
                    onMain(() -> {
                        var before = registry.snapshot(); Map<String, CompiledFeature> permitted = new TreeMap<>(before.features());
                        permitted.entrySet().removeIf(entry -> !scriptPolicy.allowed(entry.getKey()).containsAll(entry.getValue().definition().requires()));
                        if (!permitted.equals(before.features())) featureCommands.activate(before.generation(), permitted);
                        if (gameplay != null) gameplay.reconcile();
                        say(sender, "CAPABILITIES_SAVED " + id + ": " + approved + "; run ais reload " + id + " to activate an approved edit"); return null;
                    });
                } catch (Exception failure) {
                    try { reply(sender, List.of("CAPABILITY_UPDATE_FAILED: inspect ais capabilities " + id + " and owner storage before retrying")); }
                    catch (Exception ignored) { }
                } finally { busy.set(false); }
            }); } catch (RejectedExecutionException failure) { busy.set(false); say(sender, "BUSY"); }
            return true;
        }
        if (Set.of("connect", "connections", "disconnect").contains(op)) {
            if (sender != getServer().getConsoleSender()) { say(sender, "CONNECTIONS_CONSOLE_ONLY"); return true; }
            if (!ready || shared == null || connections == null) { say(sender, "CONNECTIONS_NOT_READY"); return true; }
            if (op.equals("connect")) {
                try {
                    Set<String> targets = selected == null ? Set.of() : Set.copyOf(Arrays.asList(selected.split(",", -1)));
                    if (targets.size() > 128) throw new IllegalArgumentException(); targets.forEach(FeatureParser::requireId);
                    List<Map<String, String>> players = getServer().getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName)).limit(128)
                        .map(player -> Map.of("uuid", player.getUniqueId().toString(), "name", player.getName())).toList();
                    shared.enroll(targets, executionCapabilities(), players); say(sender, "Creating a private browser approval link; no relay configuration is required.");
                } catch (RuntimeException invalid) { say(sender, "Use /ais connect [feature-id[,feature-id]]; feature IDs must be exact."); }
            } else if (op.equals("connections")) {
                if (selected != null) { say(sender, "Use /ais connections"); return true; }
                say(sender, "Named connections (revoked entries stay revoked across restart):");
                connections.state().grants().values().stream().sorted(Comparator.comparing(ConnectionStore.Grant::name)).forEach(grant ->
                    say(sender, grant.name() + " | " + grant.connectionId() + " | " + (grant.revoked() ? "REVOKED" : "APPROVED") + " | " + (grant.allScripts() ? "ALL NATIVE .aether SCRIPTS (including new IDs)" : grant.allowedScriptIds().stream().sorted().toList())
                        + " | capabilities=" + new TreeSet<>(grant.capabilities()) + " | testPlayer=" + (grant.testPlayerId().isEmpty() ? "NONE" : grant.testPlayerId())));
                if (connections.state().grants().isEmpty()) say(sender, "No approved connections. Use /ais connect.");
            } else {
                String id = null;
                if (selected != null) {
                    String choice = selected;
                    var matches = connections.state().grants().values().stream().filter(g -> g.connectionId().equals(choice) || g.name().equalsIgnoreCase(choice)).toList();
                    if (matches.size() != 1) { say(sender, "Select an unambiguous connection ID from /ais connections, or use /ais disconnect to revoke all."); return true; }
                    id = matches.getFirst().connectionId();
                }
                if (id == null) {
                    connectionServices.forEach((key, service) -> { service.revoke(); if (gameplay != null) gameplay.retireTests(key); }); connectionServices.clear();
                } else { var service = connectionServices.remove(id); if (service != null) service.revoke(); if (gameplay != null) gameplay.retireTests(id); }
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
                if (!ready || selected == null || !runFeature(selected, invocation, sender))
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
            Registry.Snapshot replacement = featureCommands.activate(before.generation(), change.target());
            if (gameplay != null) gameplay.reconcile(); return replacement;
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
                if (gameplay != null) gameplay.reconcile();
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
            var previous = connectionServices.remove(grant.connectionId()); if (previous != null) previous.revoke();
            if (gameplay != null) gameplay.retireTests(grant.connectionId()); return;
        }
        connectionServices.computeIfAbsent(grant.connectionId(), id -> {
            FeatureManager scoped = new FeatureManager(manager.scripts(), manager.store(), feature -> {
                CompiledFeature active = registry.snapshot().features().get(feature.definition().id());
                // Unchanged neighbors retain their local authority; one app cannot claim them.
                if (active != null && active.sourceHash().equals(feature.sourceHash())) scriptPolicy.validate(feature);
                else authorizeConnection(grant, feature);
            });
            return new RemoteScripts(scoped, registry, grant.allowedScriptIds(), grant.allScripts(), new RemoteScripts.Main() {
                public <T> T call(Callable<T> action) throws Exception { return onMain(action); }
            }, (before, change, authorized) -> applyConnection(grant, before, change, authorized), new RemoteScripts.NativeAccess() {
                public void authorize(CompiledFeature feature) { authorizeConnection(grant, feature); }
                public boolean testPlayerApproved() { return !grant.testPlayerId().isEmpty(); }
                public Map<String, Object> start(CompiledFeature feature, List<String> arguments, List<String> expected) {
                    authorizeConnection(grant, feature);
                    if (grant.testPlayerId().isEmpty()) throw new SecurityException("PLAYER_APPROVAL_REQUIRED: join the server and use ais connect to approve a test player");
                    GameplayRuntime runtime = gameplay; if (runtime == null) throw new SecurityException("RUNTIME_NOT_READY");
                    return runtime.startTest(id, feature, UUID.fromString(grant.testPlayerId()), arguments, expected, () -> connectionAlive(grant));
                }
                public Map<String, Object> status(String featureId, String testId) {
                    if (!connectionAlive(grant) || gameplay == null) throw new SecurityException("CONNECTION_REVOKED");
                    return gameplay.testStatus(id, featureId, testId);
                }
                public Map<String, Object> describe() {
                    return Map.of("enabled", true, "capabilities", grant.capabilities().stream().sorted().toList(), "testPlayerId", grant.testPlayerId(),
                        "testMode", "Real player, asynchronous job; poll aether_scripts_test_status for actual messages, errors and effect receipts",
                        "localOwnerCeilings", "Explicit ais permit/restrict ceilings override connection permissions");
                }
            });
        });
    }
    private boolean connectionAlive(ConnectionStore.Grant grant) {
        return running && ready && shared != null && !shared.blocked(grant.connectionId()) && connections != null
            && grant.equals(connections.state().grants().get(grant.connectionId())) && !grant.revoked();
    }
    private void authorizeConnection(ConnectionStore.Grant grant, CompiledFeature feature) {
        if (!connectionAlive(grant)) throw new SecurityException("CONNECTION_REVOKED");
        if (!grant.allScripts() && !grant.allowedScriptIds().contains(feature.definition().id())) throw new SecurityException("TARGET_DENIED");
        scriptPolicy.validateConnection(feature, grant.capabilities());
    }
    private void applyConnection(ConnectionStore.Grant grant, Registry.Snapshot before, FeatureManager.Change change, Runnable authorized) throws Exception {
        List<CompiledFeature> approved = new ArrayList<>();
        try {
            for (CompiledFeature feature : change.target().values()) {
                CompiledFeature previous = before.features().get(feature.definition().id());
                if (previous != null && previous.sourceHash().equals(feature.sourceHash())) continue;
                authorized.run(); authorizeConnection(grant, feature);
                if (scriptPolicy.approveConnection(feature, grant.capabilities())) approved.add(feature);
            }
            apply(null, "connection:" + grant.connectionId(), "remote-apply", UUID.randomUUID().toString(), before, change, authorized);
        } catch (Exception failure) {
            for (CompiledFeature feature : approved) {
                CompiledFeature active = registry.snapshot().features().get(feature.definition().id());
                if (active != null && active.sourceHash().equals(feature.sourceHash())) continue;
                try { scriptPolicy.removeGrant(feature.definition().id()); }
                catch (IOException uncertain) { ready = false; startupError = "CAPABILITY_POLICY_QUARANTINED"; getLogger().severe("Capability approval compensation failed; restore the owner policy backup before restarting."); }
            }
            throw failure;
        }
    }
    private Map<String, Object> connectionRequest(ConnectionStore.Grant grant, String operation, Map<String, Object> arguments) throws Exception {
        String id = grant.connectionId(); var service = connectionServices.get(id);
        if (!running || !ready || shared == null || shared.blocked(id) || service == null || grant.revoked()) throw new SecurityException("CONNECTION_REVOKED");
        if (!busy.compareAndSet(false, true)) throw new IllegalStateException("BUSY");
        CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        try { io.execute(() -> {
            try {
                if (shared == null || shared.blocked(id) || connectionServices.get(id) != service) throw new SecurityException("CONNECTION_REVOKED");
                if (operation.equals("scripts.test")) audit.append(UUID.randomUUID().toString(), "connection:" + id, operation, "TEST_INTENT", registry.snapshot().generation(), Map.of());
                var value = service.execute(operation, arguments);
                try { audit.append(UUID.randomUUID().toString(), "connection:" + id, operation, "SUCCEEDED", registry.snapshot().generation(), Map.of()); }
                catch (IOException failedAudit) {
                    // An accepted test may already have real effects. Preserve its job ID so the
                    // client polls the same operation rather than blindly retrying execution.
                    if (!operation.equals("scripts.test") || !value.containsKey("testId")) throw failedAudit;
                    getLogger().warning("Accepted MCP test audit completion could not be saved; the client retains its test ID. Inspect owner audit storage.");
                }
                result.complete(value);
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
