package dev.sacdj.scdi.team;

import dev.sacdj.scdi.config.ScdiConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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

    private static final ChatColor[] TEAM_COLORS = {
            ChatColor.AQUA, ChatColor.LIGHT_PURPLE, ChatColor.GREEN, ChatColor.GOLD,
            ChatColor.BLUE, ChatColor.RED, ChatColor.YELLOW, ChatColor.DARK_AQUA
    };

    private final Map<UUID, Integer> teamOf = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger nextTeamId = new AtomicInteger(1);
    private BukkitTask expiryTask;
    private boolean lastShowTeamOnTab;

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
        updateTabTeam(player);
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
        updateTabTeam(requester);
        updateTabTeam(target);
        return null;
    }

    private void expireStale() {
        long timeoutMs = config.teamRequestTimeoutSeconds() * 1000;
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> now - entry.getValue().sentAtMillis() > timeoutMs);

        // only worth a full re-scan of every teamed player when the toggle
        // itself actually flips - a plain boolean compare once/sec is free,
        // versus touching the scoreboard for everyone on every tick.
        boolean show = config.showTeamOnTab();
        if (show != lastShowTeamOnTab) {
            lastShowTeamOnTab = show;
            for (UUID id : teamOf.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    updateTabTeam(player);
                }
            }
        }
    }

    /** Rides the vanilla scoreboard-team mechanism, which the tab list (and
     * nametags) already color/prefix automatically for any player who's a
     * member - no manual per-player display-name patching needed. Each
     * SCDI team gets its own lazily-created Bukkit team named
     * "scdi_team_&lt;id&gt;"; a player can only be on one team on a given
     * scoreboard at a time, so this always clears any prior scdi_team_*
     * membership first. */
    private void updateTabTeam(Player player) {
        Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
        String entry = player.getName();
        for (Team existing : scoreboard.getTeams()) {
            if (existing.getName().startsWith("scdi_team_") && existing.hasEntry(entry)) {
                existing.removeEntry(entry);
            }
        }
        if (!config.showTeamOnTab()) {
            return;
        }
        int teamId = teamOf(player);
        if (teamId == 0) {
            return;
        }
        String teamName = "scdi_team_" + teamId;
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setColor(TEAM_COLORS[teamId % TEAM_COLORS.length]);
            team.setPrefix(TEAM_COLORS[teamId % TEAM_COLORS.length] + "[T" + teamId + "] ");
        }
        team.addEntry(entry);
    }

    private record PendingRequest(UUID requesterId, long sentAtMillis) {
    }
}
