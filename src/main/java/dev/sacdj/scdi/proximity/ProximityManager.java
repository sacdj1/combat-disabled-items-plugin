package dev.sacdj.scdi.proximity;

import dev.sacdj.scdi.combat.CombatManager;
import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.team.TeamManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps items disabled continuously while another player stays within
 * range, independent of whether a hit ever landed. Runs on a fixed 1-tick
 * scheduler but only does real work every {@link ScdiConfig#proximityIntervalTicks()}
 * ticks (a plain counter, not a rescheduled task, so the interval stays
 * live-reloadable) - the O(n^2) pairwise distance check only runs among
 * currently-online players and only that often, not every tick.
 */
public final class ProximityManager {

    private final JavaPlugin plugin;
    private final ScdiConfig config;
    private final CombatManager combat;
    private final TeamManager teams;

    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastMovementSq = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long tickCounter;

    public ProximityManager(JavaPlugin plugin, ScdiConfig config, CombatManager combat, TeamManager teams) {
        this.plugin = plugin;
        this.config = config;
        this.combat = combat;
        this.teams = teams;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void tick() {
        if (!config.proximityEnabled()) {
            return;
        }
        tickCounter++;
        if (tickCounter % config.proximityIntervalTicks() != 0) {
            return;
        }

        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        for (Player player : online) {
            Location now = player.getLocation();
            Location previous = lastLocation.put(player.getUniqueId(), now);
            double movedSq = (previous != null && previous.getWorld().equals(now.getWorld()))
                    ? previous.distanceSquared(now) : 0.0;
            lastMovementSq.put(player.getUniqueId(), movedSq);
        }

        for (int i = 0; i < online.size(); i++) {
            for (int j = i + 1; j < online.size(); j++) {
                checkPair(online.get(i), online.get(j));
            }
        }
    }

    private void checkPair(Player a, Player b) {
        if (!a.getWorld().equals(b.getWorld())) {
            return;
        }
        boolean eitherTagged = combat.isTagged(a) || combat.isTagged(b);
        double range = eitherTagged ? config.proximityRetagDistance() : config.proximityDistance();
        if (a.getLocation().distanceSquared(b.getLocation()) > range * range) {
            return;
        }

        boolean sameTeam = teams.sameTeam(a, b);
        if (sameTeam && !config.teamTagProximity()) {
            return;
        }

        if (config.proximityRoleByMovement() && !sameTeam) {
            double movedA = lastMovementSq.getOrDefault(a.getUniqueId(), 0.0);
            double movedB = lastMovementSq.getOrDefault(b.getUniqueId(), 0.0);
            Player attacker = movedA >= movedB ? a : b;
            Player victim = movedA >= movedB ? b : a;
            if (config.tagAttacker()) {
                combat.tag(attacker);
            }
            if (config.tagVictim()) {
                combat.tag(victim);
            }
            return;
        }

        combat.tag(a);
        combat.tag(b);
    }
}
