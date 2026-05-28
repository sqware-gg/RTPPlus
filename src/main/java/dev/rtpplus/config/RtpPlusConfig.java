package dev.rtpplus.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpPlusConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration defaultConfig;
    private Map<String, WorldSettings> worlds = new LinkedHashMap<>();
    private Set<Material> unsafeGround = EnumSet.noneOf(Material.class);
    private Set<Material> unsafeSpace = EnumSet.noneOf(Material.class);
    private Set<Biome> blacklistedBiomes = new HashSet<>();

    public RtpPlusConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        defaultConfig = loadBundledConfig();
        worlds = loadWorlds(config.getConfigurationSection("worlds"));
        unsafeGround = loadMaterials("safety.unsafe-ground");
        unsafeSpace = loadMaterials("safety.unsafe-space");
        blacklistedBiomes = loadBiomes("safety.blacklist-biomes");
    }

    public Map<String, WorldSettings> worlds() {
        return worlds;
    }

    public WorldSettings world(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return worlds.get(name.toLowerCase(Locale.ROOT));
    }

    public WorldSettings defaultWorld() {
        String configured = config.getString("teleport.default-world", "");
        if (configured != null && !configured.isBlank()) {
            WorldSettings settings = world(configured);
            if (settings != null && settings.enabled()) {
                return settings;
            }
        }
        return worlds.values().stream()
                .filter(WorldSettings::enabled)
                .findFirst()
                .orElse(null);
    }

    public long warmupMillis() {
        return seconds("teleport.warmup-seconds", 5L) * 1000L;
    }

    public long cooldownMillis() {
        return seconds("teleport.cooldown-seconds", 300L) * 1000L;
    }

    public boolean cancelOnMove() {
        return config.getBoolean("teleport.cancel-on-move", true);
    }

    public boolean cancelOnDamage() {
        return config.getBoolean("teleport.cancel-on-damage", true);
    }

    public double movementThresholdBlocks() {
        return Math.max(0.0D, config.getDouble("teleport.movement-threshold-blocks", 0.35D));
    }

    public boolean useTeleportAsync() {
        return config.getBoolean("teleport.use-teleport-async", true);
    }

    public int minY() {
        return config.getInt("teleport.min-y", -64);
    }

    public int maxY() {
        return config.getInt("teleport.max-y", 320);
    }

    public int concurrentSearches() {
        return Math.max(1, config.getInt("performance.concurrent-searches", 2));
    }

    public int maxAttemptsPerRequest() {
        return Math.max(1, config.getInt("performance.max-attempts-per-request", 180));
    }

    public long searchTimeoutMillis() {
        return seconds("performance.search-timeout-seconds", 45L) * 1000L;
    }

    public boolean asyncChunkLoad() {
        return config.getBoolean("performance.async-chunk-load", true);
    }

    public boolean preferLoadedChunks() {
        return config.getBoolean("performance.prefer-loaded-chunks", false);
    }

    public int loadedChunkAttempts() {
        return Math.max(0, config.getInt("performance.loaded-chunk-attempts", 0));
    }

    public boolean allowNetherRoof() {
        return config.getBoolean("safety.allow-nether-roof", false);
    }

    public boolean requireSolidGround() {
        return config.getBoolean("safety.require-solid-ground", true);
    }

    public boolean requireTwoAirBlocks() {
        return config.getBoolean("safety.require-two-air-blocks", true);
    }

    public int surfaceScanDepth() {
        return Math.max(1, config.getInt("safety.surface-scan-depth", 8));
    }

    public boolean avoidCaves() {
        return config.getBoolean("safety.avoid-caves", false);
    }

    public int minSkyLight() {
        return Math.max(0, Math.min(15, config.getInt("safety.min-sky-light", 0)));
    }

    public boolean unsafeGround(Material material) {
        return unsafeGround.contains(material);
    }

    public boolean unsafeSpace(Material material) {
        return unsafeSpace.contains(material);
    }

    public boolean blacklistedBiome(Biome biome) {
        return blacklistedBiomes.contains(biome);
    }

    public Sound sound(String key, Sound fallback) {
        String value = config.getString("effects." + key, fallback.name());
        try {
            return Sound.valueOf(value == null ? fallback.name() : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public boolean particlesEnabled() {
        return config.getBoolean("effects.particles", true);
    }

    public Particle particle() {
        String value = config.getString("effects.particle", "PORTAL");
        try {
            return Particle.valueOf(value == null ? "PORTAL" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.PORTAL;
        }
    }

    public int particleCount() {
        return Math.max(1, config.getInt("effects.particle-count", 32));
    }

    public String prefix() {
        return message("prefix");
    }

    public String message(String key) {
        String path = "messages." + key;
        String message = config.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        message = defaultConfig.getString(path);
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Missing message: " + key;
    }

    private Map<String, WorldSettings> loadWorlds(ConfigurationSection section) {
        Map<String, WorldSettings> loaded = new LinkedHashMap<>();
        if (section == null) {
            return loaded;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection worldSection = section.getConfigurationSection(key);
            if (worldSection == null) {
                continue;
            }
            int minRadius = Math.max(0, worldSection.getInt("min-radius", 500));
            int maxRadius = Math.max(minRadius + 1, worldSection.getInt("max-radius", 5000));
            WorldSettings settings = new WorldSettings(
                    key,
                    worldSection.getBoolean("enabled", true),
                    worldSection.getString("center-mode", "spawn").toLowerCase(Locale.ROOT),
                    worldSection.getInt("center-x", 0),
                    worldSection.getInt("center-z", 0),
                    minRadius,
                    maxRadius,
                    worldSection.getBoolean("respect-world-border", true),
                    worldSection.getBoolean("square-search", false)
            );
            loaded.put(key.toLowerCase(Locale.ROOT), settings);
        }
        return loaded;
    }

    private Set<Material> loadMaterials(String path) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList(path)) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().warning("Ignoring invalid material in " + path + ": " + name);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }

    private Set<Biome> loadBiomes(String path) {
        Set<Biome> biomes = new HashSet<>();
        for (String name : config.getStringList(path)) {
            NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
            Biome biome = Registry.BIOME.get(key);
            if (biome == null) {
                plugin.getLogger().warning("Ignoring invalid biome in " + path + ": " + name);
                continue;
            }
            biomes.add(biome);
        }
        return biomes;
    }

    private long seconds(String path, long fallback) {
        return Math.max(0L, config.getLong(path, fallback));
    }

    private FileConfiguration loadBundledConfig() {
        try (InputStream inputStream = plugin.getResource("config.yml")) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load bundled config defaults: " + e.getMessage());
            return new YamlConfiguration();
        }
    }
}
