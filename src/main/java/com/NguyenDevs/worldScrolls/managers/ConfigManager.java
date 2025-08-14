package com.NguyenDevs.worldScrolls.managers;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

public class ConfigManager {

    private final WorldScrolls plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final List<String> configFiles = Arrays.asList(
            "config.yml", "messages.yml", "scrolls.yml", "recipes.yml"
    );

    public ConfigManager(WorldScrolls plugin) {
        this.plugin = plugin;
    }

    public void initializeConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        for (String configFile : configFiles) {
            processConfigFile(configFile);
        }
        plugin.getLogger().info("All configurations have been loaded successfully!");
    }

    private void processConfigFile(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        try {
            if (!configFile.exists()) {
                copyResourceToFile(fileName, configFile);
                plugin.getLogger().info("Created new config file: " + fileName);
            } else {
                mergeConfigWithDefaults(fileName, configFile);
            }
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            configs.put(fileName, config);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to process config file: " + fileName, e);
        }
    }

    private void copyResourceToFile(String resourceName, File targetFile) throws IOException {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                plugin.getLogger().warning("Resource not found: " + resourceName);
                return;
            }
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void mergeConfigWithDefaults(String fileName, File configFile) {
        try {
            FileConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);

            try (InputStream defaultStream = plugin.getResource(fileName)) {
                if (defaultStream == null) {
                    plugin.getLogger().warning("Default resource not found: " + fileName);
                    return;
                }

                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8)
                );

                boolean hasChanges = mergeConfigs(defaultConfig, existingConfig);

                if (hasChanges) {
                    existingConfig.save(configFile);
                    plugin.getLogger().info("Updated config file with missing keys: " + fileName);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to merge config: " + fileName, e);
        }
    }

    private boolean mergeConfigs(ConfigurationSection source, ConfigurationSection target) {
        boolean hasChanges = false;

        for (String key : source.getKeys(false)) {
            if (source.isConfigurationSection(key)) {
                if (!target.isConfigurationSection(key)) {
                    target.createSection(key);
                    hasChanges = true;
                }
                boolean nestedChanges = mergeConfigs(source.getConfigurationSection(key),
                        target.getConfigurationSection(key));
                hasChanges = hasChanges || nestedChanges;
            } else {
                if (!target.contains(key)) {
                    target.set(key, source.get(key));
                    hasChanges = true;
                }
            }
        }

        return hasChanges;
    }


    public void reloadConfigs() {
        configs.clear();
        initializeConfigs();
        plugin.getLogger().info("All configurations reloaded!");
    }

    public FileConfiguration getConfig(String fileName) {
        return configs.get(fileName);
    }

    public FileConfiguration getMainConfig() {
        return getConfig("config.yml");
    }

    public FileConfiguration getMessages() {
        return getConfig("messages.yml");
    }

    public FileConfiguration getScrolls() {
        return getConfig("scrolls.yml");
    }

    public FileConfiguration getRecipes() {
        return getConfig("recipes.yml");
    }

    public void saveConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        if (config == null) {
            plugin.getLogger().warning("Configuration not found: " + fileName);
            return;
        }
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save config: " + fileName, e);
        }
    }

    public void saveAllConfigs() {
        for (String fileName : configs.keySet()) {
            saveConfig(fileName);
        }
        plugin.getLogger().info("All configurations saved!");
    }

    public boolean isWorldDisabled(String worldName) {
        FileConfiguration config = getMainConfig();
        if (config == null) return false;
        List<String> disabledWorlds = config.getStringList("disabled-worlds");
        return disabledWorlds.contains(worldName);
    }

    public boolean isUpdateNotifyEnabled() {
        FileConfiguration config = getMainConfig();
        if (config == null) return true;
        return config.getBoolean("update-notify", true);
    }

    public String getMessage(String path) {
        FileConfiguration messages = getMessages();
        if (messages == null) return "Message not found: " + path;
        String message = messages.getString(path, "Message not found: " + path);
        return ColorUtils.colorize(message);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return message;
    }

    public String getPrefix() {
        return getMessage("prefix");
    }

    public FileConfiguration getConfigOrDefault(String fileName) {
        return configs.getOrDefault(fileName, new YamlConfiguration());
    }

    public Object getValue(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null ? config.get(path) : null;
    }

    public String getString(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null ? config.getString(path) : null;
    }

    public int getInt(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null ? config.getInt(path) : 0;
    }

    public double getDouble(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null ? config.getDouble(path) : 0D;
    }

    public boolean getBoolean(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null && config.getBoolean(path);
    }

    public List<String> getStringList(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null ? config.getStringList(path) : Collections.emptyList();
    }
}
