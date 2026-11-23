package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AiDocsTest {
    @Test void routesAndQuickstartReportTheInstalledVersion() throws Exception {
        String summary = AiDocs.summary(BuildInfo.VERSION);
        var routes = FeatureParser.JSON.readTree(summary);
        try (var input = AiDocsTest.class.getResourceAsStream("/aetherscript-ai-docs-budget-v1.json")) {
            var limits = FeatureParser.JSON.readTree(input);
            int full = AuthoringGuide.metadata().toString().getBytes(StandardCharsets.UTF_8).length;
            int compact = summary.getBytes(StandardCharsets.UTF_8).length;
            assertTrue(compact <= limits.path("maximumSummaryBytes").asInt());
            assertTrue((double) compact / full <= limits.path("maximumSummaryToFullRatio").asDouble());
            assertEquals(AiDocs.MAX_RESPONSE_BYTES, limits.path("maximumResponseBytes").asInt());
        }
        assertEquals(BuildInfo.VERSION, routes.path("version").asText());
        assertEquals("aether_docs_read", routes.path("tool").asText());
        var start = FeatureParser.JSON.readTree(AiDocs.read(BuildInfo.VERSION, "quickstart"));
        assertEquals(BuildInfo.VERSION, start.path("version").asText());
        assertTrue(start.path("content").asText().contains("aether_capabilities"));
        assertThrows(ValidationException.class, () -> AiDocs.read("0.0.0", "quickstart"));
    }

    @Test void focusedReadIsSmallerAndFullGuideCanBeReassembled() throws Exception {
        String focused = AiDocs.read(BuildInfo.VERSION, "kits");
        assertTrue(focused.getBytes(StandardCharsets.UTF_8).length < AuthoringGuide.metadata().toString().getBytes(StandardCharsets.UTF_8).length);
        String section = "reference";
        var joined = new StringBuilder();
        while (!section.isEmpty()) {
            var page = FeatureParser.JSON.readTree(AiDocs.read(BuildInfo.VERSION, section));
            joined.append(page.path("content").asText());
            section = page.path("nextSection").asText();
        }
        assertEquals(AuthoringGuide.metadata(), FeatureParser.JSON.readTree(joined.toString()).path("contract"));
    }
}
