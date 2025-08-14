package com.NguyenDevs.worldScrolls.guis;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class RecipeGUI extends BaseGUI implements Listener, InventoryHolder {

    private enum MenuOrigin { PLAYER, ADMIN }

    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, String> viewingScroll = new HashMap<>();
    private final Map<UUID, MenuOrigin> origin = new HashMap<>();

    private static final int[] RECIPE_SLOTS = {
            11, 12, 13,
            20, 21, 22,
            29, 30, 31
    };

    private static final int RESULT_SLOT = 24;
    private static final int BACK_SLOT = 36;
    private static final int CLOSE_SLOT = 44;
    private static final int PREV_PAGE_SLOT = 37;
    private static final int NEXT_PAGE_SLOT = 43;
    private static final int SCROLL_LIST_SLOT = 40;
    private static final int SCROLL_START_SLOT = 10;

    public RecipeGUI(WorldScrolls plugin) {
        super(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openFromPlayerMenu(Player player) {
        origin.put(player.getUniqueId(), MenuOrigin.PLAYER);
        openRecipeList(player);
    }

    public void openFromAdminMenu(Player player) {
        origin.put(player.getUniqueId(), MenuOrigin.ADMIN);
        openRecipeList(player);
    }

    public void openScrollRecipeFromPlayer(Player player, String scrollType) {
        origin.put(player.getUniqueId(), MenuOrigin.PLAYER);
        openScrollRecipe(player, scrollType);
    }

    public void openScrollRecipeFromAdmin(Player player, String scrollType) {
        origin.put(player.getUniqueId(), MenuOrigin.ADMIN);
        openScrollRecipe(player, scrollType);
    }

    public void openRecipeList(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        viewingScroll.remove(player.getUniqueId());
        openGUI(player, configManager.getMessage("recipe-gui-title"));
    }

    public void openScrollRecipe(Player player, String scrollType) {
        viewingScroll.put(player.getUniqueId(), scrollType);
        openGUI(player, configManager.getMessage("recipe-gui-title") + " ► " + getScrollDisplayName(scrollType));
    }

    @Override
    protected void fillContent(Inventory inventory, Player player) {
        String viewingScrollType = viewingScroll.get(player.getUniqueId());
        if (viewingScrollType != null) {
            fillRecipeView(inventory, player, viewingScrollType);
        } else {
            fillRecipeList(inventory, player);
        }
        addNavigationItems(inventory, player);
    }

    private void fillRecipeList(Inventory inventory, Player player) {
        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = 28;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableScrolls.size());

        int currentSlot = SCROLL_START_SLOT;
        for (int i = startIndex; i < endIndex; i++) {
            String scrollType = availableScrolls.get(i);
            ItemStack scrollItem = createScrollRecipeItem(scrollType);
            inventory.setItem(currentSlot, scrollItem);

            currentSlot++;
            if (currentSlot % 9 == 8) currentSlot += 2;
            if (currentSlot >= 37) break;
        }

        if (availableScrolls.size() > itemsPerPage) {
            ItemStack pageInfo = createItem(
                    Material.PAPER,
                    "&ePage " + (page + 1) + "/" + ((availableScrolls.size() - 1) / itemsPerPage + 1),
                    Arrays.asList("&7Total recipes: &e" + availableScrolls.size(), "&7Click arrows to navigate")
            );
            inventory.setItem(31, pageInfo);
        }
    }

    private void fillRecipeView(Inventory inventory, Player player, String scrollType) {
        ConfigurationSection recipeConfig = configManager.getRecipes().getConfigurationSection(scrollType);
        if (recipeConfig == null) {
            List<String> lore = configManager.getMessages()
                    .getStringList("recipe-not-found-lore")
                    .stream()
                    .map(line -> line.replace("%scroll%", scrollType))
                    .collect(Collectors.toList());

            ItemStack errorItem = createItem(
                    Material.BARRIER,
                    configManager.getMessage("recipe-not-found"), lore
            );
            inventory.setItem(21, errorItem);
            return;
        }

        List<String> recipePattern = recipeConfig.getStringList("recipe");
        ConfigurationSection materials = recipeConfig.getConfigurationSection("material");

        if (recipePattern.size() == 3 && materials != null) {
            for (int row = 0; row < 3; row++) {
                String patternRow = recipePattern.get(row);
                for (int col = 0; col < 3; col++) {
                    if (col < patternRow.length()) {
                        char materialChar = patternRow.charAt(col);
                        if (materialChar != 'X' && materialChar != ' ') {
                            String materialName = materials.getString(String.valueOf(materialChar));
                            if (materialName != null) {
                                try {
                                    Material material = Material.valueOf(materialName.toUpperCase());
                                    ItemStack item = new ItemStack(material);
                                    inventory.setItem(RECIPE_SLOTS[row * 3 + col], item);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }
            }
        }

        ItemStack resultScroll = createScrollResultItem(scrollType);
        inventory.setItem(RESULT_SLOT, resultScroll);
    }

    private void addNavigationItems(Inventory inventory, Player player) {
        String viewingScrollType = viewingScroll.get(player.getUniqueId());

        if (viewingScrollType != null) {
            inventory.setItem(BACK_SLOT, getBackItem());
        } else {
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
        }

        ItemStack recipeBook = createItem(
                Material.ENCHANTED_BOOK,
                configManager.getMessage("recipe-book"),
                configManager.getMessages().getStringList("recipe-book-lore")
        );
        inventory.setItem(SCROLL_LIST_SLOT, recipeBook);

        inventory.setItem(CLOSE_SLOT, getCloseItem());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isOurGUI(event.getInventory(), player)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            return;
        }
        if (event.isShiftClick() || event.getClick().isKeyboardClick() || event.getClick().isCreativeAction()) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        String viewingScrollType = viewingScroll.get(player.getUniqueId());

        if (viewingScrollType != null) {
            handleRecipeViewClick(player, slot, clickedItem);
        } else {
            handleRecipeListClick(player, slot, clickedItem);
        }
    }

    private boolean isOurGUI(Inventory inventory, Player player) {
        String title = inventory.getViewers().contains(player) ? player.getOpenInventory().getTitle() : "";
        return title.contains(configManager.getMessage("recipe-gui-title")) || title.contains(configManager.getMessage("recipe-gui-title") + ": ") || viewingScroll.containsKey(player.getUniqueId());
    }

    private void handleRecipeListClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (slot == PREV_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPageNum > 0) {
                currentPage.put(player.getUniqueId(), currentPageNum - 1);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
                openRecipeList(player);
            }
            return;
        }

        if (slot == NEXT_PAGE_SLOT && clickedItem.getType() == Material.ARROW) {
            int currentPageNum = currentPage.getOrDefault(player.getUniqueId(), 0);
            List<String> availableScrolls = getAvailableScrolls();
            int itemsPerPage = 28;
            int maxPages = availableScrolls.size() > 0 ? (availableScrolls.size() - 1) / itemsPerPage + 1 : 1;
            if (currentPageNum < maxPages - 1) {
                currentPage.put(player.getUniqueId(), currentPageNum + 1);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
                openRecipeList(player);
            }
            return;
        }

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.ENCHANTED_BOOK &&
                clickedItem.hasItemMeta() &&
                clickedItem.getItemMeta().hasLocalizedName()) {
            String identifier = clickedItem.getItemMeta().getLocalizedName();
            if (identifier.startsWith("recipe:")) {
                String scrollType = identifier.replace("recipe:", "");
                openScrollRecipe(player, scrollType);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
                return;
            }
        }

        List<String> availableScrolls = getAvailableScrolls();
        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;

        int relativeSlot = getRelativeSlotIndex(slot);
        if (relativeSlot >= 0 && startIndex + relativeSlot < availableScrolls.size()) {
            String scrollType = availableScrolls.get(startIndex + relativeSlot);
            openScrollRecipe(player, scrollType);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
        }
    }

    private void handleRecipeViewClick(Player player, int slot, ItemStack clickedItem) {
        if (slot == BACK_SLOT) {
            MenuOrigin o = origin.get(player.getUniqueId());
            viewingScroll.remove(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);

            if (o == MenuOrigin.ADMIN) {
                plugin.getGuiManager().getAdminGUI().openAdminPanel(player);
            } else {
                plugin.getGuiManager().getPlayerGUI().openScrollMenu(player);
            }
            return;
        }

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        if (slot == SCROLL_LIST_SLOT && clickedItem.getType() == Material.ENCHANTED_BOOK) {
            viewingScroll.remove(player.getUniqueId());
            openRecipeList(player);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.05f);
        }
    }


    private int getRelativeSlotIndex(int slot) {
        int[] validSlots = new int[28];
        int index = 0;
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 7; col++) {
                if (index < 28) {
                    validSlots[index] = row * 9 + col;
                    if (validSlots[index] == slot) return index;
                    index++;
                }
            }
        }
        return -1;
    }

    @Override
    protected void handleClick(Player player, Inventory inventory, int slot, ItemStack clickedItem, boolean isRightClick) {}


    private ItemStack createScrollRecipeItem(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);

        List<String> materialList = new ArrayList<>();
        ConfigurationSection recipeConfig = configManager.getRecipes().getConfigurationSection(scrollType);
        if (recipeConfig != null) {
            ConfigurationSection materials = recipeConfig.getConfigurationSection("material");
            if (materials != null) {
                Set<String> usedMaterials = new HashSet<>();
                for (String key : materials.getKeys(false)) {
                    String material = materials.getString(key);
                    if (material != null && usedMaterials.add(material)) {
                        materialList.add("&8- &7" + material.replace("_", " ").toLowerCase());
                    }
                }
            }
        }

        String materialsPlaceholder = materialList.isEmpty() ? "&7None" : String.join("\n", materialList);

        List<String> lore = new ArrayList<>();
        for (String line : configManager.getMessages().getStringList("recipe-item-lore")) {
            line = line.replace("%scroll%", getScrollDisplayName(scrollType));
            line = line.replace("%type%", scrollType.replace("_", " "));
            line = line.replace("%craftable%", isScrollCraftable(scrollType) ? "&aYes" : "&cNo");

            if (line.contains("%materials%")) {
                if (!materialList.isEmpty()) {
                    lore.addAll(materialList);
                }
            } else {
                lore.add(line);
            }
        }

        return createItem(Material.ENCHANTED_BOOK, getScrollDisplayName(scrollType), lore, "recipe:" + scrollType);
    }


    private ItemStack createScrollResultItem(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) return new ItemStack(Material.PAPER);

        List<String> lore = new ArrayList<>();
        for (String line : scrollConfig.getStringList("lore")) {
            lore.add(ColorUtils.colorize(replacePlaceholders(line, scrollConfig)));
        }
        return createItem(Material.PAPER,
                ColorUtils.colorize(scrollConfig.getString("name", scrollType)),
                lore);
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

        return result;
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

    private String getScrollDisplayName(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig != null) return scrollConfig.getString("name", scrollType);
        return scrollType.replace("_", " ");
    }

    private boolean isScrollCraftable(String scrollType) {
        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig != null) return scrollConfig.getBoolean("craftable", true);
        return false;
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    @Override
    public void closeGUI(Player player) {
        super.closeGUI(player);
        currentPage.remove(player.getUniqueId());
        viewingScroll.remove(player.getUniqueId());
        origin.remove(player.getUniqueId());
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
