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

    // Lớp để lưu thông tin target location và hướng
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
        if (targetBlock != null && targetBlock.getType() != Material.AIR) {
            Location blockCenter = targetBlock.getLocation().add(0.5, 0.5, 0.5);
            Vector eyeVector = player.getEyeLocation().getDirection();
            Location eyeLoc = player.getEyeLocation();
            Vector toBlock = blockCenter.toVector().subtract(eyeLoc.toVector());

            // Xác định mặt được hit và tạo location + normal vector
            double absX = Math.abs(toBlock.getX());
            double absY = Math.abs(toBlock.getY());
            double absZ = Math.abs(toBlock.getZ());

            BlockFace face;
            Vector normal;
            Location targetLoc;

            if (absY > absX && absY > absZ) {
                // Hit top or bottom face - particle nằm ngang
                if (toBlock.getY() > 0) {
                    face = BlockFace.UP;
                    normal = new Vector(0, 1, 0);
                    targetLoc = targetBlock.getLocation().add(0.5, 1.25, 0.5); // Trên mặt block + 0.25
                } else {
                    face = BlockFace.DOWN;
                    normal = new Vector(0, -1, 0);
                    targetLoc = targetBlock.getLocation().add(0.5, -0.25, 0.5); // Dưới mặt block - 0.25
                }
            } else if (absX > absZ) {
                // Hit east or west face - particle đứng
                if (toBlock.getX() > 0) {
                    face = BlockFace.EAST;
                    normal = new Vector(1, 0, 0);
                    targetLoc = targetBlock.getLocation().add(1.25, 0.5, 0.5); // Mặt đông + 0.25
                } else {
                    face = BlockFace.WEST;
                    normal = new Vector(-1, 0, 0);
                    targetLoc = targetBlock.getLocation().add(-0.25, 0.5, 0.5); // Mặt tây - 0.25
                }
            } else {
                // Hit north or south face - particle đứng
                if (toBlock.getZ() > 0) {
                    face = BlockFace.SOUTH;
                    normal = new Vector(0, 0, 1);
                    targetLoc = targetBlock.getLocation().add(0.5, 0.5, 1.25); // Mặt nam + 0.25
                } else {
                    face = BlockFace.NORTH;
                    normal = new Vector(0, 0, -1);
                    targetLoc = targetBlock.getLocation().add(0.5, 0.5, -0.25); // Mặt bắc - 0.25
                }
            }

            return new TargetInfo(targetLoc, face, normal);
        }
        return null;
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
        BukkitTask targetGridTask = startTargetGridEffect(targetInfo, castTime);

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
        BukkitTask targetGridTask = startTargetGridEffect(targetInfo, castTime);

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

                // Bỏ kiểm tra di chuyển cho pull-self để cho phép player di chuyển khi cast
                // if (player.getLocation().distance(castLocation) > 1.0) { ... }

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
            double radius = 2.0;
            int ticks = 0;
            final int maxTicks = (int) (scrollConfig.getDouble("cast-time", 2.0) * 20);

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // Giảm mật độ particle bằng cách giảm số lượng từ 4 xuống 3
                for (int i = 0; i < 3; i++) {
                    double particleAngle = angle + (i * 120.0 * Math.PI / 180.0);
                    double x = center.getX() + radius * Math.cos(particleAngle);
                    double z = center.getZ() + radius * Math.sin(particleAngle);
                    Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.5, z);

                    center.getWorld().spawnParticle(Particle.SPELL_WITCH, particleLoc, 1, 0, 0, 0, 0.1);
                    center.getWorld().spawnParticle(Particle.CRIT_MAGIC, particleLoc, 1, 0.1, 0.1, 0.1, 0.01); // Giảm từ 2 xuống 1
                }

                angle += Math.PI / 8;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // Tạo hiệu ứng lưới tròn tại vị trí target trong giai đoạn cast với hướng phù hợp
    private BukkitTask startTargetGridEffect(TargetInfo targetInfo, double duration) {
        return new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int) (duration * 20);
            final double maxRadius = 4.0;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // Tạo các vòng tròn đồng tâm với hiệu ứng hố sâu
                for (int ring = 1; ring <= 4; ring++) {
                    double radius = (maxRadius / 4.0) * ring;
                    drawCircleOnPlane(targetInfo, radius, 0.15); // Giảm mật độ từ 0.2 xuống 0.15
                }

                // Tạo các đường thẳng từ tâm ra ngoài
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0;
                    drawRadialLineOnPlane(targetInfo, angle, maxRadius, 0.2); // Giảm từ 0.3 xuống 0.2
                }

                // Tạo lưới vuông góc
                drawCrossLinesOnPlane(targetInfo, maxRadius, 0.2); // Giảm từ 0.25 xuống 0.2

                // Tạo hiệu ứng pulse ở tâm với mật độ thấp hơn
                if (ticks % 15 == 0) { // Tăng từ 10 lên 15 để giảm tần suất
                    targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, targetInfo.location, 3, // Giảm từ 5 xuống 3
                            0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(0, 255, 255), 1.5f));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L); // Tăng từ 3 lên 4 tick để giảm tần suất
    }

    private void drawCircleOnPlane(TargetInfo targetInfo, double radius, double density) {
        double circumference = 2 * Math.PI * radius;
        int points = Math.max(6, (int) (circumference / density));

        Vector normal = targetInfo.normal;
        Vector u, v;

        // Tạo 2 vector vuông góc với normal để tạo mặt phẳng
        if (targetInfo.face == BlockFace.UP || targetInfo.face == BlockFace.DOWN) {
            // Mặt ngang - vòng tròn nằm ngang (XZ plane)
            u = new Vector(1, 0, 0); // Hướng X
            v = new Vector(0, 0, 1); // Hướng Z
        } else {
            // Mặt đứng - vòng tròn đứng
            if (targetInfo.face == BlockFace.EAST || targetInfo.face == BlockFace.WEST) {
                // Mặt đông/tây - vòng tròn trên mặt phẳng YZ
                u = new Vector(0, 1, 0); // Hướng Y
                v = new Vector(0, 0, 1); // Hướng Z
            } else {
                // Mặt bắc/nam - vòng tròn trên mặt phẳng XY
                u = new Vector(1, 0, 0); // Hướng X
                v = new Vector(0, 1, 0); // Hướng Y
            }
        }

        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double localX = radius * Math.cos(angle);
            double localY = radius * Math.sin(angle);

            double depthRatio = 1.0 - (radius / 4.0);
            double depth = depthRatio * 0.8;

            Vector localPos = u.clone().multiply(localX).add(v.clone().multiply(localY));
            Vector depthOffset = normal.clone().multiply(-depth);
            Location point = targetInfo.location.clone().add(localPos).add(depthOffset);

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                    0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.0f));
        }
    }

    private void drawRadialLineOnPlane(TargetInfo targetInfo, double angle, double length, double density) {
        int points = (int) (length / density);

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

        for (int i = 1; i <= points; i++) {
            double distance = (length * i) / points;
            double localX = distance * Math.cos(angle);
            double localY = distance * Math.sin(angle);

            double depthRatio = 1.0 - (distance / length);
            double depth = depthRatio * 0.6;

            Vector localPos = u.clone().multiply(localX).add(v.clone().multiply(localY));
            Vector depthOffset = normal.clone().multiply(-depth);
            Location point = targetInfo.location.clone().add(localPos).add(depthOffset);

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                    0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 0.8f));
        }
    }

    private void drawCrossLinesOnPlane(TargetInfo targetInfo, double length, double density) {
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

        Vector[] directions = {u, u.clone().multiply(-1), v, v.clone().multiply(-1)};

        for (Vector direction : directions) {
            int points = (int) (length / density);
            for (int i = 1; i <= points; i++) {
                double distance = (length * i) / points;

                double depthRatio = 1.0 - (distance / length);
                double depth = depthRatio * 0.5;

                Vector pos = direction.clone().multiply(distance);
                Vector depthOffset = normal.clone().multiply(-depth);
                Location point = targetInfo.location.clone().add(pos).add(depthOffset);

                targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                        0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(30, 30, 30), 0.7f));
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
            startContinuousPull(enemy, targetLocation, 80);
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
                            (int) (3 * pulseIntensity), 0.1, 0.1, 0.1, 0.02); // Giảm từ 5 xuống 3
                    center.getWorld().spawnParticle(Particle.PORTAL, center,
                            (int) (2 * pulseIntensity), 0.05, 0.05, 0.05, 0.01); // Giảm từ 3 xuống 2
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

        // Start continuous pull effect like Spider-Man
        startContinuousPlayerPull(player, targetLocation, from);

        consumeScroll(player, item);
    }

    private void startContinuousPlayerPull(Player player, Location target, Location startLocation) {
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 60; // 3 seconds max

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    // Force teleport to target if something goes wrong
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

                // If close enough, teleport to exact location and finish
                if (distance < 2.0) {
                    player.teleport(target);
                    startGravityVortex(target, 40); // 2 seconds vortex at arrival
                    Map<String, String> placeholders = locPlaceholders(target);
                    sendMessage(player, "pulled-self", placeholders);
                    playSound(player, "success-sound");
                    cancel();
                    return;
                }

                // Calculate pull direction and strength
                Vector direction = target.toVector().subtract(currentLoc.toVector());
                direction.setY(direction.getY() + 0.1); // Slight upward motion to avoid ground collision

                // Stronger pull force - like being yanked by a rope
                double pullStrength = Math.min(distance * 0.4, 3.5); // Max speed of 3.5
                player.setVelocity(direction.normalize().multiply(pullStrength));

                // Add trailing particles for visual effect
                if (ticks % 2 == 0) { // Every 2 ticks
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