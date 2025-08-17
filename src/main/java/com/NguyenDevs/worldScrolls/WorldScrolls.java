package com.NguyenDevs.worldScrolls;

import com.NguyenDevs.worldScrolls.commands.WorldScrollsCommand;
import com.NguyenDevs.worldScrolls.commands.WorldScrollsTabCompleter;
import com.NguyenDevs.worldScrolls.comp.WGPlugin;
import com.NguyenDevs.worldScrolls.comp.WorldGuardOff;
import com.NguyenDevs.worldScrolls.comp.WorldGuardOn;
import com.NguyenDevs.worldScrolls.guis.PlayerGUI;
import com.NguyenDevs.worldScrolls.listeners.PlayerListener;
import com.NguyenDevs.worldScrolls.listeners.scrolls.*;
import com.NguyenDevs.worldScrolls.managers.ConfigManager;
import com.NguyenDevs.worldScrolls.managers.GUIManager;
import com.NguyenDevs.worldScrolls.managers.RecipeManager;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class WorldScrolls extends JavaPlugin {

    private ConfigManager configManager;
    private GUIManager guiManager;
    private RecipeManager recipeManager;
    private static WorldScrolls instance;
    private WGPlugin wgPlugin;
    private boolean worldGuardReady = false;
    private ScrollOfMeteor scrollOfMeteor;
    private ScrollOfExit scrollOfExit;
    private ScrollOfGravitation scrollOfGravitation;

    @Override
    public void onLoad() {
        instance = this;
        registerWorldGuardFlags();

    }

    @Override
    public void onEnable() {

        instance = this;
        //initializeWorldGuard();

        configManager = new ConfigManager(this);
        configManager.initializeConfigs();
        recipeManager = new RecipeManager(this);
        recipeManager.loadRecipes();
        guiManager = new GUIManager(this);
        registerEventListeners();

        registerCommands();
        
        //checkDependencies();
        
        printLogo();
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aWorldScrolls plugin enabled successfully!"));

    }

    @Override
    public void onDisable() {
        if (configManager != null) {
            configManager.saveAllConfigs();
        }

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cWorld Scrolls plugin disabled!"));
        instance = null;
    }
    
    private void registerEventListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        scrollOfExit = new ScrollOfExit(this);
        Bukkit.getPluginManager().registerEvents(scrollOfExit, this);
        scrollOfMeteor = new ScrollOfMeteor(this);
        Bukkit.getPluginManager().registerEvents(scrollOfMeteor, this);
        scrollOfGravitation = new ScrollOfGravitation(this);
        Bukkit.getPluginManager().registerEvents(scrollOfGravitation, this);
    }
    
    private void registerCommands() {
        PluginCommand mainCommand = getCommand("worldscrolls");
        if (mainCommand != null) {
            WorldScrollsCommand commandExecutor = new WorldScrollsCommand(this);
            WorldScrollsTabCompleter tabCompleter = new WorldScrollsTabCompleter(this);
            
            mainCommand.setExecutor(commandExecutor);
            mainCommand.setTabCompleter(tabCompleter);

        } else {
            getLogger().severe("Failed to register main command! Check plugin.yml");
        }
    }
    
    private void checkDependencies() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cProtocolLib not found! Some visual effects may not work properly!"));
        } else {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aProtocolLib integration enabled!"));
        }
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    public RecipeManager getRecipeManager() {
        return recipeManager;
    }
    public ScrollOfMeteor getScrollOfMeteor() {
        return scrollOfMeteor;
    }
    public ScrollOfExit getScrollOfExit() {
        return scrollOfExit;
    }
    public ScrollOfGravitation getScrollOfGravitation() { return scrollOfGravitation; }
    public GUIManager getGUIManager() {
        return guiManager;
    }

    public static WorldScrolls getInstance() {
        return instance;
    }


    private void registerWorldGuardFlags() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

            String effectFlagPath = "wsc-effect";
            if (registry.get(effectFlagPath) == null) {
                StateFlag effectFlag = new StateFlag(effectFlagPath, true);
                registry.register(effectFlag);
            }
            

        } catch (FlagConflictException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFlag conflict while registering WorldGuard flags: " + e.getMessage()));

        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cUnexpected error while registering WorldGuard flags") + e.getMessage());
        }
    }

    private void initializeWorldGuard() {
        try {
            if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
                org.bukkit.plugin.Plugin wgPlugin = getServer().getPluginManager().getPlugin("WorldGuard");
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7[&dWorld&5Scroll&7] &aWorldGuard version: " + wgPlugin.getDescription().getVersion()));
                try {
                    this.wgPlugin = new WorldGuardOn();
                    if (this.wgPlugin instanceof WorldGuardOn) {
                        WorldGuardOn wgOn = (WorldGuardOn) this.wgPlugin;
                        boolean isReady = wgOn.isReady();
                        int flagCount = wgOn.getRegisteredFlags().size();
                        if (isReady && flagCount > 0) {
                            worldGuardReady = true;
                            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&7[&dWorld&5Scroll&7] &aWorldGuard integration ready with " + flagCount +
                                            " custom flags."));
                        } else {
                            getServer().getScheduler().runTaskLater(this, () -> {
                                boolean delayedReady = wgOn.isReady();
                                int delayedFlagCount = wgOn.getRegisteredFlags().size();
                                if (delayedReady && delayedFlagCount > 0) {
                                    worldGuardReady = true;
                                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                                            "&7[&dWorld&5Scroll&7] &aWorldGuard integration ready with " +
                                                    delayedFlagCount + " custom flags."));
                                } else {
                                    getLogger().severe("WorldGuard integration failed - flags not loaded properly");
                                    getLogger().severe("Ready: " + delayedReady + ", Flag count: " + delayedFlagCount);
                                    worldGuardReady = false;
                                }
                            }, 40L);
                            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&7[&dWorld&5Scroll&7] &6WorldGuard integration created, waiting for flags to load..."));
                        }
                    } else {
                        throw new IllegalStateException("WorldGuardOn instance creation failed");
                    }
                } catch (IllegalStateException e) {
                    getLogger().severe("Failed to initialize WorldGuardOn - " + e.getMessage());
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&7[&dWorld&5Scroll&7] &cWorldGuardOn failed: " + e.getMessage()));
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&7[&dWorld&5Scroll&7] &6Falling back to WorldGuardOff mode"));
                    this.wgPlugin = new WorldGuardOff();
                    worldGuardReady = true;
                } catch (NoClassDefFoundError e) {
                    getLogger().severe("WorldGuard classes not found - " + e.getMessage());
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&7[&dWorld&5Scroll&7] &cMissing WorldGuard dependencies"));
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&7[&dWorld&5Scroll&7] &6Falling back to WorldGuardOff mode"));
                    this.wgPlugin = new WorldGuardOff();
                    worldGuardReady = true;
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Unexpected error initializing WorldGuardOn", e);
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&7[&dWorld&5Scroll&7] &cUnexpected error: " + e.getClass().getSimpleName()));
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&7[&dWorld&5Scroll&7] &6Falling back to WorldGuardOff mode"));
                    this.wgPlugin = new WorldGuardOff();
                    worldGuardReady = true;
                }
            } else {
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&7[&dWorld&5Scroll&7] &6WorldGuard not found, using fallback mode"));
                this.wgPlugin = new WorldGuardOff();
                worldGuardReady = true;
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize WorldGuard integration", e);
            this.wgPlugin = new WorldGuardOff();
            worldGuardReady = true;
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7[&dWorld&5Scroll&7] &cForced fallback to WorldGuardOff due to initialization error"));
        }
    }

    public boolean isWorldGuardReady() {
        return worldGuardReady && wgPlugin != null;
    }

    public WGPlugin getWorldGuard() {
        if (wgPlugin == null) {
            getLogger().warning("WorldGuard plugin requested but not initialized, returning fallback");
            return new WorldGuardOff();
        }
        return wgPlugin;
    }
    public void printLogo() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', ""));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&d   ██╗    ██╗ ██████╗ ██████╗ ██╗     ██████╗ "));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&d   ██║    ██║██╔═══██╗██╔══██╗██║     ██╔══██╗"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&d   ██║ █╗ ██║██║   ██║██████╔╝██║     ██║  ██║"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&d   ██║███╗██║██║   ██║██╔══██╗██║     ██║  ██║"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&d   ╚███╔███╔╝╚██████╔╝██║  ██║███████╗██████╔╝"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&d    ╚══╝╚══╝  ╚═════╝ ╚═╝  ╚═╝╚══════╝╚═════╝ "));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', ""));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&5   ███████╗ ██████╗██████╗  ██████╗ ██╗     ██╗     ███████╗"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&5   ██╔════╝██╔════╝██╔══██╗██╔═══██╗██║     ██║     ██╔════╝"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&5   ███████╗██║     ██████╔╝██║   ██║██║     ██║     ███████╗"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&5   ╚════██║██║     ██╔══██╗██║   ██║██║     ██║     ╚════██║"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&5   ███████║╚██████╗██║  ██║╚██████╔╝███████╗███████╗███████║"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&5   ╚══════╝ ╚═════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚══════╝╚══════╝"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', ""));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&d         World Scrolls"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&6         Version " + getDescription().getVersion()));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&b         Development by NguyenDevs"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', ""));
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }
}
