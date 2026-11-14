package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static dev.aetherscript.core.PersistentVariables.*;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteVariablesTest {
    @TempDir Path root;
    private final Key count = new Key("demo", null, "count");
    @Test void persistsTypedNamespaceAndPlayerValuesAcrossRestart() throws Exception {
        Key player = new Key("demo", UUID.randomUUID(), "count"), other = new Key("other", null, "count");
        Map<Key, Entry> expected;
        try (var state = new SQLiteVariables(root)) {
            expected = state.commit(List.of(new Mutation(count, 0, Value.integer(Long.MIN_VALUE)), new Mutation(player, 0, Value.text("hello ☃")), new Mutation(other, 0, Value.bool(true))));
        }
        try (var state = new SQLiteVariables(root)) { assertEquals(expected, state.snapshot()); }
    }
    @Test void competingConnectionsOnlyOneCasWinsAndBothObserveCommit() throws Exception {
        try (var first = new SQLiteVariables(root); var second = new SQLiteVariables(root); var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> left = () -> { start.await(); try { first.commit(List.of(new Mutation(count, 0, Value.integer(1)))); return true; } catch (ConflictException e) { return false; } };
            Callable<Boolean> right = () -> { start.await(); try { second.commit(List.of(new Mutation(count, 0, Value.integer(2)))); return true; } catch (ConflictException e) { return false; } };
            var a = pool.submit(left); var b = pool.submit(right); start.countDown();
            assertNotEquals(a.get(), b.get()); assertEquals(first.snapshot(), second.snapshot()); assertEquals(1, first.get(count).revision());
        }
    }
    @Test void invalidBatchAndConflictLeaveAllRowsUnchanged() throws Exception {
        try (var state = new SQLiteVariables(root)) {
            var before = state.commit(List.of(new Mutation(count, 0, Value.integer(1))));
            Key fresh = new Key("demo", null, "fresh");
            assertThrows(ConflictException.class, () -> state.commit(List.of(new Mutation(fresh, 0, Value.bool(true)), new Mutation(count, 0, Value.integer(2)))));
            assertThrows(IllegalArgumentException.class, () -> state.commit(List.of(new Mutation(count, 1, Value.integer(2)), new Mutation(count, 1, Value.integer(3)))));
            assertThrows(IllegalArgumentException.class, () -> state.commit(Collections.nCopies(129, new Mutation(fresh, 0, Value.bool(true)))));
            assertThrows(IllegalArgumentException.class, () -> state.commit(List.of(new Mutation(fresh, 0, Value.text("\uD800")))));
            assertEquals(before, state.snapshot());
        }
    }
    @Test void unknownSchemaAndInvalidCodecFailClosed() throws Exception {
        try (var state = new SQLiteVariables(root)) { state.commit(List.of(new Mutation(count, 0, Value.integer(1)))); }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("variables.sqlite")); var statement = connection.createStatement()) { statement.execute("PRAGMA user_version=2"); }
        assertThrows(IOException.class, () -> new SQLiteVariables(root));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + root.resolve("variables.sqlite")); var statement = connection.createStatement()) { statement.execute("PRAGMA user_version=1"); statement.execute("UPDATE variables SET value='01'"); }
        assertThrows(IOException.class, () -> new SQLiteVariables(root));
    }
    @Test void keyLimitRejectsWholeTransaction() throws Exception {
        try (var state = new SQLiteVariables(root)) {
            for (int offset = 0; offset < MAX_KEYS; offset += MAX_BATCH) {
                var batch = new ArrayList<Mutation>();
                for (int i = offset; i < offset + MAX_BATCH; i++) batch.add(new Mutation(new Key("demo", null, "k" + i), 0, Value.bool(false)));
                state.commit(batch);
            }
            assertThrows(IllegalArgumentException.class, () -> state.commit(List.of(new Mutation(count, 0, Value.bool(true)))));
            assertEquals(MAX_KEYS, state.snapshot().size());
        }
    }
    @Test void forgedVersionOneSchemaCannotCoerceRevisionTypes() throws Exception {
        for (String revision : List.of("'1oops'", "1.5")) {
            Path directory = root.resolve(revision.equals("1.5") ? "real" : "text");
            java.nio.file.Files.createDirectory(directory);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("variables.sqlite")); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE variables(feature TEXT, player TEXT, name TEXT, revision, kind TEXT, value TEXT)");
                statement.execute("PRAGMA user_version=1");
                statement.execute("INSERT INTO variables VALUES('demo','','count'," + revision + ",'INTEGER','1')");
            }
            assertThrows(IOException.class, () -> new SQLiteVariables(directory));
        }
    }
}
