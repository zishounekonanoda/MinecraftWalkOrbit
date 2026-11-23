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
    static final String ACTION_STYLE_MENU = "style_menu";
    static final String ACTION_BACK = "back";
    /** Followed by a {@link WalkShape.Style} name, or "DEFAULT" to go back to the server's style. */
    static final String ACTION_STYLE_PREFIX = "style:";
    static final String STYLE_DEFAULT = "DEFAULT";
    static final String ACTION_CREATE = "create";
    static final String ACTION_DIALOG_TOGGLE = "dialog_toggle";

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
        if (player.hasPermission(OrbitBuilder.PERMISSION)) {
            inv.setItem(46, actionItem(plugin, Material.CRAFTING_TABLE, lm.getString("gui.item.create.name", player), ACTION_CREATE,
                    List.of(lm.getString("gui.item.create.lore", player))));
        }
        inv.setItem(47, actionItem(plugin, Material.COMPARATOR, lm.getString("gui.item.style.name", player), ACTION_STYLE_MENU,
                List.of(lm.getString("gui.item.style.lore", player))));
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

    /** Lets each player choose how their walk particles are laid out. */
    public static void openStyle(Player player, WalkOrbit plugin) {
        openStyle(player, plugin, false);
    }

    /**
     * Settings screen. Row 1: walk style. Row 2: whether menus open as dialogs.
     *
     * @param dialogChanged true right after the dialog setting was switched, to say so on the item
     */
    public static void openStyle(Player player, WalkOrbit plugin, boolean dialogChanged) {
        LocaleManager lm = plugin.getLocaleManager();
        MenuHolder holder = new MenuHolder(MenuHolder.Type.STYLE, 0);
        Inventory inv = Bukkit.createInventory(holder, 18, lm.getString("gui.title.style", player));
        holder.setInventory(inv);

        WalkShape.Style current = plugin.getWalkStyle(player);
        String selected = lm.getString("gui.item.style.selected", player);

        Object[][] options = {
                {2, WalkShape.Style.SIDES, Material.FEATHER},
                {3, WalkShape.Style.BACK, Material.LEATHER_CHESTPLATE},
                {4, WalkShape.Style.BACK_SUBTLE, Material.STRING},
                {5, WalkShape.Style.FEET, Material.LEATHER_BOOTS},
        };
        for (Object[] o : options) {
            WalkShape.Style style = (WalkShape.Style) o[1];
            // ".lore" can't be a child of the name key: Bukkit YAML reads dots in keys as nesting
            String id = style.name().toLowerCase(java.util.Locale.ROOT);
            String key = "gui.item.style." + id;
            List<String> lore = new ArrayList<>();
            lore.add(lm.getString("gui.item.style_desc." + id, player));
            if (style == current) lore.add(selected);
            ItemStack item = actionItem(plugin, (Material) o[2], lm.getString(key, player),
                    ACTION_STYLE_PREFIX + style.name(), lore);
            if (style == current) glow(item);
            inv.setItem((int) o[0], item);
        }

        List<String> defLore = new ArrayList<>();
        defLore.add(lm.getString("gui.item.style_desc.default", player));
        if (current == null) defLore.add(selected);
        ItemStack def = actionItem(plugin, Material.BOOK, lm.getString("gui.item.style.default", player),
                ACTION_STYLE_PREFIX + STYLE_DEFAULT, defLore);
        if (current == null) glow(def);
        inv.setItem(7, def);

        inv.setItem(0, actionItem(plugin, Material.ARROW, lm.getString("gui.item.back", player), ACTION_BACK));

        // Row 2: dialog on/off, with its state and (right after a change) a note that it applies next time
        boolean on = plugin.getMenus().isDialogEnabled(player);
        List<String> dlgLore = new ArrayList<>();
        dlgLore.add(lm.getString("gui.item.dialog.state", player) + lm.getString(on ? "gui.item.dialog.on" : "gui.item.dialog.off", player));
        dlgLore.add(lm.getString("gui.item.dialog.lore", player));
        if (dialogChanged) dlgLore.add(lm.getString("gui.item.dialog.changed", player));
        ItemStack dlg = actionItem(plugin, on ? Material.LIME_DYE : Material.GRAY_DYE,
                lm.getString("gui.item.dialog.name", player), ACTION_DIALOG_TOGGLE, dlgLore);
        if (dialogChanged) glow(dlg);
        inv.setItem(13, dlg);
        player.openInventory(inv);
    }

    private static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    private static ItemStack actionItem(WalkOrbit plugin, Material material, String name, String action) {
        return actionItem(plugin, material, name, action, null);
    }

    private static ItemStack actionItem(WalkOrbit plugin, Material material, String name, String action, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            meta.getPersistentDataContainer().set(actionKey(plugin), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack orbitItem(OrbitGroup group, Player player, LocaleManager lm, WalkOrbit plugin) {
        ItemStack item = new ItemStack(group.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Custom orbits show the name their creator typed, with its own &-colours
            Map<String, String> ph = group.displayName() != null
                    ? Map.of("color", "", "trail", ChatColor.translateAlternateColorCodes('&', group.displayName()) + ChatColor.RESET)
                    : Map.of("color", group.color().toString() + ChatColor.BOLD, "trail", group.name());
            meta.setDisplayName(lm.getString("gui.item.trail.name", player, ph));
            meta.setLore(List.of(lm.getString("gui.item.trail.lore", player)));
            meta.getPersistentDataContainer().set(orbitKey(plugin), PersistentDataType.STRING, group.name());
            item.setItemMeta(meta);
        }
        return item;
    }
}
