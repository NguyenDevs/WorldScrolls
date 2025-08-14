package com.NguyenDevs.worldScrolls.comp;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class WorldGuardOn implements WGPlugin {
    private final WorldGuard worldGuard;
    private final WorldGuardPlugin worldGuardPlugin;
    private final Map<String, StateFlag> customFlags;
    private final Set<String> failedFlags;
    private volatile boolean flagsRegistered = false;
    private static final String WSC_EFFECT = "wsc-effect";

    public WorldGuardOn() {
        this.customFlags = new HashMap<>();
        this.failedFlags = new HashSet<>();
        this.worldGuard = WorldGuard.getInstance();

        WorldGuardPlugin plugin = (WorldGuardPlugin) WorldScrolls.getInstance().getServer()
                .getPluginManager().getPlugin("WorldGuard");
        if (plugin == null) {
            throw new IllegalStateException("WorldGuard plugin not found. Disabling WorldGuard integration.");
        }
        this.worldGuardPlugin = plugin;
        WorldScrolls.getInstance().getServer().getScheduler().runTaskLater(
                WorldScrolls.getInstance(), this::registerCustomFlags, 1L);
    }

    private void registerCustomFlags() {
        FlagRegistry registry = worldGuard.getFlagRegistry();
        try {
            StateFlag effectFlag = (StateFlag) registry.get(WSC_EFFECT);

            if (effectFlag != null) {
                customFlags.put(WSC_EFFECT, effectFlag);
            } else {
                failedFlags.add(WSC_EFFECT);
                WorldScrolls.getInstance().getLogger().log(Level.WARNING, "Custom flag not found: " + WSC_EFFECT);
            }

        } catch (Exception e) {
            failedFlags.add(WSC_EFFECT);
            WorldScrolls.getInstance().getLogger().log(Level.SEVERE,
                    "Error loading WorldGuard custom flags", e);
        }
        flagsRegistered = true;
    }


    @Override
    public boolean isPvpAllowed(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        try {
            ApplicableRegionSet regions = getApplicableRegion(location);
            return regions.queryState(null, com.sk89q.worldguard.protection.flags.Flags.PVP) != StateFlag.State.DENY;
        } catch (Exception e) {
            WorldScrolls.getInstance().getLogger().log(Level.WARNING,
                    "Error checking PvP state at location: " + location, e);
            return true;
        }
    }

    @Override
    public boolean isFlagAllowed(Player player, String flag) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (!flag.equals(WSC_EFFECT)) {
            throw new IllegalArgumentException("Only WSC_EFFECT flag is supported");
        }
        if (!flagsRegistered) {
            return true;
        }
        if (failedFlags.contains(flag)) {
            return true;
        }
        try {
            ApplicableRegionSet regions = getApplicableRegion(player.getLocation());
            StateFlag stateFlag = customFlags.get(flag);
            if (stateFlag == null) {
                if (!failedFlags.contains(flag)) {
                    failedFlags.add(flag);
                    WorldScrolls.getInstance().getLogger().log(Level.WARNING,
                            "Custom flag not found after registration: " + flag + " - Using default behavior");
                }
                return true;
            }
            StateFlag.State state = regions.queryValue(worldGuardPlugin.wrapPlayer(player), stateFlag);
            return state == null || state == StateFlag.State.ALLOW;
        } catch (Exception e) {
            WorldScrolls.getInstance().getLogger().log(Level.WARNING,
                    "Error checking flag " + flag + " for player: " + player.getName(), e);
            return true;
        }
    }

    @Override
    public boolean isFlagAllowedAtLocation(Location location, String flag) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (!flag.equals(WSC_EFFECT)) {
            throw new IllegalArgumentException("Only WSC_EFFECT flag is supported");
        }
        if (!flagsRegistered) {
            return true;
        }
        if (failedFlags.contains(flag)) {
            return true;
        }
        try {
            ApplicableRegionSet regions = getApplicableRegion(location);
            StateFlag stateFlag = customFlags.get(flag);
            if (stateFlag == null) {
                if (!failedFlags.contains(flag)) {
                    failedFlags.add(flag);
                    WorldScrolls.getInstance().getLogger().log(Level.WARNING,
                            "Custom flag not found after registration: " + flag + " - Using default behavior");
                }
                return true;
            }
            StateFlag.State state = regions.queryState(null, stateFlag);
            return state == null || state == StateFlag.State.ALLOW;
        } catch (Exception e) {
            WorldScrolls.getInstance().getLogger().log(Level.WARNING,
                    "Error checking flag " + flag + " at location: " + location, e);
            return true;
        }
    }


    private ApplicableRegionSet getApplicableRegion(Location location) {
        try {
            return worldGuard.getPlatform().getRegionContainer()
                    .createQuery()
                    .getApplicableRegions(BukkitAdapter.adapt(location));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to query WorldGuard regions at location: " + location, e);
        }
    }

    public boolean isReady() {
        return flagsRegistered;
    }

    public Map<String, StateFlag> getRegisteredFlags() {
        return new HashMap<>(customFlags);
    }
}