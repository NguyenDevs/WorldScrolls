package com.NguyenDevs.worldScrolls.comp;

import org.bukkit.Bukkit;

public class WGPlugin {
    
    /**
     * Check if WorldGuard plugin is available
     * @return true if WorldGuard is loaded and enabled
     */
    public static boolean isWorldGuardAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null &&
               Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }
    
    /**
     * Get WorldGuard plugin version if available
     * @return version string or null if not available
     */
    public static String getWorldGuardVersion() {
        if (isWorldGuardAvailable()) {
            return Bukkit.getPluginManager().getPlugin("WorldGuard").getDescription().getVersion();
        }
        return null;
    }
}
