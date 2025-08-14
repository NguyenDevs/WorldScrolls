package com.NguyenDevs.worldScrolls.comp;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardOff implements WGPlugin {

    @Override
    public boolean isPvpAllowed(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        return true;
    }

    @Override
    public boolean isFlagAllowed(Player player, String flag) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        return true;
    }

    @Override
    public boolean isFlagAllowedAtLocation(Location location, String flag) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        return true;
    }
}