package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerGUI extends BaseGUI {
    
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final RecipeGUI recipeGUI;
    
    // Navigation slots
    private static final int CLOSE_SLOT = 44;
    private static final int PREV_PAGE_SLOT = 37;
    private static final int NEXT_PAGE_SLOT = 43;
    private static final int RECIPE_BOOK_SLOT = 40;
    
    public PlayerGUI(WorldScrolls plugin) {
        super(plugin);
        this.recipeGUI = new RecipeGUI(plugin);
    }
    
    /**
     * Open player scroll menu
     */
    public void openScrollMenu(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        openGUI(player, "&d&lWorld&5&lScrolls &8- &7Scroll Menu");
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
            ItemStack scrollItem = createScrollDisplayItem(scrollType, player);
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
        
        // Add navigation items
        addNavigationItems(inventory, player);
    }
    
    /**
     * Add navigation items to inventory
     */
    private void addNavigationItems(Inventory inventory, Player player) {
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
        
        // Recipe book
        ItemStack recipeBook = createItem(
            Material.ENCHANTED_BOOK,
            "&6&lRecipe Book",
            Arrays.asList(
                "&7Click to view all scroll recipes",
                "&7and learn how to craft them"
            )
        );
        inventory.setItem(RECIPE_BOOK_SLOT, recipeBook);
        
        // Close button
        inventory.setItem(CLOSE_SLOT, getCloseItem());
    }
    
    @Override
    protected void handleClick(Player player, Inventory inventory, int slot, ItemStack clickedItem, boolean isRightClick) {
        String identifier = "";
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLocalizedName()) {
            identifier = clickedItem.getItemMeta().getLocalizedName();
        }
        
        // Handle navigation
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        
        if (slot == RECIPE_BOOK_SLOT) {
            player.closeInventory();
            recipeGUI.openRecipeList(player);
            return;
        }
        
        if (slot == PREV_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPageNum > 0) {
                currentPage.put(player.getUniqueId(), currentPageNum - 1);
                SoundUtils.playPageTurnSound(player);
                openScrollMenu(player);
            }
            return;
        }
        
        if (slot == NEXT_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            currentPage.put(player.getUniqueId(), currentPageNum + 1);
            SoundUtils.playPageTurnSound(player);
            openScrollMenu(player);
            return;
        }
        
        // Handle scroll selection (click to view recipe)
        if (identifier.startsWith("scroll:")) {
            String scrollType = identifier.replace("scroll:", "");
            player.closeInventory();
            recipeGUI.openScrollRecipe(player, scrollType);
        }
    }
    
    /**
     * Create scroll display item for player menu
     */
    private ItemStack createScrollDisplayItem(String scrollType, Player player) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);
        
        List<String> lore = new ArrayList<>();
        
        // Add scroll description from config
        List<String> originalLore = scrollConfig.getStringList("lore");
        for (String line : originalLore) {
            // Replace placeholders with actual values
            String processedLine = replacePlaceholders(line, scrollConfig);
            lore.add(processedLine);
        }
        
        lore.add("");
        
        // Add cooldown information
        int cooldownSeconds = scrollConfig.getInt("cooldown", 0);
        if (cooldownSeconds > 0) {
            String cooldownText = formatCooldown(cooldownSeconds);
            lore.add("&6⏱ Cooldown: &e" + cooldownText);
        } else {
            lore.add("&6⏱ Cooldown: &aNone");
        }
        
        // Add usage statistics (if available)
        // TODO: This would connect to a cooldown/usage manager later
        lore.add("&a✓ Available for use");
        
        lore.add("");
        
        // Add craftable status
        boolean craftable = scrollConfig.getBoolean("craftable", true);
        lore.add("&7Craftable: " + (craftable ? "&aYes" : "&cNo"));
        
        lore.add("");
        lore.add("&e▶ Click to view recipe");
        
        // Get scroll category/type for icon
        Material iconMaterial = getScrollIcon(scrollType);
        
        return createItem(
            iconMaterial,
            scrollConfig.getString("name", scrollType),
            lore,
            "scroll:" + scrollType
        );
    }
    
    /**
     * Get appropriate icon material for scroll type
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
     * Format cooldown time into readable string
     */
    private String formatCooldown(int seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            if (remainingSeconds == 0) {
                return minutes + " minute" + (minutes != 1 ? "s" : "");
            } else {
                return minutes + "m " + remainingSeconds + "s";
            }
        } else {
            int hours = seconds / 3600;
            int remainingMinutes = (seconds % 3600) / 60;
            if (remainingMinutes == 0) {
                return hours + " hour" + (hours != 1 ? "s" : "");
            } else {
                return hours + "h " + remainingMinutes + "m";
            }
        }
    }
    
    /**
     * Replace placeholders in text with config values
     */
    private String replacePlaceholders(String text, ConfigurationSection config) {
        String result = text;
        
        // Replace all config values that might be used as placeholders
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
                ConfigurationSection scrollConfig = scrollsConfig.getConfigurationSection(scrollType);
                if (scrollConfig != null && scrollConfig.getBoolean("enabled", true)) {
                    scrolls.add(scrollType);
                }
            }
        }
        
        // Sort scrolls by category/type for better organization
        scrolls.sort((a, b) -> {
            String categoryA = getScrollCategory(a);
            String categoryB = getScrollCategory(b);
            
            if (!categoryA.equals(categoryB)) {
                return categoryA.compareTo(categoryB);
            }
            return a.compareTo(b);
        });
        
        return scrolls;
    }
    
    /**
     * Get scroll category for sorting
     */
    private String getScrollCategory(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig != null) {
            List<String> lore = scrollConfig.getStringList("lore");
            if (!lore.isEmpty()) {
                String firstLine = lore.get(0).toLowerCase();
                if (firstLine.contains("offense")) return "1_offense";
                if (firstLine.contains("defense")) return "2_defense";
                if (firstLine.contains("active")) return "3_active";
                if (firstLine.contains("passive")) return "4_passive";
            }
        }
        return "5_other";
    }
    
    /**
     * Get the recipe GUI instance for integration
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
