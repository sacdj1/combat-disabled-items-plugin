package dev.sacdj.scdi.team;

import dev.sacdj.scdi.config.ScdiConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory team assignment (not persisted across restarts - matches the
 * datapack's scdi_team scoreboard, which is likewise session/world state,
 * not meant to survive an uninstall/reinstall). No proximity guessing
 * needed for exemption checks the way the datapack has to, since Bukkit's
 * damage event already exposes both the real attacker and victim directly.
 */
public final class TeamManager {

    private final JavaPlugin plugin;
    private final ScdiConfig config;

    private final Map<UUID, Integer> teamOf = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger nextTeamId = new AtomicInteger(1);
    private BukkitTask expiryTask;

    public TeamManager(JavaPlugin plugin, ScdiConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        expiryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::expireStale, 20L, 20L);
    }

    public void stop() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
    }

    public int teamOf(Player player) {
        return teamOf.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean sameTeam(Player a, Player b) {
        int teamA = teamOf(a);
        int teamB = teamOf(b);
        return teamA != 0 && teamA == teamB;
    }

    public void reset(Player player) {
        teamOf.remove(player.getUniqueId());
    }

    /** @return an error message, or null on success (a request was sent). */
    public String request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return "You can't team up with yourself.";
        }
        pendingRequests.put(target.getUniqueId(), new PendingRequest(requester.getUniqueId(), System.currentTimeMillis()));
        return null;
    }

    /** @return an error message, or null on success (target joined the requester's team). */
    public String confirm(Player target) {
        PendingRequest request = pendingRequests.remove(target.getUniqueId());
        if (request == null) {
            return "You don't have a pending team request.";
        }
        Player requester = Bukkit.getPlayer(request.requesterId());
        if (requester == null || !requester.isOnline()) {
            return "That player isn't online anymore.";
        }
        int requesterTeam = teamOf(requester);
        if (requesterTeam == 0) {
            requesterTeam = nextTeamId.getAndIncrement();
            teamOf.put(requester.getUniqueId(), requesterTeam);
        }
        teamOf.put(target.getUniqueId(), requesterTeam);
        return null;
    }

    private void expireStale() {
        long timeoutMs = config.teamRequestTimeoutSeconds() * 1000;
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> now - entry.getValue().sentAtMillis() > timeoutMs);
    }

    private record PendingRequest(UUID requesterId, long sentAtMillis) {
    }
}
