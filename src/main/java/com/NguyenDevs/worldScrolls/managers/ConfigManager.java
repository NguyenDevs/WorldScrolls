package com.NguyenDevs.worldScrolls.managers;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    private final Map<String, ConfigurationSection> scrollConfigs = new HashMap<>();

    private final List<String> configFiles = new ArrayList<>(Arrays.asList(
            "config.yml",
            "messages.yml",
            "scrolls.yml",
            "recipes.yml"
    ));

    private final List<String> scrollConfigFiles = Arrays.asList(
            "scroll_of_exit.yml",
            "scroll_of_cyclone.yml",
            "scroll_of_frostbite.yml",
            "scroll_of_gravitation.yml",
            "scroll_of_invisibility.yml",
            "scroll_of_meteor.yml",
            "scroll_of_phoenix.yml",
            "scroll_of_radiation.yml",
            "scroll_of_solar.yml",
            "scroll_of_thorn.yml",
            "scroll_of_thunder.yml",
            "scroll_of_frostbite.yml"
    );

    public ConfigManager(WorldScrolls plugin) {
        this.plugin = plugin;
    }

    public void initializeConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File scrollsFolder = new File(plugin.getDataFolder(), "scrolls");
        if (!scrollsFolder.exists()) {
            scrollsFolder.mkdirs();
        }

        for (String scrollFile : scrollConfigFiles) {
            configFiles.add("scrolls/" + scrollFile);
        }

        for (String configFile : configFiles) {
            processConfigFile(configFile);
        }

        //Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aAll configurations have been loaded successfully!"));
    }

    private void processConfigFile(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        try {
            if (!configFile.exists()) {
                copyResourceToFile(fileName, configFile);
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aCreated new config file: " + fileName));
            } else {
                mergeConfigWithDefaults(fileName, configFile);
            }
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            configs.put(fileName, config);
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to process config file: " + fileName) + " " + e);
        }
    }

    private void copyResourceToFile(String resourceName, File targetFile) throws IOException {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cResource not found: " + resourceName));
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
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cDefault resource not found: " + fileName));
                    return;
                }

                FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8)
                );

                boolean hasChanges = mergeConfigs(defaultConfig, existingConfig);

                if (hasChanges) {
                    existingConfig.save(configFile);
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aUpdated config file with missing keys: " + fileName));
                }
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to merge config: " + fileName) + " " + e);
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

    public ConfigurationSection getScrollConfig(String scrollName) {
        if (scrollConfigs.containsKey(scrollName)) {
            return scrollConfigs.get(scrollName);
        }

        File configFile = new File(plugin.getDataFolder(), "scrolls/" + scrollName + ".yml");
        if (!configFile.exists()) {
            plugin.saveResource("scrolls/" + scrollName + ".yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        scrollConfigs.put(scrollName, config);

        return config;
    }

    public void reloadScrollConfigs() {
        scrollConfigs.clear();
    }

    public void reloadConfigs() {
        configs.clear();
        initializeConfigs();
        reloadScrollConfigs();
        //Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aAll configurations reloaded!"));
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

    public FileConfiguration getScrollMessageConfig(String scrollFileName) {
        return getConfig("scrolls/" + scrollFileName + ".yml");
    }

    public String getScrollMessage(String scrollFileName, String path, Map<String, String> placeholders) {
        FileConfiguration cfg = getScrollMessageConfig(scrollFileName);
        if (cfg == null) return "Message not found: " + path;
        String msg = cfg.getString("messages." + path, "Message not found: " + path);
        msg = ColorUtils.colorize(msg);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("%" + e.getKey() + "%", e.getValue());
            }
        }
        return msg;
    }

    public String getScrollMessage(String scrollFileName, String path) {
        return getScrollMessage(scrollFileName, path, null);
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

    public void saveConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        if (config == null) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cConfiguration not found: " + fileName));
            return;
        }
        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            config.save(configFile);
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to save config: " + fileName) + " " + e);
        }
    }

    public void saveAllConfigs() {
        for (String fileName : configs.keySet()) {
            saveConfig(fileName);
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aAll configurations saved!"));
    }

    public List<String> getStringList(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null ? config.getStringList(path) : Collections.emptyList();
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
}