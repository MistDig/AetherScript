package dev.aetherscript.core;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.events.*;
import org.yaml.snakeyaml.nodes.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** No YAML constructors, object binding, polymorphic deserialization, or executable values. */
public final class FeatureParser {
    public static final int MAX_BYTES = 65536, MAX_DEPTH = 32, MAX_TEXT = 2048, MAX_ACTIONS = 32;
    public static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_DEPTH).maxStringLength(MAX_BYTES).maxNumberLength(64).build())
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public CompiledFeature parse(byte[] source, String format) {
        if (source.length > MAX_BYTES) throw fail("$", "source exceeds 65536 bytes");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(source)).toString();
            JsonNode root;
            if (format.equals("json")) {
                try (JsonParser parser = JSON.createParser(text)) {
                    root = JSON.readTree(parser);
                    if (parser.nextToken() != null) throw fail("$", "one document required");
                }
            } else if (format.equals("yaml") || format.equals("yml")) {
                LoaderOptions options = new LoaderOptions();
                options.setCodePointLimit(MAX_BYTES); options.setNestingDepthLimit(MAX_DEPTH);
                options.setMaxAliasesForCollections(0); options.setAllowDuplicateKeys(false);
                Yaml yaml = new Yaml(options);
                int depth = 0, docs = 0;
                for (Event event : yaml.parse(new StringReader(text))) {
                    if (event instanceof AliasEvent || (event instanceof NodeEvent n && n.getAnchor() != null))
                        throw fail("$", "anchors and aliases are forbidden");
                    if (event instanceof DocumentStartEvent && ++docs > 1) throw fail("$", "one document required");
                    if (event instanceof CollectionStartEvent && ++depth > MAX_DEPTH) throw fail("$", "nesting limit");
                    if (event instanceof CollectionEndEvent) depth--;
                    if (event instanceof ScalarEvent s && s.getValue().length() > MAX_BYTES) throw fail("$", "scalar limit");
                    if (event instanceof ScalarEvent s && s.getTag() != null) throw fail("$", "explicit tags forbidden");
                    if (event instanceof CollectionStartEvent c && c.getTag() != null) throw fail("$", "explicit tags forbidden");
                }
                root = yamlNode(yaml.compose(new StringReader(text)), 0);
            } else throw fail("$", "format must be yaml or json");
            fields(root, "$", "schemaVersion", "id", "version", "requires", "commands");
            if (!root.get("schemaVersion").isIntegralNumber() || !root.get("schemaVersion").canConvertToInt() || root.get("schemaVersion").intValue() != 1)
                throw fail("$.schemaVersion", "only version 1 is supported");
            String id = string(root.get("id"), "$.id", 64);
            requireId(id);
            String version = string(root.get("version"), "$.version", 64);
            JsonNode requires = root.get("requires");
            if (!requires.isArray() || requires.size() != 1 || !requires.get(0).isTextual() || !requires.get(0).textValue().equals("runtime.message.send"))
                throw fail("$.requires", "must exactly declare inferred runtime.message.send capability");
            JsonNode commands = root.get("commands");
            if (!commands.isArray() || commands.size() != 1) throw fail("$.commands", "v0.1 supports exactly one command");
            JsonNode command = commands.get(0);
            fields(command, "$.commands[0]", "name", "permission", "sender", "actions");
            String name = string(command.get("name"), "$.commands[0].name", 64);
            requireId(name);
            String permission = string(command.get("permission"), "$.commands[0].permission", 128);
            if (!permission.matches("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+")) throw fail("$.commands[0].permission", "explicit permission node required; wildcards forbidden");
            String sender = string(command.get("sender"), "$.commands[0].sender", 16);
            if (!Set.of("any", "player").contains(sender)) throw fail("$.commands[0].sender", "expected any or player");
            JsonNode actions = command.get("actions");
            if (!actions.isArray() || actions.isEmpty() || actions.size() > MAX_ACTIONS) throw fail("$.commands[0].actions", "expected 1..32 actions");
            List<Feature.MessageAction> result = new ArrayList<>();
            for (int i = 0; i < actions.size(); i++) {
                String path = "$.commands[0].actions[" + i + "]";
                JsonNode action = actions.get(i);
                fields(action, path, "type", "target", "text");
                if (!"message.send".equals(string(action.get("type"), path + ".type", 64))) throw fail(path + ".type", "unsupported action");
                if (!"sender".equals(string(action.get("target"), path + ".target", 64))) throw fail(path + ".target", "only invoking sender is permitted");
                String message = string(action.get("text"), path + ".text", MAX_TEXT);
                if (message.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) throw fail(path + ".text", "plain text only; controls and legacy formatting forbidden");
                result.add(new Feature.MessageAction(message));
            }
            Feature feature = new Feature(1, id, version, List.of("runtime.message.send"), new Feature.Command(name, permission, sender, result));
            return new CompiledFeature(feature, hash(source), hash(JSON.writeValueAsBytes(feature)));
        } catch (ValidationException e) { throw e; }
        catch (Exception e) { throw fail("$", "invalid encoding or document structure"); }
    }
    public static void requireId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9-]{0,63}")) throw fail("$.id", "expected lowercase logical ID, not a path");
    }
    private static JsonNode yamlNode(Node node, int depth) {
        if (node == null || depth > MAX_DEPTH) throw fail("$", "empty document or nesting limit");
        if (node instanceof MappingNode m && node.getTag().equals(Tag.MAP)) {
            ObjectNode out = JSON.createObjectNode();
            for (NodeTuple pair : m.getValue()) {
                if (!(pair.getKeyNode() instanceof ScalarNode s) || !s.getTag().equals(Tag.STR)) throw fail("$", "string keys required");
                if (out.has(s.getValue())) throw fail("$", "duplicate key");
                out.set(s.getValue(), yamlNode(pair.getValueNode(), depth + 1));
            }
            return out;
        }
        if (node instanceof SequenceNode s && node.getTag().equals(Tag.SEQ)) {
            ArrayNode out = JSON.createArrayNode();
            for (Node child : s.getValue()) out.add(yamlNode(child, depth + 1));
            return out;
        }
        if (node instanceof ScalarNode s) {
            if (s.getTag().equals(Tag.STR)) return TextNode.valueOf(s.getValue());
            if (s.getTag().equals(Tag.INT) && s.getValue().matches("0|[1-9][0-9]{0,9}")) return LongNode.valueOf(Long.parseLong(s.getValue()));
        }
        throw fail("$", "unsupported scalar or tag; quote string values");
    }
    private static void fields(JsonNode node, String path, String... expected) {
        if (node == null || !node.isObject()) throw fail(path, "object required");
        Set<String> allowed = Set.of(expected);
        node.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw fail(path, "unknown field: " + k.substring(0, Math.min(k.length(), 64))); });
        for (String key : expected) if (!node.hasNonNull(key)) throw fail(path + "." + key, "required field");
    }
    private static String string(JsonNode n, String path, int max) {
        if (n == null || !n.isTextual() || n.textValue().isEmpty() || n.textValue().length() > max) throw fail(path, "nonempty string of at most " + max + " characters required");
        return n.textValue();
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static ValidationException fail(String path, String message) { return new ValidationException(path, message); }
}
