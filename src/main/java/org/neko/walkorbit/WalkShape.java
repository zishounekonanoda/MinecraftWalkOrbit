package org.neko.walkorbit;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * While moving: vertical bands of particles around the body. The band is swept along the path
 * walked since the last emission, so it reads as one continuous streak.
 *
 * <p>{@link Style} picks the layout (left/right of the hips, behind the back, at the feet).
 * Each style has its own preset values; any key written in the config overrides the preset.
 */
public record WalkShape(Style style, int interval, double lineTop, double lineBottom, double sideOffset,
                        double backOffset, double width, int points, double spread, double flare,
                        double step, double sneakDrop) {

    public enum Style {
        /** Two bands, left and right of the hips, fanning outward toward the bottom. */
        SIDES,
        /** One thick, wide band right behind the back. */
        BACK,
        /** One thin, sparse band right behind the back. */
        BACK_SUBTLE,
        /** One thick, wide band low at the feet. */
        FEET;

        boolean mirrored() {
            return this == SIDES;
        }

        WalkShape preset() {
            return switch (this) {
                //                       interval top   bottom side back  width pts spread flare step  sneak
                case SIDES -> new WalkShape(this, 2, 1.5, 0.45, 0.6, 0.15, 0.0, 5, 0.15, 0.35, 0.3, 0.25);
                case BACK -> new WalkShape(this, 2, 1.45, 0.5, 0.0, 0.35, 0.5, 10, 0.12, 0.25, 0.3, 0.25);
                case BACK_SUBTLE -> new WalkShape(this, 2, 1.3, 0.8, 0.0, 0.3, 0.12, 3, 0.05, 0.0, 0.3, 0.2);
                case FEET -> new WalkShape(this, 2, 0.35, 0.05, 0.0, 0.25, 0.8, 8, 0.15, 0.0, 0.3, 0.0);
            };
        }

        static Style parse(String s, Style def, String context, Logger logger) {
            if (s == null || s.isBlank()) return def;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning("[" + context + "] Unknown walk style '" + s + "' (SIDES / BACK / BACK_SUBTLE / FEET), using " + def);
                return def;
            }
        }
    }

    public static final WalkShape DEFAULT = Style.SIDES.preset();

    /** Beyond this distance between emissions it is a teleport, not walking: don't draw a streak across it. */
    private static final double MAX_SWEEP = 2.0;
    /** Upper bound on sub-steps per emission (sprint-jumping is ~0.6 blocks/tick). */
    private static final int MAX_STEPS = 12;

    /**
     * Reads a shape section. If it names a different {@code style}, that style's preset becomes the base
     * (so a group can switch style without inheriting the other style's sizes); otherwise {@code base} is used.
     * Keys written in the section then override the base.
     */
    public static WalkShape load(ConfigurationSection sec, WalkShape base, String context, Logger logger) {
        if (sec == null) return base;
        Style style = Style.parse(sec.getString("style"), base.style, context, logger);
        WalkShape b = style == base.style ? base : style.preset();
        return new WalkShape(
                style,
                Math.max(1, (int) num(sec, "interval", b.interval, context, logger)),
                num(sec, "line_top", b.lineTop, context, logger),
                num(sec, "line_bottom", b.lineBottom, context, logger),
                num(sec, "side_offset", b.sideOffset, context, logger),
                num(sec, "back_offset", b.backOffset, context, logger),
                Math.max(0, num(sec, "width", b.width, context, logger)),
                Math.max(1, (int) num(sec, "points", b.points, context, logger)),
                Math.max(0, num(sec, "spread", b.spread, context, logger)),
                Math.max(0, num(sec, "flare", b.flare, context, logger)),
                Math.max(0.02, num(sec, "step", b.step, context, logger)),
                num(sec, "sneak_drop", b.sneakDrop, context, logger));
    }

    /**
     * Draws the bands for the movement from {@code from} to {@code to}.
     *
     * @param from previous emission position, or null to draw only at {@code to}
     */
    public void draw(World world, Pose from, Pose to, boolean sneaking, List<ParticleEffect> effects) {
        int steps = 1;
        if (from != null) {
            double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > MAX_SWEEP) from = null;
            else steps = Math.max(1, Math.min(MAX_STEPS, (int) Math.ceil(dist / step)));
        }

        for (int s = 1; s <= steps; s++) {
            double t = (double) s / steps;
            Pose p = from == null ? to : Pose.lerp(from, to, t);
            drawBands(world, p, sneaking, effects);
        }
    }

    private void drawBands(World world, Pose p, boolean sneaking, List<ParticleEffect> effects) {
        double rad = Math.toRadians(p.yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);   // forward
        double rx = -Math.cos(rad), rz = -Math.sin(rad);  // right-hand side

        double drop = sneaking ? sneakDrop : 0;
        double top = p.y + lineTop - drop;
        double bottom = p.y + lineBottom - drop;
        double cx = p.x - fx * backOffset;
        double cz = p.z - fz * backOffset;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        int[] sides = style.mirrored() ? new int[]{-1, 1} : new int[]{0};
        for (int side : sides) {
            double sx = cx + rx * sideOffset * side;
            double sz = cz + rz * sideOffset * side;
            for (int j = 0; j < points; j++) {
                // one point per vertical slot, at a random height inside it, so points never line up in a grid
                double t = (j + rnd.nextDouble()) / points;     // 0 = bottom, 1 = top
                double py = bottom + (top - bottom) * t;
                double depth = 1 - t;                            // 0 at the top, 1 at the bottom

                // width: spread sideways across the band (used by the single-band styles)
                double lateral = width == 0 ? 0 : rnd.nextDouble(-width / 2, width / 2);
                // flare: pushed outward, more the lower it is -> the band fans out downward.
                // Mirrored bands push away from the body; a centre band pushes to a random side.
                double dir = side != 0 ? side : (rnd.nextBoolean() ? 1 : -1);
                lateral += dir * flare * depth * rnd.nextDouble();

                // spread: random scatter in every horizontal direction, growing toward the bottom
                double r = spread * (0.4 + 0.6 * depth);
                double jx = r == 0 ? 0 : rnd.nextDouble(-r, r);
                double jz = r == 0 ? 0 : rnd.nextDouble(-r, r);

                double px = sx + rx * lateral + jx;
                double pz = sz + rz * lateral + jz;
                for (ParticleEffect e : effects) e.spawn(world, px, py, pz);
            }
        }
    }

    /** Position and body yaw at one moment. */
    public record Pose(double x, double y, double z, float yaw) {
        static Pose lerp(Pose a, Pose b, double t) {
            float dyaw = ((b.yaw - a.yaw) % 360 + 540) % 360 - 180; // shortest turn
            return new Pose(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t,
                    (float) (a.yaw + dyaw * t));
        }
    }

    private static double num(ConfigurationSection sec, String key, double def, String context, Logger logger) {
        return ParticleEffect.number(sec.get(key), def, key, context, logger);
    }
}
