package org.neko.walkorbit;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;

public final class WalkOrbit extends JavaPlugin {
    private LocaleManager localeManager;
    private OrbitTask orbitTask;
    private Map<String, OrbitGroup> groups = Collections.emptyMap();
    private NamespacedKey selectedKey;
    private NamespacedKey styleKey;

    @Override
    public void onEnable() {
        selectedKey = new NamespacedKey(this, "selected");
        styleKey = new NamespacedKey(this, "walk_style");
        localeManager = new LocaleManager(this);

        saveDefaultConfig();
        loadGroups();

        orbitTask = new OrbitTask(this);
        orbitTask.runTaskTimer(this, 1L, 1L);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        PluginCommand cmd = getCommand("walkorbit");
        if (cmd != null) {
            OrbitCommand executor = new OrbitCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
        getLogger().info("WalkOrbit enabled.");
    }

    @Override
    public void onDisable() {
        if (orbitTask != null) orbitTask.cancel();
        Lingering.clear();
        if (localeManager != null) localeManager.savePlayerLocales();
    }

    public void reloadAll() {
        reloadConfig();
        loadGroups();
        localeManager.reloadLocales();
    }

    private void loadGroups() {
        Lingering.clear(); // remembered points belong to the old effect objects
        groups = OrbitGroup.loadAll(getConfig(), getLogger());
        getLogger().info("Loaded " + groups.size() + " walk orbit group(s).");
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    public OrbitTask getOrbitTask() {
        return orbitTask;
    }

    /** Parsed groups in config order. */
    public Map<String, OrbitGroup> getGroups() {
        return groups;
    }

    /*
     * The selection is stored on the player (PersistentDataContainer), not as a scoreboard tag,
     * so it survives relogs and never collides with ElytraOrbit's PREDATOR/MASTER/... tags.
     */

    public OrbitGroup getSelectedGroup(Player p) {
        String name = p.getPersistentDataContainer().get(selectedKey, PersistentDataType.STRING);
        return name == null ? null : groups.get(name);
    }

    public void setSelectedGroup(Player p, String group) {
        if (group == null) {
            p.getPersistentDataContainer().remove(selectedKey);
        } else {
            p.getPersistentDataContainer().set(selectedKey, PersistentDataType.STRING, group);
        }
    }

    /** The player's own walk style choice, or null to use the server's (config) style. */
    public WalkShape.Style getWalkStyle(Player p) {
        String s = p.getPersistentDataContainer().get(styleKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return WalkShape.Style.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setWalkStyle(Player p, WalkShape.Style style) {
        if (style == null) {
            p.getPersistentDataContainer().remove(styleKey);
        } else {
            p.getPersistentDataContainer().set(styleKey, PersistentDataType.STRING, style.name());
        }
    }
}
