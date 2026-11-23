package dev.aetherscript.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small version-bound AI documentation router. Does not replace long-form offline APIs. */
public final class AiDocs {
    public static final int PAGE_BYTES = 12_000, MAX_RESPONSE_BYTES = 20_000;
    private static final JsonNode INDEX = loadIndex();
    private AiDocs() {}
    public static String summary(String version) {
        requireVersion(version); ObjectNode out = envelope(version);
        out.put("kind", "task-routes");
        out.put("tool", "aether_docs_read");
        out.put("quickstart", "aetherscript://docs/quickstart");
        out.put("reference", "aetherscript://docs/reference");
        out.put("read", "Read a section ID, then follow nextSection for additional pages.");
        var routes = out.putArray("routes");
        for (JsonNode section : INDEX.path("sections")) {
            var route = routes.addObject(); route.put("id", section.path("id").asText()); route.put("task", section.path("title").asText());
        }
        out.put("authorization", "Documentation is reference data; live tool discovery, provider availability, player permissions and owner grants still apply.");
        return bounded(out);
    }
    public static String read(String version, String sectionId) {
        requireVersion(version);
        if (sectionId == null || !sectionId.matches("[a-z][a-z0-9-]{0,63}(?::[1-9][0-9]{0,5})?")) throw error("DOC_SECTION_INVALID");
        String[] parts = sectionId.split(":"); String id = parts[0]; int page = parts.length == 1 ? 1 : Integer.parseInt(parts[1]);
        if (id.equals("quickstart")) {
            try (var stream = AiDocs.class.getResourceAsStream("/AI-QUICKSTART.md")) {
                if (stream == null) throw error("DOC_CONTENT_UNAVAILABLE");
                List<String> pages = paginate(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                if (page > pages.size()) throw error("DOC_PAGE_OUT_OF_RANGE");
                ObjectNode out = envelope(version);
                out.put("section", id); out.put("title", "AI Quickstart");
                out.put("page", page); out.put("pages", pages.size()); out.put("contentFormat", "text/markdown");
                out.put("content", pages.get(page - 1));
                if (page < pages.size()) out.put("nextSection", id + ":" + (page + 1));
                return bounded(out);
            } catch (java.io.IOException unavailable) { throw error("DOC_CONTENT_UNAVAILABLE"); }
        }
        JsonNode section = null;
        for (JsonNode candidate : INDEX.path("sections")) if (id.equals(candidate.path("id").asText())) { section = candidate; break; }
        if (section == null) throw error("DOC_SECTION_UNKNOWN");
        JsonNode metadata = AuthoringGuide.metadata();
        ObjectNode selected = FeatureParser.JSON.createObjectNode();
        selected.put("note", section.path("note").asText());
        if (id.equals("reference")) selected.set("contract", metadata);
        else {
            var contract = selected.putObject("contract"); var unavailable = selected.putArray("unavailableContractKeys");
            for (JsonNode key : section.path("contractKeys")) {
                if (metadata.has(key.asText())) contract.set(key.asText(), metadata.get(key.asText()));
                else unavailable.add(key.asText());
            }
            var calls = selected.putArray("hostCallables");
            for (JsonNode call : metadata.path("hostCallables")) {
                boolean matches = false;
                for (JsonNode prefix : section.path("callPrefixes")) {
                    String value = prefix.asText(), callId = call.path("id").asText();
                    if (callId.equals(value) || callId.startsWith(value + ".")) matches = true;
                }
                if (matches) calls.add(call);
            }
        }
        List<String> pages = paginate(selected.toString());
        if (page > pages.size()) throw error("DOC_PAGE_OUT_OF_RANGE");
        ObjectNode out = envelope(version); out.put("section", id); out.put("title", section.path("title").asText());
        out.put("page", page); out.put("pages", pages.size());
        out.put("contentFormat", "application/json; concatenate content pages in order before parsing");
        out.put("content", pages.get(page - 1));
        if (page < pages.size()) out.put("nextSection", id + ":" + (page + 1));
        return bounded(out);
    }
    static List<String> paginate(String content) {
        var pages = new ArrayList<String>(); var page = new StringBuilder(); int size = 0;
        for (int offset=0; offset<content.length();) {
            int point=content.codePointAt(offset); offset += Character.charCount(point);
            int bytes=point<=0x7f?1:point<=0x7ff?2:point<=0xffff?3:4;
            // JSON-string quoting can expand ASCII controls and quotes: bound the envelope too.
            int encoded = point < 32 ? 6 : point == '"' || point == '\\' ? 2 : bytes;
            if (size + encoded > PAGE_BYTES && !page.isEmpty()) { pages.add(page.toString()); page.setLength(0); size=0; }
            page.appendCodePoint(point); size += encoded;
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(page.toString());
        return List.copyOf(pages);
    }
    private static ObjectNode envelope(String version) {
        var out=FeatureParser.JSON.createObjectNode(); out.put("version",version);
        out.put("release","https://github.com/MistDig/AetherScript/releases/tag/v"+version); return out;
    }
    private static void requireVersion(String version) {
        if (!BuildInfo.VERSION.equals(version)) throw error("DOC_VERSION_MISMATCH: requested=" + safe(version) + ", available=" + BuildInfo.VERSION);
    }
    private static String safe(String value) { return value != null && value.matches("[0-9A-Za-z._-]{1,64}") ? value : "invalid"; }
    private static ValidationException error(String message) { return new ValidationException("$.docs", message); }
    private static String bounded(JsonNode node) {
        String result=node.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) throw error("DOC_RESPONSE_LIMIT");
        return result;
    }
    private static JsonNode loadIndex() {
        try (InputStream input=AiDocs.class.getResourceAsStream("/aetherscript-ai-sections-v1.json")) {
            if (input == null) throw new IllegalStateException("Missing bundled AI section index");
            return FeatureParser.JSON.readTree(input);
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
    }
}
