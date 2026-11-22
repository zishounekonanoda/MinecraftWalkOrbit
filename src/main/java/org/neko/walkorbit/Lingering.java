package org.neko.walkorbit;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * How long a particle stays on screen is decided by the client per particle type; the server cannot
 * extend it. To make any particle "last" longer, points that ask for {@code linger} are remembered
 * here and re-sent at the same place every {@code refresh} ticks until {@code linger} ticks have
 * passed, optionally sliding down by {@code fall} over that time.
 *
 * <p>Ticked by {@link OrbitTask} on the main thread.
 */
public final class Lingering {
    /** Safety cap on remembered points across all players. New points beyond it simply don't linger. */
    private static final int MAX_POINTS = 20_000;

    private static final List<Point> points = new ArrayList<>();
    private static long now;

    private record Point(ParticleEffect effect, World world, double x, double y, double z, long born) {
    }

    private Lingering() {
    }

    static void add(ParticleEffect effect, World world, double x, double y, double z) {
        if (points.size() < MAX_POINTS) points.add(new Point(effect, world, x, y, z, now));
    }

    /** Advances one tick and re-sends every point that is due. */
    static void tick() {
        now++;
        Iterator<Point> it = points.iterator();
        while (it.hasNext()) {
            Point p = it.next();
            ParticleEffect e = p.effect;
            long age = now - p.born;
            if (age >= e.linger()) {
                it.remove();
                continue;
            }
            if (age % e.refresh() == 0) {
                double y = p.y - e.fall() * age / e.linger();
                e.emit(p.world, p.x, y, p.z);
            }
        }
    }

    /** Drops everything (plugin reload / disable). */
    static void clear() {
        points.clear();
    }
}
