package org.neko.walkorbit;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * Vanilla particle id -> its texture in the "minecraft:particles" atlas (a middle animation frame),
     * read from assets/minecraft/particles/*.json of the 1.21.11 client.
     */
    private static final String[] PARTICLE_SPRITE_TABLE = {
            "ambient_entity_effect", "effect_3", "angry_villager", "angry", "ash", "generic_0", "bubble", "bubble",
            "bubble_column_up", "bubble", "bubble_pop", "bubble_pop_2", "campfire_cosy_smoke", "big_smoke_6", "campfire_signal_smoke", "big_smoke_6",
            "cherry_leaves", "cherry_6", "cloud", "generic_3", "composter", "glint", "copper_fire_flame", "copper_fire_flame",
            "crimson_spore", "generic_0", "crit", "critical_hit", "current_down", "bubble", "damage_indicator", "damage",
            "dolphin", "generic_0", "dragon_breath", "generic_6", "dripping_dripstone_lava", "drip_hang", "dripping_dripstone_water", "drip_hang",
            "dripping_honey", "drip_hang", "dripping_lava", "drip_hang", "dripping_obsidian_tear", "drip_hang", "dripping_water", "drip_hang",
            "dust", "generic_3", "dust_color_transition", "generic_3", "dust_plume", "generic_3", "effect", "effect_3",
            "egg_crack", "glint", "electric_spark", "glow", "enchant", "sga_n", "enchanted_hit", "enchanted_hit",
            "end_rod", "glitter_3", "entity_effect", "effect_3", "explosion", "explosion_8", "falling_dripstone_lava", "drip_fall",
            "falling_dripstone_water", "drip_fall", "falling_dust", "generic_3", "falling_honey", "drip_fall", "falling_lava", "drip_fall",
            "falling_nectar", "drip_fall", "falling_obsidian_tear", "drip_fall", "falling_spore_blossom", "drip_fall", "falling_water", "drip_fall",
            "firefly", "firefly", "firework", "spark_3", "fishing", "splash_2", "flame", "flame",
            "flash", "flash", "glow", "glow", "glow_squid_ink", "generic_3", "gust", "gust_6",
            "happy_villager", "glint", "heart", "heart", "infested", "infested", "instant_effect", "spell_3",
            "landing_honey", "drip_land", "landing_lava", "drip_land", "landing_obsidian_tear", "drip_land", "large_smoke", "generic_3",
            "lava", "lava", "mycelium", "generic_0", "nautilus", "nautilus", "note", "note",
            "ominous_spawning", "ominous_spawning", "pale_oak_leaves", "pale_oak_6", "poof", "generic_3", "portal", "generic_4",
            "raid_omen", "raid_omen", "rain", "splash_2", "reverse_portal", "generic_4", "scrape", "glow",
            "sculk_charge", "sculk_charge_3", "sculk_charge_pop", "sculk_charge_pop_2", "sculk_soul", "sculk_soul_5", "shriek", "shriek",
            "small_flame", "flame", "small_gust", "small_gust_3", "smoke", "generic_3", "sneeze", "generic_3",
            "snowflake", "generic_3", "sonic_boom", "sonic_boom_8", "soul", "soul_5", "soul_fire_flame", "soul_fire_flame",
            "spit", "generic_3", "splash", "splash_2", "spore_blossom_air", "drip_fall", "squid_ink", "generic_3",
            "sweep_attack", "sweep_4", "tinted_leaves", "leaf_6", "totem_of_undying", "glitter_3", "trail", "generic_0",
            "trial_omen", "trial_omen", "trial_spawner_detection", "trial_spawner_detection_2", "trial_spawner_detection_ominous", "trial_spawner_detection_ominous_2", "underwater", "generic_0",
            "vault_connection", "vault_connection", "vibration", "vibration", "warped_spore", "generic_0", "wax_off", "glow",
            "wax_on", "glow", "white_ash", "generic_0", "white_smoke", "generic_3", "witch", "spell_3",
    };
    private static final Map<String, String> PARTICLE_SPRITES = new HashMap<>();
    static {
        for (int i = 0; i < PARTICLE_SPRITE_TABLE.length; i += 2) {
            PARTICLE_SPRITES.put(PARTICLE_SPRITE_TABLE[i], PARTICLE_SPRITE_TABLE[i + 1]);
        }
    }

    /** Particles that draw a block/item instead of their own texture: {atlas, sprite}. */
    private static final Map<String, String[]> FALLBACK_SPRITES = Map.of(
            "ITEM", new String[]{"items", "item/diamond"},
            "ITEM_SNOWBALL", new String[]{"items", "item/snowball"},
            "ITEM_SLIME", new String[]{"items", "item/slime_ball"},
            "ITEM_COBWEB", new String[]{"blocks", "block/cobweb"},
            "BLOCK", new String[]{"blocks", "block/grass_block_side"},
            "BLOCK_CRUMBLE", new String[]{"blocks", "block/stone"},
            "DUST_PILLAR", new String[]{"blocks", "block/sand"});

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

    /**
     * The particle's own texture as an inline text sprite (1.21.9+ object component), for dialog
     * buttons and lines. Clients older than 1.21.9 just don't draw it.
     */
    public static Component sprite(Particle p) {
        String own = PARTICLE_SPRITES.get(p.getKey().getKey());
        if (own != null) return sprite("particles", own);
        String[] fb = FALLBACK_SPRITES.get(p.name());
        if (fb != null) return sprite(fb[0], fb[1]);
        return sprite("particles", "generic_3");
    }

    private static Component sprite(String atlas, String name) {
        return Component.object(ObjectContents.sprite(Key.key("minecraft", atlas), Key.key("minecraft", name)));
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
