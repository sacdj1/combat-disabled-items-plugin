package dev.sacdj.scdi;

import dev.sacdj.scdi.combat.CombatListener;
import dev.sacdj.scdi.combat.CombatManager;
import dev.sacdj.scdi.command.ScdiCommand;
import dev.sacdj.scdi.config.ConfigCodec;
import dev.sacdj.scdi.config.ScdiConfig;
import dev.sacdj.scdi.disguise.DisguiseManager;
import dev.sacdj.scdi.menu.MenuManager;
import dev.sacdj.scdi.util.ChatInputManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScdiPlugin extends JavaPlugin {

    private ScdiConfig config;
    private DisguiseManager disguiseManager;
    private CombatManager combatManager;

    @Override
    public void onEnable() {
        config = new ScdiConfig(this);
        disguiseManager = new DisguiseManager(this, config);
        combatManager = new CombatManager(this, config, disguiseManager);
        MenuManager menuManager = new MenuManager(this);
        ChatInputManager chatInput = new ChatInputManager(this);
        ConfigCodec codec = new ConfigCodec(this);

        getServer().getPluginManager().registerEvents(new CombatListener(config, combatManager), this);
        getCommand("scdi").setExecutor(new ScdiCommand(config, combatManager, menuManager, chatInput, codec));

        combatManager.start();
        getLogger().info("Combat Disabled Items enabled.");
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.stop();
        }
        getLogger().info("Combat Disabled Items disabled.");
    }
}
