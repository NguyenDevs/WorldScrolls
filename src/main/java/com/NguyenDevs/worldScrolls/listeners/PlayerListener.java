package com.NguyenDevs.worldScrolls.listeners;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
    
    private final WorldScrolls plugin;
    
    public PlayerListener(WorldScrolls plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Close all GUIs for the disconnecting player
        if (plugin.getGUIManager() != null) {
            plugin.getGUIManager().closeAllGUIs(player);
        }
    }
}