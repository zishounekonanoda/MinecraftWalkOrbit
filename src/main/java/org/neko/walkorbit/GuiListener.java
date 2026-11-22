package org.neko.walkorbit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class GuiListener implements Listener {
    private final WalkOrbit plugin;
    private final LocaleManager lm;

    public GuiListener(WalkOrbit plugin) {
        this.plugin = plugin;
        this.lm = plugin.getLocaleManager();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String orbit = pdc.get(GuiManager.orbitKey(plugin), PersistentDataType.STRING);
        if (orbit != null) {
            if (plugin.getGroups().containsKey(orbit)) {
                plugin.setSelectedGroup(player, orbit);
                player.sendMessage(lm.getString("command.trail_set", player, Map.of("trail", meta.getDisplayName())));
            }
            player.closeInventory();
            return;
        }

        String action = pdc.get(GuiManager.actionKey(plugin), PersistentDataType.STRING);
        if (action == null) return;

        if (action.startsWith(GuiManager.ACTION_STYLE_PREFIX)) {
            String name = action.substring(GuiManager.ACTION_STYLE_PREFIX.length());
            if (name.equals(GuiManager.STYLE_DEFAULT)) {
                plugin.setWalkStyle(player, null);
                player.sendMessage(lm.getString("command.style_reset", player));
            } else {
                try {
                    plugin.setWalkStyle(player, WalkShape.Style.valueOf(name));
                    player.sendMessage(lm.getString("command.style_set", player, Map.of("style", meta.getDisplayName())));
                } catch (IllegalArgumentException ignored) {
                    return;
                }
            }
            GuiManager.openStyle(player, plugin); // refresh the highlight
            return;
        }

        switch (action) {
            case GuiManager.ACTION_STYLE_MENU -> GuiManager.openStyle(player, plugin);
            case GuiManager.ACTION_CREATE -> plugin.getBuilder().open(player);
            case GuiManager.ACTION_BACK -> GuiManager.openMain(player, plugin, 0);
            case GuiManager.ACTION_PREV -> GuiManager.openMain(player, plugin, holder.page() - 1);
            case GuiManager.ACTION_NEXT -> GuiManager.openMain(player, plugin, holder.page() + 1);
            case GuiManager.ACTION_LANG -> GuiManager.openLanguage(player, plugin);
            case GuiManager.ACTION_REMOVE -> {
                plugin.setSelectedGroup(player, null);
                player.sendMessage(lm.getString("command.trail_removed", player));
                player.closeInventory();
            }
            case GuiManager.ACTION_LANG_EN, GuiManager.ACTION_LANG_JA -> {
                lm.setPlayerLocale(player, action.equals(GuiManager.ACTION_LANG_EN) ? "en" : "ja");
                player.sendMessage(lm.getString("command.lang_set", player));
                GuiManager.openMain(player, plugin, 0);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getOrbitTask().forget(event.getPlayer().getUniqueId());
        plugin.getBuilder().forget(event.getPlayer().getUniqueId());
    }
}
