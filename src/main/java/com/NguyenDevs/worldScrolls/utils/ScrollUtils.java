package com.NguyenDevs.worldScrolls.utils;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.comp.WGPlugin;
import com.NguyenDevs.worldScrolls.comp.WorldGuardOff;
import com.NguyenDevs.worldScrolls.comp.WorldGuardOn;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ScrollUtils {
    
    private static WorldScrolls plugin;
    private static WorldGuardOn worldGuardOn;
    private static WorldGuardOff worldGuardOff;
    
    /**
     * Initialize ScrollUtils with plugin instance
     * @param pluginInstance The main plugin instance
     */
    public static void initialize(WorldScrolls pluginInstance) {
        plugin = pluginInstance;
        
        if (WGPlugin.isWorldGuardAvailable()) {
            worldGuardOn = new WorldGuardOn(plugin);
            plugin.getLogger().info("ScrollUtils initialized with WorldGuard support");
        } else {
            worldGuardOff = new WorldGuardOff();
            plugin.getLogger().info("ScrollUtils initialized without WorldGuard (scrolls allowed everywhere)");
        }
    }
    
    /**
     * Check if a player can use scrolls at their current location
     * @param player The player trying to use a scroll
     * @return true if scrolls are allowed, false if blocked
     */
    public static boolean canUseScrolls(Player player) {
        // Check if world is disabled first
        if (plugin.getConfigManager().isWorldDisabled(player.getWorld().getName())) {
            return false;
        }
        
        // Check WorldGuard regions
        if (WGPlugin.isWorldGuardAvailable() && worldGuardOn != null) {
            return worldGuardOn.canUseScrolls(player);
        } else if (worldGuardOff != null) {
            return worldGuardOff.canUseScrolls(player);
        }
        
        return true; // Fallback to allow
    }
    
    /**
     * Check if a player can use scrolls at a specific location
     * @param player The player trying to use a scroll
     * @param location The target location for the scroll effect
     * @return true if scrolls are allowed, false if blocked
     */
    public static boolean canUseScrollsAt(Player player, Location location) {
        // Check if world is disabled first
        if (plugin.getConfigManager().isWorldDisabled(location.getWorld().getName())) {
            return false;
        }
        
        // Check WorldGuard regions
        if (WGPlugin.isWorldGuardAvailable() && worldGuardOn != null) {
            return worldGuardOn.canUseScrollsAt(player, location);
        } else if (worldGuardOff != null) {
            return worldGuardOff.canUseScrollsAt(player, location);
        }
        
        return true; // Fallback to allow
    }
    
    /**
     * Get the reason why scrolls are blocked for a player
     * @param player The player
     * @return A user-friendly message explaining why scrolls are blocked
     */
    public static String getScrollBlockedReason(Player player) {
        // Check world disabled first
        if (plugin.getConfigManager().isWorldDisabled(player.getWorld().getName())) {
            return plugin.getConfigManager().getMessage("scroll-blocked-world");
        }
        
        // Check WorldGuard
        if (WGPlugin.isWorldGuardAvailable() && worldGuardOn != null) {
            if (!worldGuardOn.canUseScrolls(player)) {
                return worldGuardOn.getBlockedMessage(player);
            }
        }
        
        return ""; // No blocking reason
    }
    
    /**
     * Check if scrolls are blocked and send appropriate message to player
     * @param player The player
     * @return true if scrolls are allowed, false if blocked (message sent to player)
     */
    public static boolean checkScrollUsageAndNotify(Player player) {
        if (!canUseScrolls(player)) {
            String reason = getScrollBlockedReason(player);
            if (!reason.isEmpty()) {
                player.sendMessage(ColorUtils.colorize(reason));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("scroll-blocked-generic"));
            }
            
            // Play permission denied sound (bass with low pitch)
            com.NguyenDevs.worldScrolls.utils.SoundUtils.playPermissionDeniedSound(player);
            return false;
        }
        return true;
    }
    
    /**
     * Check if scrolls are blocked at location and send appropriate message to player
     * @param player The player
     * @param location The location to check
     * @return true if scrolls are allowed, false if blocked (message sent to player)
     */
    public static boolean checkScrollUsageAtAndNotify(Player player, Location location) {
        if (!canUseScrollsAt(player, location)) {
            // Check if it's world restriction or region restriction
            if (plugin.getConfigManager().isWorldDisabled(location.getWorld().getName())) {
                player.sendMessage(plugin.getConfigManager().getMessage("scroll-blocked-world"));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("scroll-blocked-region"));
            }
            
            // Play permission denied sound (bass with low pitch)
            com.NguyenDevs.worldScrolls.utils.SoundUtils.playPermissionDeniedSound(player);
            return false;
        }
        return true;
    }
    
    /**
     * Get WorldGuard integration status
     * @return true if WorldGuard is available and working
     */
    public static boolean isWorldGuardIntegrationActive() {
        return WGPlugin.isWorldGuardAvailable() && 
               worldGuardOn != null && 
               worldGuardOn.isIntegrationWorking();
    }
    
    /**
     * Get information about current protection status for admin
     * @param player The player to check (for location context)
     * @return Status information string
     */
    public static String getProtectionInfo(Player player) {
        StringBuilder info = new StringBuilder();
        
        info.append("&e&lScroll Protection Status:").append("\n");
        info.append("&7World: &e").append(player.getWorld().getName()).append("\n");
        info.append("&7World Disabled: ").append(plugin.getConfigManager().isWorldDisabled(player.getWorld().getName()) ? "&cYes" : "&aNo").append("\n");
        info.append("&7WorldGuard Available: ").append(WGPlugin.isWorldGuardAvailable() ? "&aYes" : "&cNo").append("\n");
        
        if (isWorldGuardIntegrationActive()) {
            info.append("&7WSC-AFFECT Flag: &aRegistered").append("\n");
            info.append("&7Scrolls Allowed Here: ").append(canUseScrolls(player) ? "&aYes" : "&cNo").append("\n");
        }
        
        return info.toString();
    }
}