package com.NguyenDevs.worldScrolls.listeners.scrolls;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
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

        double castTime = scrollConfig != null ? scrollConfig.getDouble("cast-time", 5.0) : 5.0;
        if (castTime <= 0) castTime = 0.1;

        sendMessage(player, "prepare-pull-enemies");
        Location castLocation = player.getLocation().clone();

        BukkitTask gravitationTask = startGravitationEffect(player, castLocation);
        BukkitTask targetGridTask = startTargetGridEffect(targetInfo, castTime, 1.0);

        double pullRange = scrollConfig != null ? scrollConfig.getDouble("pull-range", 8.0) : 8.0;
        double pullTickStrength = scrollConfig != null ? scrollConfig.getDouble("cast-pull-strength", 1.2) : 1.2;

        double finalCastTime = castTime;
        BukkitTask pullTask = new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = (int) (finalCastTime * 20 + 5 * 20);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                double depthMult;
                if (ticks < 20) {
                    depthMult = 0.0;
                } else if (ticks < 40) {
                    depthMult = Math.pow((ticks - 20.0) / 20.0, 0.5);
                } else if (ticks < 60) {
                    depthMult = (ticks - 40.0) / 20.0;
                } else {
                    depthMult = 1.0;
                }

                Location dynamicTarget = targetInfo.location.clone();
                Vector depthOffset = targetInfo.normal.clone().multiply(-14.0 * depthMult);
                dynamicTarget.add(depthOffset);

                if (ticks >= 150) {
                    pullEntitiesTowards(dynamicTarget, pullRange, pullTickStrength, player, depthMult, ticks, targetInfo, totalTicks);
                }

                if (ticks % 15 == 0) {
                    player.getWorld().playSound(
                            dynamicTarget,
                            Sound.BLOCK_BEACON_POWER_SELECT,
                            0.5f,
                            0.1f
                    );
                }

                ticks++;
                if (ticks >= totalTicks) {
                    finishEnemiesCast(player, item, targetInfo, pullRange);
                    cancel();
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                gravitationTask.cancel();
                targetGridTask.cancel();
                activeCasts.remove(player.getUniqueId());
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeCasts.put(player.getUniqueId(), pullTask);
    }

    private void finishEnemiesCast(Player player, ItemStack item, TargetInfo targetInfo, double pullRange) {
        World w = targetInfo.location.getWorld();
        if (w == null) return;

        List<LivingEntity> enemies = new ArrayList<>();
        for (Entity entity : w.getNearbyEntities(targetInfo.location, pullRange, pullRange, pullRange)) {
            if (entity instanceof Monster || (entity instanceof LivingEntity && entity != player && !(entity instanceof Player))) {
                enemies.add((LivingEntity) entity);
            }
        }

        for (LivingEntity e : enemies) {
            SoundConfig sc = getSoundConfig("pull-enemies-sound", "ENTITY_PLAYER_ATTACK_SWEEP", 1.0f, 1.0f);
            w.playSound(e.getLocation(), sc.sound, sc.volume, sc.pitch);
            double hp = e.getHealth();
            double dmg = Math.max(0.0, hp / 3.0);
            if (dmg > 0) e.damage(dmg, player);

            Vector knockback = targetInfo.normal.normalize().multiply(1.2).add(new Vector(0, 0.3, 0));
            e.setVelocity(knockback);

            e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0, false, false));
        }
        w.spawnParticle(
                Particle.REDSTONE,
                targetInfo.location.clone().add(0, 0.2, 0),
                30,
                0.3, 0.3, 0.3,
                0.01,
                new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f)
        );
        w.spawnParticle(
                Particle.CRIT_MAGIC,
                targetInfo.location.clone().add(0, 0.2, 0),
                20,
                0.2, 0.2, 0.2,
                0.05
        );

        playSound(player, "success-sound");
        Map<String, String> placeholders = locPlaceholders(targetInfo.location);
        placeholders.put("enemy-count", String.valueOf(enemies.size()));
        sendMessage(player, "pulled-enemies", placeholders);
        consumeScroll(player, item);
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

    private void startPullSelfProcess(Player player, ItemStack item, TargetInfo targetInfo, double castTime) {
        sendMessage(player, "prepare-pull-self");
        Location castLocation = player.getLocation().clone();

        BukkitTask gravitationTask = startGravitationEffect(player, castLocation);
        BukkitTask targetGridTask = startTargetGridEffect(targetInfo, castTime, 1.0 / 3.0);

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

    private BukkitTask startTargetGridEffect(TargetInfo targetInfo, double duration, double scale) {
        return new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int) (duration * 20);
            final double pullRange = scrollConfig.getDouble("pull-range", 8.0);
            double baseMaxRadius = (pullRange / 2.0) * scale;
            double baseMinRadius = 0.8 * scale;
            double baseHoleRadius = 0.2 * baseMaxRadius;

            final boolean isRightClick = scale < 1.0;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                if (isRightClick) {
                    baseMaxRadius = 2.0;
                    baseMinRadius = 0.8;
                    baseHoleRadius = 0.2 * baseMaxRadius;
                    double prog = (double) ticks / (maxTicks);
                    if (ticks < 20) {
                        prog = 0.0;
                    } else {
                        prog = (ticks - 20.0) / (maxTicks - 20.0);
                    }
                    drawGrid(targetInfo, baseMinRadius, baseMaxRadius, prog, ticks, baseHoleRadius);
                } else {
                    if (ticks < 20) {
                        createPointCluster(targetInfo);
                    } else if (ticks < 40) {
                        double prog = Math.pow((ticks - 20.0) / 20.0, 0.5);
                        double currentMaxRadius = baseMaxRadius * prog;
                        double currentMinRadius = baseMinRadius * prog;
                        double currentHoleRadius = 0.2 * currentMaxRadius;
                        drawGrid(targetInfo, currentMinRadius, currentMaxRadius, 0.0, ticks, currentHoleRadius);
                    } else if (ticks < 60) {
                        double prog = (ticks - 40.0) / 20.0;
                        drawGrid(targetInfo, baseMinRadius, baseMaxRadius, prog, ticks, baseHoleRadius);
                    } else {
                        drawGrid(targetInfo, baseMinRadius, baseMaxRadius, 1.0, ticks, baseHoleRadius);
                    }
                }

                ticks++;
            }

            private void drawGrid(TargetInfo targetInfo, double minRadius, double maxRadius, double depthMult, int ticks, double holeRadius) {
                int ringCount = isRightClick ? 8 : 12;
                for (int ring = 1; ring <= ringCount; ring++) {
                    double normalizedRing = (double) ring / ringCount;
                    double radius = minRadius + (maxRadius - minRadius) * (normalizedRing * normalizedRing * 0.7 + normalizedRing * 0.3);
                    drawThinCircleOnPlane(targetInfo, radius, 0.08, depthMult, holeRadius, maxRadius);
                }

                for (int i = 0; i < 16; i++) {
                    double angle = i * Math.PI / 8.0;
                    drawRadialLineWithDepth(targetInfo, angle, maxRadius, minRadius, 0.12, depthMult, holeRadius, maxRadius);
                }
                createBlackHoleEdge(targetInfo, ticks, minRadius / 0.8);

                drawCrossLinesWithDepth(targetInfo, maxRadius, minRadius, 0.15, depthMult, holeRadius, maxRadius);

                if (ticks % 10 == 0) {
                    double holeEdgeRadius = minRadius;

                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4 + ticks * 0.05;
                        double x = holeEdgeRadius * Math.cos(angle);
                        double z = holeEdgeRadius * Math.sin(angle);

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

                        Vector localPos = u.clone().multiply(x).add(v.clone().multiply(z));
                        Vector depthOffset = normal.clone().multiply(-1.2 * depthMult);
                        Location edgePoint = targetInfo.location.clone().add(localPos).add(depthOffset);

                        targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, edgePoint, 1,
                                0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.fromRGB(0, 255, 255), 0.8f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void createPointCluster(TargetInfo targetInfo) {
        Random rand = new Random();
        for (int i = 0; i < 20; i++) {
            double offsetX = (rand.nextDouble() - 0.5) * 0.2;
            double offsetY = (rand.nextDouble() - 0.5) * 0.2;
            double offsetZ = (rand.nextDouble() - 0.5) * 0.2;
            Location pointLoc = targetInfo.location.clone().add(offsetX, offsetY, offsetZ);
            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, pointLoc, 1,
                    0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(10, 10, 10), 0.25f));
        }
    }

    private void drawThinCircleOnPlane(TargetInfo targetInfo, double radius, double density, double depthMult, double holeRadius, double maxRadius) {
        double circumference = 2 * Math.PI * radius;
        int points = Math.max(8, (int) (circumference / density));

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

        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double localX = radius * Math.cos(angle);
            double localY = radius * Math.sin(angle);

            double depth = (holeRadius / Math.max(radius, 0.1)) * 6.0 * depthMult;

            Vector localPos = u.clone().multiply(localX).add(v.clone().multiply(localY));
            Vector depthOffset = normal.clone().multiply(-depth);
            Location point = targetInfo.location.clone().add(localPos).add(depthOffset);

            Color ringColor = Color.fromRGB(
                    (int) (10 + radius * 3),
                    (int) (10 + radius * 3),
                    (int) (10 + radius * 3)
            );

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                    0, 0, 0, 0, new Particle.DustOptions(ringColor, 0.25f));
        }
    }

    private void drawRadialLineWithDepth(TargetInfo targetInfo, double angle, double maxLength, double minLength, double density, double depthMult, double holeRadius, double maxRadius) {
        int points = (int) ((maxLength - minLength) / density);

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
            double distance = minLength + ((maxLength - minLength) * i) / points;
            double localX = distance * Math.cos(angle);
            double localY = distance * Math.sin(angle);

            double depth = (holeRadius / Math.max(distance, 0.1)) * 6.0 * depthMult;

            Vector localPos = u.clone().multiply(localX).add(v.clone().multiply(localY));
            Vector depthOffset = normal.clone().multiply(-depth);
            Location point = targetInfo.location.clone().add(localPos).add(depthOffset);

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                    0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(15, 15, 15), 0.25f));
        }
    }

    private void createBlackHoleEdge(TargetInfo targetInfo, int ticks, double scale) {
        Vector normal = targetInfo.normal;
        double holeRadius = 0.8 * scale;

        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6 + ticks * 0.08;
            double x = holeRadius * Math.cos(angle);
            double z = holeRadius * Math.sin(angle);

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

            Vector localPos = u.clone().multiply(x).add(v.clone().multiply(z));
            Vector depthOffset = normal.clone().multiply(-1.5);
            Location edgePoint = targetInfo.location.clone().add(localPos).add(depthOffset);

            targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, edgePoint, 1,
                    0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.fromRGB(3, 3, 3), 0.25f));

            if (ticks % 6 == 0) {
                double innerAngle = angle + Math.PI / 12;
                double innerRadius = holeRadius * 0.7;
                double innerX = innerRadius * Math.cos(innerAngle);
                double innerZ = innerRadius * Math.sin(innerAngle);

                Vector innerPos = u.clone().multiply(innerX).add(v.clone().multiply(innerZ));
                Vector innerDepthOffset = normal.clone().multiply(-1.3);
                Location innerPoint = targetInfo.location.clone().add(innerPos).add(innerDepthOffset);

                targetInfo.location.getWorld().spawnParticle(Particle.SPELL_WITCH, innerPoint, 1,
                        0.02, 0.02, 0.02, 0.001);
            }
        }
    }

    private void drawCrossLinesWithDepth(TargetInfo targetInfo, double maxLength, double minLength, double density, double depthMult, double holeRadius, double maxRadius) {
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
            int points = (int) ((maxLength - minLength) / density);
            for (int i = 1; i <= points; i++) {
                double distance = minLength + ((maxLength - minLength) * i) / points;

                double depth = (holeRadius / Math.max(distance, 0.1)) * 6.0 * depthMult;

                Vector pos = direction.clone().multiply(distance);

                Vector depthOffset = normal.clone().multiply(-depth);
                Location point = targetInfo.location.clone().add(pos).add(depthOffset);

                targetInfo.location.getWorld().spawnParticle(Particle.REDSTONE, point, 1,
                        0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 0.25f));
            }
        }
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
                                1, 0.05, 0.05, 0.05, 0.01, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 0.5f));
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
                                1, 0.03, 0.03, 0.03, 0.005, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 0.5f));
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

    private void pullEntitiesTowards(Location dynamicTarget, double range, double strengthCap, Player source, double depthMult, int ticks, TargetInfo targetInfo, int totalTicks) {
        World w = dynamicTarget.getWorld();
        if (w == null) return;

        double progress = (double) ticks / Math.max(1, (totalTicks - 1));
        double pullStrength = (ticks < totalTicks - 1) ? strengthCap * 0.3 * (1 + progress * 2) : strengthCap * 3.0;

        Vector n = targetInfo.normal.clone().normalize();
        double depth = 14.0 * depthMult;
        Location surface = targetInfo.location.clone();
        Location pullCenter = surface.clone().add(n.clone().multiply((range - depth) * 0.5));
        double effectiveRadius = 0.5 * (range + depth);
        double r2 = effectiveRadius * effectiveRadius;

        Location attractPoint = surface.clone().add(n.clone().multiply(-depth));

        for (Entity entity : w.getNearbyEntities(pullCenter, effectiveRadius, effectiveRadius, effectiveRadius)) {
            if (entity instanceof LivingEntity && entity != source && !(entity instanceof Player)) {
                LivingEntity e = (LivingEntity) entity;
                Location el = e.getLocation();
                if (el.toVector().distanceSquared(pullCenter.toVector()) <= r2) {
                    Vector dir = attractPoint.toVector().subtract(el.toVector());
                    dir.setY(dir.getY() + 0.2);
                    double distanceToAttractor = el.distance(attractPoint);
                    double adjustedStrength = Math.min(pullStrength * (distanceToAttractor / Math.max(range, 0.001)), pullStrength);
                    adjustedStrength = Math.max(adjustedStrength, 0.1);
                    e.setVelocity(dir.normalize().multiply(adjustedStrength));
                    if (ticks % 10 == 0 && e.getHealth() > 0) e.damage(0.5, source);
                }
            }
        }
    }


    private void consumeScroll(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    private void playSound(Player player, String soundKey) {
        SoundConfig sc = getSoundConfig(soundKey, "BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.0f);
        try {
            player.playSound(player.getLocation(), sc.sound, sc.volume, sc.pitch);
        } catch (IllegalArgumentException ignored) {
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

    private static class SoundConfig {
        final Sound sound;
        final float volume;
        final float pitch;

        SoundConfig(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private SoundConfig getSoundConfig(String key, String def, float defVol, float defPitch) {
        ConfigurationSection soundConfig = scrollConfig.getConfigurationSection("sounds." + key);
        String soundName = def;
        float volume = defVol;
        float pitch = defPitch;
        if (soundConfig != null) {
            soundName = soundConfig.getString("sound", def);
            volume = (float) soundConfig.getDouble("volume", defVol);
            pitch = (float) soundConfig.getDouble("pitch", defPitch);
        }
        Sound s;
        try {
            s = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            s = Sound.BLOCK_NOTE_BLOCK_PLING;
        }
        return new SoundConfig(s, volume, pitch);
    }
}