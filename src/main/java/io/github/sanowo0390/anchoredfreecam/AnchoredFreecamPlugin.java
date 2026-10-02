package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnchoredFreecamPlugin extends JavaPlugin {
    private FreecamManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new FreecamManager(this);

        FreecamCommand freecamCommand = new FreecamCommand(this, manager);
        PluginCommand command = getCommand("freecam");
        if (command == null) {
            throw new IllegalStateException("freecam command is missing from plugin.yml");
        }
        command.setExecutor(freecamCommand);
        command.setTabCompleter(freecamCommand);

        getServer().getPluginManager().registerEvents(new FreecamListener(this, manager), this);
        getLogger().info("AnchoredFreecam enabled. Max distance: " + manager.getMaxDistance());
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
