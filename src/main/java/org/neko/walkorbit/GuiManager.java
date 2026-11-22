package org.neko.walkorbit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the menus. Every clickable item carries a PDC tag ("action" or "orbit"),
 * so clicks are identified by tag, never by display name or material.
 */
public final class GuiManager {
    private static final int ITEMS_PER_PAGE = 45;

    static final String ACTION_PREV = "prev";
    static final String ACTION_NEXT = "next";
    static final String ACTION_LANG = "lang";
    static final String ACTION_REMOVE = "remove";
    static final String ACTION_LANG_EN = "lang_en";
    static final String ACTION_LANG_JA = "lang_ja";

    private GuiManager() {
    }

    static NamespacedKey actionKey(WalkOrbit plugin) {
        return new NamespacedKey(plugin, "action");
    }

    static NamespacedKey orbitKey(WalkOrbit plugin) {
        return new NamespacedKey(plugin, "orbit");
    }

    public static void openMain(Player player, WalkOrbit plugin, int page) {
        LocaleManager lm = plugin.getLocaleManager();
        List<OrbitGroup> groups = new ArrayList<>(plugin.getGroups().values());
        int totalPages = Math.max(1, (groups.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        MenuHolder holder = new MenuHolder(MenuHolder.Type.MAIN, page);
        String title = lm.getString("gui.title.main", player,
                Map.of("page", String.valueOf(page + 1), "totalPages", String.valueOf(totalPages)));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, groups.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, orbitItem(groups.get(i), player, lm, plugin));
        }

        if (page > 0) inv.setItem(45, actionItem(plugin, Material.ARROW, lm.getString("gui.item.nav.prev", player), ACTION_PREV));
        inv.setItem(48, actionItem(plugin, Material.PLAYER_HEAD, lm.getString("gui.item.lang.name", player), ACTION_LANG));
        inv.setItem(49, actionItem(plugin, Material.BARRIER, lm.getString("gui.item.remove_trail.name", player), ACTION_REMOVE));
        if (end < groups.size()) inv.setItem(53, actionItem(plugin, Material.ARROW, lm.getString("gui.item.nav.next", player), ACTION_NEXT));

        player.openInventory(inv);
    }

    public static void openLanguage(Player player, WalkOrbit plugin) {
        LocaleManager lm = plugin.getLocaleManager();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.LANGUAGE, 0);
        Inventory inv = Bukkit.createInventory(holder, 9, lm.getString("gui.title.lang", player));
        holder.setInventory(inv);
        inv.setItem(3, actionItem(plugin, Material.RED_BANNER, lm.getString("gui.item.lang.en", player), ACTION_LANG_EN));
        inv.setItem(5, actionItem(plugin, Material.WHITE_BANNER, lm.getString("gui.item.lang.ja", player), ACTION_LANG_JA));
        player.openInventory(inv);
    }

    private static ItemStack actionItem(WalkOrbit plugin, Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.getPersistentDataContainer().set(actionKey(plugin), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack orbitItem(OrbitGroup group, Player player, LocaleManager lm, WalkOrbit plugin) {
        ItemStack item = new ItemStack(group.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(lm.getString("gui.item.trail.name", player,
                    Map.of("color", group.color().toString() + ChatColor.BOLD, "trail", group.name())));
            meta.setLore(List.of(lm.getString("gui.item.trail.lore", player)));
            meta.getPersistentDataContainer().set(orbitKey(plugin), PersistentDataType.STRING, group.name());
            item.setItemMeta(meta);
        }
        return item;
    }
}
