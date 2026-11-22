package org.neko.walkorbit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Player-made orbits, kept apart from config.yml in custom_orbits.yml:
 * <pre>
 * orbits:
 *   MY_ORBIT:
 *     DISPLAY_NAME: "&aMy Orbit"
 *     MATERIAL: REDSTONE
 *     creator: SomePlayer
 *     walk: [ ...same entries as config.yml... ]
 *     idle: [ ... ]
 * </pre>
 */
public final class CustomOrbitStore {
    private final WalkOrbit plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    public CustomOrbitStore(WalkOrbit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom_orbits.yml");
        reload();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public ConfigurationSection section() {
        return yaml.getConfigurationSection("orbits");
    }

    public boolean contains(String id) {
        return yaml.contains("orbits." + id);
    }

    public boolean add(String id, String displayName, String material, String creator,
                       List<Map<String, Object>> walk, List<Map<String, Object>> idle) {
        String base = "orbits." + id;
        yaml.set(base + ".DISPLAY_NAME", displayName);
        yaml.set(base + ".MATERIAL", material);
        yaml.set(base + ".creator", creator);
        yaml.set(base + ".walk", walk);
        yaml.set(base + ".idle", idle);
        return save();
    }

    public boolean delete(String id) {
        if (!contains(id)) return false;
        yaml.set("orbits." + id, null);
        return save();
    }

    private boolean save() {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save custom_orbits.yml", e);
            return false;
        }
    }
}
