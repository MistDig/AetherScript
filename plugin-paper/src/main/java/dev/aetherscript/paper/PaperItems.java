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
                if (descriptor.model() != null) meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString(descriptor.model())));
                meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING,
                    feature + ":" + descriptor.customId());
            });
        }
        return stack;
    }

    ItemStack icon(ScriptValue.ItemDescriptor descriptor, String feature) {
        return singleStack(descriptor, feature, "MENU_ICON_LIMIT");
    }

    ItemStack drop(ScriptValue.ItemDescriptor descriptor, String feature) {
        return singleStack(descriptor, feature, "BLOCK_DROP_LIMIT");
    }

    private ItemStack singleStack(ScriptValue.ItemDescriptor descriptor, String feature, String error) {
        var stack = template(descriptor, feature);
        if (descriptor.amount() > stack.getMaxStackSize())
            throw new ValidationException("$", error + ": amount exceeds the material's stack size");
        stack.setAmount(descriptor.amount());
        return stack;
    }

    boolean inventory(String operation, PlayerInventory inventory, List<ScriptValue> values, String feature) {
        ItemStack[] original = inventory.getStorageContents();
        if (operation.equals("player.inventory.exchange") || operation.equals("player.inventory.exchange_many")) {
            boolean bundles = operation.equals("player.inventory.exchange_many");
            List<ScriptValue> payments = bundles ? values.getFirst().list() : List.of(values.getFirst());
            List<ScriptValue> rewards = bundles ? values.get(1).list() : List.of(values.get(1));
            if (payments.isEmpty() || rewards.isEmpty() || payments.size() > 16 || rewards.size() > 16)
                throw new ValidationException("$", "INVENTORY_EXCHANGE_LIMIT: each bundle needs 1..16 items");
            int maximum = inventory.getMaxStackSize();
            ItemStack[] withoutPayments = planBundle(original, payments, feature, maximum, false);
            if (withoutPayments == null) return false;
            ItemStack[] withRewards = planBundle(withoutPayments, rewards, feature, maximum, true);
            if (withRewards == null) return false;
            inventory.setStorageContents(withRewards);
            return true;
        }
        var descriptor = values.getFirst().item();
        var template = template(descriptor, feature);
        int maximum = inventory.getMaxStackSize();
        var slots = slots(original, template, maximum);
        var item = planItem(template, descriptor.amount(), maximum);
        if (operation.equals("player.inventory.has")) return InventoryPlan.has(slots, item);
        var next = operation.equals("player.inventory.give") ? InventoryPlan.give(slots, List.of(item)) : InventoryPlan.remove(slots, item);
        if (next == null) return false;
        ItemStack[] updated = contents(original, slots, next, template);
        inventory.setStorageContents(updated);
        return true;
    }

    private ItemStack[] planBundle(ItemStack[] contents, List<ScriptValue> bundle, String feature, int maximum, boolean give) {
        ItemStack[] planned = contents;
        for (ScriptValue value : bundle) {
            var descriptor = value.item();
            var template = template(descriptor, feature);
            var before = slots(planned, template, maximum);
            var item = planItem(template, descriptor.amount(), maximum);
            var after = give ? InventoryPlan.give(before, List.of(item)) : InventoryPlan.remove(before, item);
            if (after == null) return null;
            planned = contents(planned, before, after, template);
        }
        return planned;
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
