package dev.aetherscript.paper;

import dev.aetherscript.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureCommandsTest {
    private final Registry registry = new Registry();
    // Paper's map forwards get/put/remove and entrySet to Brigadier, while its
    // inherited HashMap bulk methods cannot be used to mutate the command tree.
    private static final class ForwardingBindings extends HashMap<String, Command> {
        private final Map<String, Command> tree = new HashMap<>();
        @Override public Command get(Object key) { return tree.get(key); }
        @Override public Command put(String key, Command value) { return tree.put(key, value); }
        @Override public Command remove(Object key) { return tree.remove(key); }
        @Override public int size() { return tree.size(); }
        @Override public boolean isEmpty() { return tree.isEmpty(); }
        @Override public Set<Map.Entry<String, Command>> entrySet() { return Collections.unmodifiableMap(tree).entrySet(); }
    }
    private final Map<String, Command> bindings = new ForwardingBindings();
    private boolean reject;
    private final CommandMap map = (CommandMap) Proxy.newProxyInstance(CommandMap.class.getClassLoader(), new Class<?>[]{CommandMap.class}, (proxy, method, args) -> {
        return switch (method.getName()) {
            case "getKnownCommands" -> bindings;
            case "getCommand" -> bindings.get(args[0]);
            case "register" -> {
                Command command = (Command) args[args.length - 1];
                bindings.put("aetherscript:" + command.getName(), command);
                if (reject) yield false; // Model a registration that mutates fallback before rejecting.
                bindings.put(command.getName(), command); command.register((CommandMap) proxy); yield true;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
    });
    private final FeatureCommands owner = new FeatureCommands(map, registry, () -> true);

    private CompiledFeature feature(String name, String sender) {
        return new CompiledFeature(new Feature(1, "demo", "1", List.of(), new Feature.Command(name, "demo.use", sender, List.of())), "source", "ir",
            args -> List.of(new Feature.MessageAction(String.join("|", args))));
    }

    @Test void replacementRetiresNamesAndClosePreservesForeignBindings() {
        Command foreign = new Command("foreign") { public boolean execute(CommandSender s, String l, String[] a) { return true; } };
        bindings.put("foreign", foreign);
        owner.activate(0, Map.of("demo", feature("first", "any")));
        owner.activate(1, Map.of("demo", feature("second", "any")));
        assertNull(bindings.get("first")); assertNull(bindings.get("aetherscript:first"));
        assertSame(bindings.get("second"), bindings.get("aetherscript:second"));
        owner.close(); assertEquals(Map.of("foreign", foreign), bindings);
    }

    @Test void foreignCollisionPreservesExactSnapshotAndBindings() {
        owner.activate(0, Map.of("demo", feature("first", "any")));
        Command foreign = new Command("taken") { public boolean execute(CommandSender s, String l, String[] a) { return true; } };
        for (String collision : List.of("taken", "aetherscript:taken")) {
            bindings.put(collision, foreign);
            var before = registry.snapshot(); var old = Map.copyOf(bindings);
            assertThrows(ValidationException.class, () -> owner.activate(before.generation(), Map.of("demo", feature("taken", "any"))));
            assertSame(before, registry.snapshot()); assertEquals(old, bindings);
            bindings.remove(collision);
        }
    }

    @Test void partialRegistrationFailureRestoresOldGenerationAndNames() {
        owner.activate(0, Map.of("demo", feature("first", "any")));
        var before = registry.snapshot(); var old = Map.copyOf(bindings); reject = true;
        assertThrows(ValidationException.class, () -> owner.activate(1, Map.of("demo", feature("second", "any"))));
        assertSame(before, registry.snapshot()); assertEquals(old, bindings);
        assertTrue(bindings.get("first").isRegistered());
    }

    @Test void commandForwardsArgumentsAndEnforcesPermissionAndPlayerConstraint() {
        List<Component> messages = new ArrayList<>(); boolean[] allowed = {true};
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class}, (p, method, args) -> {
            if (method.getName().equals("hasPermission")) return allowed[0] && args[0].equals("demo.use");
            if (method.getName().equals("sendMessage")) { messages.add((Component) args[0]); return null; }
            throw new UnsupportedOperationException(method.getName());
        });
        owner.activate(0, Map.of("demo", feature("first", "any")));
        bindings.get("first").execute(sender, "first", new String[]{"alpha", "beta"});
        assertEquals(List.of(Component.text("alpha|beta")), messages);
        messages.clear(); allowed[0] = false;
        bindings.get("first").execute(sender, "first", new String[]{"secret"});
        assertEquals(List.of(Component.text("Feature unavailable or permission denied.")), messages);
        messages.clear(); allowed[0] = true;
        owner.activate(1, Map.of("demo", feature("first", "player")));
        bindings.get("first").execute(sender, "first", new String[]{"secret"});
        assertEquals(List.of(Component.text("Feature unavailable or permission denied.")), messages);
    }

    @Test void registryValidationFailureRestoresOldGenerationAndNames() {
        owner.activate(0, Map.of("demo", feature("first", "any")));
        var before = registry.snapshot(); var old = Map.copyOf(bindings);
        assertThrows(ValidationException.class, () -> owner.activate(1, Map.of("wrong-id", feature("second", "any"))));
        assertSame(before, registry.snapshot()); assertEquals(old, bindings);
        assertTrue(bindings.get("first").isRegistered());
    }

    @Test void staleGenerationLeavesSnapshotAndCommandBindingsUntouched() {
        owner.activate(0, Map.of("demo", feature("first", "any")));
        var before = registry.snapshot(); var old = Map.copyOf(bindings);
        assertThrows(IllegalStateException.class, () -> owner.activate(0, Map.of("demo", feature("second", "any"))));
        assertSame(before, registry.snapshot()); assertEquals(old, bindings);
        assertTrue(bindings.get("first").isRegistered());
    }
}
