package com.NguyenDevs.worldScrolls.managers;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class RecipeManager {

    private final WorldScrolls plugin;
    private final ConfigManager configManager;
    private final Map<String, ShapedRecipe> registeredRecipes = new HashMap<>();
    private final Map<String, String> recipeHashes = new HashMap<>();

    public RecipeManager(WorldScrolls plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void loadRecipes() {
        ConfigurationSection recipesSection = plugin.getConfigManager().getRecipes();
        ConfigurationSection scrollsSection = plugin.getConfigManager().getScrolls();
        if (recipesSection == null || scrollsSection == null) return;

        Set<String> existingKeys = new HashSet<>(registeredRecipes.keySet());

        for (String scrollId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeData = recipesSection.getConfigurationSection(scrollId);
            ConfigurationSection scrollData = scrollsSection.getConfigurationSection(scrollId);
            if (recipeData == null || scrollData == null) continue;
            if (!scrollData.getBoolean("enabled", true) || !scrollData.getBoolean("craftable", true)) continue;

            String hash = generateRecipeHash(recipeData, scrollData);
            if (hash.equals(recipeHashes.get(scrollId))) {
                existingKeys.remove(scrollId);
                continue;
            }

            unregisterRecipe(scrollId);
            registerRecipe(scrollId, recipeData, scrollData);
            recipeHashes.put(scrollId, hash);
            existingKeys.remove(scrollId);
        }

        for (String removed : existingKeys) {
            unregisterRecipe(removed);
            recipeHashes.remove(removed);
        }

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &aRecipes loaded. Total: " + registeredRecipes.size()));
    }

    private void registerRecipe(String scrollId, ConfigurationSection recipeData, ConfigurationSection scrollData) {
        List<String> shape = recipeData.getStringList("recipe");
        if (shape.size() != 3) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cInvalid recipe shape for " + scrollId));
            return;
        }

        for (String row : shape) {
            if (row.length() != 3) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cInvalid recipe row length for " + scrollId + ": " + row));
                return;
            }
        }

        ItemStack result = createScrollItem(scrollId, scrollData);
        if (result == null) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to create scroll item for " + scrollId));
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, scrollId + "_recipe");

        try {
            Bukkit.removeRecipe(key);
        } catch (Exception ignored) {}

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        ConfigurationSection materialSection = recipeData.getConfigurationSection("material");
        if (materialSection != null) {
            // Track used characters in recipe
            Set<Character> usedChars = new HashSet<>();
            for (String row : shape) {
                for (char c : row.toCharArray()) {
                    if (c != 'X') {
                        usedChars.add(c);
                    }
                }
            }

            for (Map.Entry<String, Object> entry : materialSection.getValues(false).entrySet()) {
                String symbol = entry.getKey();
                String matName = (String) entry.getValue();

                if (symbol.length() != 1) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cInvalid symbol length for " + scrollId + ": " + symbol));
                    continue;
                }

                char symbolChar = symbol.charAt(0);

                if (matName == null || matName.equalsIgnoreCase("X")) continue;

                // Only set ingredient for characters used in recipe
                if (!usedChars.contains(symbolChar)) {
                    continue;
                }

                Material mat = Material.getMaterial(matName.toUpperCase());
                if (mat == null) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cInvalid material for " + scrollId + ": " + matName));
                    continue;
                }

                try {
                    recipe.setIngredient(symbolChar, mat);
                    } catch (Exception e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to set ingredient " + symbolChar + " for " + scrollId + ": " + e.getMessage()));
                    return;
                }
            }
        }

        try {
            boolean added = Bukkit.addRecipe(recipe);
            if (added) {
                registeredRecipes.put(scrollId, recipe);
                } else {
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to add recipe for " + scrollId));
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cException while adding recipe for " + scrollId + ": " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void unregisterRecipe(String scrollId) {
        ShapedRecipe recipe = registeredRecipes.remove(scrollId);
        if (recipe != null) {
            try {
                boolean removed = Bukkit.removeRecipe(recipe.getKey());
                if (removed) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &7Unregistered recipe for " + scrollId));
                }
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cError unregistering recipe for " + scrollId + ": " + e.getMessage()));
            }
        }
    }

    private String generateRecipeHash(ConfigurationSection recipeData, ConfigurationSection scrollData) {
        StringBuilder sb = new StringBuilder();
        List<String> shape = recipeData.getStringList("recipe");
        sb.append(String.join(";", shape)).append("|");
        ConfigurationSection materialSection = recipeData.getConfigurationSection("material");
        if (materialSection != null) {
            for (String key : materialSection.getKeys(false)) {
                sb.append(key).append("=").append(materialSection.getString(key)).append(";");
            }
        }
        sb.append(scrollData.getString("name", "")).append("|");
        sb.append(String.join(";", scrollData.getStringList("lore")));
        return sb.toString();
    }

    private ItemStack createScrollItem(String scrollId, ConfigurationSection scrollData) {
        String name = scrollData.getString("name", "Scroll");
        List<String> lore = scrollData.getStringList("lore");
        Material mat = Material.getMaterial(scrollData.getString("material", "PAPER").toUpperCase());
        if (mat == null) mat = Material.PAPER;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String processedName = replacePlaceholders(name, scrollData);
        List<String> processedLore = new ArrayList<>();
        for (String line : lore) {
            processedLore.add(replacePlaceholders(line, scrollData));
        }

        meta.setDisplayName(ColorUtils.colorize(processedName));
        List<String> coloredLore = new ArrayList<>();
        for (String line : processedLore) {
            coloredLore.add(ColorUtils.colorize(line));
        }
        meta.setLore(coloredLore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLocalizedName("worldscrolls:" + scrollId.toLowerCase());

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "scroll_type"),
                PersistentDataType.STRING,
                scrollId
        );

        item.setItemMeta(meta);
        return item;
    }

    private String replacePlaceholders(String text, ConfigurationSection config) {
        String result = text;

        for (String key : config.getKeys(false)) {
            if (!key.equals("name") && !key.equals("lore") && !key.equals("enabled") && !key.equals("craftable")) {
                Object value = config.get(key);
                if (value != null) {
                    result = result.replace("%" + key + "%", value.toString());
                }
            }
        }

        String scrollId = getScrollIdFromConfig(config);
        if (scrollId != null) {
            ConfigurationSection scrollSpecificConfig = configManager.getScrollConfig(scrollId);
            if (scrollSpecificConfig != null) {
                result = replaceFromScrollConfig(result, scrollSpecificConfig);
            }
        }

        return result;
    }

    private String replaceFromScrollConfig(String result, ConfigurationSection scrollConfig) {
        return replaceConfigRecursive(result, scrollConfig, "");
    }

    private String replaceConfigRecursive(String text, ConfigurationSection section, String prefix) {
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                Object value = section.get(key);
                if (value != null) {
                    text = text.replace("%" + key + "%", value.toString());

                    String[] parts = key.split("\\.");
                    if (parts.length > 1) {
                        String lastPart = parts[parts.length - 1];
                        text = text.replace("%" + lastPart + "%", value.toString());
                    }
                }
            }
        }
        return text;
    }

    private String getScrollIdFromConfig(ConfigurationSection config) {
        String currentPath = config.getCurrentPath();
        if (currentPath != null && currentPath.contains(".")) {
            return currentPath.substring(currentPath.lastIndexOf(".") + 1);
        }
        return currentPath;
    }

}