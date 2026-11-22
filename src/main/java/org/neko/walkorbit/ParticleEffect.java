package org.neko.walkorbit;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

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

    // TRAIL: a dot that lives `trailDuration` ticks while sliding `trailFall` blocks down
    private Color trailColor;
    private int trailDuration;
    private double trailFall;

    // NOTE: fixed pitch 0-24, or RANDOM_NOTE for a different colour every time; null = plain NOTE
    private static final int RANDOM_NOTE = -1;
    private Integer note;

    // Linger: keep the point visible for `linger` ticks by re-sending it every `refresh` ticks,
    // sliding `lingerFall` blocks down meanwhile. Works for any particle (see Lingering).
    private int linger;
    private int refresh = 1;
    private double lingerFall;

    int linger() {
        return linger;
    }

    int refresh() {
        return refresh;
    }

    double fall() {
        return lingerFall;
    }

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

        Map<String, Object> opts = options(map);
        Object data = null;
        if (particle.getDataType() != Void.class && particle != Particle.TRAIL) {
            data = parseData(particle, opts, context, logger);
            if (data == null) return null;
        }

        int count = Math.max(1, (int) number(map.get("count"), 1, "count", context, logger));
        double offset = number(map.get("offset"), 0, "offset", context, logger);
        double speed = number(map.get("speed"), 0, "speed", context, logger);
        double chance = Math.max(0, Math.min(1, number(map.get("chance"), 1, "chance", context, logger)));
        ParticleEffect effect = new ParticleEffect(particle, count, offset, speed, chance, data);

        if (particle == Particle.TRAIL) {
            effect.trailColor = color(opts.get("color"), Color.WHITE, context, logger);
            effect.trailDuration = Math.max(1, (int) number(opts.get("duration"), 30, "duration", context, logger));
            effect.trailFall = number(opts.get("fall"), 0.3, "fall", context, logger);
        }
        if (particle == Particle.NOTE && opts.get("note") != null) {
            Object n = opts.get("note");
            effect.note = "random".equalsIgnoreCase(n.toString().trim())
                    ? RANDOM_NOTE
                    : Math.max(0, Math.min(24, (int) number(n, 0, "note", context, logger)));
        }

        effect.linger = Math.max(0, (int) number(opts.get("linger"), 0, "linger", context, logger));
        if (effect.linger > 0) {
            effect.refresh = Math.max(1, (int) number(opts.get("refresh"), 4, "refresh", context, logger));
            // For TRAIL, "fall" already moves the dot itself, so it isn't applied twice
            effect.lingerFall = particle == Particle.TRAIL ? 0 : number(opts.get("fall"), 0, "fall", context, logger);
        }
        return effect;
    }

    /** Spawns this effect at a shape point (subject to chance), and keeps it alive if linger is set. */
    public void spawn(World world, double x, double y, double z) {
        if (chance < 1 && ThreadLocalRandom.current().nextDouble() >= chance) return;
        emit(world, x, y, z);
        if (linger > 0) Lingering.add(this, world, x, y, z);
    }

    /** Sends the particle once, with no chance roll and no lingering. */
    void emit(World world, double x, double y, double z) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (particle == Particle.TRAIL) {
            Particle.Trail trail = new Particle.Trail(new Location(world, x, y - trailFall, z), trailColor, trailDuration);
            world.spawnParticle(particle, x, y, z, count, offset, offset, offset, 0, trail, false);
            return;
        }
        if (note != null) {
            // With count 0 the client reads offsetX as the note pitch (0-24 / 24) and colours it accordingly
            int pitch = note == RANDOM_NOTE ? rnd.nextInt(25) : note;
            for (int i = 0; i < count; i++) {
                world.spawnParticle(particle, x, y, z, 0, pitch / 24.0, 0, 0, 1, null, false);
            }
            return;
        }
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

    /**
     * Data keys may be written directly on the entry (color, to_color, size, block, item, value)
     * or inside the older "redstone:" block; the entry's own keys win.
     */
    private static Map<String, Object> options(Map<?, ?> entry) {
        Map<String, Object> out = new HashMap<>();
        Object red = entry.get("redstone");
        if (red instanceof ConfigurationSection sec) red = sec.getValues(false);
        if (red instanceof Map<?, ?> m) m.forEach((k, v) -> out.put(String.valueOf(k), v));
        entry.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    /** Builds the extra data a particle type needs. Returns null (after logging) if it cannot. */
    private static Object parseData(Particle particle, Map<String, Object> o, String context, Logger logger) {
        Class<?> type = particle.getDataType();
        float size = (float) number(o.get("size"), 1.0, "size", context, logger);

        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(color(o.get("color"), Color.RED, context, logger), size);
        }
        if (type == Particle.DustTransition.class) {
            Color from = color(o.get("color"), Color.RED, context, logger);
            return new Particle.DustTransition(from, color(o.get("to_color"), from, context, logger), size);
        }
        if (type == Color.class) {
            return color(o.get("color"), Color.WHITE, context, logger);
        }
        if (type == Particle.Spell.class) {
            return new Particle.Spell(color(o.get("color"), Color.WHITE, context, logger),
                    (float) number(o.get("value"), 1.0, "value", context, logger));
        }
        if (type == Float.class) {
            return (float) number(o.get("value"), 0.0, "value", context, logger);
        }
        if (type == Integer.class) {
            return (int) number(o.get("value"), 0, "value", context, logger);
        }
        if (type == BlockData.class) {
            Material m = material(o.get("block"), context, logger);
            if (m == null || !m.isBlock()) {
                logger.warning("[" + context + "] " + particle.name() + " needs 'block: <block name>'; entry skipped.");
                return null;
            }
            return m.createBlockData();
        }
        if (type == ItemStack.class) {
            Material m = material(o.get("item"), context, logger);
            if (m == null || !m.isItem() || m.isAir()) {
                logger.warning("[" + context + "] " + particle.name() + " needs 'item: <item name>'; entry skipped.");
                return null;
            }
            return new ItemStack(m);
        }
        logger.warning("[" + context + "] Particle '" + particle.name() + "' needs "
                + type.getSimpleName() + " data, which this plugin cannot configure; entry skipped.");
        return null;
    }

    private static Color color(Object value, Color def, String context, Logger logger) {
        if (value == null) return def;
        try {
            return Color.fromRGB(parseHex(value.toString()));
        } catch (IllegalArgumentException e) {
            logger.warning("[" + context + "] Invalid color '" + value + "', using default");
            return def;
        }
    }

    private static Material material(Object value, String context, Logger logger) {
        if (value == null) return null;
        Material m = Material.matchMaterial(value.toString().trim());
        if (m == null) logger.warning("[" + context + "] Unknown block/item '" + value + "'");
        return m;
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
