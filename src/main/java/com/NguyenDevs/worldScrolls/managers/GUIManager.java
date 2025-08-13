package com.NguyenDevs.worldScrolls.managers;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.guis.AdminGUI;
import com.NguyenDevs.worldScrolls.guis.PlayerGUI;
import com.NguyenDevs.worldScrolls.guis.RecipeGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class GUIManager {
    
    private final WorldScrolls plugin;
    private final PlayerGUI playerGUI;
    private final RecipeGUI recipeGUI;
    private final AdminGUI adminGUI;
    
    public GUIManager(WorldScrolls plugin) {
        this.plugin = plugin;
        this.playerGUI = new PlayerGUI(plugin);
        this.recipeGUI = new RecipeGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        
        registerEventListeners();
    }
    
    /**
     * Register all GUI event listeners
     */
    private void registerEventListeners() {
        Bukkit.getPluginManager().registerEvents(playerGUI, plugin);
        Bukkit.getPluginManager().registerEvents(recipeGUI, plugin);
        Bukkit.getPluginManager().registerEvents(adminGUI, plugin);
        
        plugin.getLogger().info("GUI event listeners registered successfully!");
    }
    
    /**
     * Open player scroll menu
     */
    public void openPlayerMenu(Player player) {
        playerGUI.openScrollMenu(player);
    }
    
    /**
     * Open recipe book (list of all recipes)
     */
    public void openRecipeBook(Player player) {
        recipeGUI.openRecipeList(player);
    }
    
    /**
     * Open specific scroll recipe
     */
    public void openScrollRecipe(Player player, String scrollType) {
        recipeGUI.openScrollRecipe(player, scrollType);
    }
    
    /**
     * Open admin panel
     */
    public void openAdminPanel(Player player) {
        adminGUI.openAdminPanel(player);
    }
    
    /**
     * Get player GUI instance
     */
    public PlayerGUI getPlayerGUI() {
        return playerGUI;
    }
    
    /**
     * Get recipe GUI instance
     */
    public RecipeGUI getRecipeGUI() {
        return recipeGUI;
    }
    
    /**
     * Get admin GUI instance
     */
    public AdminGUI getAdminGUI() {
        return adminGUI;
    }
    
    /**
     * Close all GUIs for a player (useful on disconnect)
     */
    public void closeAllGUIs(Player player) {
        playerGUI.closeGUI(player);
        recipeGUI.closeGUI(player);
        adminGUI.closeGUI(player);
    }
}