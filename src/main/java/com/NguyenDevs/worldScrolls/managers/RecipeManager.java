package com.NguyenDevs.worldScrolls.managers;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class RecipeManager {

    private final WorldScrolls plugin;
    private final Map<String, ShapedRecipe> registeredRecipes = new HashMap<>();
    private final Map<String, String> recipeHashes = new HashMap<>();

    public RecipeManager(WorldScrolls plugin) {
        this.plugin = plugin;
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

        plugin.getLogger().info("Recipes loaded. Total: " + registeredRecipes.size());
    }

    private void registerRecipe(String scrollId, ConfigurationSection recipeData, ConfigurationSection scrollData) {
        List<String> shape = recipeData.getStringList("recipe");
        if (shape.size() != 3) {
            plugin.getLogger().warning("Invalid recipe shape for " + scrollId);
            return;
        }

        ItemStack result = createScrollItem(scrollId, scrollData);
        if (result == null) {
            plugin.getLogger().warning("Failed to create scroll item for " + scrollId);
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, scrollId + "_recipe");
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.get(0), shape.get(1), shape.get(2));

        ConfigurationSection materialSection = recipeData.getConfigurationSection("material");
        if (materialSection != null) {
            for (String symbol : materialSection.getKeys(false)) {
                String matName = materialSection.getString(symbol);
                if (matName == null || matName.equalsIgnoreCase("X")) continue;
                Material mat = Material.getMaterial(matName.toUpperCase());
                if (mat == null) {
                    plugin.getLogger().warning("Invalid material for " + scrollId + ": " + matName);
                    continue;
                }
                recipe.setIngredient(symbol.charAt(0), mat);
            }
        }

        Bukkit.addRecipe(recipe);
        registeredRecipes.put(scrollId, recipe);
        plugin.getLogger().info("Registered recipe for " + scrollId);
    }

    private void unregisterRecipe(String scrollId) {
        ShapedRecipe recipe = registeredRecipes.remove(scrollId);
        if (recipe != null) {
            Bukkit.removeRecipe(recipe.getKey());
            plugin.getLogger().info("Unregistered recipe for " + scrollId);
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

        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setLocalizedName("worldscrolls:" + scrollId.toLowerCase());
        item.setItemMeta(meta);
        return item;
    }
}
