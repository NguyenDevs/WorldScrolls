package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class AdminGUI extends BaseGUI {

    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final RecipeGUI recipeGUI;

    private static final int CLOSE_SLOT = 44;
    private static final int PREV_PAGE_SLOT = 37;
    private static final int NEXT_PAGE_SLOT = 43;
    private static final int RECIPE_BOOK_SLOT = 40;
    private static final int RELOAD_SLOT = 42;
    private static final int SCROLL_START_SLOT = 10;

    public AdminGUI(WorldScrolls plugin, RecipeGUI recipeGUI) {
        super(plugin);
        this.recipeGUI = recipeGUI;
    }

    public void openAdminPanel(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        openGUI(player, configManager.getMessage("admin-gui-title"));
    }

    @Override
    protected void fillContent(Inventory inventory, Player player) {
        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = 28;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableScrolls.size());

        int currentSlot = SCROLL_START_SLOT;
        for (int i = startIndex; i < endIndex; i++) {
            String scrollType = availableScrolls.get(i);
            ItemStack scrollItem = createAdminScrollItem(scrollType);
            inventory.setItem(currentSlot, scrollItem);

            currentSlot++;
            if (currentSlot % 9 == 8) currentSlot += 2;
            if (currentSlot >= 37) break;
        }

        if (availableScrolls.size() > itemsPerPage) {
            ItemStack pageInfo = createItem(
                    Material.PAPER,
                    "&ePage " + (page + 1) + "/" + ((availableScrolls.size() - 1) / itemsPerPage + 1),
                    Arrays.asList("&7Total scrolls: &e" + availableScrolls.size(), "&7Use arrows to navigate pages")
            );
            inventory.setItem(31, pageInfo);
        }

        addAdminNavigationItems(inventory, player);
    }

    private void addAdminNavigationItems(Inventory inventory, Player player) {
        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = 28;
        int maxPages = availableScrolls.size() > 0 ? (availableScrolls.size() - 1) / itemsPerPage + 1 : 1;

        if (page > 0) {
            ItemStack prevPage = createItem(
                    Material.ARROW,
                    configManager.getMessage("next-page"),
                    configManager.getMessages().getStringList("next-page-lore" + ": " + page)
            );
            inventory.setItem(PREV_PAGE_SLOT, prevPage);
        }

        if (page < maxPages - 1) {
            ItemStack nextPage = createItem(
                    Material.ARROW,
                    configManager.getMessage("previous-page"),
                    configManager.getMessages().getStringList("previous-page-lore" + ": " + (page+2))
            );
            inventory.setItem(NEXT_PAGE_SLOT, nextPage);
        }

        ItemStack recipeBook = createItem(
                Material.ENCHANTED_BOOK,
                configManager.getMessage("recipe-book"),
                configManager.getMessages().getStringList("recipe-book-lore")
        );
        inventory.setItem(RECIPE_BOOK_SLOT, recipeBook);

        ItemStack reload = createItem(
                Material.COMMAND_BLOCK,
                configManager.getMessage("reload-item-title"),
                configManager.getMessages().getStringList("reload-item-lore")
        );
        inventory.setItem(RELOAD_SLOT, reload);

        inventory.setItem(CLOSE_SLOT, getCloseItem());
    }

    @Override
    protected void handleClick(Player player, Inventory inventory, int slot, ItemStack clickedItem, boolean isRightClick) {
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (slot == RECIPE_BOOK_SLOT) {
            player.closeInventory();
            recipeGUI.openFromAdminMenu(player);

            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
            return;
        }

        if (slot == RELOAD_SLOT) {
            handleReloadPlugin(player);
            return;
        }

        if (slot == PREV_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPageNum > 0) {
                currentPage.put(player.getUniqueId(), currentPageNum - 1);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
                openAdminPanel(player);
            }
            return;
        }

        if (slot == NEXT_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            List<String> availableScrolls = getAvailableScrolls();
            int itemsPerPage = 28;
            int maxPages = availableScrolls.size() > 0 ? (availableScrolls.size() - 1) / itemsPerPage + 1 : 1;
            if (currentPageNum < maxPages - 1) {
                currentPage.put(player.getUniqueId(), currentPageNum + 1);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
                openAdminPanel(player);
            }
            return;
        }

        if (isScrollDisplaySlot(slot)) {
            String scrollType = getScrollTypeFromClick(player, slot, clickedItem);
            if (scrollType != null) {
                if (isRightClick) {
                    player.closeInventory();
                    recipeGUI.openScrollRecipeFromAdmin(player, scrollType);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
                } else {
                    giveScrollToAdmin(player, scrollType);
                }
            }
        }
    }

    private boolean isScrollDisplaySlot(int slot) {
        if (slot < SCROLL_START_SLOT || slot >= 37) return false;
        int col = slot % 9;
        return col != 0 && col != 8;
    }

    private String getScrollTypeFromClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLocalizedName()) {
            String identifier = clickedItem.getItemMeta().getLocalizedName();
            if (identifier.startsWith("admin_scroll:")) {
                return identifier.replace("admin_scroll:", "");
            }
        }

        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = 28;
        int itemIndex = getItemIndexFromSlot(slot) + (page * itemsPerPage);
        if (itemIndex >= 0 && itemIndex < availableScrolls.size()) return availableScrolls.get(itemIndex);
        return null;
    }

    private int getItemIndexFromSlot(int slot) {
        int row = (slot / 9) - 1;
        int col = (slot % 9) - 1;
        if (row < 0 || row > 2 || col < 0 || col > 6) return -1;
        return row * 7 + col;
    }

    private void giveScrollToAdmin(Player admin, String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) {
            admin.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cScroll configuration not found: " + scrollType));
            return;
        }
        if (!scrollConfig.getBoolean("enabled", true)) {
            admin.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("scroll-disabled")));
            return;
        }

        ItemStack scrollItem = createScrollItem(scrollType, scrollConfig);
        if (scrollItem != null) {
            if (admin.getInventory().firstEmpty() == -1) {
                admin.getWorld().dropItem(admin.getLocation(), scrollItem);
                admin.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("full-item")));
            } else {
                admin.getInventory().addItem(scrollItem);
                admin.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("receive") + ": " + scrollConfig.getString("name", scrollType)));
            }
            admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
        } else {
            admin.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cFailed to create scroll item!"));
            admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    private void handleReloadPlugin(Player admin) {
        try {
            configManager.reloadConfigs();
            admin.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("plugin-reloaded")));
            admin.playSound(admin.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.0f);
            openAdminPanel(admin);
        } catch (Exception e) {
            admin.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cReload failed, please check the console!"));
            admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    private ItemStack createAdminScrollItem(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);

        List<String> lore = new ArrayList<>();
        lore.add("&e▶ Left-Click: &7Give to yourself");
        lore.add("&e▶ Right-Click: &7View recipe");
        lore.add("");

        List<String> originalLore = scrollConfig.getStringList("lore");
        int linesToShow = Math.min(20, originalLore.size());
        for (int i = 0; i < linesToShow; i++) {
            String line = replacePlaceholders(originalLore.get(i), scrollConfig);
            lore.add(line);
        }

        lore.add("");
        lore.add("&6⏱ Cooldown: &e" + formatCooldown(scrollConfig.getInt("cooldown", 0)));
        lore.add("&7Enabled: " + (scrollConfig.getBoolean("enabled", true) ? "&aYes" : "&cNo"));
        lore.add("&7Craftable: " + (scrollConfig.getBoolean("craftable", true) ? "&aYes" : "&cNo"));

        Material iconMaterial = getScrollIcon(scrollType);

        return createItem(
                iconMaterial,
                scrollConfig.getString("name", scrollType),
                lore,
                "admin_scroll:" + scrollType
        );
    }

    private ItemStack createScrollItem(String scrollType, ConfigurationSection scrollConfig) {
        try {
            String materialName = scrollConfig.getString("material", "PAPER");
            Material mat;
            try {
                mat = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                mat = Material.PAPER;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                String name = scrollConfig.getString("name", scrollType);
                meta.setDisplayName(ColorUtils.colorize(name));

                List<String> lore = scrollConfig.getStringList("lore");
                if (!lore.isEmpty()) {
                    List<String> colorizedLore = new ArrayList<>();
                    for (String line : lore) {
                        String processedLine = replacePlaceholders(line, scrollConfig);
                        colorizedLore.add(ColorUtils.colorize(processedLine));
                    }
                    meta.setLore(colorizedLore);
                }

                meta.setLocalizedName("worldscrolls:" + scrollType);

                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "scroll_type"),
                        PersistentDataType.STRING,
                        scrollType
                );

                item.setItemMeta(meta);
            }
            return item;
        } catch (Exception e) {
            return null;
        }
    }



    private Material getScrollIcon(String scrollType) {
        switch (scrollType.toLowerCase()) {
            case "scroll_of_thorn": return Material.CACTUS;
            case "scroll_of_cyclone": return Material.GHAST_TEAR;
            case "scroll_of_exit": return Material.ENDER_PEARL;
            case "scroll_of_frostbite": return Material.BLUE_ICE;
            case "scroll_of_gravitation": return Material.OBSIDIAN;
            case "scroll_of_invisibility": return Material.FERMENTED_SPIDER_EYE;
            case "scroll_of_meteor": return Material.FIRE_CHARGE;
            case "scroll_of_phoenix": return Material.GLISTERING_MELON_SLICE;
            case "scroll_of_radiation": return Material.SPIDER_EYE;
            case "scroll_of_solar": return Material.GLOWSTONE_DUST;
            case "scroll_of_thunder": return Material.LIGHTNING_ROD;
            default: return Material.PAPER;
        }
    }

    private String formatCooldown(int seconds) {
        if (seconds == 0) return "None";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
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

    private List<String> getAvailableScrolls() {
        List<String> scrolls = new ArrayList<>();
        ConfigurationSection scrollsConfig = configManager.getScrolls();
        if (scrollsConfig != null) scrolls.addAll(scrollsConfig.getKeys(false));
        scrolls.sort(String.CASE_INSENSITIVE_ORDER);
        return scrolls;
    }

    public RecipeGUI getRecipeGUI() {
        return recipeGUI;
    }

    @Override
    public void closeGUI(Player player) {
        super.closeGUI(player);
        currentPage.remove(player.getUniqueId());
    }
}
