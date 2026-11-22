package org.neko.walkorbit;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * While moving: a vertical band of particles on each side of the hips, slightly behind the body.
 * The band is swept along the path walked since the last emission, so it reads as one
 * continuous streak instead of separate afterimages.
 */
public record WalkShape(int interval, double lineTop, double lineBottom, double sideOffset, double backOffset,
                        int points, double spread, double step, double sneakDrop) {

    public static final WalkShape DEFAULT = new WalkShape(1, 1.2, 0.25, 0.28, 0.15, 8, 0.07, 0.12, 0.25);

    /** Beyond this distance between emissions it is a teleport, not walking: don't draw a streak across it. */
    private static final double MAX_SWEEP = 2.0;
    /** Upper bound on sub-steps per emission (sprint-jumping is ~0.6 blocks/tick). */
    private static final int MAX_STEPS = 12;

    /** Reads keys from {@code sec}, using {@code base} for anything missing. */
    public static WalkShape load(ConfigurationSection sec, WalkShape base, String context, Logger logger) {
        if (sec == null) return base;
        return new WalkShape(
                Math.max(1, (int) num(sec, "interval", base.interval, context, logger)),
                num(sec, "line_top", base.lineTop, context, logger),
                num(sec, "line_bottom", base.lineBottom, context, logger),
                num(sec, "side_offset", base.sideOffset, context, logger),
                num(sec, "back_offset", base.backOffset, context, logger),
                Math.max(1, (int) num(sec, "points", base.points, context, logger)),
                Math.max(0, num(sec, "spread", base.spread, context, logger)),
                Math.max(0.02, num(sec, "step", base.step, context, logger)),
                num(sec, "sneak_drop", base.sneakDrop, context, logger));
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

        for (int side = -1; side <= 1; side += 2) {
            double sx = cx + rx * sideOffset * side;
            double sz = cz + rz * sideOffset * side;
            for (int j = 0; j < points; j++) {
                double t = points == 1 ? 0.5 : (double) j / (points - 1);
                double py = bottom + (top - bottom) * t;
                // horizontal scatter so the band isn't a perfectly straight line
                double jx = spread == 0 ? 0 : rnd.nextDouble(-spread, spread);
                double jz = spread == 0 ? 0 : rnd.nextDouble(-spread, spread);
                for (ParticleEffect e : effects) e.spawn(world, sx + jx, py, sz + jz);
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
