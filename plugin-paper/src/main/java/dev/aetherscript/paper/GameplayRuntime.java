package dev.aetherscript.paper;

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
    private volatile boolean closed;
    private volatile SQLiteVariables state; // Accessed by the single existing plugin I/O worker.

    GameplayRuntime(JavaPlugin plugin, Registry registry, ExecutorService io, RemoteScripts.Main main,
                    Supplier<ScriptCapabilityPolicy> policy, BooleanSupplier ready, Path directory) {
        this.plugin = plugin; this.registry = registry; this.io = io; this.main = main;
        this.policy = policy; this.ready = ready; this.stateDirectory = directory;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    void start(CompiledFeature feature, Player actor, List<String> arguments) {
        List<String> checked = InvocationArguments.checked(arguments);
        policy.get().validate(feature);
        if (closed || !ready.getAsBoolean()) throw error("RUNTIME_NOT_READY");
        sessions.computeIfAbsent(actor.getUniqueId(), ignored -> UUID.randomUUID());
        if (active.stream().filter(run -> run.actor.equals(actor.getUniqueId())).count() >= 4
            || active.stream().filter(run -> run.feature.definition().id().equals(feature.definition().id())).count() >= 8)
            throw error("RUNTIME_BUSY: player or feature invocation limit reached");
        if (!admission.tryAcquire()) throw error("RUNTIME_BUSY: invocation limit reached");
        Run run;
        try { run = new Run(feature, actor.getUniqueId(), checked); }
        catch (RuntimeException failure) { admission.release(); throw failure; }
        active.add(run);
        try { interpreters.execute(run); }
        catch (RejectedExecutionException failure) { active.remove(run); admission.release(); run.cancel(); throw error("RUNTIME_BUSY"); }
    }
    /** Main-thread activation and policy changes retire only affected invocations. */
    void reconcile() { for (Run run : active) if (!run.current(false)) run.cancel(); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID actor = event.getPlayer().getUniqueId();
        sessions.remove(actor); // A reconnect with the same UUID must never revive an earlier invocation.
        for (Run run : active) if (run.actor.equals(actor)) run.cancel();
    }
    private static ValidationException error(String message) { return new ValidationException("$", message); }
    private static ScriptValue unit() { return new ScriptValue(ScriptType.UNIT, ScriptValue.Unit.INSTANCE); }
    private static void cleanup(Runnable action) { try { action.run(); } catch (RuntimeException ignored) { } }
    private final class Run implements Runnable {
        final CompiledFeature feature;
        final UUID actor;
        final UUID session;
        final List<String> arguments;
        final long deadline = System.nanoTime() + MAX_NANOS;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final Map<Future<?>, Runnable> pending = new ConcurrentHashMap<>();
        final Map<PersistentVariables.Key, Long> revisions = new HashMap<>(); // I/O worker confined.
        final InvocationContext context;
        volatile Thread worker;
        Run(CompiledFeature feature, UUID actor, List<String> arguments) {
            this.feature = feature; this.actor = actor; this.session = sessions.get(actor); this.arguments = arguments;
            context = new InvocationContext(feature.definition().id(), feature.sourceHash(), actor,
                policy.get().allowed(feature.definition().id()), new ExecutionBudget(), () -> current(true),
                (descriptor, values, ignored) -> invoke(descriptor, values),
                text -> {
                    try { main.call(() -> { player().sendMessage(Component.text(text)); return null; }); }
                    catch (ValidationException failure) { throw failure; }
                    catch (Exception failure) { throw error("MESSAGE_UNAVAILABLE"); }
                });
        }
        boolean current(boolean withDeadline) {
            if (closed || cancelled.get() || !Objects.equals(session, sessions.get(actor)) || withDeadline && System.nanoTime() >= deadline) return false;
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
                if (feature.program() == null) throw error("HOST_PROGRAM_REQUIRED");
                // Host-mode sends are streamed through the guarded context; do not emit returned messages twice.
                feature.program().evaluate(arguments, context);
            } catch (Exception failure) {
                if (current(false) && !context.cancelled()) try {
                    main.call(() -> {
                        if (!current(false)) return null;
                        Player player = plugin.getServer().getPlayer(actor);
                        if (player != null && player.isOnline() && player.hasPermission(feature.definition().command().permission()))
                            player.sendMessage(Component.text(FeatureManager.display(failure instanceof ValidationException ? failure.getMessage() : "RUNTIME_FAILED")));
                        return null;
                    });
                } catch (Exception ignored) { }
            } finally { cancel(); active.remove(this); admission.release(); }
        }
        void cancel() {
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
                case "state.own.get_location", "state.own.put_location" -> state(descriptor.id(), values);
                case "scheduler.delay" -> {
                    long ticks = values.getFirst().integer();
                    if (ticks < 0 || ticks > 1200) throw error("DELAY_LIMIT: expected 0..1200 server ticks");
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
                        return unit();
                    });
                }
                default -> throw error("UNSUPPORTED_HOST_OPERATION");
            };
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
                    if (operation.equals("state.own.get_location")) {
                        revisions.put(key, revision);
                        if (previous != null && previous.value().type() != PersistentVariables.Type.TEXT) throw error("STATE_TYPE_MISMATCH: expected a location snapshot");
                        Optional<ScriptValue> value = previous == null ? Optional.empty()
                            : Optional.of(new ScriptValue(ScriptType.LOCATION, LocationCodec.decode(previous.value().text())));
                        result.complete(new ScriptValue(ScriptType.optional(ScriptType.LOCATION), value));
                    } else {
                        long expected = revisions.getOrDefault(key, revision);
                        var mutations = List.of(new PersistentVariables.Mutation(key, expected,
                            PersistentVariables.Value.text(LocationCodec.encode(values.get(1).location()))));
                        // Write acceptance boundary: retired work is discarded before commit. Once accepted,
                        // a durable write may finish after cancellation; never block Paper to wait for SQLite.
                        if (!current(true)) throw error("INVOCATION_RETIRED: state write was not accepted");
                        state.commit(mutations);
                        revisions.put(key, Math.incrementExact(expected)); result.complete(unit());
                    }
                } catch (PersistentVariables.ConflictException failure) { result.completeExceptionally(error("STATE_CONFLICT: value changed after this invocation read it")); }
                catch (ValidationException failure) { result.completeExceptionally(failure); }
                catch (Exception failure) { result.completeExceptionally(error("STATE_UNAVAILABLE: inspect owner storage and backups")); }
            }); } catch (RejectedExecutionException failure) { throw error("STATE_BUSY: bounded I/O queue is full"); }
            return await(result, () -> {});
        }
    }
    @Override public void close() {
        closed = true; sessions.clear(); active.forEach(Run::cancel); interpreters.shutdownNow(); HandlerList.unregisterAll(this);
    }
    void closeStorageAfterShutdown() {
        // Never wait for disk on Paper's thread, and never close a connection underneath an in-flight worker.
        Thread.startVirtualThread(() -> {
            try {
                if (io.awaitTermination(6, TimeUnit.SECONDS)) { if (state != null) state.close(); }
                else plugin.getLogger().warning("Player-state worker has not stopped; storage close deferred to process shutdown.");
            }
            catch (Exception ignored) { }
        });
    }
}
