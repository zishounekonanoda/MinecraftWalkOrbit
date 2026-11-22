package org.neko.walkorbit;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.logging.Logger;

/**
 * While moving: a vertical line of points on each side of the hips, slightly behind the body.
 */
public record WalkShape(int interval, double hipHeight, double sideOffset, double backOffset,
                        double lineHeight, int points, double sneakDrop) {

    public static final WalkShape DEFAULT = new WalkShape(2, 0.85, 0.3, 0.15, 0.45, 4, 0.25);

    /** Reads keys from {@code sec}, using {@code base} for anything missing. */
    public static WalkShape load(ConfigurationSection sec, WalkShape base, String context, Logger logger) {
        if (sec == null) return base;
        return new WalkShape(
                Math.max(1, (int) num(sec, "interval", base.interval, context, logger)),
                num(sec, "hip_height", base.hipHeight, context, logger),
                num(sec, "side_offset", base.sideOffset, context, logger),
                num(sec, "back_offset", base.backOffset, context, logger),
                Math.max(0, num(sec, "line_height", base.lineHeight, context, logger)),
                Math.max(1, (int) num(sec, "points", base.points, context, logger)),
                num(sec, "sneak_drop", base.sneakDrop, context, logger));
    }

    /**
     * Spawns the two vertical lines.
     *
     * @param bodyYaw body yaw in degrees (Minecraft convention: 0 = +Z, 90 = -X)
     */
    public void draw(org.bukkit.World world, double x, double y, double z, float bodyYaw, boolean sneaking,
                     List<ParticleEffect> effects) {
        double rad = Math.toRadians(bodyYaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);   // forward
        double rx = -Math.cos(rad), rz = -Math.sin(rad);  // right-hand side

        double cy = y + hipHeight - (sneaking ? sneakDrop : 0);
        double cx = x - fx * backOffset;
        double cz = z - fz * backOffset;

        for (int side = -1; side <= 1; side += 2) {
            double sx = cx + rx * sideOffset * side;
            double sz = cz + rz * sideOffset * side;
            for (int j = 0; j < points; j++) {
                double t = points == 1 ? 0.5 : (double) j / (points - 1);
                double py = cy - lineHeight / 2 + lineHeight * t;
                for (ParticleEffect e : effects) e.spawn(world, sx, py, sz);
            }
        }
    }

    private static double num(ConfigurationSection sec, String key, double def, String context, Logger logger) {
        return ParticleEffect.number(sec.get(key), def, key, context, logger);
    }
}
