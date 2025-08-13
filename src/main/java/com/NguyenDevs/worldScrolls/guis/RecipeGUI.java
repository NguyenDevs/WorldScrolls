package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class RecipeGUI extends BaseGUI {
    
    private String currentScrollType = null;
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, String> viewingScroll = new HashMap<>();
    
    // Recipe display slots (3x3 crafting grid in center)
    private static final int[] RECIPE_SLOTS = {
        11, 12, 13,  // Top row of recipe
        20, 21, 22,  // Middle row of recipe  
        29, 30, 31   // Bottom row of recipe
    };
    
    // Result slot
    private static final int RESULT_SLOT = 24;
    
    // Navigation slots
    private static final int BACK_SLOT = 36;
    private static final int CLOSE_SLOT = 44;
    private static final int PREV_PAGE_SLOT = 37;
    private static final int NEXT_PAGE_SLOT = 43;
    private static final int SCROLL_LIST_SLOT = 40;
    
    public RecipeGUI(WorldScrolls plugin) {
        super(plugin);
    }
    
    /**
     * Open recipe list (all scrolls)
     */
    public void openRecipeList(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        viewingScroll.remove(player.getUniqueId());
        openGUI(player, "&6&lScroll Recipes");
    }
    
    /**
     * Open specific scroll recipe
     */
    public void openScrollRecipe(Player player, String scrollType) {
        viewingScroll.put(player.getUniqueId(), scrollType);
        openGUI(player, "&6&lRecipe: " + getScrollDisplayName(scrollType));
    }
    
    @Override
    protected void fillContent(Inventory inventory, Player player) {
        String viewingScrollType = viewingScroll.get(player.getUniqueId());
        
        if (viewingScrollType != null) {
            // Show specific recipe
            fillRecipeView(inventory, player, viewingScrollType);
        } else {
            // Show recipe list
            fillRecipeList(inventory, player);
        }
        
        // Add navigation items
        addNavigationItems(inventory, player);
    }
    
    /**
     * Fill recipe list view
     */
    private void fillRecipeList(Inventory inventory, Player player) {
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
            ItemStack scrollItem = createScrollRecipeItem(scrollType);
            inventory.setItem(slots.get(slotIndex), scrollItem);
            slotIndex++;
        }
        
        // Add page info
        if (availableScrolls.size() > itemsPerPage) {
            ItemStack pageInfo = createItem(
                Material.PAPER,
                "&e&lPage " + (page + 1) + "/" + ((availableScrolls.size() - 1) / itemsPerPage + 1),
                Arrays.asList(
                    "&7Total recipes: &e" + availableScrolls.size(),
                    "&7Click arrows to navigate"
                )
            );
            inventory.setItem(slots.get(slots.size() - 1), pageInfo);
        }
    }
    
    /**
     * Fill recipe view for specific scroll
     */
    private void fillRecipeView(Inventory inventory, Player player, String scrollType) {
        ConfigurationSection recipeConfig = configManager.getRecipes().getConfigurationSection(scrollType);
        if (recipeConfig == null) return;
        
        // Get recipe pattern
        List<String> recipePattern = recipeConfig.getStringList("recipe");
        ConfigurationSection materials = recipeConfig.getConfigurationSection("material");
        
        if (recipePattern.size() == 3 && materials != null) {
            // Fill 3x3 recipe grid
            for (int row = 0; row < 3; row++) {
                String patternRow = recipePattern.get(row);
                for (int col = 0; col < 3; col++) {
                    if (col < patternRow.length()) {
                        char materialChar = patternRow.charAt(col);
                        if (materialChar != 'X') {
                            String materialName = materials.getString(String.valueOf(materialChar));
                            if (materialName != null) {
                                try {
                                    Material material = Material.valueOf(materialName);
                                    ItemStack item = new ItemStack(material);
                                    inventory.setItem(RECIPE_SLOTS[row * 3 + col], item);
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger().warning("Invalid material in recipe: " + materialName);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Add result item (the scroll itself)
        ItemStack resultScroll = createScrollResultItem(scrollType);
        inventory.setItem(RESULT_SLOT, resultScroll);
        
        // Add crafting info
        ItemStack craftingInfo = createItem(
            Material.CRAFTING_TABLE,
            "&a&lCrafting Information",
            Arrays.asList(
                "&7Place items in a crafting table",
                "&7following the pattern shown above",
                "&7to create this scroll.",
                "",
                "&eScroll: " + getScrollDisplayName(scrollType),
                "&7Craftable: &a" + (isScrollCraftable(scrollType) ? "Yes" : "No")
            )
        );
        inventory.setItem(33, craftingInfo);
    }
    
    /**
     * Add navigation items to inventory
     */
    private void addNavigationItems(Inventory inventory, Player player) {
        String viewingScrollType = viewingScroll.get(player.getUniqueId());
        
        if (viewingScrollType != null) {
            // In recipe view - add back to list button
            inventory.setItem(BACK_SLOT, getBackItem());
        } else {
            // In list view - add page navigation
            List<String> availableScrolls = getAvailableScrolls();
            int page = currentPage.getOrDefault(player.getUniqueId(), 0);
            int maxPages = (availableScrolls.size() - 1) / (CONTENT_SLOTS.size() - 2) + 1;
            
            if (page > 0) {
                ItemStack prevPage = createItem(
                    Material.ARROW,
                    "&e&l← Previous Page",
                    Arrays.asList("&7Go to page " + page)
                );
                inventory.setItem(PREV_PAGE_SLOT, prevPage);
            }
            
            if (page < maxPages - 1) {
                ItemStack nextPage = createItem(
                    Material.ARROW,
                    "&e&lNext Page →",
                    Arrays.asList("&7Go to page " + (page + 2))
                );
                inventory.setItem(NEXT_PAGE_SLOT, nextPage);
            }
        }
        
        // Add scroll list button (always visible)
        ItemStack scrollList = createItem(
            Material.ENCHANTED_BOOK,
            "&b&lAll Recipes",
            Arrays.asList("&7Click to view all scroll recipes")
        );
        inventory.setItem(SCROLL_LIST_SLOT, scrollList);
        
        // Add close button
        inventory.setItem(CLOSE_SLOT, getCloseItem());
    }
    
    @Override
    protected void handleClick(Player player, Inventory inventory, int slot, ItemStack clickedItem, boolean isRightClick) {
        String identifier = "";
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLocalizedName()) {
            identifier = clickedItem.getItemMeta().getLocalizedName();
        }
        
        // Handle navigation
        if (slot == BACK_SLOT) {
            // Go back to recipe list
            viewingScroll.remove(player.getUniqueId());
            openRecipeList(player);
            return;
        }
        
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        
        if (slot == SCROLL_LIST_SLOT) {
            openRecipeList(player);
            return;
        }
        
        if (slot == PREV_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPageNum > 0) {
                currentPage.put(player.getUniqueId(), currentPageNum - 1);
                openRecipeList(player);
            }
            return;
        }
        
        if (slot == NEXT_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            currentPage.put(player.getUniqueId(), currentPageNum + 1);
            openRecipeList(player);
            return;
        }
        
        // Handle scroll recipe selection
        if (identifier.startsWith("recipe:")) {
            String scrollType = identifier.replace("recipe:", "");
            openScrollRecipe(player, scrollType);
        }
    }
    
    /**
     * Create scroll recipe item for list view
     */
    private ItemStack createScrollRecipeItem(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);
        
        List<String> lore = new ArrayList<>();
        lore.add("&7Click to view crafting recipe");
        lore.add("");
        lore.add("&eScroll Type: &f" + scrollType.replace("_", " "));
        lore.add("&eCraftable: " + (isScrollCraftable(scrollType) ? "&aYes" : "&cNo"));
        
        // Add materials preview
        ConfigurationSection recipeConfig = configManager.getRecipes().getConfigurationSection(scrollType);
        if (recipeConfig != null) {
            ConfigurationSection materials = recipeConfig.getConfigurationSection("material");
            if (materials != null) {
                lore.add("");
                lore.add("&6Required Materials:");
                Set<String> usedMaterials = new HashSet<>();
                for (String key : materials.getKeys(false)) {
                    String material = materials.getString(key);
                    if (material != null && !usedMaterials.contains(material)) {
                        lore.add("&8- &7" + material.replace("_", " ").toLowerCase());
                        usedMaterials.add(material);
                    }
                }
            }
        }
        
        return createItem(
            Material.ENCHANTED_BOOK,
            getScrollDisplayName(scrollType),
            lore,
            "recipe:" + scrollType
        );
    }
    
    /**
     * Create scroll result item for recipe view
     */
    private ItemStack createScrollResultItem(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);
        
        List<String> lore = new ArrayList<>();
        lore.add("&a&lCrafting Result");
        lore.add("");
        
        // Add scroll description from config
        List<String> scrollLore = scrollConfig.getStringList("lore");
        for (String line : scrollLore) {
            lore.add(line);
        }
        
        return createItem(
            Material.PAPER,
            scrollConfig.getString("name", scrollType),
            lore
        );
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
        
        Collections.sort(scrolls);
        return scrolls;
    }
    
    /**
     * Get scroll display name
     */
    private String getScrollDisplayName(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig != null) {
            return scrollConfig.getString("name", scrollType);
        }
        return scrollType.replace("_", " ");
    }
    
    /**
     * Check if scroll is craftable
     */
    private boolean isScrollCraftable(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig != null) {
            return scrollConfig.getBoolean("craftable", true);
        }
        return false;
    }
    
    @Override
    public void closeGUI(Player player) {
        super.closeGUI(player);
        currentPage.remove(player.getUniqueId());
        viewingScroll.remove(player.getUniqueId());
    }
}
