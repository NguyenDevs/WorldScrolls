package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.managers.ConfigManager;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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

    protected static final int GUI_SIZE = 45;
    protected static final Material BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    protected static final String BORDER_NAME = " ";

    protected static final Set<Integer> BORDER_SLOTS = Set.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    );

    protected static final Set<Integer> CONTENT_SLOTS = Set.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    public BaseGUI(WorldScrolls plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void openGUI(Player player, String title) {
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, ColorUtils.colorize(title));

        fillBorder(inventory);
        fillContent(inventory, player);

        openInventories.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);

    }

    protected void fillBorder(Inventory inventory) {
        ItemStack borderItem = createBorderItem();

        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, borderItem);
        }
    }

    protected ItemStack createBorderItem() {
        ItemStack item = new ItemStack(BORDER_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(BORDER_NAME);
            item.setItemMeta(meta);
        }
        return item;
    }

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

    protected ItemStack createItem(Material material, String name, List<String> lore, String identifier) {
        ItemStack item = createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();

        if (meta != null && identifier != null) {
            meta.setLocalizedName(identifier);
            item.setItemMeta(meta);
        }

        return item;
    }

    protected abstract void fillContent(Inventory inventory, Player player);

    protected abstract void handleClick(Player player, Inventory inventory, int slot, ItemStack clickedItem, boolean isRightClick);

    protected boolean isThisGUI(Inventory inventory, Player player) {
        Inventory playerInventory = openInventories.get(player.getUniqueId());
        return playerInventory != null && playerInventory.equals(inventory);
    }

    public void closeGUI(Player player) {
        openInventories.remove(player.getUniqueId());
    }

    protected ItemStack getBackItem() {
        return createItem(
                Material.ARROW,
                configManager.getMessage("back"),
                configManager.getMessages().getStringList("back-lore")
        );
    }

    protected ItemStack getCloseItem() {
        return createItem(
                Material.BARRIER,
                configManager.getMessage("close"),
                configManager.getMessages().getStringList("close-lore")
        );
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();

        if (!isThisGUI(inventory, player)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= inventory.getSize()) return;

        ItemStack clickedItem = event.getCurrentItem();
        boolean isRightClick = event.getClick().isRightClick();

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);;

        try {
            handleClick(player, inventory, slot, clickedItem, isRightClick);
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cError handling GUI click: " + e.getMessage()));
            e.printStackTrace();
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cAn error occurred while processing your click."));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        if (isThisGUI(inventory, player)) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);;
            closeGUI(player);
        }
    }

    protected List<Integer> getContentSlots() {
        return new ArrayList<>(CONTENT_SLOTS);
    }

    protected List<Integer> getCenteredSlots(int itemCount) {
        List<Integer> contentSlots = getContentSlots();
        List<Integer> centeredSlots = new ArrayList<>();

        if (itemCount <= 0 || itemCount > contentSlots.size()) {
            return contentSlots;
        }

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