package org.neko.walkorbit;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * One parsed particle entry. Spawned once per shape point. Built at load/reload, not per tick.
 */
public final class ParticleEffect {
    private static final Particle FALLBACK = Particle.FIREWORK;

    /** Pre-1.20.5 Bukkit names mapped to their current names, so older configs keep working. */
    private static final Map<String, String> LEGACY_NAMES = new HashMap<>();
    static {
        LEGACY_NAMES.put("REDSTONE", "DUST");
        LEGACY_NAMES.put("FIREWORKS_SPARK", "FIREWORK");
        LEGACY_NAMES.put("CRIT_MAGIC", "ENCHANTED_HIT");
        LEGACY_NAMES.put("SPELL_WITCH", "WITCH");
        LEGACY_NAMES.put("VILLAGER_HAPPY", "HAPPY_VILLAGER");
        LEGACY_NAMES.put("VILLAGER_ANGRY", "ANGRY_VILLAGER");
        LEGACY_NAMES.put("SMOKE_NORMAL", "SMOKE");
        LEGACY_NAMES.put("SMOKE_LARGE", "LARGE_SMOKE");
        LEGACY_NAMES.put("EXPLOSION_NORMAL", "POOF");
        LEGACY_NAMES.put("ENCHANTMENT_TABLE", "ENCHANT");
        LEGACY_NAMES.put("TOWN_AURA", "MYCELIUM");
        LEGACY_NAMES.put("DRIP_WATER", "DRIPPING_WATER");
        LEGACY_NAMES.put("DRIP_LAVA", "DRIPPING_LAVA");
        LEGACY_NAMES.put("WATER_SPLASH", "SPLASH");
        LEGACY_NAMES.put("WATER_BUBBLE", "BUBBLE");
        LEGACY_NAMES.put("WATER_WAKE", "FISHING");
        LEGACY_NAMES.put("WATER_DROP", "RAIN");
        LEGACY_NAMES.put("SUSPENDED_DEPTH", "UNDERWATER");
        LEGACY_NAMES.put("SNOW_SHOVEL", "ITEM_SNOWBALL");
        LEGACY_NAMES.put("SLIME", "ITEM_SLIME");
        LEGACY_NAMES.put("TOTEM", "TOTEM_OF_UNDYING");
    }

    private final Particle particle;
    private final int count;
    private final double offset;
    private final double speed;
    private final double chance;
    private final Object data;

    private ParticleEffect(Particle particle, int count, double offset, double speed, double chance, Object data) {
        this.particle = particle;
        this.count = count;
        this.offset = offset;
        this.speed = speed;
        this.chance = chance;
        this.data = data;
    }

    /** Returns null (after logging why) when the entry cannot be used. */
    public static ParticleEffect from(Map<?, ?> map, String context, Logger logger) {
        Object nameObj = map.get("particle");
        if (nameObj == null) {
            logger.warning("[" + context + "] Missing 'particle'; entry skipped.");
            return null;
        }
        String name = nameObj.toString().trim().toUpperCase(Locale.ROOT);

        Particle particle = resolve(name);
        if (particle == null) {
            logger.warning("[" + context + "] Unknown particle '" + name + "', falling back to " + FALLBACK.name());
            particle = FALLBACK;
        }

        Object data = null;
        if (particle == Particle.DUST) {
            data = parseDust(map.get("redstone"), context, logger);
        } else if (particle.getDataType() != Void.class) {
            logger.warning("[" + context + "] Particle '" + particle.name() + "' needs extra data ("
                    + particle.getDataType().getSimpleName() + ") which this plugin cannot configure; entry skipped.");
            return null;
        }

        int count = Math.max(1, (int) number(map.get("count"), 1, "count", context, logger));
        double offset = number(map.get("offset"), 0, "offset", context, logger);
        double speed = number(map.get("speed"), 0, "speed", context, logger);
        double chance = Math.max(0, Math.min(1, number(map.get("chance"), 1, "chance", context, logger)));
        return new ParticleEffect(particle, count, offset, speed, chance, data);
    }

    public void spawn(World world, double x, double y, double z) {
        if (chance < 1 && ThreadLocalRandom.current().nextDouble() >= chance) return;
        world.spawnParticle(particle, x, y, z, count, offset, offset, offset, speed, data, false);
    }

    private static Particle resolve(String name) {
        Particle p = tryValueOf(name);
        if (p != null) return p;
        String modern = LEGACY_NAMES.get(name);
        return modern == null ? null : tryValueOf(modern);
    }

    private static Particle tryValueOf(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Particle.DustOptions parseDust(Object obj, String context, Logger logger) {
        Map<?, ?> m = null;
        if (obj instanceof Map<?, ?> map) m = map;
        else if (obj instanceof ConfigurationSection sec) m = sec.getValues(false);
        if (m == null) return new Particle.DustOptions(Color.RED, 1.0f);

        Color color = Color.RED;
        Object hex = m.get("color");
        if (hex != null) {
            try {
                color = Color.fromRGB(parseHex(hex.toString()));
            } catch (IllegalArgumentException e) {
                logger.warning("[" + context + "] Invalid color '" + hex + "', using #FF0000");
            }
        }
        float size = (float) number(m.get("size"), 1.0, "redstone.size", context, logger);
        return new Particle.DustOptions(color, size);
    }

    static double number(Object value, double def, String key, String context, Logger logger) {
        if (value == null) return def;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            logger.warning("[" + context + "] '" + key + "' is not a number ('" + value + "'), using " + def);
            return def;
        }
    }

    private static int parseHex(String s) {
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) {
            char r = t.charAt(0), g = t.charAt(1), b = t.charAt(2);
            t = "" + r + r + g + g + b + b;
        }
        if (t.length() != 6) throw new IllegalArgumentException("bad hex length");
        return Integer.parseInt(t, 16);
    }
}
