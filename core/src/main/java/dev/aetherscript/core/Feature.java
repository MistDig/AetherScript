package dev.aetherscript.core;
import java.util.List;
public record Feature(int schemaVersion, String id, String version, List<String> requires, Command command) {
    public Feature { requires = List.copyOf(requires); }
    public record Command(String name, String permission, String sender, List<MessageAction> actions) {
        public Command { actions = List.copyOf(actions); }
    }
    public record MessageAction(String text) {}
}
