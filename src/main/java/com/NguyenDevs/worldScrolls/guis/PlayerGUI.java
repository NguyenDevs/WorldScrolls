package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerGUI extends BaseGUI {

    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final RecipeGUI recipeGUI;

    private static final int CLOSE_SLOT = 44;
    private static final int PREV_PAGE_SLOT = 37;
    private static final int NEXT_PAGE_SLOT = 43;
    private static final int RECIPE_BOOK_SLOT = 40;
    private static final int SCROLL_START_SLOT = 10;

    public PlayerGUI(WorldScrolls plugin, RecipeGUI recipeGUI) {
        super(plugin);
        this.recipeGUI = recipeGUI;
    }

    public void openScrollMenu(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        openGUI(player, configManager.getMessage("player-gui-title"));
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
            ItemStack scrollItem = createScrollDisplayItem(scrollType, player);
            inventory.setItem(currentSlot, scrollItem);

            currentSlot++;
            if (currentSlot % 9 == 8) {
                currentSlot += 2;
            }
            if (currentSlot >= 37) break;
        }

        if (availableScrolls.size() > itemsPerPage) {
            ItemStack pageInfo = createItem(
                    Material.PAPER,
                    "&ePage " + (page + 1) + "/" + ((availableScrolls.size() - 1) / itemsPerPage + 1),
                    Arrays.asList(
                            "&7Total scrolls: &e" + availableScrolls.size(),
                            "&7Use arrows to navigate pages"
                    )
            );
            inventory.setItem(31, pageInfo);
        }

        addNavigationItems(inventory, player);
    }

    private void addNavigationItems(Inventory inventory, Player player) {
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
            recipeGUI.openFromPlayerMenu(player);

            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
            return;
        }

        if (slot == PREV_PAGE_SLOT) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPageNum > 0) {
                currentPage.put(player.getUniqueId(), currentPageNum - 1);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
                openScrollMenu(player);
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
                openScrollMenu(player);
            }
            return;
        }

        if (isScrollDisplaySlot(slot)) {
            String scrollType = getScrollTypeFromClick(player, slot, clickedItem);
            if (scrollType != null) {
                player.closeInventory();
                recipeGUI.openScrollRecipeFromPlayer(player, scrollType);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
            }
        }
    }


    private boolean isScrollDisplaySlot(int slot) {
        if (slot < SCROLL_START_SLOT || slot >= 37) return false;

        int row = slot / 9;
        int col = slot % 9;
        return col != 0 && col != 8;
    }

    private String getScrollTypeFromClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLocalizedName()) {
            String identifier = clickedItem.getItemMeta().getLocalizedName();
            if (identifier.startsWith("scroll:")) {
                return identifier.replace("scroll:", "");
            }
        }

        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = 28;

        int itemIndex = getItemIndexFromSlot(slot) + (page * itemsPerPage);

        if (itemIndex >= 0 && itemIndex < availableScrolls.size()) {
            return availableScrolls.get(itemIndex);
        }

        return null;
    }

    private int getItemIndexFromSlot(int slot) {
        int row = (slot / 9) - 1;
        int col = (slot % 9) - 1;

        if (row < 0 || row > 2 || col < 0 || col > 6) return -1;

        return row * 7 + col;
    }

    private ItemStack createScrollDisplayItem(String scrollType, Player player) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);

        List<String> lore = new ArrayList<>();

        List<String> originalLore = scrollConfig.getStringList("lore");
        for (String line : originalLore) {
            String processedLine = replacePlaceholders(line, scrollConfig);
            lore.add(processedLine);
        }

        lore.add("");

        int cooldownSeconds = scrollConfig.getInt("cooldown", 0);
        if (cooldownSeconds > 0) {
            String cooldownText = formatCooldown(cooldownSeconds);
            lore.add(configManager.getMessage("player-gui-cooldown") + cooldownText);
        } else {
            lore.add(configManager.getMessage("player-gui-cooldown") + "None");
        }

        lore.add(configManager.getMessage("player-gui-available"));

        lore.add("");

        boolean craftable = scrollConfig.getBoolean("craftable", true);
        lore.add(configManager.getMessage("player-gui-craftable") + (craftable ? configManager.getMessage("player-gui-craftable-yes") : configManager.getMessage("player-gui-craftable-no")));

        lore.add("");
        lore.add(configManager.getMessage("player-gui-view-recipe"));

        Material iconMaterial = getScrollIcon(scrollType);

        return createItem(
                iconMaterial,
                scrollConfig.getString("name", scrollType),
                lore,
                "scroll:" + scrollType
        );
    }

    private Material getScrollIcon(String scrollType) {
        switch (scrollType.toLowerCase()) {
            case "scroll_of_thorn":
                return Material.CACTUS;
            case "scroll_of_cyclone.yml":
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

        if (scrollsConfig != null) {
            for (String scrollType : scrollsConfig.getKeys(false)) {
                ConfigurationSection scrollConfig = scrollsConfig.getConfigurationSection(scrollType);
                if (scrollConfig != null && scrollConfig.getBoolean("enabled", true)) {
                    scrolls.add(scrollType);
                }
            }
        }

        scrolls.sort(String.CASE_INSENSITIVE_ORDER);
        return scrolls;
    }

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

    public RecipeGUI getRecipeGUI() {
        return recipeGUI;
    }

    @Override
    public void closeGUI(Player player) {
        super.closeGUI(player);
        currentPage.remove(player.getUniqueId());
    }
}