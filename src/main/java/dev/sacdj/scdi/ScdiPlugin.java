package dev.sacdj.scdi;

import dev.sacdj.scdi.combat.CombatListener;
import dev.sacdj.scdi.combat.CombatManager;
import dev.sacdj.scdi.combat.OneShotTracker;
import dev.sacdj.scdi.command.ScdiCommand;
import dev.sacdj.scdi.config.ConfigCodec;
import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.disguise.DisguiseManager;
import dev.sacdj.scdi.disguise.DisguiseProtectionListener;
import dev.sacdj.scdi.dummy.DummyListener;
import dev.sacdj.scdi.dummy.DummyManager;
import dev.sacdj.scdi.menu.MenuManager;
import dev.sacdj.scdi.proximity.ProximityManager;
import dev.sacdj.scdi.team.TeamManager;
import dev.sacdj.scdi.util.ChatInputManager;
import dev.sacdj.scdi.warning.WarningManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScdiPlugin extends JavaPlugin {

    private ScdiConfig config;
    private DisguiseManager disguiseManager;
    private CombatManager combatManager;
    private ProximityManager proximityManager;
    private TeamManager teamManager;
    private DummyManager dummyManager;

    @Override
    public void onEnable() {
        config = new ScdiConfig(this);
        WarningManager warningManager = new WarningManager(this, config);
        disguiseManager = new DisguiseManager(this, config, warningManager);
        OneShotTracker oneShotTracker = new OneShotTracker(config);
        combatManager = new CombatManager(this, config, disguiseManager, oneShotTracker);
        teamManager = new TeamManager(this, config);
        proximityManager = new ProximityManager(this, config, combatManager, teamManager);
        dummyManager = new DummyManager(this, config);
        MenuManager menuManager = new MenuManager(this);
        ChatInputManager chatInput = new ChatInputManager(this);
        ConfigCodec codec = new ConfigCodec(this);

        getServer().getPluginManager().registerEvents(
                new CombatListener(config, combatManager, oneShotTracker, teamManager), this);
        getServer().getPluginManager().registerEvents(new DummyListener(dummyManager), this);
        getServer().getPluginManager().registerEvents(new DisguiseProtectionListener(disguiseManager), this);
        getCommand("scdi").setExecutor(new ScdiCommand(
                config, combatManager, menuManager, chatInput, codec, teamManager, dummyManager, warningManager));

        combatManager.start();
        disguiseManager.start();
        proximityManager.start();
        teamManager.start();
        dummyManager.start();
        getLogger().info("Combat Disabled Items enabled.");
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.stop();
        }
        if (disguiseManager != null) {
            disguiseManager.stop();
        }
        if (proximityManager != null) {
            proximityManager.stop();
        }
        if (teamManager != null) {
            teamManager.stop();
        }
        if (dummyManager != null) {
            dummyManager.stop();
        }
        getLogger().info("Combat Disabled Items disabled.");
    }
}
