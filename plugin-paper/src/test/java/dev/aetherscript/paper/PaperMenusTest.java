package dev.aetherscript.paper;

import dev.aetherscript.core.ValidationException;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaperMenusTest {
    @Test void menuBoundsAndAutomaticRowsAreExplicit() {
        assertEquals(9, PaperMenus.size("Shop", 1));
        assertEquals(9, PaperMenus.size("Shop", 9));
        assertEquals(18, PaperMenus.size("Shop", 10));
        assertEquals(54, PaperMenus.size("x".repeat(128), 54));
        assertThrows(ValidationException.class, () -> PaperMenus.size("", 1));
        assertThrows(ValidationException.class, () -> PaperMenus.size("bad\nname", 1));
        assertThrows(ValidationException.class, () -> PaperMenus.size("Shop", 0));
        assertThrows(ValidationException.class, () -> PaperMenus.size("Shop", 55));
        assertThrows(ValidationException.class, () -> PaperMenus.size("x".repeat(129), 1));
    }
    @Test void onlyOrdinaryClicksOnPopulatedTopSlotsSelect() {
        for (ClickType type : ClickType.values()) {
            boolean normal = type == ClickType.LEFT || type == ClickType.RIGHT;
            assertEquals(normal, PaperMenus.selectable(0, 10, type), type.name());
            assertEquals(normal, PaperMenus.selectable(9, 10, type), type.name());
            assertFalse(PaperMenus.selectable(10, 10, type), "Unpopulated top slot: " + type);
            assertFalse(PaperMenus.selectable(18, 10, type), "Player inventory: " + type);
            assertFalse(PaperMenus.selectable(-999, 10, type), "Outside: " + type);
        }
    }
    @Test void cancellationIsInvocationOwnedAndRetirementCompletesWaiters() throws Exception {
        org.bukkit.Server server = (org.bukkit.Server) java.lang.reflect.Proxy.newProxyInstance(
            org.bukkit.Server.class.getClassLoader(), new Class<?>[]{org.bukkit.Server.class}, (proxy, method, args) -> {
                if (method.getName().equals("getPlayer")) return null;
                throw new AssertionError(method.getName());
            });
        PaperMenus menus = new PaperMenus(server, Runnable::run);
        var first = new PaperMenus.Owner("shop", "r1", java.util.UUID.randomUUID());
        var second = new PaperMenus.Owner("shop", "r1", java.util.UUID.randomUUID());
        var firstResult = insert(menus, first);
        var secondResult = insert(menus, second);
        menus.cancel(first);
        assertEquals(-1, firstResult.join());
        assertFalse(secondResult.isDone(), "Retiring one invocation must preserve the other");
        menus.reconcile(owner -> !owner.revision().equals("r1"));
        assertEquals(-1, secondResult.join());
        var thirdResult = insert(menus, new PaperMenus.Owner("other", "r2", java.util.UUID.randomUUID()));
        menus.close();
        assertEquals(-1, thirdResult.join());
        menus.close();
    }
    @SuppressWarnings("unchecked")
    private static java.util.concurrent.CompletableFuture<Integer> insert(PaperMenus menus, PaperMenus.Owner owner) throws Exception {
        Class<?> type = Class.forName("dev.aetherscript.paper.PaperMenus$Menu");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object menu = constructor.newInstance(owner, null, 1, (java.util.function.BooleanSupplier) () -> true);
        var field = PaperMenus.class.getDeclaredField("menus");
        field.setAccessible(true);
        ((java.util.Map<java.util.UUID, Object>) field.get(menus)).put(java.util.UUID.randomUUID(), menu);
        var result = type.getDeclaredField("result");
        result.setAccessible(true);
        return (java.util.concurrent.CompletableFuture<Integer>) result.get(menu);
    }}
