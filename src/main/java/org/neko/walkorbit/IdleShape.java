package org.neko.walkorbit;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.logging.Logger;

/**
 * While standing still: drops that circle the player and slide down from {@code top} to {@code bottom},
 * then loop back to the top. Each drop leaves a short trail along its own path, so it reads as a
 * descending spiral.
 */
public record IdleShape(int delay, int interval, double radius, double top, double bottom, int strands,
                        double rotateSpeed, double fallSpeed, int trail, int trailSpacing) {

    public static final IdleShape DEFAULT = new IdleShape(10, 1, 0.7, 1.9, 0.1, 2, 12, 0.06, 4, 1);

    public static IdleShape load(ConfigurationSection sec, IdleShape base, String context, Logger logger) {
        if (sec == null) return base;
        return new IdleShape(
                Math.max(0, (int) num(sec, "delay", base.delay, context, logger)),
                Math.max(1, (int) num(sec, "interval", base.interval, context, logger)),
                num(sec, "radius", base.radius, context, logger),
                num(sec, "top", base.top, context, logger),
                num(sec, "bottom", base.bottom, context, logger),
                Math.max(1, (int) num(sec, "strands", base.strands, context, logger)),
                num(sec, "rotate_speed", base.rotateSpeed, context, logger),
                num(sec, "fall_speed", base.fallSpeed, context, logger),
                Math.max(1, (int) num(sec, "trail", base.trail, context, logger)),
                Math.max(1, (int) num(sec, "trail_spacing", base.trailSpacing, context, logger)));
    }

    /**
     * @param time animation time in ticks (how long this player has been idle)
     */
    public void draw(World world, double x, double y, double z, long time, List<ParticleEffect> effects) {
        double range = Math.max(0.01, top - bottom);
        double rotRad = Math.toRadians(rotateSpeed);
        double strandAngle = 2 * Math.PI / strands;

        for (int i = 0; i < strands; i++) {
            for (int k = 0; k < trail; k++) {
                long t = time - (long) k * trailSpacing;
                if (t < 0) break; // trail grows in as the idle effect starts
                double angle = t * rotRad + i * strandAngle;
                double fallen = floorMod(t * fallSpeed + i * range / strands, range);
                double px = x + Math.cos(angle) * radius;
                double py = y + top - fallen;
                double pz = z + Math.sin(angle) * radius;
                for (ParticleEffect e : effects) e.spawn(world, px, py, pz);
            }
        }
    }

    private static double floorMod(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }

    private static double num(ConfigurationSection sec, String key, double def, String context, Logger logger) {
        return ParticleEffect.number(sec.get(key), def, key, context, logger);
    }
}
