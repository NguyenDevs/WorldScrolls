package com.NguyenDevs.worldScrolls.comp;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.entity.Player;

public class WorldGuardIntegration {
    
    private final WorldScrolls plugin;
    private static BooleanFlag WSC_AFFECT_FLAG;
    private boolean isWorldGuardAvailable = false;
    
    public WorldGuardIntegration(WorldScrolls plugin) {
        this.plugin = plugin;
        initializeWorldGuard();
    }
    
    /**
     * Initialize WorldGuard integration and register custom flag
     */
    private void initializeWorldGuard() {
        try {
            // Check if WorldGuard is available
            if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
                plugin.getLogger().warning("WorldGuard not found! WorldGuard integration disabled.");
                return;
            }
            
            // Register custom flag
            registerCustomFlag();
            isWorldGuardAvailable = true;
            
            plugin.getLogger().info("WorldGuard integration enabled! WSC-AFFECT flag registered.");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize WorldGuard integration: " + e.getMessage());
            e.printStackTrace();
            isWorldGuardAvailable = false;
        }
    }
    
    /**
     * Register the WSC-AFFECT custom flag
     */
    private void registerCustomFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        
        try {
            // Create the flag - default ALLOW means scrolls work normally
            // Setting to DENY will block scroll usage in that region
            BooleanFlag flag = new BooleanFlag("wsc-affect");
            registry.register(flag);
            WSC_AFFECT_FLAG = flag;
            
            plugin.getLogger().info("Successfully registered WSC-AFFECT flag!");
            plugin.getLogger().info("Usage: /rg flag <region> wsc-affect deny (to block scrolls in region)");
            
        } catch (FlagConflictException e) {
            // Flag already exists, try to get it
            Flag<?> existing = registry.get("wsc-affect");
            if (existing instanceof BooleanFlag) {
                WSC_AFFECT_FLAG = (BooleanFlag) existing;
                plugin.getLogger().info("WSC-AFFECT flag already registered, using existing flag.");
            } else {
                plugin.getLogger().severe("WSC-AFFECT flag exists but is not a boolean flag!");
                throw new RuntimeException("Flag conflict: WSC-AFFECT already exists as different type");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register WSC-AFFECT flag: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Check if scrolls are allowed at the player's location
     * @param player The player using the scroll
     * @return true if scrolls are allowed, false if blocked by region
     */
    public boolean areScrollsAllowed(Player player) {
        if (!isWorldGuardAvailable || WSC_AFFECT_FLAG == null) {
            return true; // If WorldGuard not available, allow scrolls
        }
        
        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            Location location = BukkitAdapter.adapt(player.getLocation());
            
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));
            
            if (regions == null) {
                return true; // No regions in this world, allow scrolls
            }
            
            ApplicableRegionSet set = regions.getApplicableRegions(location.toVector().toBlockPoint());
            
            // Check the flag value - default ALLOW (null/true), DENY (false) blocks scrolls
            Boolean flagValue = set.queryValue(localPlayer, WSC_AFFECT_FLAG);
            
            // If flag is not set (null) or set to true, allow scrolls
            // If flag is set to false (deny), block scrolls
            return flagValue == null || flagValue;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard region for scroll usage: " + e.getMessage());
            return true; // On error, default to allowing scrolls
        }
    }
    
    /**
     * Check if scrolls are allowed at a specific location for a player
     * @param player The player using the scroll
     * @param location The location to check
     * @return true if scrolls are allowed, false if blocked by region
     */
    public boolean areScrollsAllowedAt(Player player, org.bukkit.Location location) {
        if (!isWorldGuardAvailable || WSC_AFFECT_FLAG == null) {
            return true;
        }
        
        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            Location wgLocation = BukkitAdapter.adapt(location);
            
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(location.getWorld()));
            
            if (regions == null) {
                return true;
            }
            
            ApplicableRegionSet set = regions.getApplicableRegions(wgLocation.toVector().toBlockPoint());
            Boolean flagValue = set.queryValue(localPlayer, WSC_AFFECT_FLAG);
            
            return flagValue == null || flagValue;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard region at location for scroll usage: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * Get a user-friendly message about why scrolls are blocked
     * @param player The player
     * @return Message explaining the restriction
     */
    public String getScrollBlockedMessage(Player player) {
        return plugin.getConfigManager().getMessage("scroll-blocked-region");
    }
    
    /**
     * Check if WorldGuard integration is available
     * @return true if WorldGuard is properly integrated
     */
    public boolean isAvailable() {
        return isWorldGuardAvailable && WSC_AFFECT_FLAG != null;
    }
    
    /**
     * Get the WSC-AFFECT flag instance
     * @return The custom flag, or null if not available
     */
    public static BooleanFlag getWSCAffectFlag() {
        return WSC_AFFECT_FLAG;
    }
    
    /**
     * Get information about the flag for admin commands
     * @return Flag information string
     */
    public String getFlagInfo() {
        if (!isAvailable()) {
            return "WorldGuard integration not available";
        }
        
        return "WSC-AFFECT flag registered successfully\n" +
               "Usage: /rg flag <region> wsc-affect allow|deny\n" +
               "Default: allow (scrolls work normally)\n" +
               "Set to 'deny' to block scroll usage in a region";
    }
}