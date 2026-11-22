package org.neko.walkorbit;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every tick. Tracks each player's movement itself (server-side velocity is unreliable for
 * walking players) and draws either the walk shape or the idle shape.
 */
public class OrbitTask extends BukkitRunnable {
    /** Horizontal movement per tick (squared) above which the player counts as moving. Sneaking is ~0.065/tick. */
    private static final double MOVE_THRESHOLD_SQ = 0.03 * 0.03;

    private final WalkOrbit plugin;
    private final Map<UUID, Motion> motions = new HashMap<>();
    private long tick;

    private static final class Motion {
        UUID world;
        double x, z;
        long stillTicks;
        /** Where the walk bands were last drawn; null when the streak should start fresh. */
        WalkShape.Pose lastWalk;
    }

    public OrbitTask(WalkOrbit plugin) {
        this.plugin = plugin;
    }

    public void forget(UUID player) {
        motions.remove(player);
    }

    @Override
    public void run() {
        tick++;
        Lingering.tick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            OrbitGroup group = plugin.getSelectedGroup(p);
            if (group == null) {
                motions.remove(p.getUniqueId());
                continue;
            }

            Location loc = p.getLocation();
            World world = loc.getWorld();
            if (world == null) continue;
            boolean moved = updateMotion(p.getUniqueId(), loc);
            Motion m = motions.get(p.getUniqueId());

            if (!canShow(p)) {
                m.lastWalk = null;
                continue;
            }

            if (moved) {
                if (!group.walk().isEmpty() && tick % group.walkShape().interval() == 0) {
                    WalkShape.Pose now = new WalkShape.Pose(loc.getX(), loc.getY(), loc.getZ(), p.getBodyYaw());
                    group.walkShape().draw(world, m.lastWalk, now, p.isSneaking(), group.walk());
                    m.lastWalk = now;
                }
            } else {
                m.lastWalk = null;
                IdleShape shape = group.idleShape();
                long idleTime = m.stillTicks - shape.delay();
                if (!group.idle().isEmpty() && idleTime >= 0 && tick % shape.interval() == 0) {
                    shape.draw(world, loc.getX(), loc.getY(), loc.getZ(), idleTime, group.idle());
                }
            }
        }
    }

    /** Returns true if the player moved horizontally since last tick. */
    private boolean updateMotion(UUID id, Location loc) {
        Motion m = motions.computeIfAbsent(id, k -> new Motion());
        UUID worldId = loc.getWorld().getUID();
        boolean moved;
        if (!worldId.equals(m.world)) {
            moved = false;
            m.stillTicks = 0;
            m.lastWalk = null;
        } else {
            double dx = loc.getX() - m.x, dz = loc.getZ() - m.z;
            moved = dx * dx + dz * dz > MOVE_THRESHOLD_SQ;
        }
        m.world = worldId;
        m.x = loc.getX();
        m.z = loc.getZ();
        m.stillTicks = moved ? 0 : m.stillTicks + 1;
        return moved;
    }

    /** Walk Orbit is for being on foot: skip elytra, creative flight, vehicles, swimming, spectators and invisibility. */
    private static boolean canShow(Player p) {
        return !p.isDead()
                && p.getGameMode() != GameMode.SPECTATOR
                && !p.isGliding()
                && !p.isFlying()
                && !p.isInsideVehicle()
                && !p.isSwimming()
                && !p.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }
}
