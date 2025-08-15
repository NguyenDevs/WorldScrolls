package com.NguyenDevs.worldScrolls.listeners.scrolls;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class ScrollOfExit implements Listener {

    private final WorldScrolls plugin;
    private final NamespacedKey KEY_DATA;
    private final NamespacedKey KEY_SCROLL_TYPE;
    private static final String SCROLL_FILE = "scroll_of_exit";

    private final Map<UUID, Long> lastUseTime = new HashMap<>();
    private final Map<UUID, BukkitTask> activeCasts = new HashMap<>();

    private ConfigurationSection scrollConfig;
    private ConfigurationSection scrollsConfig;

    public ScrollOfExit(WorldScrolls plugin) {
        this.plugin = plugin;
        this.KEY_DATA = new NamespacedKey(plugin, "exit_location");
        this.KEY_SCROLL_TYPE = new NamespacedKey(plugin, "scroll_type");
        loadConfigurations();
    }

    private void loadConfigurations() {
        this.scrollConfig = plugin.getConfigManager().getScrollConfig(SCROLL_FILE);
        this.scrollsConfig = plugin.getConfigManager().getScrolls().getConfigurationSection("scroll_of_exit");
    }

    public void reloadConfigurations() {
        loadConfigurations();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER || !isScrollOfExit(item)) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleSaveExitPoint(event, player, item);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handleUseExit(event, player, item);
        }
    }

    private void handleSaveExitPoint(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        if (hasSavedLocation(item)) {
            sendMessage(player, "save-already-locked");
            playSound(player, "lock-sound");
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
                sendMessage(player, "drop-on-ground");
            }
        } else {
            player.getInventory().setItemInMainHand(savedScroll);
        }

        Map<String, String> placeholders = locPlaceholders(saveLocation);
        sendMessage(player, "saved-location", placeholders);
        spawnParticles(player, saveLocation, "save-particles");
        playSound(player, "save-sound");
    }

    private void handleUseExit(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        if (isOnCooldown(player)) return;

        Location exitLocation = getSavedLocation(item);
        if (exitLocation == null) {
            sendMessage(player, "no-location");
            playSound(player, "lock-sound");
            return;
        }

        if (activeCasts.containsKey(player.getUniqueId())) {
            sendMessage(player, "already-casting");
            return;
        }

        double castTime = scrollConfig != null ? scrollConfig.getDouble("cast-time", 3.0) : 3.0;
        if (castTime > 0) {
            startCastingProcess(player, item, exitLocation, castTime);
        } else {
            executeTeleport(player, item, exitLocation);
        }
    }

    private boolean isOnCooldown(Player player) {
        int cooldownSeconds = scrollsConfig != null ? scrollsConfig.getInt("cooldown", 0) : 0;

        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            if (lastUseTime.containsKey(player.getUniqueId())) {
                long last = lastUseTime.get(player.getUniqueId());
                long elapsed = now - last;
                if (elapsed < cooldownSeconds * 1000L) {
                    long remaining = (cooldownSeconds * 1000L - elapsed) / 1000L;
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("remaining", String.valueOf(remaining));
                    sendMessage(player, "on-cooldown", placeholders);
                    return true;
                }
            }
            lastUseTime.put(player.getUniqueId(), now);
        }
        return false;
    }

    private void startCastingProcess(Player player, ItemStack item, Location exitLocation, double castTime) {
        sendMessage(player, "prepare-teleport");
        Location castLocation = player.getLocation().clone();

        BukkitTask matrixTask = startMatrixEffect(player, castLocation);

        BukkitTask castTask = new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = (int) (castTime * 20);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    matrixTask.cancel();
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (player.getLocation().distance(castLocation) > 1.0) {
                    sendMessage(player, "teleport-cancelled");
                    playSound(player, "cancel-sound");
                    matrixTask.cancel();
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                ticks++;
                if (ticks >= totalTicks) {
                    matrixTask.cancel();
                    executeTeleport(player, item, exitLocation);
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeCasts.put(player.getUniqueId(), castTask);
    }

    private BukkitTask startMatrixEffect(Player player, Location center) {
        return new BukkitRunnable() {
            double angle = 0;
            double radius = 1.5;
            int ticks = 0;
            final int maxTicks = (int) (scrollConfig.getDouble("cast-time", 3.0) * 20);

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    cancel();
                    return;
                }

                double currentRadius = radius * (1.0 - (double) ticks / maxTicks);

                for (int i = 0; i < 3; i++) {
                    double particleAngle = angle + (i * 120.0 * Math.PI / 180.0);
                    double x = center.getX() + currentRadius * Math.cos(particleAngle);
                    double z = center.getZ() + currentRadius * Math.sin(particleAngle);
                    Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.5, z);

                    center.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, particleLoc, 1, 0, 0, 0, 0.1);
                    center.getWorld().spawnParticle(Particle.PORTAL, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
                }

                angle += Math.PI / 10;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private Collection<Player> getNearbyPlayers(Location center, double radius) {
        Collection<Player> nearby = new ArrayList<>();
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distance(center) <= radius) {
                nearby.add(p);
            }
        }
        return nearby;
    }

    private void applyDarknessEffect(Collection<Player> players) {
        int duration = scrollConfig.getInt("darkness-duration", 200);
        for (Player p : players) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false));
        }
    }

    private void executeTeleport(Player player, ItemStack item, Location exitLocation) {
        World world = exitLocation.getWorld();
        if (world != null) world.getChunkAt(exitLocation).load();

        Location from = player.getLocation();
        spawnParticles(player, from, "teleport-from-particles");
        playSound(player, "teleport-from-sound");

        player.teleport(exitLocation);

        spawnParticles(player, exitLocation, "teleport-to-particles");
        playSound(player, "teleport-to-sound");

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        Map<String, String> placeholders = locPlaceholders(exitLocation);
        sendMessage(player, "teleported", placeholders);
        playSound(player, "success-sound");
    }

    private void playSound(Player player, String soundKey) {
        ConfigurationSection soundConfig = scrollConfig.getConfigurationSection("sounds." + soundKey);
        if (soundConfig != null) {
            String soundName = soundConfig.getString("sound", "BLOCK_NOTE_BLOCK_PLING");
            float volume = (float) soundConfig.getDouble("volume", 1.0);
            float pitch = (float) soundConfig.getDouble("pitch", 1.0);

            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound: " + soundName);
            }
        }
    }

    private void playSound(Location location, String soundKey) {
        ConfigurationSection soundConfig = scrollConfig.getConfigurationSection("sounds." + soundKey);
        if (soundConfig != null) {
            String soundName = soundConfig.getString("sound", "BLOCK_NOTE_BLOCK_PLING");
            float volume = (float) soundConfig.getDouble("volume", 1.0);
            float pitch = (float) soundConfig.getDouble("pitch", 1.0);

            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                location.getWorld().playSound(location, sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound: " + soundName);
            }
        }
    }

    private void spawnParticles(Player player, Location location, String particleKey) {
        ConfigurationSection particleConfig = scrollConfig.getConfigurationSection("particles." + particleKey);
        if (particleConfig != null) {
            String particleName = particleConfig.getString("particle", "PORTAL");
            int count = particleConfig.getInt("count", 50);
            double offsetX = particleConfig.getDouble("offset-x", 1.0);
            double offsetY = particleConfig.getDouble("offset-y", 1.0);
            double offsetZ = particleConfig.getDouble("offset-z", 1.0);
            double speed = particleConfig.getDouble("speed", 0.3);

            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid particle: " + particleName);
            }
        }
    }

    private void sendMessage(Player player, String messageKey) {
        sendMessage(player, messageKey, new HashMap<>());
    }

    private void sendMessage(Player player, String messageKey, Map<String, String> placeholders) {
        String prefix = plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix");
        String message = plugin.getConfigManager().getScrollMessage(SCROLL_FILE, messageKey, placeholders);
        player.sendMessage(ColorUtils.colorize(prefix + " " + message));
    }

    private boolean isScrollOfExit(ItemStack item) {
        String material = scrollConfig != null ? scrollConfig.getString("material", "PAPER") : "PAPER";
        if (item == null || item.getType() != Material.valueOf(material)) return false;
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