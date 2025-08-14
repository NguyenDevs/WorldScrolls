package com.NguyenDevs.worldScrolls.comp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface WGPlugin {
    boolean isPvpAllowed(Location location);
    boolean isFlagAllowed(Player player, String flag);
    boolean isFlagAllowedAtLocation(Location location, String flag);
}