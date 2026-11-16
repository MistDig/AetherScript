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
}
