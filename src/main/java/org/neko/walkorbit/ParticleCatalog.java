package org.neko.walkorbit;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What the custom-orbit builder offers: which particles, what extra data each needs, and an icon for each. */
public final class ParticleCatalog {

    /** The kind of extra data a particle needs, as far as the builder is concerned. */
    public enum Kind {
        NONE, COLOR, TRANSITION, SPELL, TRAIL, BLOCK, ITEM, NUMBER;

        /** Kinds where the builder asks for a colour. */
        public boolean colored() {
            return this == COLOR || this == TRANSITION || this == SPELL || this == TRAIL;
        }
    }

    /**
     * Left out of the builder: ELDER_GUARDIAN covers the whole screen, VIBRATION needs a moving target,
     * the *_EMITTER ones chain into many explosions/gusts, BLOCK_MARKER floats a large block icon.
     */
    private static final Set<Particle> EXCLUDED = EnumSet.of(
            Particle.ELDER_GUARDIAN, Particle.VIBRATION, Particle.EXPLOSION_EMITTER,
            Particle.GUST_EMITTER_LARGE, Particle.GUST_EMITTER_SMALL, Particle.BLOCK_MARKER);

    /** Particles that disappear almost at once; the builder gives them linger so they read as a line. */
    private static final Set<String> SHORT_LIVED = Set.of(
            "DUST", "DUST_COLOR_TRANSITION", "ENTITY_EFFECT", "EFFECT", "INSTANT_EFFECT", "GLOW",
            "ELECTRIC_SPARK", "SOUL_FIRE_FLAME", "FLAME", "SMALL_FLAME", "COPPER_FIRE_FLAME", "CRIT",
            "ENCHANTED_HIT", "WAX_ON", "WAX_OFF", "SCRAPE", "TRIAL_SPAWNER_DETECTION",
            "TRIAL_SPAWNER_DETECTION_OMINOUS", "HAPPY_VILLAGER", "FIREWORK", "END_ROD", "WITCH");

    /** Keyword in the particle name -> icon. Checked in order; first match wins. */
    private static final String[][] ICONS = {
            {"DUST_COLOR_TRANSITION", "GLOWSTONE_DUST"}, {"DUST_PLUME", "BRUSH"}, {"DUST_PILLAR", "GRASS_BLOCK"},
            {"FALLING_DUST", "SAND"}, {"DUST", "REDSTONE"}, {"OBSIDIAN_TEAR", "CRYING_OBSIDIAN"},
            {"HONEY", "HONEY_BOTTLE"}, {"NECTAR", "HONEY_BOTTLE"}, {"SOUL", "SOUL_TORCH"},
            {"COPPER_FIRE", "COPPER_TORCH"}, {"LAVA", "LAVA_BUCKET"}, {"FLAME", "BLAZE_POWDER"},
            {"SPORE_BLOSSOM", "SPORE_BLOSSOM"}, {"SPORE", "CRIMSON_FUNGUS"}, {"CHERRY", "CHERRY_LEAVES"},
            {"PALE_OAK", "PALE_OAK_LEAVES"}, {"LEAVES", "OAK_LEAVES"}, {"FIREFLY", "FIREFLY_BUSH"},
            {"WATER", "WATER_BUCKET"}, {"SPLASH", "WATER_BUCKET"}, {"RAIN", "WATER_BUCKET"},
            {"BUBBLE", "PUFFERFISH"}, {"FISHING", "FISHING_ROD"}, {"UNDERWATER", "SEAGRASS"},
            {"DOLPHIN", "COD"}, {"CURRENT", "MAGMA_BLOCK"}, {"NAUTILUS", "NAUTILUS_SHELL"},
            {"SQUID_INK", "INK_SAC"}, {"GLOW", "GLOW_INK_SAC"}, {"SNOW", "SNOWBALL"},
            {"SMOKE", "CAMPFIRE"}, {"ASH", "BASALT"}, {"POOF", "GRAY_DYE"}, {"CLOUD", "WHITE_WOOL"},
            {"SNEEZE", "SLIME_BALL"}, {"SLIME", "SLIME_BALL"}, {"COBWEB", "COBWEB"},
            {"REVERSE_PORTAL", "CRYING_OBSIDIAN"}, {"PORTAL", "OBSIDIAN"}, {"END_ROD", "END_ROD"},
            {"HEART", "RED_DYE"}, {"NOTE", "NOTE_BLOCK"}, {"VILLAGER", "EMERALD"},
            {"ENCHANTED_HIT", "IRON_SWORD"}, {"ENCHANT", "ENCHANTING_TABLE"}, {"CRIT", "IRON_SWORD"},
            {"SWEEP", "DIAMOND_SWORD"}, {"DAMAGE", "IRON_AXE"}, {"SCULK", "SCULK"}, {"SHRIEK", "SCULK_SHRIEKER"},
            {"SONIC", "ECHO_SHARD"}, {"TOTEM", "TOTEM_OF_UNDYING"}, {"FIREWORK", "FIREWORK_ROCKET"},
            {"EXPLOSION", "TNT"}, {"GUST", "WIND_CHARGE"}, {"OMINOUS", "OMINOUS_TRIAL_KEY"},
            {"TRIAL", "TRIAL_KEY"}, {"VAULT", "VAULT"}, {"RAID", "OMINOUS_BOTTLE"},
            {"ELECTRIC", "LIGHTNING_ROD"}, {"WAX", "HONEYCOMB"}, {"SCRAPE", "COPPER_INGOT"},
            {"DRAGON", "DRAGON_BREATH"}, {"WITCH", "CAULDRON"}, {"EFFECT", "POTION"}, {"FLASH", "GLOWSTONE"},
            {"SPIT", "LLAMA_SPAWN_EGG"}, {"MYCELIUM", "MYCELIUM"}, {"EGG_CRACK", "EGG"},
            {"COMPOSTER", "COMPOSTER"}, {"INFESTED", "INFESTED_STONE"}, {"TRAIL", "CREAKING_HEART"},
            {"ITEM", "ITEM_FRAME"}, {"BLOCK", "GRASS_BLOCK"},
    };

    private static final List<Particle> SELECTABLE = Arrays.stream(Particle.values())
            .filter(p -> !EXCLUDED.contains(p))
            .filter(p -> kindOf(p) != null)
            .sorted(Comparator.comparing(Particle::name))
            .toList();

    private ParticleCatalog() {
    }

    public static List<Particle> selectable() {
        return SELECTABLE;
    }

    /** The extra data kind, or null if the builder can't configure this particle. */
    public static Kind kindOf(Particle p) {
        if (p == Particle.TRAIL) return Kind.TRAIL;
        Class<?> t = p.getDataType();
        if (t == Void.class) return Kind.NONE;
        if (t == Particle.DustOptions.class || t == Color.class) return Kind.COLOR;
        if (t == Particle.DustTransition.class) return Kind.TRANSITION;
        if (t == Particle.Spell.class) return Kind.SPELL;
        if (t == BlockData.class) return Kind.BLOCK;
        if (t == ItemStack.class) return Kind.ITEM;
        if (t == Float.class || t == Integer.class) return Kind.NUMBER;
        return null;
    }

    public static boolean hasSize(Particle p) {
        return p == Particle.DUST || p == Particle.DUST_COLOR_TRANSITION;
    }

    public static boolean shortLived(Particle p) {
        return SHORT_LIVED.contains(p.name());
    }

    public static Material icon(Particle p) {
        String name = p.name();
        for (String[] e : ICONS) {
            if (name.contains(e[0])) {
                Material m = Material.matchMaterial(e[1]);
                if (m != null && m.isItem()) return m;
            }
        }
        return Material.FIREWORK_STAR;
    }

    /** A readable label: "SOUL_FIRE_FLAME" -> "soul fire flame". */
    public static String label(Particle p) {
        return p.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
