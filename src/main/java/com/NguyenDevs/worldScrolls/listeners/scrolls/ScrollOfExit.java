package com.NguyenDevs.worldScrolls.listeners.scrolls;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ScrollOfExit implements Listener {

    private final WorldScrolls plugin;
    private final NamespacedKey KEY_DATA;
    private final NamespacedKey KEY_LOCKED;
    private final NamespacedKey KEY_SCROLL_TYPE;
    private static final String SCROLL_FILE = "scroll_of_exit";

    private final Map<UUID, Long> lastUseTime = new HashMap<>();

    public ScrollOfExit(WorldScrolls plugin) {
        this.plugin = plugin;
        this.KEY_DATA = new NamespacedKey(plugin, "exit_location");
        this.KEY_LOCKED = new NamespacedKey(plugin, "exit_locked");
        this.KEY_SCROLL_TYPE = new NamespacedKey(plugin, "scroll_type");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) return;
        if (!isScrollOfExit(item)) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleSaveExitPoint(event, player, item);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            ConfigurationSection scrollConfig = plugin.getConfigManager().getScrolls().getConfigurationSection("scroll_of_exit");
            int cooldownSeconds = scrollConfig != null ? scrollConfig.getInt("cooldown", 0) : 0;

            if (cooldownSeconds > 0) {
                long now = System.currentTimeMillis();
                if (lastUseTime.containsKey(player.getUniqueId())) {
                    long last = lastUseTime.get(player.getUniqueId());
                    long elapsed = now - last;
                    if (elapsed < cooldownSeconds * 1000L) {
                        long remaining = (cooldownSeconds * 1000L - elapsed) / 1000L;
                        String cooldownMsg = plugin.getConfigManager()
                                .getScrollMessage(SCROLL_FILE, "on-cooldown")
                                .replace("%remaining%", String.valueOf(remaining));
                        player.sendMessage(ColorUtils.colorize(
                                plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " + cooldownMsg
                        ));
                        return;
                    }
                }
                lastUseTime.put(player.getUniqueId(), now);
            }

            handleUseExit(event, player, item);
        }
    }

    private void handleSaveExitPoint(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        if (hasSavedLocation(item)) {
            player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " +
                    plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "save-already-locked")));
            player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.5f, 0.1f);
            return;
        }

        Block targetBlock = player.getTargetBlockExact(100);
        Location saveLocation = (targetBlock != null && event.getAction() == Action.LEFT_CLICK_BLOCK)
                ? targetBlock.getLocation().add(0.5, 1, 0.5)
                : player.getLocation();

        ItemStack savedScroll = createSavedExitScroll(item, saveLocation);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(savedScroll);
            } else {
                player.getWorld().dropItem(player.getLocation(), savedScroll);
                player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " +
                        plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "drop-on-ground")));
            }
        } else {
            player.getInventory().setItemInMainHand(savedScroll);
        }

        Map<String, String> ph = locPlaceholders(saveLocation);
        player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " +
                plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "saved-location", ph)));

        player.spawnParticle(Particle.ENCHANTMENT_TABLE, saveLocation, 20, 1, 1, 1, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
    }

    private void handleUseExit(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        Location exitLocation = getSavedLocation(item);
        if (exitLocation == null) {
            player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " +
                    plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "no-location")));
            player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.5f, 0.1f);
            return;
        }

        ConfigurationSection scrollConfig = plugin.getConfigManager().getScrolls().getConfigurationSection("scroll_of_exit");
        double castTime = scrollConfig != null ? scrollConfig.getDouble("cast") : 0.5;
        if (castTime > 0) {
            handleDelayedTeleport(player, item, exitLocation, castTime);
        } else {
            executeTeleport(player, item, exitLocation);
        }
    }

    private void handleDelayedTeleport(Player player, ItemStack item, Location exitLocation, double castTime) {
        player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " +
                plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prepare-teleport")));
        Location originalLocation = player.getLocation().clone();

        new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = (int) (castTime * 20);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.getLocation().distance(originalLocation) > 1.0) {
                    player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " +
                            plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "teleport-cancelled")));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 0.1f);
                    cancel();
                    return;
                }
                player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                ticks++;
                if (ticks >= totalTicks) {
                    executeTeleport(player, item, exitLocation);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executeTeleport(Player player, ItemStack item, Location exitLocation) {
        World world = exitLocation.getWorld();
        if (world != null) world.getChunkAt(exitLocation).load();

        Location from = player.getLocation();
        player.getWorld().spawnParticle(Particle.PORTAL, from, 50, 1, 1, 1, 0.3);
        player.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        player.teleport(exitLocation);

        exitLocation.getWorld().spawnParticle(Particle.PORTAL, exitLocation, 50, 1, 1, 1, 0.3);
        exitLocation.getWorld().playSound(exitLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);

        }
        else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        Map<String, String> ph = locPlaceholders(exitLocation);
        player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " +
                plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "teleported", ph)));
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.1f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, 0.7f, 1.1f);
    }

    private boolean isScrollOfExit(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return "scroll_of_exit".equalsIgnoreCase(
                pdc.get(KEY_SCROLL_TYPE, PersistentDataType.STRING)
        );
    }

    private boolean hasSavedLocation(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(KEY_DATA, PersistentDataType.STRING)) return true;
        if (meta.hasLocalizedName()) {
            String id = meta.getLocalizedName();
            return id.startsWith("worldscrolls:scroll_of_exit:");
        }
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    String clean = ChatColor.stripColor(line);
                    if (clean.contains("Lock:")) return true;
                }
            }
        }
        return false;
    }

    private ItemStack createSavedExitScroll(ItemStack originalScroll, Location location) {
        ItemStack saved = originalScroll.clone();
        saved.setAmount(1);

        ItemMeta meta = saved.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            lore.removeIf(line -> ChatColor.stripColor(line).contains("Lock:"));
            lore.add("");
            lore.add(ColorUtils.colorize("&6⚫ Lock: &e" + location.getWorld().getName() + " &7(" +
                    location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")"));

            String data = serializeLocation(location);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_DATA, PersistentDataType.STRING, data);
            pdc.set(KEY_LOCKED, PersistentDataType.INTEGER, 1);

            meta.setLocalizedName("worldscrolls:scroll_of_exit:" + data);
            meta.setLore(lore);
            saved.setItemMeta(meta);
        }
        return saved;
    }

    private String serializeLocation(Location l) {
        return l.getWorld().getName() + ":" + l.getX() + ":" + l.getY() + ":" + l.getZ() + ":" + l.getYaw() + ":" + l.getPitch();
    }

    private Location deserializeLocation(String data) {
        String[] parts = data.split(":");
        if (parts.length < 4) return null;
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return null;
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
        float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
        return new Location(w, x, y, z, yaw, pitch);
    }

    private Location getSavedLocation(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(KEY_DATA, PersistentDataType.STRING)) {
            String data = pdc.get(KEY_DATA, PersistentDataType.STRING);
            return deserializeLocation(data);
        }
        if (meta.hasLocalizedName()) {
            String id = meta.getLocalizedName();
            String prefix = "worldscrolls:scroll_of_exit:";
            if (id.startsWith(prefix) && id.length() > prefix.length()) {
                return deserializeLocation(id.substring(prefix.length()));
            }
        }
        return null;
    }

    private Map<String, String> locPlaceholders(Location l) {
        Map<String, String> ph = new HashMap<>();
        ph.put("world", l.getWorld().getName());
        ph.put("x", String.valueOf(l.getBlockX()));
        ph.put("y", String.valueOf(l.getBlockY()));
        ph.put("z", String.valueOf(l.getBlockZ()));
        return ph;
    }
}
