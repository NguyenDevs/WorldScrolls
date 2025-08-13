package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.managers.ConfigManager;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public abstract class BaseGUI implements Listener {
    
    protected final WorldScrolls plugin;
    protected final ConfigManager configManager;
    protected final Map<UUID, Inventory> openInventories = new HashMap<>();
    
    // GUI Constants
    protected static final int GUI_SIZE = 45; // 5 rows
    protected static final Material BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    protected static final String BORDER_NAME = " ";
    
    // Border slots (edges of 45-slot inventory)
    protected static final Set<Integer> BORDER_SLOTS = Set.of(
        0, 1, 2, 3, 4, 5, 6, 7, 8,        // Top row
        9, 17,                            // Left and right of second row
        18, 26,                           // Left and right of third row
        27, 35,                           // Left and right of fourth row
        36, 37, 38, 39, 40, 41, 42, 43, 44 // Bottom row
    );
    
    // Content slots (middle area)
    protected static final Set<Integer> CONTENT_SLOTS = Set.of(
        10, 11, 12, 13, 14, 15, 16,       // Second row middle
        19, 20, 21, 22, 23, 24, 25,       // Third row middle
        28, 29, 30, 31, 32, 33, 34        // Fourth row middle
    );
    
    public BaseGUI(WorldScrolls plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }
    
    /**
     * Create and open a GUI for a player
     */
    public void openGUI(Player player, String title) {
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, ColorUtils.colorize(title));
        
        // Add border decorations
        fillBorder(inventory);
        
        // Fill content (implemented by subclasses)
        fillContent(inventory, player);
        
        // Store and open inventory
        openInventories.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }
    
    /**
     * Fill border slots with gray stained glass panes
     */
    protected void fillBorder(Inventory inventory) {
        ItemStack borderItem = createBorderItem();
        
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, borderItem);
        }
    }
    
    /**
     * Create border item (gray stained glass pane)
     */
    protected ItemStack createBorderItem() {
        ItemStack item = new ItemStack(BORDER_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(BORDER_NAME);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Create an item with name and lore
     */
    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            
            if (lore != null && !lore.isEmpty()) {
                List<String> colorizedLore = new ArrayList<>();
                for (String line : lore) {
                    colorizedLore.add(ColorUtils.colorize(line));
                }
                meta.setLore(colorizedLore);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Create an item with custom model data for identification
     */
    protected ItemStack createItem(Material material, String name, List<String> lore, String identifier) {
        ItemStack item = createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null && identifier != null) {
            meta.setLocalizedName(identifier);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Abstract method to fill content - implemented by subclasses
     */
    protected abstract void fillContent(Inventory inventory, Player player);
    
    /**
     * Handle GUI clicks - implemented by subclasses
     */
    protected abstract void handleClick(Player player, Inventory inventory, int slot, ItemStack clickedItem, boolean isRightClick);
    
    /**
     * Check if an inventory belongs to this GUI
     */
    protected boolean isThisGUI(Inventory inventory, Player player) {
        return openInventories.containsKey(player.getUniqueId()) && 
               openInventories.get(player.getUniqueId()).equals(inventory);
    }
    
    /**
     * Close GUI for a player
     */
    public void closeGUI(Player player) {
        openInventories.remove(player.getUniqueId());
    }
    
    /**
     * Get navigation item for going back
     */
    protected ItemStack getBackItem() {
        return createItem(
            Material.ARROW,
            "&c&l← Back",
            Arrays.asList("&7Click to go back to previous menu")
        );
    }
    
    /**
     * Get close item for closing GUI
     */
    protected ItemStack getCloseItem() {
        return createItem(
            Material.BARRIER,
            "&c&lClose",
            Arrays.asList("&7Click to close this menu")
        );
    }
    
    /**
     * Handle inventory click events
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        
        if (!isThisGUI(inventory, player)) return;
        
        event.setCancelled(true); // Cancel all clicks in GUI
        
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        boolean isRightClick = event.getClick().isRightClick();
        
        // Ignore clicks on border items or empty slots
        if (clickedItem == null || clickedItem.getType() == Material.AIR || 
            BORDER_SLOTS.contains(slot)) {
            return;
        }
        
        // Handle click
        handleClick(player, inventory, slot, clickedItem, isRightClick);
    }
    
    /**
     * Handle inventory close events
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        
        if (isThisGUI(inventory, player)) {
            closeGUI(player);
        }
    }
    
    /**
     * Get all content slots as a list for easy iteration
     */
    protected List<Integer> getContentSlots() {
        return new ArrayList<>(CONTENT_SLOTS);
    }
    
    /**
     * Center content in available slots
     */
    protected List<Integer> getCenteredSlots(int itemCount) {
        List<Integer> contentSlots = getContentSlots();
        List<Integer> centeredSlots = new ArrayList<>();
        
        if (itemCount <= 0 || itemCount > contentSlots.size()) {
            return contentSlots;
        }
        
        // Calculate starting position to center items
        int totalSlots = contentSlots.size();
        int startIndex = (totalSlots - itemCount) / 2;
        
        for (int i = 0; i < itemCount; i++) {
            if (startIndex + i < contentSlots.size()) {
                centeredSlots.add(contentSlots.get(startIndex + i));
            }
        }
        
        return centeredSlots;
    }
}