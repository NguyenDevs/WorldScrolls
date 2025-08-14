package com.NguyenDevs.worldScrolls.commands;

import com.NguyenDevs.worldScrolls.WorldScrolls;
import com.NguyenDevs.worldScrolls.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class WorldScrollsTabCompleter implements TabCompleter {
    
    private final WorldScrolls plugin;
    private final ConfigManager configManager;
    
    private final List<String> mainCommands = Arrays.asList(
        "menu", "recipe", "admin", "give", "reload", "check", "help"
    );
    
    public WorldScrollsTabCompleter(WorldScrolls plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return getFilteredCommands(sender, args[0]);
        }
        
        if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "give":
                    return handleGiveTabComplete(sender, args);
                case "menu":
                case "recipe":
                case "admin":
                case "reload":
                case "help":
                    return Collections.emptyList();
                default:
                    return Collections.emptyList();
            }
        }
        
        return Collections.emptyList();
    }

    private List<String> getFilteredCommands(CommandSender sender, String input) {
        List<String> availableCommands = new ArrayList<>();

        availableCommands.add("menu");
        availableCommands.add("recipe");
        availableCommands.add("help");
        
        // Add admin commands if sender has permissions
        if (sender.hasPermission("worldscrolls.admin")) {
            availableCommands.add("admin");
        }
        
        if (sender.hasPermission("worldscrolls.give")) {
            availableCommands.add("give");
        }
        
        if (sender.hasPermission("worldscrolls.reload")) {
            availableCommands.add("reload");
        }

        return availableCommands.stream()
                .filter(cmd -> cmd.toLowerCase().startsWith(input.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }
    

    private List<String> handleGiveTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldscrolls.give")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return getOnlinePlayerNames(args[1]);
        } else if (args.length == 3) {
            return getAvailableScrolls(args[2]);
        } else if (args.length == 4) {

            return getAmountSuggestions(args[3]);
        }
        
        return Collections.emptyList();
    }

    private List<String> getOnlinePlayerNames(String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> getAvailableScrolls(String input) {
        List<String> scrolls = new ArrayList<>();
        
        try {
            ConfigurationSection scrollsConfig = configManager.getScrolls();
            if (scrollsConfig != null) {
                for (String scrollKey : scrollsConfig.getKeys(false)) {
                    ConfigurationSection scrollSection = scrollsConfig.getConfigurationSection(scrollKey);
                    if (scrollSection != null && scrollSection.getBoolean("enabled", true)) {
                        if (scrollKey.toLowerCase().startsWith(input.toLowerCase())) {
                            scrolls.add(scrollKey);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&7[&dWorld&5Scroll&7] &cFailed to get scroll list for tab completion: " + e.getMessage()));
        }
        
        return scrolls.stream().sorted().collect(Collectors.toList());
    }

    private List<String> getAmountSuggestions(String input) {
        List<String> amounts = Arrays.asList("1", "5", "10", "16", "32", "64");
        
        return amounts.stream()
                .filter(amount -> amount.startsWith(input))
                .collect(Collectors.toList());
    }
}
