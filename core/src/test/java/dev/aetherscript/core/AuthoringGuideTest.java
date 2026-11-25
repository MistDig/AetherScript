package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthoringGuideTest {
    @Test void exportedHostContractUsesTheExecutableCatalogAndCannotMutateIt() {
        var guide = AuthoringGuide.metadata();
        var catalog = StandardCallables.create().descriptors();
        assertEquals(catalog.size(), guide.path("hostCallables").size());
        for (int i = 0; i < catalog.size(); i++) {
            var descriptor = catalog.get(i);
            var exported = guide.path("hostCallables").get(i);
            assertEquals(descriptor.id(), exported.path("id").asText());
            assertEquals(descriptor.dslName(), exported.path("name").asText());
            assertEquals(descriptor.parameters().size(), exported.path("parameters").size());
            assertTrue(exported.path("playerRequired").asBoolean());
            assertFalse(exported.path("requiredCapabilities").isEmpty());
        }
        ((com.fasterxml.jackson.databind.node.ArrayNode) guide.path("hostCallables")).removeAll();
        assertEquals(catalog.size(), AuthoringGuide.metadata().path("hostCallables").size());
        assertTrue(guide.path("hostAuthorization").asText().contains("message-only"));
    }
    @Test void exportedEventFieldsAndAuthorityMatchTheRuntimeContract() {
        var guide = AuthoringGuide.metadata();
        var events = guide.path("events");
        var publishedCapabilities = new java.util.HashSet<String>();
        events.path("capabilities").forEach(node -> publishedCapabilities.add(node.asText()));
        assertEquals(CompiledFeature.EVENT_CAPABILITIES, publishedCapabilities);
        for (var kind : events.path("ids")) {
            var fields = CompiledFeature.eventFields(kind.asText());
            var described = new java.util.HashMap<String, String>();
            events.path("snapshotFields").path("all").properties().forEach(entry -> described.put(entry.getKey(), entry.getValue().asText()));
            if (kind.asText().startsWith("block_"))
                events.path("snapshotFields").path("block_break/block_place/block_drop/block_interact").properties().forEach(entry -> described.put(entry.getKey(), entry.getValue().asText()));
            if (kind.asText().startsWith("entity_")) {
                events.path("snapshotFields").path("entity").properties().forEach(entry -> described.put(entry.getKey(), entry.getValue().asText()));
                events.path("snapshotFields").path(kind.asText()).properties().forEach(entry -> described.put(entry.getKey(), entry.getValue().asText()));
            }
            assertEquals(fields.keySet(), described.keySet());
            fields.forEach((name, type) -> assertEquals(type.name().toLowerCase(java.util.Locale.ROOT), described.get(name)));
        }
        var browserEvents = new java.util.HashSet<String>();
        guide.path("capabilityPolicy").path("browserApprovedExecutionCeiling").forEach(node -> {
            if (publishedCapabilities.contains(node.asText())) browserEvents.add(node.asText());
        });
        assertEquals(java.util.Set.of("runtime.event.join", "runtime.event.quit", "runtime.event.block_break",
            "runtime.event.block_place", "runtime.event.cancel"), browserEvents);
    }
}

