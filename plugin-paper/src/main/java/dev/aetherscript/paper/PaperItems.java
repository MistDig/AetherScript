package dev.aetherscript.paper;

import dev.aetherscript.core.ScriptValue;
import dev.aetherscript.core.ValidationException;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

final class PaperItems {
    private final NamespacedKey idKey;

    PaperItems(Plugin plugin) { idKey = new NamespacedKey(plugin, "custom_item"); }

    ItemStack template(ScriptValue.ItemDescriptor descriptor, String feature) {
        var material = Material.matchMaterial(descriptor.material());
        if (material == null || !material.isItem() || material.isAir())
            throw new ValidationException("$", "ITEM_MATERIAL_INVALID");
        var stack = new ItemStack(material);
        if (descriptor.custom()) {
            if (!feature.equals(descriptor.featureId()))
                throw new ValidationException("$", "CUSTOM_ITEM_OWNER_MISMATCH");
            stack.editMeta(meta -> {
                meta.displayName(Component.text(descriptor.displayName()));
                meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING,
                    feature + ":" + descriptor.customId());
            });
        }
        return stack;
    }

    ItemStack icon(ScriptValue.ItemDescriptor descriptor, String feature) {
        var stack = template(descriptor, feature);
        if (descriptor.amount() > stack.getMaxStackSize())
            throw new ValidationException("$", "MENU_ICON_LIMIT: amount exceeds the material's stack size");
        stack.setAmount(descriptor.amount());
        return stack;
    }

    boolean inventory(String operation, PlayerInventory inventory, List<ScriptValue> values, String feature) {
        ItemStack[] original = inventory.getStorageContents();
        var descriptor = values.getFirst().item();
        var template = template(descriptor, feature);
        int maximum = inventory.getMaxStackSize();
        var slots = slots(original, template, maximum);
        var item = planItem(template, descriptor.amount(), maximum);
        if (operation.equals("player.inventory.has")) return InventoryPlan.has(slots, item);
        var next = operation.equals("player.inventory.give") ? InventoryPlan.give(slots, List.of(item)) : InventoryPlan.remove(slots, item);
        if (next == null) return false;
        ItemStack[] updated = contents(original, slots, next, template);
        if (operation.equals("player.inventory.exchange")) {
            var reward = values.get(1).item();
            var target = template(reward, feature);
            var available = slots(updated, target, maximum);
            var exchange = InventoryPlan.give(available, List.of(planItem(target, reward.amount(), maximum)));
            if (exchange == null) return false;
            updated = contents(updated, available, exchange, target);
        }
        inventory.setStorageContents(updated);
        return true;
    }

    private static InventoryPlan.Item planItem(ItemStack template, int amount, int maximum) {
        return new InventoryPlan.Item(template.getType().name(), amount, Math.min(maximum, template.getMaxStackSize()));
    }

    private static InventoryPlan.Slot[] slots(ItemStack[] contents, ItemStack template, int maximum) {
        var slots = new InventoryPlan.Slot[contents.length];
        for (int i = 0; i < contents.length; i++) {
            var stack = contents[i];
            if (stack != null && !stack.getType().isAir()) {
                slots[i] = new InventoryPlan.Slot(stack.getType().name(), stack.getAmount(),
                    Math.min(maximum, stack.getMaxStackSize()), stack.isSimilar(template));
            }
        }
        return slots;
    }

    private static ItemStack[] contents(ItemStack[] original, InventoryPlan.Slot[] before,
                                        InventoryPlan.Slot[] after, ItemStack template) {
        var result = original.clone();
        for (int i = 0; i < after.length; i++) {
            if (Objects.equals(before[i], after[i])) continue;
            result[i] = after[i] == null ? null : template.asQuantity(after[i].amount());
        }
        return result;
    }
}
