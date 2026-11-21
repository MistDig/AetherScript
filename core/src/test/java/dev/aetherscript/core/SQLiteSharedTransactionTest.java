package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static dev.aetherscript.core.PersistentVariables.*;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteSharedTransactionTest {
    @TempDir Path root;
    private final Key a = new Key("demo", null, "a"), b = new Key("demo", null, "b");

    @Test void readOnlyAndMissingPreconditionsRejectWholeTransaction() throws Exception {
        try (var first = SQLiteVariables.openShared(root); var second = SQLiteVariables.openShared(root)) {
            assertEquals(new SQLiteVariables.SharedEntry(0, null), first.sharedRead(a));
            second.sharedCommit(Map.of(a, 0L), Map.of(a, Value.integer(5)), Set.of());
            assertThrows(ConflictException.class, () -> first.sharedCommit(Map.of(a, 0L, b, 0L), Map.of(b, Value.integer(8)), Set.of()));
            assertNull(first.sharedRead(b).value());
            assertThrows(ConflictException.class, () -> first.sharedCommit(Map.of(a, 0L), Map.of(), Set.of()));
            assertEquals(Value.integer(5), first.sharedRead(a).value());
        }
    }
    @Test void deleteRecreateAndReopenRetainRevisionAndIsolation() throws Exception {
        Key world = new Key("demo", UUID.randomUUID(), "a");
        Key other = new Key("other", null, "a");
        try (var shared = SQLiteVariables.openShared(root); var player = new SQLiteVariables(root)) {
            player.commit(List.of(new Mutation(a, 0, Value.text("player"))));
            shared.sharedCommit(Map.of(a, 0L, world, 0L, other, 0L), Map.of(a, Value.integer(1), world, Value.bool(true), other, Value.text("other")), Set.of());
            shared.sharedCommit(Map.of(a, 1L), Map.of(), Set.of(a));
            assertEquals(new SQLiteVariables.SharedEntry(2, null), shared.sharedRead(a));
            assertFalse(shared.snapshot().containsKey(a));
            assertEquals(Value.text("player"), player.get(a).value());
        }
        try (var shared = SQLiteVariables.openShared(root)) {
            assertThrows(ConflictException.class, () -> shared.sharedCommit(Map.of(a, 0L), Map.of(a, Value.integer(2)), Set.of()));
            assertThrows(ConflictException.class, () -> shared.sharedCommit(Map.of(a, 1L), Map.of(a, Value.integer(2)), Set.of()));
            shared.sharedCommit(Map.of(a, 2L), Map.of(a, Value.integer(2)), Set.of());
            assertEquals(3, shared.sharedRead(a).revision());
            assertEquals(Value.bool(true), shared.sharedRead(world).value());
            assertEquals(Value.text("other"), shared.sharedRead(other).value());
        }
    }
    @Test void racingConnectionsCannotLoseUpdate() throws Exception {
        try (var first = SQLiteVariables.openShared(root); var second = SQLiteVariables.openShared(root); var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> left = () -> { start.await(); try { first.sharedCommit(Map.of(a, 0L), Map.of(a, Value.integer(1)), Set.of()); return true; } catch (ConflictException e) { return false; } };
            Callable<Boolean> right = () -> { start.await(); try { second.sharedCommit(Map.of(a, 0L), Map.of(a, Value.integer(2)), Set.of()); return true; } catch (ConflictException e) { return false; } };
            var x = pool.submit(left); var y = pool.submit(right); start.countDown();
            assertNotEquals(x.get(), y.get());
            assertEquals(first.sharedRead(a), second.sharedRead(a));
        }
    }
    @Test void invalidInputsAndRevisionOverflowLeaveDatabaseUnchanged() throws Exception {
        try (var state = SQLiteVariables.openShared(root)) {
            assertThrows(IllegalArgumentException.class, () -> state.sharedCommit(Map.of(a, 0L), Map.of(b, Value.bool(true)), Set.of()));
            assertThrows(IllegalArgumentException.class, () -> state.sharedCommit(Map.of(a, 0L), Map.of(a, Value.bool(true)), Set.of(a)));
            assertThrows(IllegalArgumentException.class, () -> state.sharedCommit(Map.of(a, 0L), Map.of(a, Value.text("\uD800")), Set.of()));
            var oversized = new HashMap<Key, Long>();
            for (int i = 0; i < 129; i++) oversized.put(new Key("demo", null, "k" + i), 0L);
            assertThrows(IllegalArgumentException.class, () -> state.sharedCommit(oversized, Map.of(), Set.of()));
            assertTrue(state.snapshot().isEmpty());
            state.sharedCommit(Map.of(a, 0L), Map.of(a, Value.integer(1)), Set.of());
        }
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("shared.sqlite")); var statement = connection.createStatement()) {
            statement.execute("UPDATE variables SET revision=9223372036854775807");
        }
        try (var state = SQLiteVariables.openShared(root)) {
            assertThrows(ArithmeticException.class, () -> state.sharedCommit(Map.of(a, Long.MAX_VALUE, b, 0L), Map.of(b, Value.integer(2)), Set.of(a)));
            assertEquals(Long.MAX_VALUE, state.sharedRead(a).revision());
            assertEquals(0, state.sharedRead(b).revision());
        }
    }
    @Test void durableTombstonesRemainWithinNamespaceBound() throws Exception {
        try (var state = SQLiteVariables.openShared(root)) {
            for (int offset = 0; offset < MAX_KEYS; offset += MAX_BATCH) {
                var expected = new HashMap<Key, Long>(); var deletes = new HashSet<Key>();
                for (int i = offset; i < offset + MAX_BATCH; i++) { Key key = new Key("demo", null, "k" + i); expected.put(key, 0L); deletes.add(key); }
                state.sharedCommit(expected, Map.of(), deletes);
            }
            assertTrue(state.snapshot().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> state.sharedCommit(Map.of(a, 0L), Map.of(a, Value.bool(true)), Set.of()));
            assertEquals(0, state.sharedRead(a).revision());
        }
    }
}
