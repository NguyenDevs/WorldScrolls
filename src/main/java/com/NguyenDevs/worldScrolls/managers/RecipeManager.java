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

        Set<String> currentScrollIds = new HashSet<>();
        int unchanged = 0, updated = 0, added = 0;

        for (String scrollId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeData = recipesSection.getConfigurationSection(scrollId);
            ConfigurationSection scrollData = scrollsSection.getConfigurationSection(scrollId);

            if (recipeData == null || scrollData == null) continue;
            if (!scrollData.getBoolean("enabled", true) || !scrollData.getBoolean("craftable", true)) {
                if (registeredRecipes.containsKey(scrollId)) {
                    unregisterRecipe(scrollId);
                }
                continue;
            }

            currentScrollIds.add(scrollId);
            String newHash = generateRecipeHash(recipeData, scrollData, scrollId);
            String oldHash = recipeHashes.get(scrollId);

            if (!newHash.equals(oldHash) || !registeredRecipes.containsKey(scrollId)) {
                if (registeredRecipes.containsKey(scrollId)) {
                    unregisterRecipe(scrollId);
                    updated++;
                } else {
                    added++;
                }

                if (registerRecipe(scrollId, recipeData, scrollData)) {
                    recipeHashes.put(scrollId, newHash);
                }
            } else {
                unchanged++;
            }
        }

        Set<String> toRemove = new HashSet<>(registeredRecipes.keySet());
        toRemove.removeAll(currentScrollIds);

        for (String removedScrollId : toRemove) {
            unregisterRecipe(removedScrollId);
            recipeHashes.remove(removedScrollId);
        }

        String message = String.format("&7[&dWorld&5Scroll&7] &aRecipes loaded. Total: %d &7(Added: &a%d&7, Updated: &e%d&7, Unchanged: &b%d&7, Removed: &c%d&7)",
                registeredRecipes.size(), added, updated, unchanged, toRemove.size());

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private boolean registerRecipe(String scrollId, ConfigurationSection recipeData, ConfigurationSection scrollData) {
        List<String> shape = recipeData.getStringList("recipe");
        if (shape.size() != 3) {
            logError("Invalid recipe shape for " + scrollId);
            return false;
        }

        for (String row : shape) {
            if (row.length() != 3) {
                logError("Invalid recipe row length for " + scrollId + ": " + row);
                return false;
            }
        }

        ItemStack result = createScrollItem(scrollId, scrollData);
        if (result == null) {
            logError("Failed to create scroll item for " + scrollId);
            return false;
        }

        NamespacedKey key = new NamespacedKey(plugin, scrollId + "_recipe");

        try {
            Bukkit.removeRecipe(key);
        } catch (Exception ignored) {}

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        if (!setRecipeIngredients(recipe, recipeData, scrollId, shape)) {
            return false;
        }

        try {
            boolean added = Bukkit.addRecipe(recipe);
            if (added) {
                registeredRecipes.put(scrollId, recipe);
                return true;
            } else {
                logError("Failed to add recipe for " + scrollId);
                return false;
            }
        } catch (Exception e) {
            logError("Exception while adding recipe for " + scrollId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean setRecipeIngredients(ShapedRecipe recipe, ConfigurationSection recipeData, String scrollId, List<String> shape) {
        ConfigurationSection materialSection = recipeData.getConfigurationSection("material");
        if (materialSection == null) return true;

        Set<Character> usedChars = new HashSet<>();
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != 'X' && c != ' ') {
                    usedChars.add(c);
                }
            }
        }

        for (Map.Entry<String, Object> entry : materialSection.getValues(false).entrySet()) {
            String symbol = entry.getKey();
            String matName = (String) entry.getValue();

            if (symbol.length() != 1) {
                logError("Invalid symbol length for " + scrollId + ": " + symbol);
                continue;
            }

            char symbolChar = symbol.charAt(0);

            if (matName == null || matName.equalsIgnoreCase("X")) continue;
            if (!usedChars.contains(symbolChar)) continue;

            Material mat = Material.getMaterial(matName.toUpperCase());
            if (mat == null) {
                logError("Invalid material for " + scrollId + ": " + matName);
                continue;
            }

            try {
                recipe.setIngredient(symbolChar, mat);
            } catch (Exception e) {
                logError("Failed to set ingredient " + symbolChar + " for " + scrollId + ": " + e.getMessage());
                return false;
            }
        }

        return true;
    }

    private void unregisterRecipe(String scrollId) {
        ShapedRecipe recipe = registeredRecipes.remove(scrollId);
        if (recipe != null) {
            try {
                Bukkit.removeRecipe(recipe.getKey());
            } catch (Exception e) {
                logError("Error unregistering recipe for " + scrollId + ": " + e.getMessage());
            }
        }
    }

    private String generateRecipeHash(ConfigurationSection recipeData, ConfigurationSection scrollData, String scrollId) {
        StringBuilder sb = new StringBuilder();

        List<String> shape = recipeData.getStringList("recipe");
        sb.append("SHAPE:").append(String.join(";", shape)).append("|");

        ConfigurationSection materialSection = recipeData.getConfigurationSection("material");
        if (materialSection != null) {
            sb.append("MATERIALS:");
            TreeMap<String, Object> sortedMaterials = new TreeMap<>(materialSection.getValues(false));
            for (Map.Entry<String, Object> entry : sortedMaterials.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
            }
            sb.append("|");
        }

        sb.append("SCROLL_META:");
        sb.append("name=").append(scrollData.getString("name", "")).append(";");
        sb.append("lore=").append(String.join(";;", scrollData.getStringList("lore"))).append(";");
        sb.append("enabled=").append(scrollData.getBoolean("enabled", true)).append(";");
        sb.append("craftable=").append(scrollData.getBoolean("craftable", true)).append("|");

        ConfigurationSection scrollConfig = configManager.getScrollConfig(scrollId);
        if (scrollConfig != null) {
            sb.append("SCROLL_CONFIG:");
            sb.append("material=").append(scrollConfig.getString("material", "PAPER")).append(";");
        }

        return sb.toString();
    }

    private ItemStack createScrollItem(String scrollId, ConfigurationSection scrollData) {
        ConfigurationSection scrollConfig = plugin.getConfigManager().getScrollConfig(scrollId);

        String name = scrollData.getString("name", "Scroll");
        List<String> lore = scrollData.getStringList("lore");
        String materialName = scrollConfig != null ? scrollConfig.getString("material", "PAPER") : "PAPER";

        Material mat;
        try {
            mat = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String processedName = replacePlaceholders(name, scrollData, scrollId);
        List<String> processedLore = new ArrayList<>();
        for (String line : lore) {
            processedLore.add(replacePlaceholders(line, scrollData, scrollId));
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

    private String replacePlaceholders(String text, ConfigurationSection scrollData, String scrollId) {
        String result = text;

        for (String key : scrollData.getKeys(false)) {
            if (!key.equals("name") && !key.equals("lore") && !key.equals("enabled") && !key.equals("craftable")) {
                Object value = scrollData.get(key);
                if (value != null) {
                    result = result.replace("%" + key + "%", value.toString());
                }
            }
        }

        ConfigurationSection scrollConfig = configManager.getScrollConfig(scrollId);
        if (scrollConfig != null) {
            result = replaceFromScrollConfig(result, scrollConfig);
        }

        return result;
    }

    private String replaceFromScrollConfig(String result, ConfigurationSection scrollConfig) {
        return replaceConfigRecursive(result, scrollConfig);
    }

    private String replaceConfigRecursive(String text, ConfigurationSection section) {
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                Object value = section.get(key);
                if (value != null) {
                    text = text.replace("%" + key + "%", value.toString());

                    // Hỗ trợ nested keys (ví dụ: messages.prefix -> %prefix%)
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

    private void logError(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7[&dWorld&5Scroll&7] &c" + message));
    }

    public int getRegisteredRecipeCount() {
        return registeredRecipes.size();
    }

    public Set<String> getRegisteredScrollIds() {
        return new HashSet<>(registeredRecipes.keySet());
    }

    public boolean isRecipeRegistered(String scrollId) {
        return registeredRecipes.containsKey(scrollId);
    }

    public void clearAllRecipes() {
        Set<String> scrollIds = new HashSet<>(registeredRecipes.keySet());
        for (String scrollId : scrollIds) {
            unregisterRecipe(scrollId);
        }
        recipeHashes.clear();
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7[&dWorld&5Scroll&7] &cAll recipes cleared."));
    }
}