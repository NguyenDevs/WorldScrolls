package com.NguyenDevs.worldScrolls.comp;

import org.bukkit.entity.Player;

public class WorldGuardOff {
    
    /**
     * Fallback when WorldGuard is not available - always allow scrolls
     * @param player The player
     * @return always true (no region protection)
     */
    public boolean canUseScrolls(Player player) {
        return true;
    }
    
    /**
     * Fallback location check when WorldGuard is not available
     * @param player The player
     * @param location The location to check
     * @return always true (no region protection)
     */
    public boolean canUseScrollsAt(Player player, org.bukkit.Location location) {
        return true;
    }
    
    /**
     * Get message when WorldGuard is not available
     * @param player The player
     * @return Empty string (no blocking)
     */
    public String getBlockedMessage(Player player) {
        return "";
    }
    
    /**
     * Check if WorldGuard integration is working
     * @return always false (no WorldGuard)
     */
    public boolean isIntegrationWorking() {
        return false;
    }
}
