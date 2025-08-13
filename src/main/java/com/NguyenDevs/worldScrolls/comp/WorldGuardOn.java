package com.NguyenDevs.worldScrolls.comp;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import org.bukkit.entity.Player;

public class WorldGuardOn {
    
    private final WorldScrolls plugin;
    private final WorldGuardIntegration worldGuardIntegration;
    
    public WorldGuardOn(WorldScrolls plugin) {
        this.plugin = plugin;
        this.worldGuardIntegration = new WorldGuardIntegration(plugin);
    }
    
    /**
     * Check if scrolls are allowed for a player at their current location
     * @param player The player
     * @return true if allowed, false if blocked by region
     */
    public boolean canUseScrolls(Player player) {
        return worldGuardIntegration.areScrollsAllowed(player);
    }
    
    /**
     * Check if scrolls are allowed at a specific location
     * @param player The player
     * @param location The location to check
     * @return true if allowed, false if blocked by region
     */
    public boolean canUseScrollsAt(Player player, org.bukkit.Location location) {
        return worldGuardIntegration.areScrollsAllowedAt(player, location);
    }
    
    /**
     * Get the blocked message for display to player
     * @param player The player
     * @return The blocked message
     */
    public String getBlockedMessage(Player player) {
        return worldGuardIntegration.getScrollBlockedMessage(player);
    }
    
    /**
     * Get WorldGuard integration instance
     * @return The integration instance
     */
    public WorldGuardIntegration getIntegration() {
        return worldGuardIntegration;
    }
    
    /**
     * Check if integration is working properly
     * @return true if WorldGuard integration is functional
     */
    public boolean isIntegrationWorking() {
        return worldGuardIntegration.isAvailable();
    }
}
