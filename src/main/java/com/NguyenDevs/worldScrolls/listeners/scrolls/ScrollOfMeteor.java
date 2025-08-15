package com.NguyenDevs.worldScrolls.listeners.scrolls;

import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.NguyenDevs.worldScrolls.WorldScrolls;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class ScrollOfMeteor implements Listener {

    private static final int MIN_METEOR_SIZE = 3;
    private static final int MAX_METEOR_SIZE = 10;
    private static final int SOUND_RADIUS = 150;
    private static final int SPAWN_HEIGHT = 100;
    private static final int VIEW_RANGE = 192;
    private static final String SCROLL_FILE = "scroll_of_meteor";

    private static final Material[] METEOR_MATERIALS = {
            Material.DEEPSLATE, Material.DEEPSLATE, Material.DEEPSLATE,
            Material.BLACKSTONE, Material.BLACKSTONE, Material.BLACKSTONE,
            Material.MAGMA_BLOCK, Material.GILDED_BLACKSTONE
    };

    private static final Material[] SCORCHED_MATERIALS = {
            Material.COBBLESTONE, Material.DEEPSLATE, Material.MAGMA_BLOCK
    };

    private static final Material[] METEOR_CRUST_MATERIALS = {
            Material.DEEPSLATE, Material.BLACKSTONE, Material.COBBLED_DEEPSLATE
    };

    private final WorldScrolls plugin;
    private final NamespacedKey KEY_SCROLL_TYPE;
    private final Map<UUID, MeteorTask> activeMeteors = new ConcurrentHashMap<>();
    private final Map<BlockPosition, Set<UUID>> phantomBlockTracking = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUseTime = new HashMap<>();
    private final Map<UUID, Long> lastUseTime2 = new HashMap<>();

    private ConfigurationSection scrollConfig;
    private ConfigurationSection scrollsConfig;


    public ScrollOfMeteor(WorldScrolls plugin) {
        this.plugin = plugin;
        this.KEY_SCROLL_TYPE = new NamespacedKey(plugin, "scroll_type");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadConfigurations();
    }

    private void loadConfigurations() {
        this.scrollConfig = plugin.getConfigManager().getScrollConfig(SCROLL_FILE);
        this.scrollsConfig = plugin.getConfigManager().getScrolls().getConfigurationSection("scroll_of_meteor");
    }

    @EventHandler
    public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String material = scrollConfig != null ? scrollConfig.getString("material", "PAPER") : "PAPER";
        if (item == null || item.getType() != Material.valueOf(material)) return;
        if (!isScrollOfMeteor(item)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR) return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        if (lastUseTime.containsKey(player.getUniqueId()) &&
                now - lastUseTime.get(player.getUniqueId()) < 500) {
            return;
        }

        lastUseTime.put(player.getUniqueId(), now);

        int minSpawn = (int) (scrollConfig != null ? scrollConfig.getDouble("min", 1.0) : 1.0);
        int maxSpawn = (int) (scrollConfig != null ? scrollConfig.getDouble("max", 3.0) : 1.0);
        int cooldownSeconds = scrollsConfig != null ? scrollsConfig.getInt("cooldown", 0) : 0;

        if(cooldownSeconds > 0) {
            long now1 = System.currentTimeMillis();
            if (lastUseTime2.containsKey(player.getUniqueId())) {
                long last = lastUseTime2.get(player.getUniqueId());
                long elapsed = now1 - last;
                if (elapsed < cooldownSeconds * 1000L) {
                    long remaining = (cooldownSeconds * 1000L - elapsed) / 1000L;
                    String cooldownMsg = plugin.getConfigManager()
                        .getScrollMessage(SCROLL_FILE, "on-cooldown")
                        .replace("%remaining%", String.valueOf(remaining));
                    player.sendMessage(ColorUtils.colorize(plugin.getConfigManager().getScrollMessage(SCROLL_FILE, "prefix") + " " + cooldownMsg));
                return;
                }
            }
            lastUseTime2.put(player.getUniqueId(), now1);
        }

        if (player.getGameMode() == GameMode.SPECTATOR) return;
        Block target = player.getTargetBlockExact(100);
        if (target == null) return;
        Location targetLocation = target.getLocation().add(0.5, 1, 0.5);
        World world = targetLocation.getWorld();
        if (world == null) return;

        spawnLavaTelegraph(targetLocation, 20);

        int meteorCount = ThreadLocalRandom.current().nextInt(minSpawn, maxSpawn + 1);

        for (int i = 0; i < meteorCount; i++) {
            int delayTicks = (i == 0) ? 20 : (20 + i * 5);
            Bukkit.getScheduler().runTaskLater(WorldScrolls.getInstance(), () -> {
                Vector diagonalDirection = generateRandomDiagonalDirection();
                double spawnDistance = ThreadLocalRandom.current().nextDouble(80, 120);
                Location startLocation = targetLocation.clone()
                        .add(diagonalDirection.getX() * spawnDistance, SPAWN_HEIGHT, diagonalDirection.getZ() * spawnDistance);
                int meteorSize = ThreadLocalRandom.current().nextInt(MIN_METEOR_SIZE, MAX_METEOR_SIZE + 1);
                UUID meteorId = UUID.randomUUID();
                MeteorTask meteorTask = new MeteorTask(startLocation, targetLocation, meteorSize, meteorId);
                activeMeteors.put(meteorId, meteorTask);
                meteorTask.runTaskTimer(WorldScrolls.getInstance(), 0L, 1L);
            }, delayTicks);
        }

        consumeOne(player, item);
        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.5f);
    }


    private void spawnLavaTelegraph(Location center, int durationTicks) {
        World world = center.getWorld();
        if (world == null) return;

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= durationTicks) {
                    cancel();
                    return;
                }

                double radius = 1.5 + (ticks / 20.0) * 1.0;
                int points = 24;
                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI * i) / points;
                    double x = center.getX() + Math.cos(angle) * radius;
                    double z = center.getZ() + Math.sin(angle) * radius;
                    int y = world.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z));
                    Location ringLoc = new Location(world, x, y + 0.2, z);

                    world.spawnParticle(Particle.LAVA, ringLoc, 1, 0.02, 0.02, 0.02, 0.0);
                    world.spawnParticle(Particle.SMOKE_NORMAL, ringLoc, 1, 0.02, 0.02, 0.02, 0.0);
                    if (ticks % 5 == 0) {
                        world.spawnParticle(Particle.LAVA, ringLoc, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }

                if (ticks % 10 == 0) {
                    world.playSound(center, Sound.BLOCK_LAVA_POP, SoundCategory.AMBIENT, 0.8f, 0.6f);
                    world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.AMBIENT, 0.6f, 0.5f);
                }

                world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.5, 0), 8, 0.25, 0.6, 0.25, 0.0);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0, 0.2, 0), 6, 0.2, 0.4, 0.2, 0.01);

                ticks += 2;
            }
        }.runTaskTimer(WorldScrolls.getInstance(), 0L, 2L);
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && hand.equals(item)) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                item.setAmount(0);
            }
        }
    }

    private boolean isScrollOfMeteor(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String t = pdc.get(KEY_SCROLL_TYPE, PersistentDataType.STRING);
        if ("scroll_of_meteor".equalsIgnoreCase(String.valueOf(t))) return true;
        if (meta.hasLocalizedName()) {
            String id = meta.getLocalizedName();
            if (id.equals("worldscrolls:scroll_of_meteor") || id.startsWith("worldscrolls:scroll_of_meteor:")) return true;
        }
        return false;
    }

    private Vector generateRandomDiagonalDirection() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(0.3, 2 * Math.PI - 0.3);
        double[] avoidAngles = {0, Math.PI/2, Math.PI, 3*Math.PI/2};
        for (double avoidAngle : avoidAngles) {
            if (Math.abs(angle - avoidAngle) < 0.2) angle += 0.3;
        }
        double x = Math.cos(angle);
        double z = Math.sin(angle);
        return new Vector(x, 0, z).normalize();
    }

    private void sendBlockChange(Player player, BlockPosition pos, WrappedBlockData data) {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_CHANGE);
            packet.getBlockPositionModifier().write(0, pos);
            packet.getBlockData().write(0, data);
            pm.sendServerPacket(player, packet, false);
        } catch (Exception ignored) {}
    }

    private static boolean inHorizontalRange(Player p, Location c, int range) {
        if (!p.getWorld().equals(c.getWorld())) return false;
        Location pl = p.getLocation();
        double dx = pl.getX() - c.getX();
        double dz = pl.getZ() - c.getZ();
        return (dx * dx + dz * dz) <= (range * range);
    }

    private class MeteorTask extends BukkitRunnable {
        private final ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        private final Location startLocation;
        private final Location targetLocation;
        private final int meteorSize;
        private final UUID meteorId;
        private final Vector flightDirection;
        private final MeteorShape meteorShape;
        private Location currentLocation;
        private int tickCount = 0;
        private boolean hasImpacted = false;
        private final double speed;
        private final Map<UUID, Set<BlockPosition>> lastSentPerPlayer = new HashMap<>();
        private final List<MeteorVoxel> meteorVoxels;
        private final int MAX_TICKS = 20 * 40;

        private class MeteorVoxel {
            final Vector offset;
            final WrappedBlockData data;
            MeteorVoxel(Vector offset, WrappedBlockData data) {
                this.offset = offset;
                this.data = data;
            }
        }

        public MeteorTask(Location start, Location target, int size, UUID meteorId) {
            this.startLocation = start.clone();
            this.targetLocation = target.clone();
            this.meteorSize = size;
            this.meteorId = meteorId;
            this.currentLocation = start.clone();
            this.meteorShape = selectRandomMeteorShape(size);
            Vector baseDirection = target.toVector().subtract(start.toVector()).normalize();
            //double randomOffset = ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
            this.flightDirection = new Vector(
                    baseDirection.getX(),
                    Math.min(baseDirection.getY(), -0.55),
                    baseDirection.getZ()
            ).normalize();
            this.speed = ThreadLocalRandom.current().nextDouble(2.0, 3.5);
            this.meteorVoxels = generateMeteorVoxels(size, meteorId, meteorShape);
        }

        private MeteorShape selectRandomMeteorShape(int size) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (size >= 9) {
                double rand = random.nextDouble();
                if (rand < 0.25) return MeteorShape.COMPLEX;
                if (rand < 0.45) return MeteorShape.IRREGULAR;
                if (rand < 0.65) return MeteorShape.TAILED;
                if (rand < 0.85) return MeteorShape.ARROW;
                return MeteorShape.SPHERICAL;
            } else if (size >= 6) {
                double rand = random.nextDouble();
                if (rand < 0.2) return MeteorShape.COMPLEX;
                if (rand < 0.4) return MeteorShape.IRREGULAR;
                if (rand < 0.6) return MeteorShape.TAILED;
                if (rand < 0.8) return MeteorShape.ARROW;
                return MeteorShape.SPHERICAL;
            } else {
                double rand = random.nextDouble();
                if (rand < 0.1) return MeteorShape.COMPLEX;
                if (rand < 0.3) return MeteorShape.IRREGULAR;
                if (rand < 0.5) return MeteorShape.TAILED;
                if (rand < 0.7) return MeteorShape.ARROW;
                return MeteorShape.SPHERICAL;
            }
        }

        @Override
        public void run() {
            try {
                if (hasImpacted || tickCount++ > MAX_TICKS) {
                    if (!hasImpacted) impact(currentLocation.clone());
                    cancel();
                    return;
                }
                Location prev = currentLocation.clone();
                Vector movement = flightDirection.clone().multiply(speed);
                currentLocation.add(movement);
                World w = currentLocation.getWorld();
                if (w != null) {
                    org.bukkit.util.RayTraceResult r = w.rayTraceBlocks(prev, movement.normalize(), movement.length(), org.bukkit.FluidCollisionMode.NEVER, true);
                    if (r != null && r.getHitPosition() != null) {
                        Block hitBlock = r.getHitBlock();
                        if (hitBlock != null && isSolidTerrain(hitBlock.getType())) {
                            Location hit = new Location(w, r.getHitPosition().getX(), r.getHitPosition().getY(), r.getHitPosition().getZ());
                            hasImpacted = true;
                            impact(hit);
                            cancel();
                            return;
                        }
                    }
                }
                createTrailEffect();
                if (tickCount % 6 == 0) playMeteorFlightSound();
                if (tickCount % 2 == 0) renderFakeMeteor();
                if (currentLocation.getBlockY() <= getGroundLevel(currentLocation)) {
                    hasImpacted = true;
                    impact(currentLocation.clone());
                    cancel();
                }
            } catch (Exception e) {
                WorldScrolls.getInstance().getLogger().log(Level.WARNING, "Error in meteor task", e);
                cancel();
            }
        }

        private void createTrailEffect() {
            World world = currentLocation.getWorld();
            if (world == null) return;
            double offset = meteorSize / 2.0;
            world.spawnParticle(Particle.FLAME, currentLocation, 20, offset, offset, offset, 0.03);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, currentLocation, 15, offset, offset, offset, 0.05);
            world.spawnParticle(Particle.LAVA, currentLocation, 8, offset/2, offset/2, offset/2, 0);
            world.spawnParticle(Particle.CRIT, currentLocation, 10, offset, offset, offset, 0.1);
            world.spawnParticle(Particle.EXPLOSION_NORMAL, currentLocation, 5, offset/3, offset/3, offset/3, 0.02);
        }

        private void playMeteorFlightSound() {
            World world = currentLocation.getWorld();
            if (world == null) return;
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distance(currentLocation) <= SOUND_RADIUS) {
                    player.playSound(currentLocation, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.AMBIENT, 0.8f, 0.6f);
                    player.playSound(currentLocation, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.AMBIENT, 1.0f, 0.4f);
                    player.playSound(currentLocation, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.AMBIENT, 0.5f, 0.8f);
                }
            }
        }

        private void renderFakeMeteor() {
            World world = currentLocation.getWorld();
            if (world == null) return;
            Map<BlockPosition, WrappedBlockData> currentBlocks = new HashMap<>();
            for (MeteorVoxel voxel : meteorVoxels) {
                Location blockLoc = currentLocation.clone().add(voxel.offset);
                BlockPosition pos = new BlockPosition(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ());
                currentBlocks.put(pos, voxel.data);
            }
            for (Player player : world.getPlayers()) {
                if (!inHorizontalRange(player, currentLocation, VIEW_RANGE)) continue;
                Set<BlockPosition> lastSent = lastSentPerPlayer.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
                for (BlockPosition oldPos : lastSent) {
                    sendBlockChange(player, oldPos, WrappedBlockData.createData(Material.AIR.createBlockData()));
                    removePhantomBlockTracking(oldPos, player.getUniqueId());
                }
                lastSent.clear();
                for (Map.Entry<BlockPosition, WrappedBlockData> entry : currentBlocks.entrySet()) {
                    sendBlockChange(player, entry.getKey(), entry.getValue());
                    addPhantomBlockTracking(entry.getKey(), player.getUniqueId());
                }
                lastSent.addAll(currentBlocks.keySet());
            }
        }

        private void addPhantomBlockTracking(BlockPosition pos, UUID playerId) {
            phantomBlockTracking.computeIfAbsent(pos, k -> new HashSet<>()).add(playerId);
        }

        private void removePhantomBlockTracking(BlockPosition pos, UUID playerId) {
            Set<UUID> players = phantomBlockTracking.get(pos);
            if (players != null) {
                players.remove(playerId);
                if (players.isEmpty()) {
                    phantomBlockTracking.remove(pos);
                }
            }
        }

        private void clearAllFakeBlocks() {
            for (Map.Entry<UUID, Set<BlockPosition>> entry : lastSentPerPlayer.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) continue;
                for (BlockPosition pos : entry.getValue()) {
                    sendBlockChange(player, pos, WrappedBlockData.createData(Material.AIR.createBlockData()));
                    removePhantomBlockTracking(pos, entry.getKey());
                }
                entry.getValue().clear();
            }
        }

        private void impact(Location impactPoint) {
            try {
                World world = impactPoint.getWorld();
                if (world == null) return;
                clearAllFakeBlocks();
                world.playSound(impactPoint, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 2.5f, 0.5f);
                world.playSound(impactPoint, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 1.5f, 0.7f);
                world.playSound(impactPoint, Sound.BLOCK_ANVIL_PLACE, SoundCategory.AMBIENT, 1.2f, 0.4f);
                world.spawnParticle(Particle.EXPLOSION_LARGE, impactPoint, 10, 3, 3, 3, 0);
                world.spawnParticle(Particle.LAVA, impactPoint, 50, 4, 4, 4, 0);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, impactPoint, 30, 3, 3, 3, 0.05);
                world.spawnParticle(Particle.FLAME, impactPoint, 40, 3, 3, 3, 0.1);
                applyShockwaveAt(impactPoint);
                createEnhancedMeteorCrater(impactPoint);
            } catch (Exception e) {
                WorldScrolls.getInstance().getLogger().log(Level.WARNING, "Error creating meteor impact", e);
            }
        }

        private int getGroundLevel(Location loc) {
            World world = loc.getWorld();
            if (world == null) return loc.getBlockY();
            for (int y = loc.getBlockY(); y >= world.getMinHeight(); y--) {
                Material mat = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType();
                if (isSolidTerrain(mat)) {
                    return y;
                }
            }
            return world.getMinHeight();
        }

        private boolean isSolidTerrain(Material material) {
            return material.isSolid() &&
                    material != Material.WATER &&
                    !material.name().contains("LEAVES") &&
                    !material.name().contains("LOG") &&
                    !material.name().contains("WOOD") &&
                    !isVegetation(material);
        }

        private boolean isVegetation(Material material) {
            return material == Material.GRASS ||
                    material == Material.TALL_GRASS ||
                    material == Material.FERN ||
                    material == Material.LARGE_FERN ||
                    material == Material.DEAD_BUSH ||
                    material == Material.DANDELION ||
                    material == Material.POPPY ||
                    material == Material.BLUE_ORCHID ||
                    material == Material.ALLIUM ||
                    material == Material.AZURE_BLUET ||
                    material == Material.RED_TULIP ||
                    material == Material.ORANGE_TULIP ||
                    material == Material.WHITE_TULIP ||
                    material == Material.PINK_TULIP ||
                    material == Material.OXEYE_DAISY ||
                    material == Material.SUNFLOWER ||
                    material == Material.LILAC ||
                    material == Material.ROSE_BUSH ||
                    material == Material.PEONY ||
                    material == Material.GLOWSTONE ||
                    material.name().contains("SAPLING") ||
                    material.name().contains("MUSHROOM");
        }

        private void applyShockwaveAt(Location center) {
            ConfigurationSection scrollConfig = plugin.getConfigManager().getScrolls().getConfigurationSection("scroll_of_meteor");
            double baseDamage = scrollConfig != null ? scrollConfig.getDouble("damage") : 15 + 5.0 + meteorSize * 1.0;
            World world = center.getWorld();
            double knockRadius = meteorSize * 2.5;
            for (Player player : world.getPlayers()) {
                if (!player.getWorld().equals(world)) continue;
                double distance = player.getLocation().distance(center);
                if (distance <= knockRadius) {
                    Vector direction = player.getLocation().toVector().subtract(center.toVector()).normalize();
                    double strength = (1.0 - distance / knockRadius) * (meteorSize / 3.5) + 1.5;
                    player.setVelocity(direction.multiply(strength).add(new Vector(0, 0.8 + meteorSize * 0.04, 0)));
                    double damage = Math.max(1.0, baseDamage * (1.0 - (distance / knockRadius)));
                    player.damage(damage);
                }
            }
        }

        private void createEnhancedMeteorCrater(Location impactPoint) {
            World world = impactPoint.getWorld();
            if (world == null) return;
            clearVegetationAndFloatingObjects(impactPoint);
            createInstantDrillingCrater(impactPoint);
            createMinimalScorchedArea(impactPoint);
            Bukkit.getScheduler().runTaskLater(WorldScrolls.getInstance(), () -> performFinalCleanup(impactPoint), 60L);
        }

        private void performFinalCleanup(Location impactPoint) {
            World world = impactPoint.getWorld();
            if (world == null) return;
            int radius = Math.max(8, meteorSize + 6);
            for (int x = -radius; x <= radius; x++) {
                for (int y = -15; y <= 10; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        double distance = Math.sqrt(x*x + z*z);
                        if (distance > radius) continue;
                        Location loc = impactPoint.clone().add(x, y, z);
                        Block block = world.getBlockAt(loc);
                        if ((block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE)) {
                            Block below = world.getBlockAt(loc.clone().subtract(0, 1, 0));
                            if (!below.getType().isSolid()) block.setType(Material.AIR, false);
                        }
                    }
                }
            }
            smoothCraterEdges(impactPoint);
        }

        private void smoothCraterEdges(Location center) {
            World world = center.getWorld();
            if (world == null) return;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double smoothRadius = meteorSize * 1.5;
            for (double angle = 0; angle < 2 * Math.PI; angle += 0.1) {
                for (double r = smoothRadius * 0.8; r <= smoothRadius * 1.2; r += 0.3) {
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location edgeLoc = center.clone().add(x, 0, z);
                    int groundY = world.getHighestBlockYAt(edgeLoc);
                    boolean isEdge = false;
                    for (int checkY = groundY - 1; checkY >= groundY - 5; checkY--) {
                        Block checkBlock = world.getBlockAt(edgeLoc.getBlockX(), checkY, edgeLoc.getBlockZ());
                        if (checkBlock.getType().isAir()) {
                            isEdge = true;
                            break;
                        }
                    }
                    if (isEdge && random.nextDouble() < 0.6) {
                        for (int step = 0; step < 3; step++) {
                            Location stepLoc = new Location(world, edgeLoc.getBlockX(), groundY - step, edgeLoc.getBlockZ());
                            Block stepBlock = world.getBlockAt(stepLoc);
                            if (stepBlock.getType().isAir() && random.nextDouble() < 0.4) {
                                Material material = step == 0 ? SCORCHED_MATERIALS[random.nextInt(SCORCHED_MATERIALS.length)] : METEOR_CRUST_MATERIALS[random.nextInt(METEOR_CRUST_MATERIALS.length)];
                                stepBlock.setType(material, false);
                            }
                        }
                    }
                }
            }
        }

        private void clearVegetationAndFloatingObjects(Location center) {
            World world = center.getWorld();
            int radius = Math.max(6, meteorSize + 4);
            for (int x = -radius; x <= radius; x++) {
                for (int y = -12; y <= 12; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        double distance = Math.sqrt(x*x + z*z);
                        if (distance > radius) continue;
                        Location loc = center.clone().add(x, y, z);
                        Block block = world.getBlockAt(loc);
                        if (isVegetation(block.getType()) ||
                                block.getType() == Material.FIRE ||
                                block.getType() == Material.SOUL_FIRE ||
                                block.getType().name().contains("TORCH") ||
                                block.getType().name().contains("SIGN") ||
                                block.getType().name().contains("BANNER") ||
                                block.getType() == Material.SNOW ||
                                block.getType() == Material.POWDER_SNOW ||
                                (!block.getType().isSolid() && block.getType() != Material.AIR && block.getType() != Material.WATER)) {
                            block.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }

        private boolean isStableTerrainMaterial(Material material) {
            return material == Material.STONE ||
                    material == Material.DEEPSLATE ||
                    material == Material.COBBLESTONE ||
                    material == Material.COBBLED_DEEPSLATE ||
                    material == Material.BLACKSTONE ||
                    material == Material.MAGMA_BLOCK ||
                    material == Material.BEDROCK ||
                    material == Material.DIRT ||
                    material == Material.GRASS_BLOCK ||
                    material == Material.ANDESITE ||
                    material == Material.GRANITE ||
                    material == Material.DIORITE ||
                    material.name().contains("_ORE");
        }

        private void createInstantDrillingCrater(Location impactPoint) {
            World world = impactPoint.getWorld();
            if (world == null) return;
            Vector direction = flightDirection.clone().normalize();
            int totalDepth = meteorSize * 2 + 8;
            double maxRadius = meteorSize * 1.2;
            new BukkitRunnable() {
                int currentDepth = 0;
                double rotationAngle = 0;
                boolean craterComplete = false;
                @Override
                public void run() {
                    if (currentDepth >= totalDepth) {
                        if (!craterComplete) {
                            placeMeteorWithCrust(impactPoint, direction, totalDepth);
                            craterComplete = true;
                        }
                        cancel();
                        return;
                    }
                    for (int layer = 0; layer < 4 && currentDepth < totalDepth; layer++) {
                        double depthRatio = (double) currentDepth / totalDepth;
                        double currentRadius = maxRadius * (1.0 - depthRatio * 0.7);
                        if (currentRadius < 1.0) currentRadius = 1.0;
                        drillCraterLayer(impactPoint, direction, currentDepth, currentRadius, rotationAngle);
                        currentDepth++;
                        rotationAngle += 0.15;
                    }
                    if (currentDepth % 6 == 0) {
                        Location drillPoint = impactPoint.clone().add(direction.clone().multiply(currentDepth * 0.8));
                        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, drillPoint, 2, 0.4, 0.4, 0.4, 0.01);
                        for (Player player : world.getPlayers()) {
                            if (player.getLocation().distance(drillPoint) <= 20) {
                                player.playSound(drillPoint, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.AMBIENT, 0.15f, 1.9f);
                            }
                        }
                    }
                }
            }.runTaskTimer(WorldScrolls.getInstance(), 3L, 1L);
        }

        private void drillCraterLayer(Location impactPoint, Vector direction, int depth, double radius, double rotation) {
            World world = impactPoint.getWorld();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location layerCenter = impactPoint.clone().add(direction.clone().multiply(depth * 0.8));
            if (depth < 4) {
                addCrustAndScorchedMaterials(layerCenter, radius * 1.8, depth);
            }
            Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
            Vector up = direction.clone().crossProduct(right).normalize();
            int numPoints = Math.max(12, (int)(radius * 4));
            for (int i = 0; i < numPoints; i++) {
                double angle = (2.0 * Math.PI * i / numPoints) + rotation;
                for (double r = 0; r <= radius; r += 0.5) {
                    double localX = Math.cos(angle) * r;
                    double localY = Math.sin(angle) * r;
                    Vector offset = right.clone().multiply(localX).add(up.clone().multiply(localY));
                    Location blockLoc = layerCenter.clone().add(offset);
                    Block block = world.getBlockAt(blockLoc);
                    if (block.getType() != Material.BEDROCK && block.getType().isSolid()) {
                        if (random.nextDouble() < 0.4) {
                            world.spawnParticle(Particle.BLOCK_CRACK, blockLoc.add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0, block.getBlockData());
                        }
                        if (depth <= 2 && random.nextDouble() < 0.2) {
                            Material scorchedMaterial = SCORCHED_MATERIALS[random.nextInt(SCORCHED_MATERIALS.length)];
                            block.setType(scorchedMaterial, false);
                        } else {
                            block.setType(Material.AIR, false);
                        }
                    }
                }
            }
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    Vector offset = right.clone().multiply(x * 0.5).add(up.clone().multiply(y * 0.5));
                    Location centerBlockLoc = layerCenter.clone().add(offset);
                    Block centerBlock = world.getBlockAt(centerBlockLoc);
                    if (centerBlock.getType() != Material.BEDROCK && centerBlock.getType().isSolid()) {
                        if (depth <= 3 && random.nextDouble() < 0.3) {
                            Material scorchedMaterial = SCORCHED_MATERIALS[random.nextInt(SCORCHED_MATERIALS.length)];
                            centerBlock.setType(scorchedMaterial, false);
                        } else {
                            centerBlock.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }

        private void addCrustAndScorchedMaterials(Location center, double radius, int depth) {
            World world = center.getWorld();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (double angle = 0; angle < 2 * Math.PI; angle += 0.3) {
                for (double r = radius * 0.6; r <= radius * 1.2; r += 0.5) {
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    for (int y = -1; y <= 1; y++) {
                        Location loc = center.clone().add(x, y + random.nextInt(2), z);
                        Block block = world.getBlockAt(loc);
                        if (block.getType().isSolid() && block.getType() != Material.BEDROCK) {
                            double placementChance = depth == 0 ? 0.4 : 0.25;
                            if (random.nextDouble() < placementChance) {
                                if (random.nextDouble() < 0.6) {
                                    Material scorchedMaterial = SCORCHED_MATERIALS[random.nextInt(SCORCHED_MATERIALS.length)];
                                    block.setType(scorchedMaterial, false);
                                } else {
                                    Material crustMaterial = METEOR_CRUST_MATERIALS[random.nextInt(METEOR_CRUST_MATERIALS.length)];
                                    block.setType(crustMaterial, false);
                                }
                            }
                        }
                    }
                }
            }
        }

        private void placeMeteorWithCrust(Location impactPoint, Vector direction, int depth) {
            World world = impactPoint.getWorld();
            Location meteorCenter = impactPoint.clone().add(direction.clone().multiply(depth * 0.8));

            double yaw = Math.atan2(-direction.getX(), direction.getZ());
            double pitch = Math.atan2(-direction.getY(), Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ()));

            ThreadLocalRandom random = ThreadLocalRandom.current();
            double crustRadius = meteorSize * 0.6;

            for (double x = -crustRadius; x <= crustRadius; x += 0.8) {
                for (double y = -crustRadius; y <= crustRadius; y += 0.8) {
                    for (double z = -crustRadius; z <= crustRadius; z += 0.8) {
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance >= crustRadius * 0.4 && distance <= crustRadius && random.nextDouble() < 0.7) {
                            Vector offset = new Vector(x, y, z);
                            Vector rotatedOffset = rotateOffset(offset, yaw, pitch);
                            Location blockLoc = meteorCenter.clone().add(rotatedOffset);
                            Block block = world.getBlockAt(blockLoc);
                            if (block.getType() != Material.BEDROCK && block.getType().isAir()) {
                                Material crustMaterial = METEOR_CRUST_MATERIALS[random.nextInt(METEOR_CRUST_MATERIALS.length)];
                                block.setType(crustMaterial, false);
                            }
                        }
                    }
                }
            }
        }

        private void createMinimalScorchedArea(Location center) {
            World world = center.getWorld();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int radius = Math.max(3, meteorSize / 2);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x*x + z*z);
                    if (distance <= radius) {
                        double placementChance = distance <= radius * 0.5 ? 0.15 : 0.05;
                        if (random.nextDouble() < placementChance) {
                            int groundY = world.getHighestBlockYAt(center.getBlockX() + x, center.getBlockZ() + z);
                            for (int y = 0; y >= -1; y--) {
                                Location blockLoc = new Location(world, center.getBlockX() + x, groundY + y, center.getBlockZ() + z);
                                Block block = blockLoc.getBlock();
                                if (block.getType().isSolid() && block.getType() != Material.BEDROCK) {
                                    double replaceChance = y == 0 ? 0.6 : 0.3;
                                    if (random.nextDouble() < replaceChance) {
                                        Material scorchedMaterial;
                                        if (distance <= radius * 0.3) {
                                            double rand = random.nextDouble();
                                            if (rand < 0.3) scorchedMaterial = Material.MAGMA_BLOCK;
                                            else if (rand < 0.6) scorchedMaterial = Material.DEEPSLATE;
                                            else scorchedMaterial = Material.COBBLESTONE;
                                        } else {
                                            scorchedMaterial = random.nextDouble() < 0.7 ? Material.COBBLESTONE : Material.DEEPSLATE;
                                        }
                                        block.setType(scorchedMaterial, false);
                                    }
                                }
                            }
                        }
                        if (random.nextDouble() < 0.08 && distance <= radius * 0.6) {
                            int groundY = world.getHighestBlockYAt(center.getBlockX() + x, center.getBlockZ() + z);
                            Location fireLoc = new Location(world, center.getBlockX() + x, groundY + 1, center.getBlockZ() + z);
                            Block fireBlock = world.getBlockAt(fireLoc);
                            Block belowFire = world.getBlockAt(center.getBlockX() + x, groundY, center.getBlockZ() + z);
                            if (fireBlock.getType().isAir() && belowFire.getType().isSolid()) {
                                fireBlock.setType(Material.FIRE, false);
                                int burnTime = 200 + random.nextInt(400);
                                Bukkit.getScheduler().runTaskLater(WorldScrolls.getInstance(), () -> {
                                    if (fireBlock.getType() == Material.FIRE) {
                                        Block supportBlock = world.getBlockAt(fireBlock.getLocation().subtract(0, 1, 0));
                                        if (!supportBlock.getType().isSolid()) {
                                            fireBlock.setType(Material.AIR, false);
                                            return;
                                        }
                                        if (random.nextDouble() < 0.5) return;
                                        fireBlock.setType(Material.AIR, false);
                                    }
                                }, burnTime);
                            }
                        }
                    }
                }
            }
            int fireRadius = radius + 2;
            for (int x = -fireRadius; x <= fireRadius; x++) {
                for (int z = -fireRadius; z <= fireRadius; z++) {
                    double distance = Math.sqrt(x*x + z*z);
                    if (distance > radius && distance <= fireRadius && random.nextDouble() < 0.04) {
                        int groundY = world.getHighestBlockYAt(center.getBlockX() + x, center.getBlockZ() + z);
                        Location groundLoc = new Location(world, center.getBlockX() + x, groundY, center.getBlockZ() + z);
                        Block groundBlock = world.getBlockAt(groundLoc);
                        if (groundBlock.getType().isSolid() && random.nextDouble() < 0.2) {
                            Material scorchedMaterial = random.nextDouble() < 0.8 ? Material.COBBLESTONE : Material.DEEPSLATE;
                            groundBlock.setType(scorchedMaterial, false);
                        }
                        if (groundBlock.getType().isSolid()) {
                            Location fireLoc = groundLoc.clone().add(0, 1, 0);
                            Block fireBlock = world.getBlockAt(fireLoc);
                            if (fireBlock.getType().isAir()) {
                                boolean nearLeaves = checkNearbyVegetation(fireLoc);
                                if (nearLeaves || random.nextDouble() < 0.3) {
                                    fireBlock.setType(Material.FIRE, false);
                                    Bukkit.getScheduler().runTaskLater(WorldScrolls.getInstance(), () -> {
                                        Block supportCheck = world.getBlockAt(fireBlock.getLocation().subtract(0, 1, 0));
                                        if (!supportCheck.getType().isSolid()) {
                                            if (fireBlock.getType() == Material.FIRE) {
                                                fireBlock.setType(Material.AIR, false);
                                            }
                                        }
                                    }, 20L);
                                }
                            }
                        }
                    }
                }
            }
        }

        private boolean checkNearbyVegetation(Location fireLoc) {
            World world = fireLoc.getWorld();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        Block nearby = world.getBlockAt(fireLoc.clone().add(dx, dy, dz));
                        if (nearby.getType().name().contains("LEAVES") ||
                                nearby.getType().name().contains("LOG") ||
                                nearby.getType().name().contains("WOOD")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private Vector rotateOffset(Vector o, double yaw, double pitch) {
            double cy = Math.cos(yaw), sy = Math.sin(yaw);
            double x1 = o.getX() * cy - o.getZ() * sy;
            double z1 = o.getX() * sy + o.getZ() * cy;
            double cp = Math.cos(pitch), sp = Math.sin(pitch);
            double y2 = o.getY() * cp - z1 * sp;
            double z2 = o.getY() * sp + z1 * cp;
            return new Vector(x1, y2, z2);
        }


        @Override
        public void cancel() {
            super.cancel();
            try {
                clearAllFakeBlocks();
            } catch (Exception ignored) {}
            activeMeteors.remove(this.meteorId);
        }

        private List<MeteorVoxel> generateMeteorVoxels(int size, UUID seed, MeteorShape shape) {
            long s = seed.getMostSignificantBits() ^ seed.getLeastSignificantBits();
            Random random = new Random(s);
            switch (shape) {
                case SPHERICAL:
                    return generateSphericalMeteor(size, random);
                case ARROW:
                    return generateArrowMeteor(size, random);
                case IRREGULAR:
                    return generateIrregularMeteor(size, random);
                case TAILED:
                    return generateTailedMeteor(size, random);
                case COMPLEX:
                    return generateComplexMeteor(size, random);
                default:
                    return generateSphericalMeteor(size, random);
            }
        }

        private List<MeteorVoxel> generateSphericalMeteor(int size, Random random) {
            List<MeteorVoxel> voxels = new ArrayList<>();
            double rx = size * (0.55 + random.nextDouble() * 0.25);
            double ry = size * (0.50 + random.nextDouble() * 0.25);
            double rz = size * (0.55 + random.nextDouble() * 0.25);
            int maxX = (int) Math.ceil(rx) + 1;
            int maxY = (int) Math.ceil(ry) + 1;
            int maxZ = (int) Math.ceil(rz) + 1;
            for (int x = -maxX; x <= maxX; x++) {
                for (int y = -maxY; y <= maxY; y++) {
                    for (int z = -maxZ; z <= maxZ; z++) {
                        double nx = x / rx;
                        double ny = y / ry;
                        double nz = z / rz;
                        double distance = nx * nx + ny * ny + nz * nz;
                        double noise = (random.nextDouble() * 2 - 1) * 0.12;
                        if (distance <= 1.0 + noise && random.nextDouble() < 0.85) {
                            Material material = METEOR_MATERIALS[random.nextInt(METEOR_MATERIALS.length)];
                            voxels.add(new MeteorVoxel(new Vector(x, y, z), WrappedBlockData.createData(material.createBlockData())));
                        }
                    }
                }
            }
            return voxels;
        }

        private List<MeteorVoxel> generateArrowMeteor(int size, Random random) {
            List<MeteorVoxel> voxels = new ArrayList<>();
            double rx = size * (0.35 + random.nextDouble() * 0.2);
            double ry = size * (0.35 + random.nextDouble() * 0.2);
            double rz = size * (1.0 + random.nextDouble() * 0.5);
            int maxX = (int) Math.ceil(rx) + 1;
            int maxY = (int) Math.ceil(ry) + 1;
            int maxZ = (int) Math.ceil(rz) + 1;
            for (int x = -maxX; x <= maxX; x++) {
                for (int y = -maxY; y <= maxY; y++) {
                    for (int z = -maxZ; z <= maxZ; z++) {
                        double tapering = z < 0 ? 1.0 + (z / rz) * 0.8 : 1.0;
                        double nx = x / (rx * tapering);
                        double ny = y / (ry * tapering);
                        double nz = z / rz;
                        double distance = nx * nx + ny * ny + nz * nz;
                        double noise = (random.nextDouble() * 2 - 1) * 0.1;
                        if (distance <= 1.0 + noise && random.nextDouble() < 0.82) {
                            Material material = METEOR_MATERIALS[random.nextInt(METEOR_MATERIALS.length)];
                            voxels.add(new MeteorVoxel(new Vector(x, y, z), WrappedBlockData.createData(material.createBlockData())));
                        }
                    }
                }
            }
            return voxels;
        }

        private List<MeteorVoxel> generateIrregularMeteor(int size, Random random) {
            List<MeteorVoxel> voxels = new ArrayList<>();
            int numChunks = 2 + random.nextInt(3);
            for (int chunk = 0; chunk < numChunks; chunk++) {
                Vector chunkCenter = new Vector(random.nextDouble(-size * 0.6, size * 0.6), random.nextDouble(-size * 0.6, size * 0.6), random.nextDouble(-size * 0.6, size * 0.6));
                double chunkSize = size * (0.3 + random.nextDouble() * 0.4);
                double rx = chunkSize * (0.5 + random.nextDouble() * 0.5);
                double ry = chunkSize * (0.5 + random.nextDouble() * 0.5);
                double rz = chunkSize * (0.5 + random.nextDouble() * 0.5);
                int maxX = (int) Math.ceil(rx + Math.abs(chunkCenter.getX())) + 1;
                int maxY = (int) Math.ceil(ry + Math.abs(chunkCenter.getY())) + 1;
                int maxZ = (int) Math.ceil(rz + Math.abs(chunkCenter.getZ())) + 1;
                for (int x = -maxX; x <= maxX; x++) {
                    for (int y = -maxY; y <= maxY; y++) {
                        for (int z = -maxZ; z <= maxZ; z++) {
                            Vector pos = new Vector(x, y, z).subtract(chunkCenter);
                            double nx = pos.getX() / rx;
                            double ny = pos.getY() / ry;
                            double nz = pos.getZ() / rz;
                            double distance = nx * nx + ny * ny + nz * nz;
                            double noise = (random.nextDouble() * 2 - 1) * 0.25;
                            if (distance <= 1.0 + noise && random.nextDouble() < 0.75) {
                                Material material = METEOR_MATERIALS[random.nextInt(METEOR_MATERIALS.length)];
                                voxels.add(new MeteorVoxel(new Vector(x, y, z), WrappedBlockData.createData(material.createBlockData())));
                            }
                        }
                    }
                }
            }
            return voxels;
        }

        private List<MeteorVoxel> generateTailedMeteor(int size, Random random) {
            List<MeteorVoxel> voxels = new ArrayList<>();
            double headSize = size * 0.8;
            double rx = headSize * (0.55 + random.nextDouble() * 0.25);
            double ry = headSize * (0.50 + random.nextDouble() * 0.25);
            double rz = headSize * (0.35 + random.nextDouble() * 0.25);
            int maxX = (int) Math.ceil(rx) + 1;
            int maxY = (int) Math.ceil(ry) + 1;
            int maxZ = (int) Math.ceil(rz) + 1;
            for (int x = -maxX; x <= maxX; x++) {
                for (int y = -maxY; y <= maxY; y++) {
                    for (int z = -maxZ; z <= maxZ; z++) {
                        double nx = x / rx;
                        double ny = y / ry;
                        double nz = z / rz;
                        double distance = nx * nx + ny * ny + nz * nz;
                        if (distance <= 1.0 && random.nextDouble() < 0.88) {
                            Material material = METEOR_MATERIALS[random.nextInt(METEOR_MATERIALS.length)];
                            voxels.add(new MeteorVoxel(new Vector(x, y, z), WrappedBlockData.createData(material.createBlockData())));
                        }
                    }
                }
            }
            int tailLength = Math.max(3, size + random.nextInt(size));
            for (int t = 1; t <= tailLength; t++) {
                double tailFactor = 1.0 - (double) t / tailLength;
                double tailRadius = Math.max(1, rx * tailFactor * 0.6);
                for (int x = (int) -tailRadius; x <= tailRadius; x++) {
                    for (int y = (int) -tailRadius; y <= tailRadius; y++) {
                        double distance = Math.sqrt(x * x + y * y);
                        if (distance <= tailRadius && random.nextDouble() < 0.4 * tailFactor) {
                            Material material = METEOR_MATERIALS[random.nextInt(METEOR_MATERIALS.length)];
                            voxels.add(new MeteorVoxel(new Vector(x, y, maxZ + t), WrappedBlockData.createData(material.createBlockData())));
                        }
                    }
                }
            }
            return voxels;
        }

        private List<MeteorVoxel> generateComplexMeteor(int size, Random random) {
            List<MeteorVoxel> voxels = new ArrayList<>();
            voxels.addAll(generateSphericalMeteor(size, random));
            int numProtrusions = 2 + random.nextInt(3);
            for (int i = 0; i < numProtrusions; i++) {
                double angle1 = random.nextDouble() * 2 * Math.PI;
                double angle2 = random.nextDouble() * Math.PI;
                Vector direction = new Vector(Math.cos(angle1) * Math.sin(angle2), Math.cos(angle2), Math.sin(angle1) * Math.sin(angle2)).normalize();
                double protrusionSize = size * (0.3 + random.nextDouble() * 0.4);
                int protrusionLength = (int) (protrusionSize * (1.0 + random.nextDouble()));
                for (int j = 1; j <= protrusionLength; j++) {
                    double shrinkFactor = 1.0 - (double) j / protrusionLength * 0.8;
                    double radius = Math.max(1, protrusionSize * 0.3 * shrinkFactor);
                    Vector basePos = direction.clone().multiply(size * 0.7 + j);
                    for (int x = (int) -radius; x <= radius; x++) {
                        for (int y = (int) -radius; y <= radius; y++) {
                            for (int z = (int) -radius; z <= radius; z++) {
                                double distance = Math.sqrt(x * x + y * y + z * z);
                                if (distance <= radius && random.nextDouble() < 0.7) {
                                    Vector finalPos = basePos.clone().add(new Vector(x, y, z));
                                    Material material = METEOR_MATERIALS[random.nextInt(METEOR_MATERIALS.length)];
                                    voxels.add(new MeteorVoxel(finalPos, WrappedBlockData.createData(material.createBlockData())));
                                }
                            }
                        }
                    }
                }
            }
            return voxels;
        }
    }

    private enum MeteorShape {
        SPHERICAL, ARROW, IRREGULAR, TAILED, COMPLEX
    }
}
