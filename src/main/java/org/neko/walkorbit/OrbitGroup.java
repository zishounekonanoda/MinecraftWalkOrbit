package org.neko.walkorbit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * One configured walk orbit, parsed once per load/reload.
 */
public record OrbitGroup(String name, Material icon, ChatColor color,
                         List<ParticleEffect> walk, List<ParticleEffect> idle,
                         WalkShape walkShape, IdleShape idleShape) {

    private static final Material DEFAULT_ICON = Material.GLOWSTONE_DUST;

    public static Map<String, OrbitGroup> loadAll(FileConfiguration config, Logger logger) {
        WalkShape walkBase = WalkShape.load(config.getConfigurationSection("walk_shape"), WalkShape.DEFAULT, "walk_shape", logger);
        IdleShape idleBase = IdleShape.load(config.getConfigurationSection("idle_shape"), IdleShape.DEFAULT, "idle_shape", logger);

        ConfigurationSection groups = config.getConfigurationSection("groups");
        if (groups == null) return Collections.emptyMap();

        Map<String, OrbitGroup> result = new LinkedHashMap<>();
        for (String name : groups.getKeys(false)) {
            ConfigurationSection sec = groups.getConfigurationSection(name);
            if (sec == null) continue;
            List<ParticleEffect> walk = effects(sec, "walk", name, logger);
            List<ParticleEffect> idle = effects(sec, "idle", name, logger);
            if (walk.isEmpty() && idle.isEmpty()) {
                logger.warning("Group '" + name + "' has no usable walk or idle particles.");
            }
            result.put(name, new OrbitGroup(name, icon(name, sec, logger), color(name, sec, logger), walk, idle,
                    WalkShape.load(sec.getConfigurationSection("walk_shape"), walkBase, name + ".walk_shape", logger),
                    IdleShape.load(sec.getConfigurationSection("idle_shape"), idleBase, name + ".idle_shape", logger)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<ParticleEffect> effects(ConfigurationSection sec, String key, String group, Logger logger) {
        List<ParticleEffect> out = new ArrayList<>();
        List<Map<?, ?>> list = sec.getMapList(key);
        for (int i = 0; i < list.size(); i++) {
            ParticleEffect e = ParticleEffect.from(list.get(i), group + "." + key + "[" + i + "]", logger);
            if (e != null) out.add(e);
        }
        return List.copyOf(out);
    }

    private static Material icon(String name, ConfigurationSection sec, Logger logger) {
        String value = sec.getString("MATERIAL");
        if (value == null || value.isBlank()) return DEFAULT_ICON;
        Material m = Material.matchMaterial(value.trim());
        if (m != null && m.isItem() && !m.isAir()) return m;
        logger.warning("Invalid MATERIAL '" + value + "' for group '" + name + "'. Falling back to " + DEFAULT_ICON + ".");
        return DEFAULT_ICON;
    }

    /** COLOR accepts a code ("&c") or a ChatColor name ("RED"). */
    private static ChatColor color(String name, ConfigurationSection sec, Logger logger) {
        String value = sec.getString("COLOR");
        if (value != null && !value.isBlank()) {
            String v = value.trim();
            ChatColor c = null;
            if (v.length() == 2 && (v.charAt(0) == '&' || v.charAt(0) == ChatColor.COLOR_CHAR)) {
                c = ChatColor.getByChar(v.charAt(1));
            } else {
                try {
                    c = ChatColor.valueOf(v.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // handled below
                }
            }
            if (c != null && c.isColor()) return c;
            logger.warning("Invalid COLOR '" + value + "' for group '" + name + "'. Using YELLOW.");
        }
        return ChatColor.YELLOW;
    }
}
