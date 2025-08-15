package com.NguyenDevs.worldScrolls.commands;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.managers.ConfigManager;
import com.NguyenDevs.worldScrolls.managers.RecipeManager;
import com.NguyenDevs.worldScrolls.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class WorldScrollsCommand implements CommandExecutor {
    
    private final WorldScrolls plugin;
    private final ConfigManager configManager;
    private final RecipeManager recipeManager;
    
    public WorldScrollsCommand(WorldScrolls plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.recipeManager = plugin.getRecipeManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            if(sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.1f);
            }
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "menu":
                return handleMenuCommand(sender, args);
            case "recipe":
                return handleRecipeCommand(sender, args);
            case "admin":
                return handleAdminCommand(sender, args);
            case "give":
                return handleGiveCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender, args);
            case "help":
                sendHelpMessage(sender);
                return true;
            default:
                sender.sendMessage(configManager.getMessage("unknown-command"));
                return true;
        }
    }

    private boolean handleMenuCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("&7[&dWorld&5Scroll&7]" + " " + configManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (configManager.isWorldDisabled(player.getWorld().getName())) {
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("scroll-blocked-world")));
            return true;
        }

        plugin.getGUIManager().openPlayerMenu(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        return true;
    }

    private boolean handleRecipeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("&7[&dWorld&5Scroll&7]" + " " + configManager.getMessage("player-only"));
            return true;
        }
        
        Player player = (Player) sender;

        if (configManager.isWorldDisabled(player.getWorld().getName())) {
            player.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("scroll-blocked-world")));
            return true;
        }

        plugin.getGUIManager().openRecipeBook(player);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 0.8f);
        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldscrolls.admin")) {
            sender.sendMessage(configManager.getMessage("prefix") + " " + configManager.getMessage("no-permission"));
            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("&7[&dWorld&5Scroll&7]" + " " + configManager.getMessage("player-only"));
            return true;
        }
        
        Player player = (Player) sender;

        plugin.getGUIManager().openAdminPanel(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        return true;
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldscrolls.give")) {
            sender.sendMessage(configManager.getMessage("prefix") + " " + configManager.getMessage("no-permission"));
            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cUsage: /wsc give <player> <scroll> [amount]"));
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&7Available scrolls:"));

            ConfigurationSection scrolls = configManager.getScrolls();
            if (scrolls != null) {
                for (String scrollKey : scrolls.getKeys(false)) {
                    if (scrolls.getBoolean(scrollKey + ".enabled", true)) {
                        sender.sendMessage(ColorUtils.colorize("&8- &e" + scrollKey));
                    }
                }
            }
            return true;
        }
        
        String targetPlayerName = args[1];
        String scrollType = args[2].toLowerCase();
        int amount = 1;
        
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cAmount must be between 1 and 64!"));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cInvalid amount! Please enter a valid number."));
                return true;
            }
        }
        
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cPlayer not found: " + targetPlayerName));
            return true;
        }

        ConfigurationSection scrollConfig = configManager.getScrolls().getConfigurationSection(scrollType);
        if (scrollConfig == null) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cScroll not found: " + scrollType));
            return true;
        }
        
        if (!scrollConfig.getBoolean("enabled", true)) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cScroll is disabled: " + scrollType));
            return true;
        }

        ItemStack scrollItem = createScrollItem(scrollType, scrollConfig, amount);
        if (scrollItem != null) {
            if (targetPlayer.getInventory().firstEmpty() == -1) {
                targetPlayer.getWorld().dropItem(targetPlayer.getLocation(), scrollItem);
                targetPlayer.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("full-item")));
            } else {
                targetPlayer.getInventory().addItem(scrollItem);
            }
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(amount));
            placeholders.put("scroll", scrollConfig.getString("name", scrollType));
            placeholders.put("player", targetPlayer.getName());
            
            targetPlayer.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + configManager.getMessage("receive") + ": " + amount + "x " + scrollConfig.getString("name", scrollType) + "&a!"));
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " +  configManager.getMessage("give") + ": " +  targetPlayer.getName() + " " + amount + "x " + scrollConfig.getString("name", scrollType) + "!"));

            targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
            if (sender instanceof Player && !sender.equals(targetPlayer)) {
                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
            }
        } else {
            sender.sendMessage(ColorUtils.colorize("&7[&dWorld&5Scroll&7]" + " " + "&cFailed to create scroll item!"));
        }
        
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldscrolls.reload")) {
            sender.sendMessage(configManager.getMessage("prefix") + " " + configManager.getMessage("no-permission"));
            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return true;
        }
        
        try {
            configManager.reloadConfigs();
            recipeManager.loadRecipes();
            sender.sendMessage(configManager.getMessage("prefix") + " " + configManager.getMessage("plugin-reloaded"));

            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.0f);
            }
        } catch (Exception e) {
            sender.sendMessage(ColorUtils.colorize(configManager.getMessage("prefix") + " " + "&cFailed to reload plugin! Check console for errors."));
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to reload plugin: " + e.getMessage()));
            e.printStackTrace();

            if (sender instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        }
        
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(configManager.getMessage("command-help.header"));
        sender.sendMessage("");
        
        sender.sendMessage(configManager.getMessage("command-help.user-commands"));
        sender.sendMessage(configManager.getMessage("command-help.menu"));
        sender.sendMessage(configManager.getMessage("command-help.recipe"));
        sender.sendMessage("");
        
        if (sender.hasPermission("worldscrolls.admin") || sender.hasPermission("worldscrolls.give") || sender.hasPermission("worldscrolls.reload")) {
            sender.sendMessage(configManager.getMessage("command-help.admin-commands"));
            
            if (sender.hasPermission("worldscrolls.admin")) {
                sender.sendMessage(configManager.getMessage("command-help.admin"));
            }
            if (sender.hasPermission("worldscrolls.give")) {
                sender.sendMessage(configManager.getMessage("command-help.give"));
            }
            if (sender.hasPermission("worldscrolls.reload")) {
                sender.sendMessage(configManager.getMessage("command-help.reload"));
            }
            sender.sendMessage("");
        }
        
        sender.sendMessage(configManager.getMessage("command-help.footer"));
    }

    private ItemStack createScrollItem(String scrollType, ConfigurationSection scrollConfig, int amount) {
        try {
            String materialName = scrollConfig.getString("material", "PAPER");
            Material mat;
            try {
                mat = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                mat = Material.PAPER;
            }

            ItemStack item = new ItemStack(mat, amount);
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
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7[&dWorld&5Scroll&7] &cFailed to create scroll item for: " + scrollType));
            e.printStackTrace();
            return null;
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
}
