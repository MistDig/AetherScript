package dev.aetherscript.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class ItemCallTest {
    private CompiledFeature compile(String body) {
        return new TextFrontend().parse(("feature kits version \"1\"\ncommand kits permission \"aether.kits\" sender player {\n" + body + "\n}\n").getBytes(StandardCharsets.UTF_8));
    }
    @Test void descriptorNormalizesBoundsAndPersistsWithoutChangingExistingWireTags() {
        var item = new ScriptValue(ScriptType.ITEM, new ScriptValue.ItemDescriptor("bread", 16));
        assertEquals("minecraft:bread", item.item().material());
        assertEquals(item, ScriptValueCodec.decode(ScriptValueCodec.encode(item)));
        var bundle = new ScriptValue(ScriptType.list(ScriptType.ITEM), List.of(item));
        assertEquals(bundle, ScriptValueCodec.decode(ScriptValueCodec.encode(bundle)));
        for (int amount : new int[] {0, -1, 2305}) assertThrows(IllegalArgumentException.class, () -> new ScriptValue.ItemDescriptor("bread", amount));
        for (String material : List.of("AIR", "other:bread", "bread;op")) assertThrows(IllegalArgumentException.class, () -> new ScriptValue.ItemDescriptor(material, 1));
        assertThrows(IllegalArgumentException.class, () -> ScriptType.set(ScriptType.ITEM));
    }
    @Test void kitAndInventoryCallsUseTypedDescriptorsAndCapabilities() throws Exception {
        var compiled = compile("let gift = item(\"bread\", 16)\nlet owned = inventory_has(gift)\nlet gave = inventory_give(gift)\nlet removed = inventory_remove(gift)\nlet result = kit_claim(\"daily\", [gift], 86400)\nsend result");
        assertEquals(Set.of("player.inventory.read", "player.inventory.write", "player.kit.claim", "runtime.message.send"), Set.copyOf(compiled.definition().requires()));
        List<String> called = new ArrayList<>();
        var context = new InvocationContext("kits", "rev", UUID.randomUUID(), Set.copyOf(compiled.definition().requires()), new ExecutionBudget(), () -> true,
            (descriptor, args, invocation) -> {
                called.add(descriptor.dslName());
                if (descriptor.dslName().equals("kit_claim")) {
                    assertEquals("minecraft:bread", args.get(1).list().getFirst().item().material());
                    assertEquals(86400, args.get(2).integer());
                    return new ScriptValue(ScriptType.TEXT, "claimed");
                }
                assertEquals(16, args.getFirst().item().amount());
                return new ScriptValue(ScriptType.BOOLEAN, true);
            }, called::add);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> compiled.program().evaluate(List.of(), context)).get();
        }
        assertEquals(List.of("inventory_has", "inventory_give", "inventory_remove", "kit_claim", "claimed"), called);
    }
    @Test void wrongTypesAndAmountsFailBeforeInventory() {
        assertThrows(ValidationException.class, () -> compile("let bad = item(1, 1)"));
        assertThrows(ValidationException.class, () -> compile("let bad = inventory_give(\"bread\")"));
        assertThrows(ValidationException.class, () -> compile("let bad = kit_claim(\"daily\", [\"bread\"], 86400)"));
        assertThrows(ValidationException.class, () -> compile("let bad = item(\"bread\", 0)").program().evaluate(List.of()));
        assertThrows(ValidationException.class, () -> compile("let bad = item(\"bread\", 9223372036854775807)").program().evaluate(List.of()));
    }
}
