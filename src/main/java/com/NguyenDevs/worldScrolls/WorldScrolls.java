package com.NguyenDevs.worldScrolls;

import com.NguyenDevs.worldScrolls.commands.WorldScrollsCommand;
import com.NguyenDevs.worldScrolls.commands.WorldScrollsTabCompleter;
import com.NguyenDevs.worldScrolls.listeners.PlayerListener;
import com.NguyenDevs.worldScrolls.listeners.ScrollOfExit;
import com.NguyenDevs.worldScrolls.managers.ConfigManager;
import com.NguyenDevs.worldScrolls.managers.GUIManager;
import com.NguyenDevs.worldScrolls.utils.ScrollUtils;
import com.NguyenDevs.worldScrolls.utils.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldScrolls extends JavaPlugin {

    private ConfigManager configManager;
    private GUIManager guiManager;
    private static WorldScrolls instance;
    
    @Override
    public void onEnable() {
        // Set instance for static access
        instance = this;
        
        // Initialize ConfigManager first
        configManager = new ConfigManager(this);
        configManager.initializeConfigs();
        
        // Initialize GUIManager
        guiManager = new GUIManager(this);
        
        // Initialize ScrollUtils for WorldGuard integration
        ScrollUtils.initialize(this);
        
        // Register additional event listeners
        registerEventListeners();
        
        // Register commands and tab completers
        registerCommands();
        
        // Check for updates if enabled
        if (configManager.isUpdateNotifyEnabled()) {
            checkForUpdates();
        }
        
        // Print startup message
        getLogger().info("WorldScrolls v" + getDescription().getVersion() + " has been enabled!");
        getLogger().info("Plugin developed by NguyenDevs");
        
        // Check dependencies
        checkDependencies();
    }

    @Override
    public void onDisable() {
        // Save all configurations before shutdown
        if (configManager != null) {
            configManager.saveAllConfigs();
        }
        
        getLogger().info("WorldScrolls has been disabled!");
        instance = null;
    }
    
    /**
     * Register additional event listeners
     */
    private void registerEventListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ScrollOfExit(this), this);
        getLogger().info("Additional event listeners registered successfully!");
    }
    
    /**
     * Register commands and tab completers
     */
    private void registerCommands() {
        PluginCommand mainCommand = getCommand("worldscrolls");
        if (mainCommand != null) {
            WorldScrollsCommand commandExecutor = new WorldScrollsCommand(this);
            WorldScrollsTabCompleter tabCompleter = new WorldScrollsTabCompleter(this);
            
            mainCommand.setExecutor(commandExecutor);
            mainCommand.setTabCompleter(tabCompleter);
            
            getLogger().info("Commands registered successfully!");
        } else {
            getLogger().severe("Failed to register main command! Check plugin.yml");
        }
    }
    
    /**
     * Check for plugin updates
     */
    private void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                UpdateChecker updateChecker = new UpdateChecker(this, 123456); // Replace with actual Spigot resource ID
                updateChecker.getVersion(version -> {
                    if (!getDescription().getVersion().equals(version)) {
                        getLogger().info("A new version of WorldScrolls is available: " + version);
                        getLogger().info("Download it from: https://www.spigotmc.org/resources/worldscrolls.123456/");
                    } else {
                        getLogger().info("You are running the latest version of WorldScrolls!");
                    }
                });
            } catch (Exception e) {
                getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }
    
    /**
     * Check if required dependencies are present
     */
    private void checkDependencies() {
        // Check WorldGuard
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().warning("WorldGuard not found! Some features may not work properly.");
        } else {
            getLogger().info("WorldGuard integration enabled!");
        }
        
        // Check ProtocolLib
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().warning("ProtocolLib not found! Some visual effects may not work properly.");
        } else {
            getLogger().info("ProtocolLib integration enabled!");
        }
        
        // Check PlaceholderAPI (soft dependency)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI integration enabled!");
        }
    }
    
    /**
     * Get the ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Get the GUIManager instance
     */
    public GUIManager getGUIManager() {
        return guiManager;
    }
    
    /**
     * Get the plugin instance
     */
    public static WorldScrolls getInstance() {
        return instance;
    }
    
    /**
     * Reload the plugin
     */
    public void reloadPlugin() {
        if (configManager != null) {
            configManager.reloadConfigs();
        }
        getLogger().info("Plugin reloaded successfully!");
    }
}
