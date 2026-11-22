package dev.aetherscript.paper;

import java.io.IOException;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Bounded command invocations. Only virtual threads wait; platform effects re-enter Paper. */
final class GameplayRuntime implements AutoCloseable, Listener {
    private static final int MAX_ACTIVE = 32;
    private static final long MAX_NANOS = TimeUnit.SECONDS.toNanos(120);
    private final JavaPlugin plugin;
    private final Registry registry;
    private final ExecutorService io;
    private final RemoteScripts.Main main;
    private final Supplier<ScriptCapabilityPolicy> policy;
    private final BooleanSupplier ready;
    private final Path stateDirectory;
    private final Semaphore admission = new Semaphore(MAX_ACTIVE);
    private final ExecutorService interpreters = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Run> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private final Map<String, TestJob> tests = new HashMap<>();
    private final ScheduledExecutorService testExpiry = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aetherscript-test-expiry"); thread.setDaemon(true); return thread;
    });
    private static final long TEST_NANOS = TimeUnit.SECONDS.toNanos(180);
    /** Capture limits use wire bytes, including JSON escaping; capture never gates real effects. */
    static final class CaptureBudget {
        private final int limit;
        private int used;
        private boolean truncated;
        CaptureBudget(int limit) { this.limit = limit; }
        synchronized boolean accept(Object value) {
            if (truncated) return false;
            try {
                int bytes = FeatureParser.JSON.writeValueAsBytes(value).length + 1; // Array separator.
                if (bytes > limit - used) { truncated = true; return false; }
                used += bytes; return true;
            } catch (java.io.IOException | RuntimeException failure) { truncated = true; return false; }
        }
        synchronized void truncate() { truncated = true; }
        synchronized boolean truncated() { return truncated; }
    }
    static String boundedError(String text) {
        // At most 2 KiB of encoded JSON, including control-character escaping and UTF-8.
        int codePoints = Math.min(text.codePointCount(0, text.length()), 2048);
        int low = 0, high = codePoints;
        String best = "";
        while (low <= high) {
            int count = (low + high) >>> 1;
            String candidate = text.substring(0, text.offsetByCodePoints(0, count));
            try {
                if (FeatureParser.JSON.writeValueAsBytes(candidate).length <= 2048) {
                    best = candidate; low = count + 1;
                } else high = count - 1;
            } catch (java.io.IOException | RuntimeException failure) { return "RUNTIME_FAILED"; }
        }
        return best;
    }

    private final class TestJob {
        final String id = UUID.randomUUID().toString(), connection;
        final CompiledFeature feature;
        final List<String> expected;
        final BooleanSupplier alive;
        final long expires = System.nanoTime() + TEST_NANOS;
        final List<String> messages = new ArrayList<>();
        final CaptureBudget outputBudget = new CaptureBudget(12 * 1024);
        final CaptureBudget receiptBudget = new CaptureBudget(6 * 1024);
        final List<Map<String, Object>> receipts = new ArrayList<>();
        volatile Run run;
        String status = "pending", failure = "";
        int line, column;
        TestJob(String connection, CompiledFeature feature, List<String> expected, BooleanSupplier alive) {
            this.connection = connection; this.feature = feature; this.expected = List.copyOf(expected); this.alive = alive;
        }
        synchronized void finish(String state, Throwable error) {
            if (!status.equals("pending")) return;
            status = state;
            if (error != null) {
                failure = boundedError(error instanceof ValidationException ? error.getMessage() : "RUNTIME_FAILED");
                if (error instanceof ValidationException v) { line = v.line(); column = v.column(); }
            }
        }
        synchronized void message(String text) {
            if (messages.size() >= 128) outputBudget.truncate();
            else if (outputBudget.accept(text)) messages.add(text);
        }
        synchronized void receipt(Map<String, Object> value) {
            if (receipts.size() >= 128) receiptBudget.truncate();
            else if (receiptBudget.accept(value)) {
                try { receipts.add(Map.copyOf(value)); }
                catch (RuntimeException failure) { receiptBudget.truncate(); }
            }
        }
        synchronized boolean pending() { return status.equals("pending"); }
        synchronized Map<String, Object> view() {
            return Map.ofEntries(Map.entry("testId", id), Map.entry("id", feature.definition().id()),
                Map.entry("sourceHash", feature.sourceHash()), Map.entry("status", status),
                Map.entry("actualMessages", List.copyOf(messages)), Map.entry("receipts", List.copyOf(receipts)),
                Map.entry("matched", status.equals("completed") && !outputBudget.truncated() && messages.equals(expected)), Map.entry("error", failure),
                Map.entry("outputTruncated", outputBudget.truncated()), Map.entry("receiptsTruncated", receiptBudget.truncated()),
                Map.entry("line", line), Map.entry("column", column));
        }
    }
    /** Bounded asynchronous tests; the actor identity comes only from the browser grant. */
    synchronized Map<String, Object> startTest(String connectionId, CompiledFeature feature, UUID actor,
            List<String> arguments, List<String> expected, BooleanSupplier connectionAlive) {
        expireTests();
        if (closed || !ready.getAsBoolean()) throw error("RUNTIME_NOT_READY");
        Objects.requireNonNull(actor); Objects.requireNonNull(connectionId); Objects.requireNonNull(connectionAlive);
        if (feature.program() != null && feature.program().eventOnly()) throw error("EVENT_ONLY_FEATURE: trigger a real server event");
        List<String> checked = InvocationArguments.checked(arguments);
        if (expected.size() > 128) throw error("INVALID_EXPECTED_MESSAGES");
        if (tests.size() >= 32 || tests.values().stream().filter(j -> j.connection.equals(connectionId)).count() >= 8) throw error("TEST_BUSY");
        TestJob job = new TestJob(connectionId, feature, expected, connectionAlive);
        tests.put(job.id, job);
        Map<String, Object> admitted = job.view();
        try { interpreters.execute(() -> {
            try {
                main.call(() -> {
                    if (!job.pending() || !job.alive.getAsBoolean() || System.nanoTime() >= job.expires) throw error("TEST_RETIRED");
                    Player player = plugin.getServer().getPlayer(actor);
                    if (player == null || !player.isOnline()) throw error("PLAYER_DISCONNECTED");
                    if (!player.hasPermission(feature.definition().command().permission())) throw error("PERMISSION_DENIED");
                    startRun(feature, player, checked, job);
                    return null;
                });
            } catch (Exception failure) { job.finish("failed", failure); }
        }); } catch (RejectedExecutionException failure) { tests.remove(job.id); throw error("TEST_BUSY"); }
        return admitted;
    }
    synchronized Map<String, Object> testStatus(String connectionId, String featureId, String testId) {
        expireTests(); TestJob job = tests.get(testId);
        if (job == null || !job.connection.equals(connectionId) || !job.feature.definition().id().equals(featureId)) throw new SecurityException("TEST_NOT_FOUND");
        CompiledFeature current = registry.snapshot().features().get(featureId);
        if (current == null || !current.sourceHash().equals(job.feature.sourceHash())) {
            if (job.run != null) job.run.cancel(); job.finish("cancelled", error("STALE_SOURCE"));
            throw new SecurityException("TEST_STALE_SOURCE");
        }
        return job.view();
    }
    synchronized void retireTests(String connectionId) {
        for (TestJob job : tests.values()) if (job.connection.equals(connectionId)) {
            job.finish("cancelled", error("CONNECTION_REVOKED")); if (job.run != null) job.run.cancel();
        }
    }
    private synchronized void expireTests() {
        tests.values().removeIf(job -> {
            if (System.nanoTime() >= job.expires) { job.finish("timeout", error("TEST_EXPIRED")); if (job.run != null) job.run.cancel(); return true; }
            if (!job.alive.getAsBoolean()) { job.finish("cancelled", error("CONNECTION_REVOKED")); if (job.run != null) job.run.cancel(); }
            return false;
        });
    }
    private volatile boolean closed;
    private final PaperWorldDisplays worldDisplays;
    private final PaperItems items;
    private final PaperMenus menus;
    private final PaperEntities ownedEntities;
    private volatile KitCooldowns kitCooldowns;
    private volatile SQLiteVariables state; // Accessed by the single existing plugin I/O worker.
    private volatile SQLiteVariables sharedState;

    GameplayRuntime(JavaPlugin plugin, Registry registry, ExecutorService io, RemoteScripts.Main main,
                    Supplier<ScriptCapabilityPolicy> policy, BooleanSupplier ready, Path directory) {
        this.plugin = plugin; this.registry = registry; this.io = io; this.main = main;
        this.policy = policy; this.ready = ready; this.stateDirectory = directory;
        this.worldDisplays = new PaperWorldDisplays(plugin.getServer());
        this.items = new PaperItems(plugin);
        this.menus = new PaperMenus(plugin);
        this.ownedEntities = new PaperEntities(plugin.getServer());
        plugin.getServer().getPluginManager().registerEvents(ownedEntities, plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        testExpiry.scheduleAtFixedRate(this::expireTests, 1, 1, TimeUnit.SECONDS);
    }
    void start(CompiledFeature feature, Player actor, List<String> arguments) {
        startRun(feature, actor, arguments, null);
    }
    private void startRun(CompiledFeature feature, Player actor, List<String> arguments, TestJob job) {
        List<String> checked = InvocationArguments.checked(arguments);
        policy.get().validate(feature);
        if (closed || !ready.getAsBoolean()) throw error("RUNTIME_NOT_READY");
        sessions.computeIfAbsent(actor.getUniqueId(), ignored -> UUID.randomUUID());
        if (active.stream().filter(run -> run.actor.equals(actor.getUniqueId())).count() >= 4
            || active.stream().filter(run -> run.feature.definition().id().equals(feature.definition().id())).count() >= 8)
            throw error("RUNTIME_BUSY: player or feature invocation limit reached");
        if (!admission.tryAcquire()) throw error("RUNTIME_BUSY: invocation limit reached");
        Run run;
        try { run = new Run(feature, actor.getUniqueId(), actor.getWorld().getUID(), checked, job); }
        catch (RuntimeException failure) { admission.release(); throw failure; }
        if (job != null) job.run = run;
        active.add(run);
        try { interpreters.execute(run); }
        catch (RejectedExecutionException failure) { active.remove(run); admission.release(); run.cancel(); throw error("RUNTIME_BUSY"); }
    }
    /** Main-thread activation and policy changes retire only affected invocations. */
    void reconcile() {
        expireTests();
        for (Run run : active) if (!run.current(false)) run.cancel();
        worldDisplays.reconcile(owner -> revisionActive(owner.feature(), owner.revision()));
        menus.reconcile(owner -> revisionActive(owner.feature(), owner.revision()));
        ownedEntities.reconcile(owner -> revisionActive(owner.feature(), owner.revision()));
    }
    private boolean revisionActive(String id, String revision) {
        var feature = registry.snapshot().features().get(id);
        return feature != null && feature.sourceHash().equals(revision)
            && policy.get().allowed(id).containsAll(feature.definition().requires());
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID actor = event.getPlayer().getUniqueId();
        sessions.remove(actor); // A reconnect with the same UUID must never revive an earlier invocation.
        worldDisplays.forget(actor);
        ownedEntities.forget(actor);
        for (Run run : active) if (run.actor.equals(actor)) run.cancel();
    }
    private static ValidationException error(String message) { return new ValidationException("$", message); }
    private static ScriptValue unit() { return new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE); }
    private static void cleanup(Runnable action) { try { action.run(); } catch (RuntimeException ignored) { } }
    private final class Run implements Runnable {
        final CompiledFeature feature;
        final TestJob job;
        final UUID actor;
        final UUID session;
        final List<String> arguments;
        final long deadline = System.nanoTime() + MAX_NANOS;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final Map<Future<?>, Runnable> pending = new ConcurrentHashMap<>();
        final Map<PersistentVariables.Key, Long> revisions = new HashMap<>(); // I/O worker confined.
        final Map<PersistentVariables.Key, SharedRead> sharedReads = new HashMap<>();
        final UUID startingWorld;
        final UUID invocation = UUID.randomUUID();
        final InvocationContext context;
        volatile Thread worker;
        Run(CompiledFeature feature, UUID actor, UUID startingWorld, List<String> arguments, TestJob job) {
            this.job = job;
            this.startingWorld = startingWorld;
            this.feature = feature; this.actor = actor; this.session = sessions.get(actor); this.arguments = arguments;
            context = new InvocationContext(feature.definition().id(), feature.sourceHash(), actor,
                policy.get().allowed(feature.definition().id()), new ExecutionBudget(), () -> current(true),
                (descriptor, values, ignored) -> invoke(descriptor, values),
                text -> {
                    try { main.call(() -> { player().sendMessage(Component.text(text)); if (job != null) job.message(text); return null; }); }
                    catch (ValidationException failure) { throw failure; }
                    catch (Exception failure) { throw error("MESSAGE_UNAVAILABLE"); }
                });
        }
        boolean current(boolean withDeadline) {
            if (closed || cancelled.get() || job != null && (!job.alive.getAsBoolean() || System.nanoTime() >= job.expires) || !Objects.equals(session, sessions.get(actor)) || withDeadline && System.nanoTime() >= deadline) return false;
            CompiledFeature current = registry.snapshot().features().get(feature.definition().id());
            return current != null && current.sourceHash().equals(feature.sourceHash())
                && policy.get().allowed(feature.definition().id()).containsAll(feature.definition().requires());
        }
        Player player() {
            if (!current(true)) throw error("INVOCATION_RETIRED: revision, capability or deadline changed");
            Player player = plugin.getServer().getPlayer(actor);
            if (player == null || !player.isOnline()) throw error("PLAYER_DISCONNECTED");
            if (!player.hasPermission(feature.definition().command().permission())) throw error("PERMISSION_DENIED");
            return player;
        }
        @Override public void run() {
            worker = Thread.currentThread();
            if (cancelled.get()) worker.interrupt();
            try {
                main.call(() -> { player(); return null; });
                if (feature.definition().requires().stream().allMatch(ScriptCapabilityPolicy.MESSAGE::equals)) {
                    main.call(() -> {
                        boolean invoked = registry.run(feature.definition().id(), arguments, true,
                            permission -> { player(); return true; }, text -> {
                                player().sendMessage(Component.text(text));
                                if (job != null) job.message(text);
                            });
                        if (!invoked) throw error("INVOCATION_DENIED");
                        return null;
                    });
                } else {
                    if (feature.program() == null) throw error("HOST_PROGRAM_REQUIRED");
                    feature.program().evaluate(arguments, context);
                }
                if (!current(true)) throw error("INVOCATION_RETIRED");
                if (job != null) job.finish("completed", null);
            } catch (Exception failure) {
                if (current(false) && !context.cancelled()) try {
                    main.call(() -> {
                        if (!current(false)) return null;
                        Player player = plugin.getServer().getPlayer(actor);
                        if (player != null && player.isOnline() && player.hasPermission(feature.definition().command().permission())) {
                            String text = FeatureManager.display(failure instanceof ValidationException ? failure.getMessage() : "RUNTIME_FAILED");
                            player.sendMessage(Component.text(text));
                            if (job != null) job.message(text);
                        }
                        return null;
                    });
                } catch (Exception ignored) { }
                if (job != null) job.finish(System.nanoTime() >= deadline ? "timeout" : cancelled.get() ? "cancelled" : "failed", failure);
            } finally { cancel(); active.remove(this); if (job != null) job.run = null; admission.release(); }
        }
        void cancel() {
            if (job != null) job.finish("cancelled", error("INVOCATION_RETIRED"));
            cancelled.set(true); context.cancel();
            pending.forEach((future, action) -> { future.cancel(false); cleanup(action); });
            Thread thread = worker; if (thread != null) thread.interrupt();
        }
        <T> T await(CompletableFuture<T> future, Runnable cleanup) throws Exception {
            pending.put(future, cleanup);
            if (!current(true)) { future.cancel(false); cleanup(cleanup); }
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw error("INVOCATION_TIMEOUT: exceeded 120 seconds");
                return future.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException failure) { throw error("INVOCATION_TIMEOUT: exceeded 120 seconds"); }
            catch (ExecutionException failure) {
                if (failure.getCause() instanceof Exception cause) throw cause;
                throw new IllegalStateException("Host operation failed");
            } finally { pending.remove(future); cleanup(cleanup); }
        }
        ScriptValue invoke(CallableRegistry.Descriptor descriptor, List<ScriptValue> values) throws Exception {
            // All reads/permission tests of live Bukkit objects happen on the platform thread.
            main.call(() -> { player(); return null; });
            return switch (descriptor.id()) {
                case "player.location.read" -> main.call(() -> {
                    Location place = player().getLocation();
                    return new ScriptValue(ScriptType.LOCATION, new ScriptValue.LocationSnapshot(place.getWorld().getUID(), place.getX(), place.getY(), place.getZ(), place.getYaw(), place.getPitch()));
                });
                case "player.inventory.has", "player.inventory.give", "player.inventory.remove", "player.inventory.exchange" -> main.call(() -> {
                    Player actorPlayer = player();
                    boolean succeeded = items.inventory(descriptor.id(), actorPlayer.getInventory(), values, feature.definition().id());
                    if (succeeded && !descriptor.id().equals("player.inventory.has")) {
                        var item = values.getFirst().item();
                        receipt(Map.of("operation", descriptor.id(), "material", item.material(), "amount", item.amount(), "delivered", true));
                    }
                    return new ScriptValue(ScriptType.BOOLEAN, succeeded);
                });
                case "player.menu.choose" -> chooseMenu(values);
                case "entity.own.spawn", "entity.own.remove", "entity.own.health", "entity.own.target_player",
                     "player.team.join", "player.team.is", "player.team.leave" ->
                    main.call(() -> ownedEntities.invoke(descriptor.id(), player(), values,
                        new PaperEntities.Owner(feature.definition().id(), feature.sourceHash())));
                case "player.kit.claim" -> claimKit(values);
                case "player.identity.self" -> new ScriptValue(ScriptType.TEXT, "p" + actor.toString().replace("-", ""));
                case "state.shared.read", "state.shared.commit" -> shared(descriptor.id(), values);
                case "world.from_location", "world.block.at", "world.block.material", "world.block.set",
                     "world.time.read", "world.time.set", "world.weather.read", "world.weather.set",
                     "player.display.actionbar", "player.display.title", "player.sound",
                     "player.display.bossbar", "player.display.bossbar.clear",
                     "player.display.scoreboard", "player.display.scoreboard.clear" ->
                    main.call(() -> worldDisplays.invoke(descriptor.id(), player(), values,
                        new PaperWorldDisplays.Owner(feature.definition().id(), feature.sourceHash())));
                case "state.own.get_location", "state.own.put_location", "state.own.save", "state.own.load" -> state(descriptor.id(), values);
                case "scheduler.delay", "scheduler.repeat" -> {
                    long ticks = values.getFirst().integer();
                    long minimum = descriptor.id().equals("scheduler.repeat") ? 1 : 0;
                    if (ticks < minimum || ticks > 1200) throw error("DELAY_LIMIT: expected " + minimum + "..1200 server ticks");
                    CompletableFuture<ScriptValue> done = new CompletableFuture<>();
                    BukkitTask task = main.call(() -> {
                        player();
                        return plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            try { player(); done.complete(unit()); }
                            catch (Exception failure) { done.completeExceptionally(failure); }
                        }, ticks);
                    });
                    yield await(done, task::cancel);
                }
                case "player.teleport.self" -> {
                    ScriptValue.LocationSnapshot target = values.getFirst().location(); LocationCodec.bounded(target);
                    // Load chunks asynchronously, then guard again immediately before the actual teleport.
                    CompletableFuture<?> chunk = main.call(() -> {
                        player(); var world = plugin.getServer().getWorld(target.worldId());
                        if (world == null) throw error("WORLD_UNAVAILABLE");
                        return world.getChunkAtAsync(((int) Math.floor(target.x())) >> 4, ((int) Math.floor(target.z())) >> 4);
                    });
                    await(chunk, () -> {});
                    yield main.call(() -> {
                        Player player = player(); var world = plugin.getServer().getWorld(target.worldId());
                        if (world == null || !world.isChunkLoaded(((int) Math.floor(target.x())) >> 4, ((int) Math.floor(target.z())) >> 4)) throw error("WORLD_OR_CHUNK_UNAVAILABLE");
                        if (!player.teleport(new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch()))) throw error("TELEPORT_CANCELLED");
                        if (job != null) receipt(Map.of("operation", "player.teleport.self", "actualLocation", LocationCodec.encode(new ScriptValue.LocationSnapshot(player.getWorld().getUID(), player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), player.getLocation().getYaw(), player.getLocation().getPitch()))));
                        return unit();
                    });
                }
                default -> throw error("UNSUPPORTED_HOST_OPERATION");
            };
        }
        private ScriptValue chooseMenu(List<ScriptValue> values) throws Exception {
            String id = feature.definition().id();
            var owner = new PaperMenus.Owner(id, feature.sourceHash(), invocation);
            var options = values.get(1).list();
            PaperMenus.size(values.getFirst().text(), options.size());
            var result = main.call(() -> menus.choose(player(), owner, values.getFirst().text(),
                options.stream().map(value -> items.icon(value.item(), id)).toList(), () -> {
                    try { player(); return true; }
                    catch (ValidationException denied) { return false; }
                }));
            int slot = await(result, () -> plugin.getServer().getScheduler().runTask(plugin, () -> menus.cancel(owner)));
            return new ScriptValue(ScriptType.INTEGER, (long) slot);
        }
        ScriptValue claimKit(List<ScriptValue> values) throws Exception {
            String name = values.getFirst().text(); long seconds = values.get(2).integer();
            if (!name.matches("[a-z][a-z0-9_]{0,63}") || seconds < 1 || seconds > 31536000) throw error("KIT_ARGUMENT_INVALID");
            var descriptors = values.get(1).list();
            if (descriptors.stream().anyMatch(value -> value.item().custom()))
                throw error("KIT_CUSTOM_ITEM_UNAVAILABLE: use plain material kit items");
            if (descriptors.isEmpty() || descriptors.size() > 64) throw error("KIT_ARGUMENT_INVALID: expected 1..64 items");
            // The worker owns reservation and compensation through completion, even after await is cancelled.
            CompletableFuture<ScriptValue> result = new CompletableFuture<>();
            try { io.execute(() -> {
                KitCooldowns.Reservation reservation = null; boolean delivered = false;
                var delivery = new java.util.concurrent.atomic.AtomicInteger(); // 0 pending, 1 effect started, 2 abandoned
                try {
                    boolean fits = main.call(() -> kitFits(descriptors, null));
                    if (!fits) { result.complete(new ScriptValue(ScriptType.TEXT, "inventory_full")); return; }
                    if (kitCooldowns == null) kitCooldowns = new KitCooldowns(stateDirectory);
                    if (!current(true)) throw error("INVOCATION_RETIRED");
                    reservation = kitCooldowns.reserve(feature.definition().id(), actor, name, System.currentTimeMillis(), seconds);
                    if (reservation == null) { result.complete(new ScriptValue(ScriptType.TEXT, "cooldown")); return; }
                    delivered = main.call(() -> kitFits(descriptors, delivery));
                    if (!delivered) { kitCooldowns.compensate(reservation); reservation = null; }
                    else receipt(Map.of("operation", "player.kit.claim", "key", name, "status", "claimed", "cooldownSeconds", seconds));
                    result.complete(new ScriptValue(ScriptType.TEXT, delivered ? "claimed" : "inventory_full"));
                } catch (Exception failure) {
                    if (reservation != null && !delivered && delivery.compareAndSet(0, 2)) try { kitCooldowns.compensate(reservation); }
                    catch (Exception compensation) { plugin.getLogger().severe("Kit reservation compensation unavailable; cooldown retained. Inspect owner storage."); }
                    result.completeExceptionally(failure instanceof ValidationException ? failure : error("KIT_UNAVAILABLE: inspect owner storage"));
                }
            }); } catch (RejectedExecutionException failure) { throw error("STATE_BUSY: bounded I/O queue is full"); }
            return await(result, () -> {});
        }
        boolean kitFits(List<ScriptValue> descriptors, java.util.concurrent.atomic.AtomicInteger delivery) {
            var inventory = player().getInventory();
            var items = descriptors.stream().map(value -> inventoryItem(value.item(), inventory.getMaxStackSize())).toList();
            var original = inventory.getStorageContents();
            var slots = inventorySlots(original, inventory.getMaxStackSize());
            var plan = InventoryPlan.give(slots, items);
            if (plan == null) return false;
            if (delivery != null) {
                var contents = inventoryContents(original, slots, plan);
                if (!delivery.compareAndSet(0, 1)) throw error("KIT_DELIVERY_ABANDONED");
                inventory.setStorageContents(contents);
                // No fallible work after mutation: an effect must never be compensated as undelivered.
            }
            return true;
        }
        private record SharedRead(long revision, ScriptType type) {}
        ScriptValue shared(String operation, List<ScriptValue> values) throws Exception {
            UUID scope = switch (values.getFirst().text()) {
                case "server" -> null;
                case "world" -> startingWorld;
                default -> throw error("SHARED_SCOPE_INVALID: use server or world");
            };
            CompletableFuture<ScriptValue> result = new CompletableFuture<>();
            try {
                io.execute(() -> {
                    try {
                        main.call(() -> { player(); return null; });
                        if (sharedState == null) sharedState = SQLiteVariables.openShared(stateDirectory);
                        if (operation.equals("state.shared.read")) result.complete(readShared(scope, values));
                        else result.complete(commitShared(scope, values));
                    } catch (ValidationException failure) {
                        result.completeExceptionally(failure);
                    } catch (Exception failure) {
                        result.completeExceptionally(error("SHARED_UNAVAILABLE: inspect shared state storage"));
                    }
                });
            } catch (RejectedExecutionException failure) {
                throw error("STATE_BUSY: bounded I/O queue is full");
            }
            return await(result, () -> {});
        }
        private PersistentVariables.Key sharedKey(UUID scope, String name) {
            try { return new PersistentVariables.Key(feature.definition().id(), scope, name); }
            catch (IllegalArgumentException failure) { throw error("STATE_KEY_INVALID: use a lowercase name up to 64 characters"); }
        }
        private ScriptValue readShared(UUID scope, List<ScriptValue> values) throws IOException {
            var key = sharedKey(scope, values.get(1).text());
            if (!sharedReads.containsKey(key) && sharedReads.size() >= 128) throw error("SHARED_READ_LIMIT: up to 128 keys per invocation");
            ScriptValue fallback = values.get(2);
            var entry = sharedState.sharedRead(key);
            ScriptValue value = fallback;
            if (entry.value() != null) {
                try { value = ScriptValueCodec.decode(entry.value().text()); }
                catch (IllegalArgumentException failure) { throw error("SHARED_TYPE_MISMATCH: invalid stored value"); }
                if (!value.type().equals(fallback.type())) throw error("SHARED_TYPE_MISMATCH: stored type differs from fallback");
            }
            sharedReads.put(key, new SharedRead(entry.revision(), fallback.type()));
            return value;
        }
        private ScriptValue commitShared(UUID scope, List<ScriptValue> values) throws IOException {
            Map<PersistentVariables.Key, Long> expected = new LinkedHashMap<>();
            sharedReads.forEach((key, read) -> { if (Objects.equals(key.player(), scope)) expected.put(key, read.revision()); });
            Map<PersistentVariables.Key, PersistentVariables.Value> writes = new LinkedHashMap<>();
            Set<PersistentVariables.Key> deletes = new LinkedHashSet<>();
            for (var entry : values.get(1).map().entrySet()) {
                var key = sharedKey(scope, entry.getKey().text());
                if (!expected.containsKey(key)) throw error("SHARED_READ_REQUIRED: read the key before writing");
                if (!sharedReads.get(key).type().equals(entry.getValue().type()))
                    throw error("SHARED_TYPE_MISMATCH: write must match the type read for this key");
                try { writes.put(key, PersistentVariables.Value.text(ScriptValueCodec.encode(entry.getValue()))); }
                catch (IllegalArgumentException failure) { throw error("STATE_VALUE_LIMIT: value exceeds typed state bounds"); }
            }
            for (var name : values.get(2).list()) {
                var key = sharedKey(scope, name.text());
                if (!expected.containsKey(key)) throw error("SHARED_READ_REQUIRED: read the key before deleting");
                if (writes.containsKey(key) || !deletes.add(key)) throw error("SHARED_DUPLICATE_KEY");
            }
            if (writes.isEmpty() && deletes.isEmpty()) throw error("SHARED_EMPTY_COMMIT");
            if (!current(true)) throw error("INVOCATION_RETIRED: transaction was not accepted");
            try {
                var current = sharedState.sharedCommit(expected, writes, deletes);
                current.forEach((key, entry) -> sharedReads.put(key, new SharedRead(entry.revision(), sharedReads.get(key).type())));
                return new ScriptValue(ScriptType.BOOLEAN, true);
            } catch (PersistentVariables.ConflictException conflict) {
                return new ScriptValue(ScriptType.BOOLEAN, false);
            }
        }
        void receipt(Map<String, Object> receipt) {
            if (job != null) job.receipt(receipt);
        }
        ScriptValue.LocationSnapshot readLocation(String text) {
            try { return LocationCodec.decode(text); }
            catch (java.io.IOException | IllegalArgumentException invalid) { throw error("STATE_TYPE_MISMATCH: expected a legacy location snapshot"); }
        }
        ScriptValue state(String operation, List<ScriptValue> values) throws Exception {
            String name = values.getFirst().text();
            PersistentVariables.Key key;
            try { key = new PersistentVariables.Key(feature.definition().id(), actor, name); }
            catch (IllegalArgumentException failure) { throw error("STATE_KEY_INVALID: use a lowercase name up to 64 characters"); }
            CompletableFuture<ScriptValue> result = new CompletableFuture<>();
            try { io.execute(() -> {
                try {
                    // Registry activation and policy writes share this worker, so a commit cannot cross a revision replacement.
                    main.call(() -> { player(); return null; });
                    if (state == null) state = new SQLiteVariables(stateDirectory);
                    var previous = state.get(key);
                    long revision = previous == null ? 0 : previous.revision();
                    boolean typed = operation.equals("state.own.save") || operation.equals("state.own.load");
                    boolean read = operation.equals("state.own.load") || operation.equals("state.own.get_location");
                    ScriptValue stored = null;
                    if (previous != null) {
                        if (previous.value().type() != PersistentVariables.Type.TEXT) throw error("STATE_TYPE_MISMATCH: expected encoded player state");
                        if (typed) {
                            try { stored = ScriptValueCodec.decode(previous.value().text()); }
                            catch (IllegalArgumentException invalid) { throw error("STATE_TYPE_MISMATCH: key contains legacy or invalid state"); }
                            if (!stored.type().equals(values.get(1).type())) throw error("STATE_TYPE_MISMATCH: stored type differs from requested type");
                        } else {
                            stored = new ScriptValue(ScriptType.LOCATION, readLocation(previous.value().text()));
                        }
                    }
                    if (read) {
                        revisions.put(key, revision);
                        result.complete(typed ? stored == null ? values.get(1) : stored
                            : new ScriptValue(ScriptType.optional(ScriptType.LOCATION), Optional.ofNullable(stored)));
                    } else {
                        String encoded;
                        if (typed) {
                            try { encoded = ScriptValueCodec.encode(values.get(1)); }
                            catch (IllegalArgumentException invalid) { throw error("STATE_VALUE_LIMIT: value exceeds typed state bounds"); }
                        } else encoded = LocationCodec.encode(values.get(1).location());
                        long expected = revisions.getOrDefault(key, revision);
                        var mutations = List.of(new PersistentVariables.Mutation(key, expected, PersistentVariables.Value.text(encoded)));
                        // Write acceptance boundary: retired work is discarded before commit. Once accepted,
                        // a durable write may finish after cancellation; never block Paper to wait for SQLite.
                        if (!current(true)) throw error("INVOCATION_RETIRED: state write was not accepted");
                        state.commit(mutations);
                        receipt(Map.of("operation", operation, "key", name, "revision", Math.incrementExact(expected), "acknowledged", true));
                        revisions.put(key, Math.incrementExact(expected)); result.complete(unit());
                    }
                } catch (PersistentVariables.ConflictException failure) { result.completeExceptionally(error("STATE_CONFLICT: value changed after this invocation read it")); }
                catch (ValidationException failure) { result.completeExceptionally(failure); }
                catch (Exception failure) { result.completeExceptionally(error("STATE_UNAVAILABLE: inspect owner storage and backups")); }
            }); } catch (RejectedExecutionException failure) { throw error("STATE_BUSY: bounded I/O queue is full"); }
            return await(result, () -> {});
        }
    }
    private static InventoryPlan.Item inventoryItem(ScriptValue.ItemDescriptor item, int maximum) {
        var material = org.bukkit.Material.matchMaterial(item.material());
        if (material == null || !material.isItem() || material.isAir()) throw error("ITEM_MATERIAL_INVALID");
        return new InventoryPlan.Item(material.getKey().toString(), item.amount(), Math.min(maximum, material.getMaxStackSize()));
    }
    private static InventoryPlan.Slot[] inventorySlots(org.bukkit.inventory.ItemStack[] contents, int maximum) {
        var slots = new InventoryPlan.Slot[contents.length];
        for (int i=0; i<contents.length; i++) {
            var item = contents[i];
            if (item != null && !item.getType().isAir()) slots[i] = new InventoryPlan.Slot(item.getType().getKey().toString(), item.getAmount(), Math.min(maximum,item.getMaxStackSize()), !item.hasItemMeta());
        }
        return slots;
    }
    private static org.bukkit.inventory.ItemStack[] inventoryContents(org.bukkit.inventory.ItemStack[] original, InventoryPlan.Slot[] before, InventoryPlan.Slot[] after) {
        var result = original.clone();
        for (int i=0; i<after.length; i++) if (!Objects.equals(before[i], after[i])) {
            var slot = after[i];
            result[i] = slot == null ? null : new org.bukkit.inventory.ItemStack(Objects.requireNonNull(org.bukkit.Material.matchMaterial(slot.material())), slot.amount());
        }
        return result;
    }
    @Override public void close() {
        menus.close();
        ownedEntities.close();
        worldDisplays.reconcile(owner -> false);
        closed = true; testExpiry.shutdownNow(); sessions.clear(); active.forEach(Run::cancel); interpreters.shutdownNow(); HandlerList.unregisterAll(this);
    }
    void closeStorageAfterShutdown() {
        // Never wait for disk on Paper's thread, and never close a connection underneath an in-flight worker.
        Thread.startVirtualThread(() -> {
            try {
                if (io.awaitTermination(6, TimeUnit.SECONDS)) {
                    if (state != null) state.close();
                    if (kitCooldowns != null) kitCooldowns.close();
                    if (sharedState != null) sharedState.close();
                }
                else plugin.getLogger().warning("Player-state worker has not stopped; storage close deferred to process shutdown.");
            }
            catch (Exception ignored) { }
        });
    }
}
