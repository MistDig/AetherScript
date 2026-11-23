package dev.aetherscript.paper;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/** Strict owner-console and scoped MCP operations for the third-party file registry. */
final class ServerFileOperations {
    private final AuthorizedServerFiles files;
    ServerFileOperations(AuthorizedServerFiles files) { this.files = files; }

    Map<String, Object> request(String connection, String operation, Map<String,Object> args) throws IOException {
        return switch (operation) {
            case "files.list" -> {
                fields(args);
                yield Map.of("targets", files.targetIds());
            }
            case "files.config.read" -> {
                fields(args, "target");
                yield Map.of("config", files.read(text(args, "target")));
            }
            case "files.config.preview" -> {
                fields(args, "target", "expectedHash", "replacements");
                if (!(args.get("replacements") instanceof Map<?, ?> replacements)) throw invalid("INVALID_ARGUMENTS");
                Map<String, Object> patch = new LinkedHashMap<>();
                for (var entry : replacements.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw invalid("INVALID_ARGUMENTS");
                    patch.put(key, entry.getValue());
                }
                yield Map.of("requiresConsoleApproval", true, "change", files.previewConfig(connection, text(args,"target"), text(args,"expectedHash"), patch));
            }
            case "files.jar.stage" -> {
                fields(args, "target", "filename", "sha256");
                yield Map.of("requiresConsoleApproval", true, "change", files.stageJarInbox(connection,
                    text(args, "target"), text(args, "filename"), text(args, "sha256")));
            }
            default -> throw invalid("FILE_OPERATION_UNKNOWN");
        };
    }

    List<String> console(String[] args, Predicate<String> activeConnection) throws IOException {
        if (args.length == 1) return List.of("Use ais files list, allow-config, allow-jar, pending, approve, deny, rollback or remove.");
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                count(args, 2, "ais files list");
                yield List.of("FILE_TARGETS " + files.targetIds());
            }
            case "allow-config" -> {
                count(args, 6, "ais files allow-config <id> <plugin/file> <json|yaml> <key[,key]>");
                AuthorizedServerFiles.Format format = switch(args[4].toLowerCase(Locale.ROOT)) {
                    case "json" -> AuthorizedServerFiles.Format.JSON;
                    case "yaml" -> AuthorizedServerFiles.Format.YAML;
                    default -> throw invalid("EXPECTED_JSON_OR_YAML");
                };
                files.defineConfig(args[2], args[3], format, Set.copyOf(Arrays.asList(args[5].split(",", -1))));
                yield List.of("FILE_TARGET_SAVED " + args[2]);
            }
            case "allow-jar" -> {
                count(args, 5, "ais files allow-jar <id> <jar-name> <plugin-name>");
                files.defineJar(args[2], args[3], args[4]);
                yield List.of("FILE_TARGET_SAVED " + args[2] + "; put reviewed artifacts in AetherScript/file-management/inbox");
            }
            case "pending" -> {
                count(args, 2, "ais files pending");
                var pending = files.pending();
                if (pending.isEmpty()) yield List.of("NO_PENDING_FILE_CHANGES");
                List<String> lines = new ArrayList<>();
                for (var change : pending) {
                    lines.add("FILE_CHANGE " + change.token() + " | " + change.kind() + " | " + change.target() + " | " + change.connection());
                    lines.add("Before " + change.beforeHash() + " | after " + change.afterHash());
                    change.details().forEach((field, value) -> lines.add(field + " = " + printable(value)));
                }
                yield lines;
            }
            case "approve" -> {
                count(args, 3, "ais files approve <change-token>");
                var change = files.pending().stream().filter(item -> item.token().equals(args[2])).findFirst()
                    .orElseThrow(() -> invalid("APPROVAL_INVALID"));
                if (!activeConnection.test(change.connection())) {
                    files.cancel(change.token());
                    throw invalid("CONNECTION_REVOKED");
                }
                var applied = files.approve(change.token(), change.connection(), activeConnection);
                yield List.of("FILE_APPLIED " + applied.target() + " sha256=" + applied.hash() + " rollback=" + applied.rollbackToken(),
                    applied.restartRequired() ? "Restart Paper manually to load the JAR." : "Use the third-party plugin's own reload or restart Paper to read the file.");
            }
            case "deny" -> {
                count(args, 3, "ais files deny <change-token>");
                files.cancel(args[2]); yield List.of("FILE_CHANGE_DENIED");
            }
            case "rollback" -> {
                count(args, 3, "ais files rollback <rollback-token>");
                var rolledBack = files.rollback(args[2]);
                yield List.of("FILE_ROLLED_BACK " + rolledBack.target() + " sha256=" + rolledBack.hash());
            }
            case "remove" -> {
                count(args, 3, "ais files remove <id>");
                files.undefine(args[2]); yield List.of("FILE_TARGET_REMOVED " + args[2]);
            }
            default -> throw invalid("FILE_COMMAND_UNKNOWN");
        };
    }

    private static String printable(Object value) {
        return Objects.toString(value).replaceAll("[\\p{Cc}\\p{Cf}]", " ");
    }
    private static void fields(Map<String, Object> args, String... expected) {
        if (!args.keySet().equals(Set.of(expected))) throw invalid("INVALID_ARGUMENTS");
    }
    private static String text(Map<String, Object> args, String key) {
        if (!(args.get(key) instanceof String value) || value.isEmpty()) throw invalid("INVALID_ARGUMENTS");
        return value;
    }
    private static void count(String[] args, int expected, String usage) {
        if (args.length != expected) throw invalid("Use " + usage);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
