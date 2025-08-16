package com.NguyenDevs.worldScrolls.listeners.scrolls;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.*;

public class ScrollOfGravitation implements Listener {

    private final WorldScrolls plugin;
    private final NamespacedKey KEY_SCROLL_TYPE;
    private static final String SCROLL_FILE = "scroll_of_gravitation";

    private final Map<UUID, Long> lastUseTime = new HashMap<>();
    private final Map<UUID, BukkitTask> activeCasts = new HashMap<>();

    private ConfigurationSection scrollConfig;
    private ConfigurationSection scrollsConfig;

    public ScrollOfGravitation(WorldScrolls plugin) {
        this.plugin = plugin;
        this.KEY_SCROLL_TYPE = new NamespacedKey(plugin, "scroll_type");
        loadConfigurations();
    }

    private void loadConfigurations() {
        this.scrollConfig = plugin.getConfigManager().getScrollConfig(SCROLL_FILE);
        this.scrollsConfig = plugin.getConfigManager().getScrolls().getConfigurationSection("scroll_of_gravitation");
    }

    public void reloadConfigurations() {
        loadConfigurations();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String material = scrollConfig != null ? scrollConfig.getString("material", "PAPER") : "PAPER";
        if (item == null || item.getType() != Material.valueOf(material) || !isScrollOfGravitation(item)) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handlePullEnemies(event, player, item);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handlePullSelf(event, player, item);
        }
    }

    private void handlePullEnemies(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        if (isOnCooldown(player)) return;

        TargetInfo targetInfo = getTargetLocationWithFace(player);
        if (targetInfo == null) {
            sendMessage(player, "no-target");
            playSound(player, "cancel-sound");
            return;
        }

        if (activeCasts.containsKey(player.getUniqueId())) {
            sendMessage(player, "already-casting");
            return;
        }

        double castTime = scrollConfig != null ? scrollConfig.getDouble("cast-time", 2.0) : 2.0;
        if (castTime > 0) {
            startPullEnemiesProcess(player, item, targetInfo, castTime);
        } else {
            executePullEnemies(player, item, targetInfo.location);
        }
    }

    private void handlePullSelf(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);

        if (isOnCooldown(player)) return;

        TargetInfo targetInfo = getTargetLocationWithFace(player);
        if (targetInfo == null) {
            sendMessage(player, "no-target");
            playSound(player, "cancel-sound");
            return;
        }

        double maxRange = scrollConfig != null ? scrollConfig.getDouble("max-range", 50.0) : 50.0;
        if (player.getLocation().distance(targetInfo.location) > maxRange) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("max-range", String.valueOf((int) maxRange));
            sendMessage(player, "too-far", placeholders);
            return;
        }

        if (activeCasts.containsKey(player.getUniqueId())) {
            sendMessage(player, "already-casting");
            return;
        }

        double castTime = scrollConfig != null ? scrollConfig.getDouble("cast-time", 2.0) : 2.0;
        if (castTime > 0) {
            startPullSelfProcess(player, item, targetInfo, castTime);
        } else {
            executePullSelf(player, item, targetInfo.location);
        }
    }

    private static class TargetInfo {
        final Location location;
        final BlockFace face;
        final Vector normal;

        TargetInfo(Location location, BlockFace face, Vector normal) {
            this.location = location;
            this.face = face;
            this.normal = normal;
        }
    }

    private Location getTargetLocation(Player player) {
        Block targetBlock = player.getTargetBlockExact(100);
        if (targetBlock != null && targetBlock.getType() != Material.AIR) {
            return targetBlock.getLocation().add(0.5, 1, 0.5);
        }
        return null;
    }

    private TargetInfo getTargetLocationWithFace(Player player) {
        Block targetBlock = player.getTargetBlockExact(100);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            return null;
        }

        Location blockCenter = targetBlock.getLocation().add(0.5, 0.5, 0.5);
        Location playerLoc = player.getLocation();
        Vector playerToBlock = blockCenter.toVector().subtract(playerLoc.toVector());

        double deltaX = playerToBlock.getX();
        double deltaY = playerToBlock.getY();
        double deltaZ = playerToBlock.getZ();

        double absX = Math.abs(deltaX);
        double absY = Math.abs(deltaY);
        double absZ = Math.abs(deltaZ);

        BlockFace face;
        Vector normal;
        Location targetLoc;

        if (absY > absX && absY > absZ) {
            if (deltaY > 0) {
                face = BlockFace.DOWN;
                normal = new Vector(0, -1, 0);
                targetLoc = targetBlock.getLocation().add(0.5, -0.5, 0.5);
            } else {
                face = BlockFace.UP;
                normal = new Vector(0, 1, 0);
                targetLoc = targetBlock.getLocation().add(0.5, 1.5, 0.5);
            }
        } else if (absX > absZ) {
            if (deltaX > 0) {
                face = BlockFace.WEST;
                normal = new Vector(-1, 0, 0);
                targetLoc = targetBlock.getLocation().add(-0.5, 0.5, 0.5);
            } else {
                face = BlockFace.EAST;
                normal = new Vector(1, 0, 0);
                targetLoc = targetBlock.getLocation().add(1.5, 0.5, 0.5);
            }
        } else {
            if (deltaZ > 0) {
                face = BlockFace.NORTH;
                normal = new Vector(0, 0, -1);
                targetLoc = targetBlock.getLocation().add(0.5, 0.5, -0.5);
            } else {
                face = BlockFace.SOUTH;
                normal = new Vector(0, 0, 1);
                targetLoc = targetBlock.getLocation().add(0.5, 0.5, 1.5);
            }
        }

        return new TargetInfo(targetLoc, face, normal);
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

    private void startPullEnemiesProcess(Player player, ItemStack item, TargetInfo targetInfo, double castTime) {
        sendMessage(player, "prepare-pull-enemies");
        Location castLocation = player.getLocation().clone();

        BukkitTask gravitationTask = startGravitationEffect(player, castLocation);
        BukkitTask targetGridTask = startAnimatedTargetGridEffect(targetInfo, castTime);

        BukkitTask castTask = new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = (int) (castTime * 20);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    gravitationTask.cancel();
                    targetGridTask.cancel();
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (player.getLocation().distance(castLocation) > 1.0) {
                    sendMessage(player, "pull-cancelled");
                    playSound(player, "cancel-sound");
                    gravitationTask.cancel();
                    targetGridTask.cancel();
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                ticks++;
                if (ticks >= totalTicks) {
                    gravitationTask.cancel();
                    targetGridTask.cancel();
                    executePullEnemies(player, item, targetInfo.location);
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeCasts.put(player.getUniqueId(), castTask);
    }

    private void startPullSelfProcess(Player player, ItemStack item, TargetInfo targetInfo, double castTime) {
        sendMessage(player, "prepare-pull-self");
        Location castLocation = player.getLocation().clone();

        BukkitTask gravitationTask = startGravitationEffect(player, castLocation);
        BukkitTask targetGridTask = startAnimatedTargetGridEffect(targetInfo, castTime);

        BukkitTask castTask = new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = (int) (castTime * 20);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    gravitationTask.cancel();
                    targetGridTask.cancel();
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                ticks++;
                if (ticks >= totalTicks) {
                    gravitationTask.cancel();
                    targetGridTask.cancel();
                    executePullSelf(player, item, targetInfo.location);
                    activeCasts.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeCasts.put(player.getUniqueId(), castTask);
    }

    private BukkitTask startGravitationEffect(Player player, Location center) {
        return new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;
            final int maxTicks = (int) (scrollConfig.getDouble("cast-time", 2.0) * 20);

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 3; i++) {
                    double particleAngle = angle + (i * 120.0 * Math.PI / 180.0);
                    double x = center.getX() + 2.0 * Math.cos(particleAngle);
                    double z = center.getZ() + 2.0 * Math.sin(particleAngle);
                    Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.5, z);

                    center.getWorld().spawnParticle(Particle.SPELL_WITCH, particleLoc, 1, 0, 0, 0, 0.1);
                    center.getWorld().spawnParticle(Particle.CRIT_MAGIC, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
                }

                angle += Math.PI / 8;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private BukkitTask startAnimatedTargetGridEffect(TargetInfo targetInfo, double duration) {
        return new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int) (duration * 20);

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                double progress = (double) ticks / maxTicks;
                drawAnimatedGravityField(targetInfo, progress);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void drawAnimatedGravityField(TargetInfo targetInfo, double progress) {
        Vector normal = targetInfo.normal;
        Vector u, v;

        if (targetInfo.face == BlockFace.UP || targetInfo.face == BlockFace.DOWN) {
            u = new Vector(1, 0, 0);
            v = new Vector(0, 0, 1);
        } else {
            if (targetInfo.face == BlockFace.EAST || targetInfo.face == BlockFace.WEST) {
                u = new Vector(0, 1, 0);
                v = new Vector(0, 0, 1);
            } else {
                u = new Vector(1, 0, 0);
                v = new Vector(0, 1, 0);
            }
        }

        double startRadius = 0.8;
        double endRadius = 6.0;
        double maxDepth = 1.8 * progress;

        double currentMaxRadius = startRadius + (endRadius - startRadius) * progress;

        int totalRings = 12;
        int currentRingCount = Math.max(1, (int) (totalRings * progress));

        for (int ring = 1; ring <= currentRingCount; ring++) {
            double ringProgress = (double) ring / totalRings;

            if (ringProgress <= progress) {
                double radius = startRadius + (currentMaxRadius - startRadius) * ringProgress;
                drawAnimatedCircleOnPlane(targetInfo, radius, 0.08, maxDepth, u, v, normal);
            }
        }

        if (progress > 0.3) {
            int totalLines = 16;
            int currentLineCount = Math.max(4, (int) (totalLines * ((progress - 0.3) / 0.7)));
            for (int i = 0; i < currentLineCount; i++) {
                double angle = i * Math.PI / 8.0;
                drawAnimatedRadialLine(targetInfo, angle, currentMaxRadius, startRadius, 0.12, maxDepth, u, v, normal);
            }
        }

        if (progress > 0.5) {
            double holeProgress = (progress - 0.5) / 0.5;
            createAnimatedBlackHoleEdge(targetInfo, (int) (System.currentTimeMillis() / 50), holeProgress, u, v, normal, maxDepth);
        }

        if (progress > 0.4) {
            drawAnimatedCrossLines(targetInfo, currentMaxRadius, startRadius, 0.15, maxDepth, u, v, normal);
        }
    }

    private void drawAnimatedCircleOnPlane(TargetInfo targetInfo, double radius, double density, double maxDepth,
                                           Vector u, Vector v, Vector normal) {
        double circumference = 2 * Math.PI * radius;
        int points = Math.max(8, (int) (circumference / density));

        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double localX = radius * Math.cos(angle);
            double localY = radius * Math.sin(angle);

            double holeRadius = 0.8;
            double distanceFromHole = Math.abs(radius - holeRadius);
            double maxDistanceFromHole = Math.max(0.1, 6.0 - holeRadius);

            double depthRatio = 1.0 - (distanceFromHole / maxDistanceFromHole);
            double depth = depthRatio * depthRatio * maxDepth;

            Vector localPos = u.clone().multiply(localX).add(v.clone().multiply(localY));
            Vector depthOffset = normal.clone().multiply(-depth);
            Location point = targetInfo.location.clone().add(localPos).add(depthOffset);

            Color ringColor = Color.fromRGB(
                    (int) (10 + radius * 3),
                    (int) (10 + radius * 3),
                    (int) (10 + radius * 3)
            );

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                    0, 0, 0, 0, new Particle.DustOptions(ringColor, 0.6f));
        }
    }

    private void drawAnimatedRadialLine(TargetInfo targetInfo, double angle, double maxLength, double minLength,
                                        double density, double maxDepth, Vector u, Vector v, Vector normal) {
        int points = (int) ((maxLength - minLength) / density);

        for (int i = 1; i <= points; i++) {
            double distance = minLength + ((maxLength - minLength) * i) / points;
            double localX = distance * Math.cos(angle);
            double localY = distance * Math.sin(angle);

            double distanceFromHole = Math.abs(distance - minLength);
            double maxDistanceFromHole = maxLength - minLength;
            double depthRatio = 1.0 - (distanceFromHole / maxDistanceFromHole);
            double depth = depthRatio * depthRatio * maxDepth * 0.78;

            Vector localPos = u.clone().multiply(localX).add(v.clone().multiply(localY));
            Vector depthOffset = normal.clone().multiply(-depth);
            Location point = targetInfo.location.clone().add(localPos).add(depthOffset);

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                    0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(15, 15, 15), 0.5f));
        }
    }

    private void createAnimatedBlackHoleEdge(TargetInfo targetInfo, int ticks, double progress,
                                             Vector u, Vector v, Vector normal, double maxDepth) {
        double holeRadius = 0.8;
        int particleCount = Math.max(6, (int) (12 * progress));

        for (int i = 0; i < particleCount; i++) {
            double angle = i * Math.PI / 6 + ticks * 0.08;
            double x = holeRadius * Math.cos(angle);
            double z = holeRadius * Math.sin(angle);

            Vector localPos = u.clone().multiply(x).add(v.clone().multiply(z));
            Vector depthOffset = normal.clone().multiply(-maxDepth * 0.83);
            Location edgePoint = targetInfo.location.clone().add(localPos).add(depthOffset);

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, edgePoint, 1,
                    0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.fromRGB(3, 3, 3), 0.7f));

            if (ticks % 6 == 0 && progress > 0.7) {
                double innerAngle = angle + Math.PI / 12;
                double innerRadius = holeRadius * 0.7;
                double innerX = innerRadius * Math.cos(innerAngle);
                double innerZ = innerRadius * Math.sin(innerAngle);

                Vector innerPos = u.clone().multiply(innerX).add(v.clone().multiply(innerZ));
                Vector innerDepthOffset = normal.clone().multiply(-maxDepth * 0.72);
                Location innerPoint = targetInfo.location.clone().add(innerPos).add(innerDepthOffset);

                targetInfo.location.getWorld().spawnParticle(Particle.SPELL_WITCH, innerPoint, 1,
                        0.02, 0.02, 0.02, 0.001);
            }
        }
    }

    private void drawAnimatedCrossLines(TargetInfo targetInfo, double maxLength, double minLength,
                                        double density, double maxDepth, Vector u, Vector v, Vector normal) {
        Vector[] directions = {u, u.clone().multiply(-1), v, v.clone().multiply(-1)};

        for (Vector direction : directions) {
            int points = (int) ((maxLength - minLength) / density);
            for (int i = 1; i <= points; i++) {
                double distance = minLength + ((maxLength - minLength) * i) / points;

                double distanceFromHole = Math.abs(distance - minLength);
                double maxDistanceFromHole = maxLength - minLength;
                double depthRatio = 1.0 - (distanceFromHole / maxDistanceFromHole);
                double depth = depthRatio * depthRatio * depthRatio * maxDepth * 0.67;

                Vector pos = direction.clone().multiply(distance);
                Vector depthOffset = normal.clone().multiply(-depth);
                Location point = targetInfo.location.clone().add(pos).add(depthOffset);

                targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                        0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 0.5f));
            }
        }
    }

    private void executePullEnemies(Player player, ItemStack item, Location targetLocation) {
        double pullRange = scrollConfig != null ? scrollConfig.getDouble("pull-range", 8.0) : 8.0;

        List<LivingEntity> enemies = new ArrayList<>();
        for (Entity entity : targetLocation.getWorld().getNearbyEntities(targetLocation, pullRange, pullRange, pullRange)) {
            if (entity instanceof Monster || (entity instanceof LivingEntity && entity != player && !(entity instanceof Player))) {
                enemies.add((LivingEntity) entity);
            }
        }

        if (enemies.isEmpty()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("pull-range", String.valueOf((int) pullRange));
            sendMessage(player, "no-enemies", placeholders);
            return;
        }

        for (LivingEntity enemy : enemies) {
            startContinuousPull(enemy, targetLocation, 40);
            spawnParticles(player, enemy.getLocation(), "pull-enemies-particles");
        }

        startGravityVortex(targetLocation, 80);
        playSound(player, "pull-enemies-sound");

        Map<String, String> placeholders = locPlaceholders(targetLocation);
        placeholders.put("enemy-count", String.valueOf(enemies.size()));
        sendMessage(player, "pulled-enemies", placeholders);

        consumeScroll(player, item);
        playSound(player, "success-sound");
    }

    private void startContinuousPull(LivingEntity entity, Location target, int duration) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead() || ticks >= duration) {
                    cancel();
                    return;
                }

                Location entityLoc = entity.getLocation();
                double distance = entityLoc.distance(target);

                if (distance < 1.5) {
                    entity.teleport(target);
                    cancel();
                    return;
                }

                Vector direction = target.toVector().subtract(entityLoc.toVector());
                direction.setY(direction.getY() + 0.2);

                double pullStrength = Math.min(distance * 0.3, 2.5);
                entity.setVelocity(direction.normalize().multiply(pullStrength));

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startGravityVortex(Location center, int duration) {
        new BukkitRunnable() {
            double angle = 0;
            double angle2 = Math.PI;
            int ticks = 0;
            final double maxRadius = 3.0;

            @Override
            public void run() {
                if (ticks >= duration) {
                    cancel();
                    return;
                }

                for (int layer = 0; layer < 3; layer++) {
                    double radius = maxRadius * (1.0 - (double) layer / 3.0);
                    double heightOffset = layer * 0.3;

                    for (int i = 0; i < 4; i++) {
                        double spiralAngle = angle + (i * Math.PI / 2.0);
                        double currentRadius = radius * (1.0 - (double) ticks / duration * 0.3);

                        double x = center.getX() + currentRadius * Math.cos(spiralAngle);
                        double z = center.getZ() + currentRadius * Math.sin(spiralAngle);
                        double y = center.getY() + heightOffset + Math.sin(angle * 2) * 0.2;

                        Location particleLoc = new Location(center.getWorld(), x, y, z);

                        center.getWorld().spawnParticle(Particle.REDSTONE, particleLoc,
                                1, 0.05, 0.05, 0.05, 0.01, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.0f));
                        if (ticks % 4 == 0) {
                            center.getWorld().spawnParticle(Particle.SPELL_WITCH, particleLoc, 1, 0.02, 0.02, 0.02, 0.005);
                        }
                    }

                    for (int i = 0; i < 3; i++) {
                        double spiralAngle = -angle2 + (i * 2 * Math.PI / 3.0);
                        double currentRadius = radius * 0.7 * (1.0 - (double) ticks / duration * 0.2);

                        double x = center.getX() + currentRadius * Math.cos(spiralAngle);
                        double z = center.getZ() + currentRadius * Math.sin(spiralAngle);
                        double y = center.getY() + heightOffset + 0.1;

                        Location particleLoc = new Location(center.getWorld(), x, y, z);
                        center.getWorld().spawnParticle(Particle.REDSTONE, particleLoc,
                                1, 0.03, 0.03, 0.03, 0.005, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.0f));
                    }
                }

                if (ticks % 5 == 0) {
                    double pulseIntensity = 1.0 + Math.sin(ticks * 0.3) * 0.3;
                    center.getWorld().spawnParticle(Particle.SPELL_WITCH, center,
                            (int) (3 * pulseIntensity), 0.1, 0.1, 0.1, 0.02);
                    center.getWorld().spawnParticle(Particle.PORTAL, center,
                            (int) (2 * pulseIntensity), 0.05, 0.05, 0.05, 0.01);
                }

                angle += Math.PI / 12;
                angle2 += Math.PI / 16;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void executePullSelf(Player player, ItemStack item, Location targetLocation) {
        World world = targetLocation.getWorld();
        if (world != null) world.getChunkAt(targetLocation).load();

        Location from = player.getLocation();
        spawnParticles(player, from, "pull-self-particles");
        playSound(player, "pull-self-sound");

        startContinuousPlayerPull(player, targetLocation, from);

        consumeScroll(player, item);
    }

    private void startContinuousPlayerPull(Player player, Location target, Location startLocation) {
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 60;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    if (player.isOnline()) {
                        player.teleport(target);
                        spawnParticles(player, target, "target-particles");
                        Map<String, String> placeholders = locPlaceholders(target);
                        sendMessage(player, "pulled-self", placeholders);
                        playSound(player, "success-sound");
                    }
                    cancel();
                    return;
                }

                Location currentLoc = player.getLocation();
                double distance = currentLoc.distance(target);

                if (distance < 2.0) {
                    player.teleport(target);
                    startGravityVortex(target, 40);
                    Map<String, String> placeholders = locPlaceholders(target);
                    sendMessage(player, "pulled-self", placeholders);
                    playSound(player, "success-sound");
                    cancel();
                    return;
                }

                Vector direction = target.toVector().subtract(currentLoc.toVector());
                direction.setY(direction.getY() + 0.1);

                double pullStrength = Math.min(distance * 0.4, 3.5);
                player.setVelocity(direction.normalize().multiply(pullStrength));

                if (ticks % 2 == 0) {
                    spawnParticles(player, currentLoc, "pull-self-particles");
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void consumeScroll(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
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

    private boolean isScrollOfGravitation(ItemStack item) {
        String material = scrollConfig != null ? scrollConfig.getString("material", "PAPER") : "PAPER";
        if (item == null || item.getType() != Material.valueOf(material)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return "scroll_of_gravitation".equalsIgnoreCase(
                pdc.get(KEY_SCROLL_TYPE, PersistentDataType.STRING)
        );
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