package dev.aetherscript.paper;

import dev.aetherscript.core.ValidationException;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Main-thread-only, invocation-owned read-only chest selection. Icons never carry authority. */
final class PaperMenus implements Listener, AutoCloseable {
    record Owner(String feature, String revision, UUID invocation) {}
    private static final class Menu {
        final Owner owner;
        final Inventory inventory;
        final int items;
        final BooleanSupplier authorized;
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        boolean selecting;
        Menu(Owner owner, Inventory inventory, int items, BooleanSupplier authorized) {
            this.owner = owner; this.inventory = inventory; this.items = items; this.authorized = authorized;
        }
    }
    private final Server server;
    private final Consumer<Runnable> nextTick;
    private final Map<UUID, Menu> menus = new HashMap<>();
    private boolean closed;

    PaperMenus(JavaPlugin plugin) {
        this(plugin.getServer(), task -> plugin.getServer().getScheduler().runTask(plugin, task));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    PaperMenus(Server server, Consumer<Runnable> nextTick) { this.server = server; this.nextTick = nextTick; }

    CompletableFuture<Integer> choose(Player player, Owner owner, String title, List<ItemStack> icons, BooleanSupplier authorized) {
        if (closed || !authorized.getAsBoolean()) throw error("MENU_RETIRED");
        int size = size(title, icons.size());
        Inventory inventory = server.createInventory(null, size, Component.text(title));
        for (int slot = 0; slot < icons.size(); slot++) {
            ItemStack icon = Objects.requireNonNull(icons.get(slot));
            if (icon.getType().isAir() || !icon.getType().isItem() || icon.getAmount() < 1 || icon.getAmount() > icon.getMaxStackSize())
                throw error("MENU_ITEM_INVALID");
            inventory.setItem(slot, icon.clone());
        }
        UUID id = player.getUniqueId();
        finish(id, menus.get(id), -1, true);
        Menu menu = new Menu(owner, inventory, icons.size(), authorized);
        menus.put(id, menu);
        try {
            player.openInventory(inventory);
            if (player.getOpenInventory().getTopInventory() != inventory) finish(id, menu, -1, false);
        } catch (RuntimeException failure) { finish(id, menu, -1, true); throw failure; }
        return menu.result;
    }
    static int size(String title, int count) {
        if (title == null || title.isBlank() || title.length() > 128 || title.codePoints().anyMatch(Character::isISOControl)) throw error("MENU_TITLE_LIMIT: expected 1..128 plain characters");
        if (count < 1 || count > 54) throw error("MENU_ITEM_LIMIT: expected 1..54 items");
        return ((count + 8) / 9) * 9;
    }
    static boolean selectable(int rawSlot, int count, ClickType click) {
        return rawSlot >= 0 && rawSlot < count && (click == ClickType.LEFT || click == ClickType.RIGHT);
    }
    private Menu matching(UUID id, Inventory inventory) {
        Menu menu = menus.get(id);
        return menu != null && menu.inventory == inventory ? menu : null;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        UUID id = event.getWhoClicked().getUniqueId();
        Menu menu = matching(id, event.getView().getTopInventory());
        if (menu == null) return;
        boolean previouslyCancelled = event.isCancelled();
        event.setCancelled(true); // Includes bottom inventory, outside, shift, hotbar, double and drop clicks.
        if (!menu.selecting && !previouslyCancelled && selectable(event.getRawSlot(), menu.items, event.getClick())) {
            menu.selecting = true;
            int slot = event.getRawSlot();
            // Bukkit inventory changes are deferred out of the click callback.
            nextTick.accept(() -> {
                if (menus.get(id) == menu) finish(id, menu, menu.authorized.getAsBoolean() ? slot : -1, true);
            });
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (matching(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory()) != null) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) {
        if (menus.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler public void inventoryClosed(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        finish(id, matching(id, event.getInventory()), -1, false);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { forget(event.getPlayer().getUniqueId()); }
    void cancel(Owner owner) { retire(menu -> menu.owner.equals(owner)); }
    void forget(UUID player) { finish(player, menus.get(player), -1, true); }
    void reconcile(Predicate<Owner> active) { retire(menu -> !active.test(menu.owner)); }
    private void retire(Predicate<Menu> retired) {
        for (UUID id : List.copyOf(menus.keySet())) {
            Menu menu = menus.get(id);
            if (menu != null && retired.test(menu)) finish(id, menu, -1, true);
        }
    }
    private void finish(UUID id, Menu menu, int selected, boolean close) {
        if (menu == null || !menus.remove(id, menu)) return;
        Player player = server.getPlayer(id);
        try {
            if (close && player != null && player.getOpenInventory().getTopInventory() == menu.inventory) player.closeInventory();
        } finally { menu.result.complete(selected); }
    }
    @Override public void close() { closed = true; retire(menu -> true); }
    private static ValidationException error(String text) { return new ValidationException("$", text); }
}
