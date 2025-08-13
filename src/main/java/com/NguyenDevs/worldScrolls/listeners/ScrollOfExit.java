package com.NguyenDevs.worldScrolls.listeners;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import com.NguyenDevs.worldScrolls.utils.ScrollUtils;
import com.NguyenDevs.worldScrolls.utils.SoundUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class ScrollOfExit implements Listener {
    
    private final WorldScrolls plugin;
    
    public ScrollOfExit(WorldScrolls plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if player is holding a scroll
        if (item == null || item.getType() != Material.PAPER) {
            return;
        }
        
        // Check if it's a Scroll of Exit
        if (!isScrollOfExit(item)) {
            return;
        }
        
        // Check scroll usage permissions
        if (!ScrollUtils.checkScrollUsageAndNotify(player)) {
            event.setCancelled(true);
            return;
        }
        
        Action action = event.getAction();
        
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // Left click - Save exit point
            handleSaveExitPoint(event, player, item);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // Right click - Use exit (teleport)
            handleUseExit(event, player, item);
        }
    }
    
    /**
     * Handle left-click to save exit point
     */
    private void handleSaveExitPoint(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        
        // Get target block location that player is looking at
        Block targetBlock = player.getTargetBlockExact(100);
        Location saveLocation;
        
        if (targetBlock != null && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Player clicked on a block, save that location
            saveLocation = targetBlock.getLocation().add(0.5, 1, 0.5); // Center of block + 1 up
        } else {
            // Player clicked in air, save current location
            saveLocation = player.getLocation();
        }
        
        // Check if target location is valid
        if (!ScrollUtils.canUseScrollsAt(player, saveLocation)) {
            player.sendMessage(ColorUtils.colorize("&cYou cannot save an exit point in this protected area!"));
            SoundUtils.playPermissionDeniedSound(player);
            return;
        }
        
        // Create enchanted scroll with saved location
        ItemStack savedScroll = createSavedExitScroll(item, saveLocation);
        
        // Replace scroll in player's hand
        if (item.getAmount() > 1) {
            // If player has multiple scrolls, reduce by 1 and add the saved scroll separately
            item.setAmount(item.getAmount() - 1);
            
            // Give saved scroll to player
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(savedScroll);
            } else {
                player.getWorld().dropItem(player.getLocation(), savedScroll);
                player.sendMessage(ColorUtils.colorize("&eExit scroll dropped at your feet (inventory full)"));
            }
        } else {
            // Replace the single scroll
            player.getInventory().setItemInMainHand(savedScroll);
        }
        
        // Send success message
        String worldName = saveLocation.getWorld().getName();
        int x = saveLocation.getBlockX();
        int y = saveLocation.getBlockY();
        int z = saveLocation.getBlockZ();
        
        player.sendMessage(ColorUtils.colorize("&aExit point saved at: &e" + worldName + " (" + x + ", " + y + ", " + z + ")"));
        
        // Play success sound and effects
        SoundUtils.playSuccessSound(player);
        player.spawnParticle(Particle.ENCHANTMENT_TABLE, saveLocation, 20, 1, 1, 1, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
    }
    
    /**
     * Handle right-click to use exit (teleport)
     */
    private void handleUseExit(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        
        // Check if scroll has saved location
        Location exitLocation = getSavedLocation(item);
        if (exitLocation == null) {
            player.sendMessage(ColorUtils.colorize("&cThis scroll has no saved exit point! Left-click to save a location first."));
            SoundUtils.playErrorSound(player);
            return;
        }
        
        // Check if target location is still valid
        if (!ScrollUtils.canUseScrollsAt(player, exitLocation)) {
            player.sendMessage(ColorUtils.colorize("&cYou cannot teleport to this protected area!"));
            SoundUtils.playPermissionDeniedSound(player);
            return;
        }
        
        // Check cooldown
        ConfigurationSection scrollConfig = plugin.getConfigManager().getScrolls().getConfigurationSection("scroll_of_exit");
        if (scrollConfig != null) {
            double castTime = scrollConfig.getDouble("cast", 0.5);
            
            if (castTime > 0) {
                // Delayed teleportation
                handleDelayedTeleport(player, item, exitLocation, castTime);
            } else {
                // Instant teleportation
                executeTeleport(player, item, exitLocation);
            }
        } else {
            // Fallback to instant teleport
            executeTeleport(player, item, exitLocation);
        }
    }
    
    /**
     * Handle delayed teleportation with cast time
     */
    private void handleDelayedTeleport(Player player, ItemStack item, Location exitLocation, double castTime) {
        player.sendMessage(ColorUtils.colorize("&ePreparing to teleport... Stay still!"));
        
        // Store original location to check if player moved
        Location originalLocation = player.getLocation().clone();
        
        // Visual effects during cast time
        new BukkitRunnable() {
            private int ticks = 0;
            private final int totalTicks = (int) (castTime * 20);
            
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                
                // Check if player moved (tolerance of 1 block)
                if (player.getLocation().distance(originalLocation) > 1.0) {
                    player.sendMessage(ColorUtils.colorize("&cTeleportation cancelled! You moved too much."));
                    SoundUtils.playErrorSound(player);
                    cancel();
                    return;
                }
                
                // Show particles during cast
                player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                
                ticks++;
                if (ticks >= totalTicks) {
                    // Cast time completed, execute teleport
                    executeTeleport(player, item, exitLocation);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Execute the actual teleportation
     */
    private void executeTeleport(Player player, ItemStack item, Location exitLocation) {
        // Ensure the chunk is loaded
        World world = exitLocation.getWorld();
        if (world != null) {
            world.getChunkAt(exitLocation).load();
        }
        
        // Teleport with ender pearl effect
        Location playerLoc = player.getLocation();
        
        // Spawn particles at departure
        player.getWorld().spawnParticle(Particle.PORTAL, playerLoc, 50, 1, 1, 1, 0.3);
        player.getWorld().playSound(playerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        
        // Teleport player
        player.teleport(exitLocation);
        
        // Spawn particles at arrival
        exitLocation.getWorld().spawnParticle(Particle.PORTAL, exitLocation, 50, 1, 1, 1, 0.3);
        exitLocation.getWorld().playSound(exitLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        
        // Consume scroll
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        
        // Success message and effects
        String worldName = exitLocation.getWorld().getName();
        int x = exitLocation.getBlockX();
        int y = exitLocation.getBlockY();
        int z = exitLocation.getBlockZ();
        
        player.sendMessage(ColorUtils.colorize("&aTeleported to exit point: &e" + worldName + " (" + x + ", " + y + ", " + z + ")"));
        SoundUtils.playSuccessSound(player);
        
        // Play scroll use sound
        SoundUtils.playScrollUseSound(player);
    }
    
    /**
     * Check if an item is a Scroll of Exit
     */
    private boolean isScrollOfExit(ItemStack item) {
        if (item.getType() != Material.PAPER) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLocalizedName()) {
            return false;
        }
        
        String identifier = meta.getLocalizedName();
        return identifier.equals("worldscrolls:scroll_of_exit") || identifier.startsWith("worldscrolls:scroll_of_exit:");
    }
    
    /**
     * Create a saved exit scroll with enchant glow and location data
     */
    private ItemStack createSavedExitScroll(ItemStack originalScroll, Location location) {
        ItemStack savedScroll = originalScroll.clone();
        savedScroll.setAmount(1);
        
        ItemMeta meta = savedScroll.getItemMeta();
        if (meta != null) {
            // Add enchant glow
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            
            // Update lore with saved location
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            
            // Add separator and lock information
            lore.add("");
            lore.add(ColorUtils.colorize("&6⚫ Lock: &e" + location.getWorld().getName() + " &7(" + 
                    location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")"));
            
            meta.setLore(lore);
            
            // Update identifier to include location data
            String locationData = location.getWorld().getName() + ":" + 
                                location.getX() + ":" + location.getY() + ":" + location.getZ() + ":" +
                                location.getYaw() + ":" + location.getPitch();
            meta.setLocalizedName("worldscrolls:scroll_of_exit:" + locationData);
            
            savedScroll.setItemMeta(meta);
        }
        
        return savedScroll;
    }
    
    /**
     * Get saved location from scroll item
     */
    private Location getSavedLocation(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLocalizedName()) {
            return null;
        }
        
        String identifier = meta.getLocalizedName();
        if (!identifier.startsWith("worldscrolls:scroll_of_exit:")) {
            return null;
        }
        
        try {
            // Parse location data from identifier
            String locationData = identifier.substring("worldscrolls:scroll_of_exit:".length());
            String[] parts = locationData.split(":");
            
            if (parts.length >= 4) {
                String worldName = parts[0];
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0;
                float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0;
                
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    return new Location(world, x, y, z, yaw, pitch);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse exit scroll location data: " + identifier);
        }
        
        return null;
    }
}
