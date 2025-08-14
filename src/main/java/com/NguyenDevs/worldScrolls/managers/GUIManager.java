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

        this.recipeGUI = new RecipeGUI(plugin);
        this.playerGUI = new PlayerGUI(plugin, recipeGUI);
        this.adminGUI = new AdminGUI(plugin, recipeGUI);

        registerEventListeners();
    }


    private void registerEventListeners() {
        Bukkit.getPluginManager().registerEvents(playerGUI, plugin);
        Bukkit.getPluginManager().registerEvents(recipeGUI, plugin);
        Bukkit.getPluginManager().registerEvents(adminGUI, plugin);

        plugin.getLogger().info("GUI event listeners registered successfully!");
    }

    public void openPlayerMenu(Player player) {
        playerGUI.openScrollMenu(player);
    }

    public void openRecipeBook(Player player) {
        recipeGUI.openRecipeList(player);
    }

    public void openScrollRecipe(Player player, String scrollType) {
        recipeGUI.openScrollRecipe(player, scrollType);
    }

    public void openAdminPanel(Player player) {
        adminGUI.openAdminPanel(player);
    }

    public PlayerGUI getPlayerGUI() {
        return playerGUI;
    }

    public RecipeGUI getRecipeGUI() {
        return recipeGUI;
    }

    public AdminGUI getAdminGUI() {
        return adminGUI;
    }

    public void closeAllGUIs(Player player) {
        playerGUI.closeGUI(player);
        recipeGUI.closeGUI(player);
        adminGUI.closeGUI(player);
    }
}