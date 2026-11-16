package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptCapabilityPolicyTest {
    @TempDir Path root;
    static final Set<String> KNOWN = Set.of(ScriptCapabilityPolicy.MESSAGE, "player.location.read", "player.teleport.self", "state.own", "scheduler.delay");
    CompiledFeature compiled(String id, String... requirements) {
        return new CompiledFeature(new Feature(1, id, "1", List.of(requirements), new Feature.Command("hello", "test.hello", "any", List.of())), "hash", "ir");
    }
    @Test void browserApprovalStagesWithoutWritingAndPersistsOnlyWithinTheOwnerCeiling() throws Exception {
        Path file = root.resolve("policy.json"); var policy = new ScriptCapabilityPolicy(file, KNOWN);
        var feature = compiled("native-new", ScriptCapabilityPolicy.MESSAGE, "state.own");
        policy.validateConnection(feature, KNOWN);
        assertFalse(Files.exists(file), "Validation must not persist a capability approval");
        assertThrows(ValidationException.class, () -> policy.validateConnection(feature, Set.of(ScriptCapabilityPolicy.MESSAGE)));
        assertTrue(policy.approveConnection(feature, KNOWN));
        assertFalse(policy.approveConnection(feature, KNOWN));
        new ScriptCapabilityPolicy(file, KNOWN).validate(feature);
        policy.grant("native-new", Set.of(ScriptCapabilityPolicy.MESSAGE));
        assertThrows(ValidationException.class, () -> policy.validateConnection(feature, KNOWN), "An app cannot undo ais restrict");
        policy.removeGrant("native-new");
        assertFalse(new ScriptCapabilityPolicy(file, KNOWN).configured("native-new"));
        assertThrows(ValidationException.class, () -> ScriptCapabilityPolicy.messageOnly().validateConnection(feature, KNOWN));
    }
    @Test void exactOwnerCeilingsPersistAndCannotBeChangedThroughReturnedSets() throws Exception {
        Path file = root.resolve("policy.json"); var policy = new ScriptCapabilityPolicy(file, KNOWN);
        policy.validate(compiled("home", ScriptCapabilityPolicy.MESSAGE));
        assertThrows(ValidationException.class, () -> policy.validate(compiled("home", "state.own")));
        policy.grant("home", Set.of(ScriptCapabilityPolicy.MESSAGE, "state.own"));
        var reopened = new ScriptCapabilityPolicy(file, KNOWN);
        reopened.validate(compiled("home", "state.own"));
        assertThrows(ValidationException.class, () -> reopened.validate(compiled("home-other", "state.own")));
        assertThrows(UnsupportedOperationException.class, () -> reopened.allowed("home").clear());
        policy.grant("home", Set.of());
        assertThrows(ValidationException.class, () -> policy.validate(compiled("home", ScriptCapabilityPolicy.MESSAGE)));
    }
    @Test void corruptUnknownOversizedAndNonregularStorageFailsClosed() throws Exception {
        Path file = root.resolve("policy.json"); var policy = new ScriptCapabilityPolicy(file, KNOWN);
        policy.grant("home", Set.of("state.own"));
        String original = Files.readString(file);
        Files.writeString(file, original.replace("state.own", "state.bad"));
        assertThrows(java.io.IOException.class, () -> new ScriptCapabilityPolicy(file, KNOWN));
        Files.writeString(file, original);
        assertThrows(java.io.IOException.class, () -> new ScriptCapabilityPolicy(file, Set.of(ScriptCapabilityPolicy.MESSAGE)));
        Files.writeString(file, " ".repeat(65537));
        assertThrows(java.io.IOException.class, () -> new ScriptCapabilityPolicy(file, KNOWN));
        assertThrows(java.io.IOException.class, () -> new ScriptCapabilityPolicy(root, KNOWN));
        var fallback = ScriptCapabilityPolicy.messageOnly();
        fallback.validate(compiled("home", ScriptCapabilityPolicy.MESSAGE));
        assertThrows(ValidationException.class, () -> fallback.validate(compiled("home", "state.own")));
        assertThrows(java.io.IOException.class, () -> fallback.grant("home", Set.of(ScriptCapabilityPolicy.MESSAGE)));
    }
    @Test void failedSaveNeverPublishesNewGrantAndBoundsRejectUnknownIds() throws Exception {
        Path file = root.resolve("policy.json");
        Map<String, List<String>> entries = new TreeMap<>(); for (int i = 0; i < 128; i++) entries.put("feature" + i, List.of());
        byte[] payload = FeatureParser.JSON.writeValueAsBytes(entries);
        Files.write(file, FeatureParser.JSON.writeValueAsBytes(Map.of("formatVersion", 1, "grants", entries, "checksum", FeatureParser.hash(payload))));
        var policy = new ScriptCapabilityPolicy(file, KNOWN);
        assertThrows(IllegalArgumentException.class, () -> policy.grant("home", Set.of("unknown")));
        assertThrows(ValidationException.class, () -> policy.grant("../home", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> policy.grant("overflow", Set.of()));
        Files.delete(file); Files.createDirectory(file);
        assertThrows(java.io.IOException.class, () -> policy.grant("feature0", Set.of("state.own")));
        assertEquals(Set.of(), policy.allowed("feature0"));
    }
    @Test void parentSymlinksAndFileSymlinksAreRejectedWhenSupported() throws Exception {
        Path actual = Files.createDirectory(root.resolve("actual")), link = root.resolve("link");
        try { Files.createSymbolicLink(link, actual); } catch (java.io.IOException | UnsupportedOperationException | SecurityException unavailable) { return; }
        assertThrows(java.io.IOException.class, () -> new ScriptCapabilityPolicy(link.resolve("nested/policy.json"), KNOWN));
        Path target = actual.resolve("target"); Files.writeString(target, "{}");
        Path file = root.resolve("policy.json"); Files.createSymbolicLink(file, target);
        assertThrows(java.io.IOException.class, () -> new ScriptCapabilityPolicy(file, KNOWN));
    }
}
