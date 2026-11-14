package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.aetherscript.core.PersistentVariables.*;

class PersistentVariablesTest {
    @TempDir Path directory;
    private final Key global = new Key("example", null, "counter");
    @Test void typedScopesRevisionsAndRestart() throws Exception {
        Key player = new Key("example", UUID.randomUUID(), "counter");
        Key feature = new Key("other", null, "counter");
        try (var store = new PersistentVariables(directory)) {
            store.commit(List.of(new Mutation(global, 0, Value.integer(5)), new Mutation(player, 0, Value.bool(true)), new Mutation(feature, 0, Value.text("hello"))));
            store.commit(List.of(new Mutation(global, 1, Value.integer(6))));
            assertEquals(2, store.get(global).revision());
            assertThrows(UnsupportedOperationException.class, () -> store.snapshot().clear());
        }
        try (var reopened = new PersistentVariables(directory)) {
            assertEquals(Value.integer(6), reopened.get(global).value());
            assertEquals(Value.bool(true), reopened.get(player).value());
            assertEquals(Value.text("hello"), reopened.get(feature).value());
            assertEquals(2, reopened.get(global).revision());
        }
    }
    @Test void conflictAndInvalidBatchNeverPartiallyCommit() throws Exception {
        Key other = new Key("example", null, "other");
        try (var store = new PersistentVariables(directory)) {
            store.commit(List.of(new Mutation(global, 0, Value.integer(1))));
            byte[] before = Files.readAllBytes(directory.resolve("variables.json"));
            assertThrows(ConflictException.class, () -> store.commit(List.of(new Mutation(other, 0, Value.text("new")), new Mutation(global, 0, Value.integer(2)))));
            assertNull(store.get(other)); assertArrayEquals(before, Files.readAllBytes(directory.resolve("variables.json")));
            assertThrows(IllegalArgumentException.class, () -> store.commit(List.of(new Mutation(other, 0, Value.bool(true)), new Mutation(other, 0, Value.bool(false)))));
            assertNull(store.get(other));
            assertThrows(IllegalArgumentException.class, () -> Value.text("x".repeat(MAX_TEXT_BYTES + 1)));
            assertThrows(IllegalArgumentException.class, () -> new Key("example", null, "../escape"));
            assertThrows(IllegalArgumentException.class, () -> store.commit(Collections.nCopies(MAX_BATCH + 1, new Mutation(other, 0, Value.bool(true)))));
        }
    }
    @Test void interruptedSaveRetainsCommittedStateAndIgnoresOrphans() throws Exception {
        try (var store = new PersistentVariables(directory)) { store.commit(List.of(new Mutation(global, 0, Value.integer(1)))); }
        try (var failing = new PersistentVariables(directory, boundary -> { throw new IOException("simulated crash before replace"); })) {
            assertThrows(IOException.class, () -> failing.commit(List.of(new Mutation(global, 1, Value.integer(2)))));
            assertEquals(Value.integer(1), failing.get(global).value());
        }
        Files.writeString(directory.resolve(".variables-pending-orphan"), "not committed");
        try (var reopened = new PersistentVariables(directory)) { assertEquals(Value.integer(1), reopened.get(global).value()); }
    }
    @Test void corruptionFailsClosedAndExclusiveWriterReleasesLock() throws Exception {
        try (var store = new PersistentVariables(directory)) {
            assertThrows(IOException.class, () -> new PersistentVariables(directory));
            store.commit(List.of(new Mutation(global, 0, Value.integer(1))));
        }
        Path file = directory.resolve("variables.json");
        Files.writeString(file, Files.readString(file).replace("INTEGER", "BOOLEAN"));
        IOException error = assertThrows(IOException.class, () -> new PersistentVariables(directory));
        assertTrue(error.getMessage().contains("STORAGE_CORRUPT"));
        assertThrows(IOException.class, () -> new PersistentVariables(directory));
    }
    @Test void concurrentCompareAndSwapHasOneWinner() throws Exception {
        try (var store = new PersistentVariables(directory); var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var wins = new java.util.concurrent.atomic.AtomicInteger();
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> {
                try { start.await(); store.commit(List.of(new Mutation(global, 0, Value.integer(1)))); wins.incrementAndGet(); }
                catch (ConflictException expected) { }
                catch (Exception unexpected) { throw new RuntimeException(unexpected); }
            }));
            start.countDown(); for (var task : tasks) task.get();
            assertEquals(1, wins.get()); assertEquals(1, store.get(global).revision());
        }
    }
    @Test void postReplacementFlushFailureKeepsMemoryAndDiskInAgreement() throws Exception {
        try (var store = new PersistentVariables(directory, boundary -> {
            if (boundary.equals("AFTER_REPLACE")) throw new IOException("simulated directory flush failure");
        })) {
            store.commit(List.of(new Mutation(global, 0, Value.integer(1))));
            assertFalse(store.directoryFlushSupported());
            assertEquals(1, store.get(global).revision());
            store.commit(List.of(new Mutation(global, 1, Value.integer(1))));
            assertEquals(2, store.get(global).revision());
        }
        try (var reopened = new PersistentVariables(directory)) {
            assertEquals(new Entry(2, Value.integer(1)), reopened.get(global));
        }
    }
    @Test void keyLimitRejectsWholeBatch() throws Exception {
        try (var store = new PersistentVariables(directory)) {
            for (int offset = 0; offset < MAX_KEYS; offset += MAX_BATCH) {
                var batch = new ArrayList<Mutation>();
                for (int i = offset; i < offset + MAX_BATCH; i++)
                    batch.add(new Mutation(new Key("example", null, "key_" + i), 0, Value.bool(true)));
                store.commit(batch);
            }
            byte[] before = Files.readAllBytes(directory.resolve("variables.json"));
            assertThrows(IllegalArgumentException.class, () -> store.commit(List.of(new Mutation(global, 0, Value.integer(1)))));
            assertEquals(MAX_KEYS, store.snapshot().size());
            assertNull(store.get(global));
            assertArrayEquals(before, Files.readAllBytes(directory.resolve("variables.json")));
        }
    }
    @Test void validChecksumDoesNotPermitCoercedValueTypes() throws Exception {
        try (var store = new PersistentVariables(directory)) { store.commit(List.of(new Mutation(global, 0, Value.integer(1)))); }
        Path file = directory.resolve("variables.json");
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) FeatureParser.JSON.readTree(Files.readAllBytes(file));
        var value = (com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("rows").get(0).path("entry").path("value");
        value.put("integer", 1.5);
        envelope.put("checksum", FeatureParser.hash(FeatureParser.JSON.writeValueAsBytes(envelope.get("rows"))));
        Files.write(file, FeatureParser.JSON.writeValueAsBytes(envelope));
        IOException failure = assertThrows(IOException.class, () -> new PersistentVariables(directory));
        assertTrue(failure.getMessage().contains("STORAGE_CORRUPT"));
    }

}
