package dev.aetherscript.paper;

import dev.aetherscript.core.FeatureParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GameplayRuntimeCaptureTest {
    @Test void budgetCountsEscapedUtf8AndKeepsTruncationSticky() throws Exception {
        String value = "\"\\\n" + Character.toString(0x1f600);
        int encoded = FeatureParser.JSON.writeValueAsBytes(value).length + 1;
        var exact = new GameplayRuntime.CaptureBudget(encoded);
        assertTrue(exact.accept(value));
        assertFalse(exact.accept(""));
        assertTrue(exact.truncated());
        assertFalse(exact.accept("small"));
        var shortBudget = new GameplayRuntime.CaptureBudget(encoded - 1);
        assertFalse(shortBudget.accept(value));
        assertTrue(shortBudget.truncated());
        assertTrue(encoded > value.length());
    }
    @Test void errorsBoundEncodedBytesWithoutSplittingSupplementaryCharacters() throws Exception {
        String text = (Character.toString(0x1f600) + String.valueOf((char) 0)).repeat(2000);
        String bounded = GameplayRuntime.boundedError(text);
        assertTrue(text.startsWith(bounded));
        assertFalse(bounded.isEmpty());
        assertTrue(FeatureParser.JSON.writeValueAsBytes(bounded).length <= 2048);
        assertFalse(Character.isHighSurrogate(bounded.charAt(bounded.length() - 1)));
    }
    @Test void maximumCapturedSnapshotFitsDuplicatedMcpWireEnvelope() throws Exception {
        var messages = new ArrayList<String>();
        var receipts = new ArrayList<Map<String, Object>>();
        var output = new GameplayRuntime.CaptureBudget(12 * 1024);
        var receiptBudget = new GameplayRuntime.CaptureBudget(6 * 1024);
        String escaped = "\\".repeat(96);
        while (output.accept(escaped)) messages.add(escaped);
        Map<String, Object> receipt = Map.of("operation", "state.own.put_location", "key", escaped, "acknowledged", true);
        while (receiptBudget.accept(receipt)) receipts.add(receipt);
        Map<String, Object> snapshot = Map.ofEntries(
            Map.entry("testId", UUID.randomUUID().toString()), Map.entry("id", "nativehomes"),
            Map.entry("sourceHash", "a".repeat(64)), Map.entry("status", "failed"),
            Map.entry("actualMessages", messages), Map.entry("receipts", receipts),
            Map.entry("matched", false), Map.entry("outputTruncated", output.truncated()),
            Map.entry("receiptsTruncated", receiptBudget.truncated()),
            Map.entry("error", GameplayRuntime.boundedError(escaped.repeat(128))), Map.entry("line", 123), Map.entry("column", 42));
        assertTrue(FeatureParser.JSON.writeValueAsBytes(snapshot).length < 24 * 1024);
        Map<String, Object> envelope = Map.of("jsonrpc", "2.0", "id", 1, "result", Map.of(
            "structuredContent", snapshot,
            "content", List.of(Map.of("type", "text", "text", FeatureParser.JSON.writeValueAsString(snapshot))),
            "isError", false));
        assertTrue(FeatureParser.JSON.writeValueAsBytes(envelope).length < 65536);
        assertTrue(output.truncated()); assertTrue(receiptBudget.truncated());
    }
}
