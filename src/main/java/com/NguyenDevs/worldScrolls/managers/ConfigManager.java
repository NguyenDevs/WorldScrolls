package com.NguyenDevs.worldScrolls.managers;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
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
    
    /**
     * Initialize all configurations
     */
    public void initializeConfigs() {
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Process each config file
        for (String configFile : configFiles) {
            processConfigFile(configFile);
        }
        
        plugin.getLogger().info("All configurations have been loaded successfully!");
    }
    
    /**
     * Process a single config file (copy from resources if needed, merge if exists)
     */
    private void processConfigFile(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        
        try {
            if (!configFile.exists()) {
                // File doesn't exist, copy from resources
                copyResourceToFile(fileName, configFile);
                plugin.getLogger().info("Created new config file: " + fileName);
            } else {
                // File exists, merge with default to add missing keys
                mergeConfigWithDefaults(fileName, configFile);
            }
            
            // Load the configuration into memory
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            configs.put(fileName, config);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to process config file: " + fileName, e);
        }
    }
    
    /**
     * Copy resource file to plugin data folder
     */
    private void copyResourceToFile(String resourceName, File targetFile) throws IOException {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                plugin.getLogger().warning("Resource not found: " + resourceName);
                return;
            }
            
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Merge existing config with defaults to add missing keys while preserving existing values
     */
    private void mergeConfigWithDefaults(String fileName, File configFile) {
        try {
            // Load existing config
            FileConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Load default config from resources
            InputStream defaultStream = plugin.getResource(fileName);
            if (defaultStream == null) {
                plugin.getLogger().warning("Default resource not found: " + fileName);
                return;
            }
            
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, "UTF-8")
            );
            
            // Merge configurations
            boolean hasChanges = mergeConfigs(defaultConfig, existingConfig);
            
            // Save if there were changes
            if (hasChanges) {
                existingConfig.save(configFile);
                plugin.getLogger().info("Updated config file with missing keys: " + fileName);
            }
            
            defaultStream.close();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to merge config: " + fileName, e);
        }
    }
    
    /**
     * Recursively merge two configurations
     * @param source The source config (defaults)
     * @param target The target config (existing)
     * @return true if any changes were made
     */
    private boolean mergeConfigs(ConfigurationSection source, ConfigurationSection target) {
        boolean hasChanges = false;
        
        for (String key : source.getKeys(false)) {
            if (source.isConfigurationSection(key)) {
                // Handle nested sections
                if (!target.isConfigurationSection(key)) {
                    target.createSection(key);
                    hasChanges = true;
                }
                boolean nestedChanges = mergeConfigs(source.getConfigurationSection(key), 
                                                   target.getConfigurationSection(key));
                hasChanges = hasChanges || nestedChanges;
            } else {
                // Handle regular values
                if (!target.contains(key)) {
                    target.set(key, source.get(key));
                    hasChanges = true;
                }
            }
        }
        
        return hasChanges;
    }
    
    /**
     * Reload all configurations
     */
    public void reloadConfigs() {
        configs.clear();
        initializeConfigs();
        plugin.getLogger().info("All configurations reloaded!");
    }
    
    /**
     * Get a specific configuration
     */
    public FileConfiguration getConfig(String fileName) {
        return configs.get(fileName);
    }
    
    /**
     * Get the main config.yml
     */
    public FileConfiguration getMainConfig() {
        return getConfig("config.yml");
    }
    
    /**
     * Get the messages.yml
     */
    public FileConfiguration getMessages() {
        return getConfig("messages.yml");
    }
    
    /**
     * Get the scrolls.yml
     */
    public FileConfiguration getScrolls() {
        return getConfig("scrolls.yml");
    }
    
    /**
     * Get the recipes.yml
     */
    public FileConfiguration getRecipes() {
        return getConfig("recipes.yml");
    }
    
    /**
     * Save a specific configuration
     */
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
    
    /**
     * Save all configurations
     */
    public void saveAllConfigs() {
        for (String fileName : configs.keySet()) {
            saveConfig(fileName);
        }
        plugin.getLogger().info("All configurations saved!");
    }
    
    /**
     * Check if a world is disabled
     */
    public boolean isWorldDisabled(String worldName) {
        FileConfiguration config = getMainConfig();
        if (config == null) return false;
        
        List<String> disabledWorlds = config.getStringList("disabled-worlds");
        return disabledWorlds.contains(worldName);
    }
    
    /**
     * Check if update notifications are enabled
     */
    public boolean isUpdateNotifyEnabled() {
        FileConfiguration config = getMainConfig();
        if (config == null) return true;
        
        return config.getBoolean("update-notify", true);
    }
    
    /**
     * Get a message from messages.yml with color support
     */
    public String getMessage(String path) {
        FileConfiguration messages = getMessages();
        if (messages == null) return "Message not found: " + path;
        
        String message = messages.getString(path, "Message not found: " + path);
        return com.NguyenDevs.worldScrolls.utils.ColorUtils.colorize(message);
    }
    
    /**
     * Get a message with placeholder replacement
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        
        return message;
    }
    
    /**
     * Get prefix from messages
     */
    public String getPrefix() {
        return getMessage("prefix");
    }
}
