package dev.rtpplus.rtp;

import dev.rtpplus.config.RtpPlusConfig;
import dev.rtpplus.config.WorldSettings;
import dev.rtpplus.util.DurationFormatter;
import dev.rtpplus.util.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpService {
    private final JavaPlugin plugin;
    private RtpPlusConfig config;
    private final Queue<RtpRequest> queue = new ArrayDeque<>();
    private final Map<UUID, RtpRequest> requests = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Set<UUID> activeSearches = new HashSet<>();
    private final RtpStats stats = new RtpStats();

    public RtpService(JavaPlugin plugin, RtpPlusConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void reload(RtpPlusConfig config) {
        this.config = config;
    }

    public RtpPlusConfig config() {
        return config;
    }

    public RtpStats stats() {
        return stats;
    }

    public int queuedCount() {
        return queue.size();
    }

    public int activeCount() {
        return requests.size();
    }

    public int searchingCount() {
        return activeSearches.size();
    }

    public boolean hasRequest(UUID uuid) {
        return requests.containsKey(uuid);
    }

    public void request(Player player, String worldName, boolean adminTest) {
        if (requests.containsKey(player.getUniqueId())) {
            send(player, "already-searching", Map.of());
            return;
        }
        WorldSettings settings = resolveWorld(player, worldName);
        if (settings == null || !settings.enabled() || Bukkit.getWorld(settings.worldName()) == null) {
            send(player, "invalid-world", Map.of());
            return;
        }
        if (!adminTest && !player.hasPermission("rtpplus.cooldown.bypass")) {
            long remaining = cooldownRemaining(player.getUniqueId());
            if (remaining > 0L) {
                send(player, "cooldown", Map.of("time", DurationFormatter.compact(remaining)));
                return;
            }
        }

        RtpRequest request = new RtpRequest(player.getUniqueId(), settings, player.getLocation().clone(),
                System.currentTimeMillis(), adminTest);
        requests.put(player.getUniqueId(), request);
        queue.add(request);
        send(player, "queued", Map.of(
                "world", settings.worldName(),
                "position", Integer.toString(queue.size())
        ));
        pumpQueue();
    }

    public boolean cancel(UUID uuid, String messageKey) {
        RtpRequest request = requests.remove(uuid);
        if (request == null) {
            return false;
        }
        queue.remove(request);
        activeSearches.remove(uuid);
        request.status(RtpStatus.CANCELLED);
        if (request.warmupTask() != null) {
            request.warmupTask().cancel();
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && messageKey != null) {
            send(player, messageKey, Map.of());
            player.playSound(player.getLocation(), config.sound("sound-fail", Sound.BLOCK_NOTE_BLOCK_BASS), 0.8F, 0.8F);
        }
        pumpQueue();
        return true;
    }

    public void clearCooldown(UUID uuid) {
        cooldownUntil.remove(uuid);
    }

    public void shutdown() {
        for (RtpRequest request : new ArrayList<>(requests.values())) {
            if (request.warmupTask() != null) {
                request.warmupTask().cancel();
            }
            request.status(RtpStatus.CANCELLED);
        }
        queue.clear();
        activeSearches.clear();
        requests.clear();
    }

    public long cooldownRemaining(UUID uuid) {
        return Math.max(0L, cooldownUntil.getOrDefault(uuid, 0L) - System.currentTimeMillis());
    }

    public void handleMove(Player player, Location to) {
        if (!config.cancelOnMove()) {
            return;
        }
        RtpRequest request = requests.get(player.getUniqueId());
        if (request == null || request.status() != RtpStatus.WARMUP) {
            return;
        }
        Location start = request.startLocation();
        if (start.getWorld() == null || to.getWorld() == null || !start.getWorld().equals(to.getWorld())) {
            cancel(player.getUniqueId(), "cancelled-move");
            return;
        }
        double threshold = config.movementThresholdBlocks();
        if (Math.abs(start.getX() - to.getX()) > threshold
                || Math.abs(start.getY() - to.getY()) > threshold
                || Math.abs(start.getZ() - to.getZ()) > threshold) {
            cancel(player.getUniqueId(), "cancelled-move");
        }
    }

    public void handleDamage(Player player) {
        if (config.cancelOnDamage()) {
            cancel(player.getUniqueId(), "cancelled-damage");
        }
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String rendered = Text.color(config.prefix() + Text.render(config.message(key), placeholders));
        for (String line : rendered.split("\\R", -1)) {
            sender.sendMessage(line);
        }
    }

    public Optional<WorldSettings> worldSettings(String name) {
        return Optional.ofNullable(config.world(name));
    }

    private WorldSettings resolveWorld(Player player, String worldName) {
        if (worldName == null || worldName.isBlank()) {
            WorldSettings current = config.world(player.getWorld().getName());
            if (current != null && current.enabled()) {
                return current;
            }
            return config.defaultWorld();
        }
        return config.world(worldName);
    }

    private void pumpQueue() {
        while (activeSearches.size() < config.concurrentSearches() && !queue.isEmpty()) {
            RtpRequest request = queue.poll();
            Player player = Bukkit.getPlayer(request.playerUuid());
            if (player == null || !player.isOnline() || !requests.containsKey(request.playerUuid())) {
                requests.remove(request.playerUuid());
                continue;
            }
            long warmup = request.adminTest() || player.hasPermission("rtpplus.warmup.bypass") ? 0L : config.warmupMillis();
            if (warmup > 0L) {
                beginWarmup(player, request, warmup);
            } else {
                beginSearch(player, request);
            }
        }
    }

    private void beginWarmup(Player player, RtpRequest request, long warmupMillis) {
        request.status(RtpStatus.WARMUP);
        send(player, "warmup", Map.of("seconds", Long.toString(Math.max(1L, warmupMillis / 1000L))));
        player.playSound(player.getLocation(), config.sound("sound-warmup", Sound.BLOCK_NOTE_BLOCK_PLING), 0.8F, 1.2F);
        request.warmupTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (requests.get(player.getUniqueId()) == request) {
                beginSearch(player, request);
            }
        }, Math.max(1L, warmupMillis / 50L)));
    }

    private void beginSearch(Player player, RtpRequest request) {
        request.status(RtpStatus.SEARCHING);
        activeSearches.add(request.playerUuid());
        stats.searched();
        send(player, "searching", Map.of("world", request.settings().worldName()));
        searchNext(request, 0, 0, System.currentTimeMillis());
    }

    private void searchNext(RtpRequest request, int attempts, int loadAttempts, long startedAt) {
        Player player = Bukkit.getPlayer(request.playerUuid());
        if (player == null || !player.isOnline() || requests.get(request.playerUuid()) != request) {
            finish(request, RtpResult.failed(attempts));
            return;
        }
        if (attempts >= config.maxAttemptsPerRequest()
                || System.currentTimeMillis() - startedAt > config.searchTimeoutMillis()) {
            finish(request, RtpResult.failed(attempts));
            return;
        }
        World world = Bukkit.getWorld(request.settings().worldName());
        if (world == null) {
            finish(request, RtpResult.failed(attempts));
            return;
        }

        Candidate candidate = candidate(world, request.settings(), attempts);
        boolean generate = !config.preferLoadedChunks() || loadAttempts >= config.loadedChunkAttempts()
                || world.isChunkLoaded(candidate.chunkX(), candidate.chunkZ());
        if (config.asyncChunkLoad()) {
            CompletableFuture<Chunk> future = world.getChunkAtAsync(candidate.chunkX(), candidate.chunkZ(), generate);
            future.whenComplete((chunk, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || chunk == null) {
                    searchNext(request, attempts, loadAttempts + 1, startedAt);
                    return;
                }
                validateCandidate(request, world, candidate, attempts + 1, loadAttempts + 1, startedAt);
            }));
            return;
        }

        if (!generate && !world.isChunkLoaded(candidate.chunkX(), candidate.chunkZ())) {
            searchNext(request, attempts, loadAttempts + 1, startedAt);
            return;
        }
        world.getChunkAt(candidate.chunkX(), candidate.chunkZ());
        validateCandidate(request, world, candidate, attempts + 1, loadAttempts + 1, startedAt);
    }

    private void validateCandidate(RtpRequest request, World world, Candidate candidate, int attempts,
                                   int loadAttempts, long startedAt) {
        Optional<Location> safe = safeLocation(world, candidate.x(), candidate.z());
        if (safe.isPresent()) {
            request.attempts(attempts);
            finish(request, RtpResult.success(safe.get(), attempts));
            return;
        }
        searchNext(request, attempts, loadAttempts, startedAt);
    }

    private Optional<Location> safeLocation(World world, int x, int z) {
        int minY = Math.max(world.getMinHeight() + 1, config.minY());
        int maxY = Math.min(world.getMaxHeight() - 2, config.maxY());
        if (maxY < minY) {
            return Optional.empty();
        }
        int heightmapY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int startY = Math.min(maxY, Math.max(minY, heightmapY + 2));
        int lowestY = Math.max(minY, startY - config.surfaceScanDepth());
        for (int candidateY = startY; candidateY >= lowestY; candidateY--) {
            Block ground = world.getBlockAt(x, candidateY - 1, z);
            Block feet = world.getBlockAt(x, candidateY, z);
            Block head = world.getBlockAt(x, candidateY + 1, z);
            if (safe(world, ground, feet, head)) {
                return Optional.of(new Location(world, x + 0.5D, candidateY, z + 0.5D));
            }
        }
        return Optional.empty();
    }

    private boolean safe(World world, Block ground, Block feet, Block head) {
        if (world.getEnvironment() == World.Environment.NETHER && !config.allowNetherRoof()
                && ground.getY() >= world.getMaxHeight() - 8) {
            return false;
        }
        Material groundType = ground.getType();
        if (config.requireSolidGround() && !groundType.isSolid()) {
            return false;
        }
        if (config.unsafeGround(groundType) || config.unsafeSpace(feet.getType()) || config.unsafeSpace(head.getType())) {
            return false;
        }
        if (config.requireTwoAirBlocks() && (!feet.isPassable() || !head.isPassable())) {
            return false;
        }
        if (config.avoidCaves() && world.getEnvironment() == World.Environment.NORMAL && feet.getLightFromSky() <= 0) {
            return false;
        }
        if (config.minSkyLight() > 0 && feet.getLightFromSky() < config.minSkyLight()) {
            return false;
        }
        Biome biome = feet.getBiome();
        return !config.blacklistedBiome(biome);
    }

    private void finish(RtpRequest request, RtpResult result) {
        requests.remove(request.playerUuid());
        activeSearches.remove(request.playerUuid());
        Player player = Bukkit.getPlayer(request.playerUuid());
        if (player == null || !player.isOnline()) {
            pumpQueue();
            return;
        }
        if (!result.success()) {
            request.status(RtpStatus.FAILED);
            stats.recordFailed();
            send(player, "failed", Map.of("attempts", Integer.toString(result.attempts())));
            player.playSound(player.getLocation(), config.sound("sound-fail", Sound.BLOCK_NOTE_BLOCK_BASS), 0.8F, 0.8F);
            pumpQueue();
            return;
        }
        request.status(RtpStatus.TELEPORTING);
        Runnable success = () -> {
            request.status(RtpStatus.COMPLETE);
            stats.recordSuccess();
            if (!request.adminTest()) {
                cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + config.cooldownMillis());
            }
            Location location = result.location();
            send(player, "success", Map.of(
                    "world", location.getWorld().getName(),
                    "x", Integer.toString(location.getBlockX()),
                    "y", Integer.toString(location.getBlockY()),
                    "z", Integer.toString(location.getBlockZ())
            ));
            player.playSound(player.getLocation(), config.sound("sound-success", Sound.ENTITY_ENDERMAN_TELEPORT), 1.0F, 1.0F);
            if (config.particlesEnabled()) {
                player.getWorld().spawnParticle(config.particle(), player.getLocation().add(0.0D, 0.8D, 0.0D),
                        config.particleCount(), 0.4D, 0.5D, 0.4D, 0.05D);
            }
            pumpQueue();
        };
        if (config.useTeleportAsync()) {
            player.teleportAsync(result.location()).thenAccept(teleported ->
                    Bukkit.getScheduler().runTask(plugin, teleported ? success : () -> {
                        stats.recordFailed();
                        send(player, "failed", Map.of("attempts", Integer.toString(result.attempts())));
                        pumpQueue();
                    }));
        } else {
            if (player.teleport(result.location())) {
                success.run();
            } else {
                stats.recordFailed();
                send(player, "failed", Map.of("attempts", Integer.toString(result.attempts())));
                pumpQueue();
            }
        }
    }

    private Candidate candidate(World world, WorldSettings settings, int attempts) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int centerX = centerX(world, settings);
        int centerZ = centerZ(world, settings);
        SearchRadius radius = effectiveRadius(world, settings, centerX, centerZ);
        int x;
        int z;
        if (settings.squareSearch()) {
            int distance = random.nextInt(radius.min(), radius.max() + 1);
            x = centerX + random.nextInt(-distance, distance + 1);
            z = centerZ + random.nextInt(-distance, distance + 1);
        } else {
            double angle = random.nextDouble(Math.PI * 2.0D);
            double distance = Math.sqrt(random.nextDouble(
                    radius.min() * (double) radius.min(),
                    radius.max() * (double) radius.max()));
            x = centerX + (int) Math.round(Math.cos(angle) * distance);
            z = centerZ + (int) Math.round(Math.sin(angle) * distance);
        }
        if (settings.respectWorldBorder()) {
            int[] clamped = clampToWorldBorder(world, x, z);
            x = clamped[0];
            z = clamped[1];
        }
        return new Candidate(x, z, x >> 4, z >> 4);
    }

    private SearchRadius effectiveRadius(World world, WorldSettings settings, int centerX, int centerZ) {
        int min = settings.minRadius();
        int max = settings.maxRadius();
        if (!settings.respectWorldBorder()) {
            return new SearchRadius(min, max);
        }
        WorldBorder border = world.getWorldBorder();
        Location borderCenter = border.getCenter();
        double halfSize = Math.max(1.0D, border.getSize() / 2.0D - 8.0D);
        int borderMinX = (int) Math.ceil(borderCenter.getX() - halfSize);
        int borderMaxX = (int) Math.floor(borderCenter.getX() + halfSize);
        int borderMinZ = (int) Math.ceil(borderCenter.getZ() - halfSize);
        int borderMaxZ = (int) Math.floor(borderCenter.getZ() + halfSize);
        int available = Math.max(1, Math.min(
                Math.min(Math.abs(centerX - borderMinX), Math.abs(borderMaxX - centerX)),
                Math.min(Math.abs(centerZ - borderMinZ), Math.abs(borderMaxZ - centerZ))
        ));
        max = Math.min(max, available);
        if (max < min) {
            min = Math.max(0, Math.min(max / 2, max));
        }
        return new SearchRadius(Math.max(0, min), Math.max(1, max));
    }

    private int centerX(World world, WorldSettings settings) {
        if ("configured".equals(settings.centerMode())) {
            return settings.centerX();
        }
        return world.getSpawnLocation().getBlockX();
    }

    private int centerZ(World world, WorldSettings settings) {
        if ("configured".equals(settings.centerMode())) {
            return settings.centerZ();
        }
        return world.getSpawnLocation().getBlockZ();
    }

    private int[] clampToWorldBorder(World world, int x, int z) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = Math.max(1.0D, border.getSize() / 2.0D - 2.0D);
        int minX = (int) Math.ceil(center.getX() - halfSize);
        int maxX = (int) Math.floor(center.getX() + halfSize);
        int minZ = (int) Math.ceil(center.getZ() - halfSize);
        int maxZ = (int) Math.floor(center.getZ() + halfSize);
        return new int[] {
                Math.max(minX, Math.min(maxX, x)),
                Math.max(minZ, Math.min(maxZ, z))
        };
    }

    private record Candidate(int x, int z, int chunkX, int chunkZ) {
    }

    private record SearchRadius(int min, int max) {
    }
}
