package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AdminGUI extends BaseGUI {
    
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final RecipeGUI recipeGUI;
    
    // Navigation slots
    private static final int CLOSE_SLOT = 44;
    private static final int PREV_PAGE_SLOT = 37;
    private static final int NEXT_PAGE_SLOT = 43;
    private static final int RECIPE_BOOK_SLOT = 40;
    private static final int SETTINGS_SLOT = 38;
    private static final int RELOAD_SLOT = 42;
    
    public AdminGUI(WorldScrolls plugin) {
        super(plugin);
        this.recipeGUI = new RecipeGUI(plugin);
    }
    
    /**
     * Open admin panel
     */
    public void openAdminPanel(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        openGUI(player, "&c&lAdmin Panel &8- &7WorldScrolls");
    }
    
    @Override
    protected void fillContent(Inventory inventory, Player player) {
        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = CONTENT_SLOTS.size() - 2; // Reserve slots for navigation
        
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableScrolls.size());
        
        List<Integer> slots = getContentSlots();
        int slotIndex = 0;
        
        // Add scroll items
        for (int i = startIndex; i < endIndex && slotIndex < slots.size() - 2; i++) {
            String scrollType = availableScrolls.get(i);
            ItemStack scrollItem = createAdminScrollItem(scrollType);
            inventory.setItem(slots.get(slotIndex), scrollItem);
            slotIndex++;
        }
        
        // Add page info if needed
        if (availableScrolls.size() > itemsPerPage) {
            ItemStack pageInfo = createItem(
                Material.PAPER,
                "&e&lPage " + (page + 1) + "/" + ((availableScrolls.size() - 1) / itemsPerPage + 1),
                Arrays.asList(
                    "&7Total scrolls: &e" + availableScrolls.size(),
                    "&7Use arrows to navigate pages"
                )
            );
            inventory.setItem(slots.get(slots.size() - 1), pageInfo);
        }
        
        // Add admin navigation items
        addAdminNavigationItems(inventory, player);
    }
    
    /**
     * Add admin-specific navigation items
     */
    private void addAdminNavigationItems(Inventory inventory, Player player) {
        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = CONTENT_SLOTS.size() - 2;
        int maxPages = (availableScrolls.size() - 1) / itemsPerPage + 1;
        
        // Previous page
        if (page > 0) {
            ItemStack prevPage = createItem(
                Material.ARROW,
                "&e&l← Previous Page",
                Arrays.asList("&7Go to page " + page)
            );
            inventory.setItem(PREV_PAGE_SLOT, prevPage);
        }
        
        // Next page
        if (page < maxPages - 1) {
            ItemStack nextPage = createItem(
                Material.ARROW,
                "&e&lNext Page →",
                Arrays.asList("&7Go to page " + (page + 2))
            );
            inventory.setItem(NEXT_PAGE_SLOT, nextPage);
        }
        
        // Settings button
        ItemStack settings = createItem(
            Material.REDSTONE,
            "&c&lPlugin Settings",
            Arrays.asList(
                "&7Manage plugin configuration",
                "&7View world restrictions",
                "&7Toggle scroll states"
            )
        );
        inventory.setItem(SETTINGS_SLOT, settings);
        
        // Recipe book
        ItemStack recipeBook = createItem(
            Material.ENCHANTED_BOOK,
            "&6&lRecipe Book",
            Arrays.asList(
                "&7View all scroll recipes",
                "&7and crafting information"
            )
        );
        inventory.setItem(RECIPE_BOOK_SLOT, recipeBook);
        
        // Reload button
        ItemStack reload = createItem(
            Material.COMMAND_BLOCK,
            "&a&lReload Plugin",
            Arrays.asList(
                "&7Reload all configurations",
                "&7without restarting the server",
                "",
                "&eClick to reload"
            )
        );
        inventory.setItem(RELOAD_SLOT, reload);
        
        // Close button
        inventory.setItem(CLOSE_SLOT, getCloseItem());
    }
    
    @Override
    protected void handleClick(Player player, Inventory inventory, int slot, ItemStack clickedItem, boolean isRightClick) {
        String identifier = "";
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLocalizedName()) {
            identifier = clickedItem.getItemMeta().getLocalizedName();
        }
        
        // Handle admin navigation
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        
        if (slot == RECIPE_BOOK_SLOT) {
            player.closeInventory();
            recipeGUI.openRecipeList(player);
            return;
        }
        
        if (slot == RELOAD_SLOT) {
            handleReloadPlugin(player);
            return;
        }
        
        if (slot == SETTINGS_SLOT) {
            handleSettingsMenu(player);
            return;
        }
        
        if (slot == PREV_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPageNum > 0) {
                currentPage.put(player.getUniqueId(), currentPageNum - 1);
                openAdminPanel(player);
            }
            return;
        }
        
        if (slot == NEXT_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            currentPage.put(player.getUniqueId(), currentPageNum + 1);
            openAdminPanel(player);
            return;
        }
        
        // Handle scroll interactions
        if (identifier.startsWith("admin_scroll:")) {
            String scrollType = identifier.replace("admin_scroll:", "");
            
            if (isRightClick) {
                // Right-click: Open recipe
                player.closeInventory();
                recipeGUI.openScrollRecipe(player, scrollType);
            } else {
                // Left-click: Give scroll to admin
                giveScrollToAdmin(player, scrollType);
            }
        }
    }
    
    /**
     * Give scroll to admin player
     */
    private void giveScrollToAdmin(Player admin, String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) {
            admin.sendMessage(ColorUtils.colorize("&cScroll configuration not found: " + scrollType));
            return;
        }
        
        if (!scrollConfig.getBoolean("enabled", true)) {
            admin.sendMessage(ColorUtils.colorize("&cThis scroll is currently disabled!"));
            return;
        }
        
        // Create scroll item
        ItemStack scrollItem = createScrollItem(scrollType, scrollConfig);
        if (scrollItem != null) {
            // Check inventory space
            if (admin.getInventory().firstEmpty() == -1) {
                admin.getWorld().dropItem(admin.getLocation(), scrollItem);
                admin.sendMessage(ColorUtils.colorize("&aScroll dropped at your feet (inventory full)"));
            } else {
                admin.getInventory().addItem(scrollItem);
                admin.sendMessage(ColorUtils.colorize("&aYou received: " + scrollConfig.getString("name", scrollType)));
            }
            
            // Log admin action
            plugin.getLogger().info("Admin " + admin.getName() + " gave themselves scroll: " + scrollType);
        } else {
            admin.sendMessage(ColorUtils.colorize("&cFailed to create scroll item!"));
        }
    }
    
    /**
     * Handle plugin reload
     */
    private void handleReloadPlugin(Player admin) {
        try {
            configManager.reloadConfigs();
            admin.sendMessage(ColorUtils.colorize("&aPlugin reloaded successfully!"));
            plugin.getLogger().info("Plugin reloaded by admin: " + admin.getName());
            
            // Refresh the GUI
            openAdminPanel(admin);
        } catch (Exception e) {
            admin.sendMessage(ColorUtils.colorize("&cFailed to reload plugin! Check console for errors."));
            plugin.getLogger().severe("Failed to reload plugin via admin GUI: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle settings menu (placeholder for future expansion)
     */
    private void handleSettingsMenu(Player admin) {
        admin.sendMessage(ColorUtils.colorize("&e&lPlugin Settings"));
        admin.sendMessage(ColorUtils.colorize("&7Update notifications: " + (configManager.isUpdateNotifyEnabled() ? "&aEnabled" : "&cDisabled")));
        
        List<String> disabledWorlds = configManager.getMainConfig().getStringList("disabled-worlds");
        admin.sendMessage(ColorUtils.colorize("&7Disabled worlds: &e" + (disabledWorlds.isEmpty() ? "None" : String.join(", ", disabledWorlds))));
        
        admin.sendMessage(ColorUtils.colorize("&7Total scrolls: &e" + getAvailableScrolls().size()));
        admin.sendMessage(ColorUtils.colorize("&8(Detailed settings GUI coming in future update)"));
    }
    
    /**
     * Create admin scroll item with special admin lore
     */
    private ItemStack createAdminScrollItem(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);
        
        List<String> lore = new ArrayList<>();
        
        // Add admin instructions
        lore.add("&c&lADMIN CONTROLS:");
        lore.add("&e▶ Left-Click: &7Give to yourself");
        lore.add("&e▶ Right-Click: &7View recipe");
        lore.add("");
        
        // Add scroll description (first 3 lines)
        List<String> originalLore = scrollConfig.getStringList("lore");
        int linesToShow = Math.min(3, originalLore.size());
        for (int i = 0; i < linesToShow; i++) {
            String line = replacePlaceholders(originalLore.get(i), scrollConfig);
            lore.add(line);
        }
        
        if (originalLore.size() > 3) {
            lore.add("&8... (right-click for full details)");
        }
        
        lore.add("");
        
        // Add admin info
        lore.add("&6⏱ Cooldown: &e" + formatCooldown(scrollConfig.getInt("cooldown", 0)));
        lore.add("&7Enabled: " + (scrollConfig.getBoolean("enabled", true) ? "&aYes" : "&cNo"));
        lore.add("&7Craftable: " + (scrollConfig.getBoolean("craftable", true) ? "&aYes" : "&cNo"));
        
        // Get scroll icon
        Material iconMaterial = getScrollIcon(scrollType);
        
        return createItem(
            iconMaterial,
            "&c&l[ADMIN] &r" + scrollConfig.getString("name", scrollType),
            lore,
            "admin_scroll:" + scrollType
        );
    }
    
    /**
     * Create actual scroll item for giving
     */
    private ItemStack createScrollItem(String scrollType, ConfigurationSection scrollConfig) {
        try {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            
            if (meta != null) {
                // Set display name
                String name = scrollConfig.getString("name", scrollType);
                meta.setDisplayName(ColorUtils.colorize(name));
                
                // Set lore
                List<String> lore = scrollConfig.getStringList("lore");
                if (!lore.isEmpty()) {
                    List<String> colorizedLore = new ArrayList<>();
                    for (String line : lore) {
                        String processedLine = replacePlaceholders(line, scrollConfig);
                        colorizedLore.add(ColorUtils.colorize(processedLine));
                    }
                    meta.setLore(colorizedLore);
                }
                
                // Set identifier
                meta.setLocalizedName("worldscrolls:" + scrollType);
                
                item.setItemMeta(meta);
            }
            
            return item;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create scroll item for admin: " + scrollType);
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get scroll icon material (same as PlayerGUI)
     */
    private Material getScrollIcon(String scrollType) {
        switch (scrollType.toLowerCase()) {
            case "scroll_of_thorn":
                return Material.CACTUS;
            case "scroll_of_cyclone":
                return Material.GHAST_TEAR;
            case "scroll_of_exit":
                return Material.ENDER_PEARL;
            case "scroll_of_frostbite":
                return Material.BLUE_ICE;
            case "scroll_of_gravitation":
                return Material.OBSIDIAN;
            case "scroll_of_invisibility":
                return Material.FERMENTED_SPIDER_EYE;
            case "scroll_of_meteor":
                return Material.FIRE_CHARGE;
            case "scroll_of_phoenix":
                return Material.GLISTERING_MELON_SLICE;
            case "scroll_of_radiation":
                return Material.SPIDER_EYE;
            case "scroll_of_solar":
                return Material.GLOWSTONE_DUST;
            case "scroll_of_thunder":
                return Material.LIGHTNING_ROD;
            default:
                return Material.PAPER;
        }
    }
    
    /**
     * Format cooldown time
     */
    private String formatCooldown(int seconds) {
        if (seconds == 0) return "None";
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }
    
    /**
     * Replace placeholders in text
     */
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
        
        return result;
    }
    
    /**
     * Get all available scroll types
     */
    private List<String> getAvailableScrolls() {
        List<String> scrolls = new ArrayList<>();
        ConfigurationSection scrollsConfig = configManager.getScrolls();
        
        if (scrollsConfig != null) {
            for (String scrollType : scrollsConfig.getKeys(false)) {
                scrolls.add(scrollType); // Admin can see all scrolls, even disabled ones
            }
        }
        
        Collections.sort(scrolls);
        return scrolls;
    }
    
    /**
     * Get the recipe GUI instance
     */
    public RecipeGUI getRecipeGUI() {
        return recipeGUI;
    }
    
    @Override
    public void closeGUI(Player player) {
        super.closeGUI(player);
        currentPage.remove(player.getUniqueId());
    }
}
