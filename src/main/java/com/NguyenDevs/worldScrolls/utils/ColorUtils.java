package com.NguyenDevs.worldScrolls.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.awt.*;
import java.util.EnumSet;
import java.util.Set;
import java.util.LinkedHashSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.md_5.bungee.api.ChatColor.*;

public class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:#([a-fA-F0-9]{6}):#([a-fA-F0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);

    public static String colorize(String text) {
        if (text == null) return "";
        text = processGradients(text);
        text = processHexColors(text);
        text = ChatColor.translateAlternateColorCodes('&', text);
        return text;
    }

    private static String processGradients(String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String content = matcher.group(3);
            String gradientText = createGradient(content, startHex, endHex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(gradientText));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String processHexColors(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hexColor = matcher.group();
            String replacement = convertHexToMinecraft(hexColor);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String createGradient(String text, String startHex, String endHex) {
        if (text.isEmpty()) return "";

        Color startColor = Color.decode("#" + startHex);
        Color endColor = Color.decode("#" + endHex);

        StringBuilder out = new StringBuilder();

        String stripped = stripLegacyCodesKeepState(text, out);
        Set<ChatColor> activeFormats = new LinkedHashSet<>();


        int visibleCount = countVisibleChars(text);
        if (visibleCount == 0) return "";

        int seenVisible = 0;
        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);

            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                applyFormatCode(code, activeFormats);
                i += 2;
                continue;
            }

            boolean isVisible = true;
            if (isVisible) {
                double ratio = visibleCount == 1 ? 1.0 : (double) seenVisible / (visibleCount - 1);
                int red = (int) Math.round(startColor.getRed() * (1 - ratio) + endColor.getRed() * ratio);
                int green = (int) Math.round(startColor.getGreen() * (1 - ratio) + endColor.getGreen() * ratio);
                int blue = (int) Math.round(startColor.getBlue() * (1 - ratio) + endColor.getBlue() * ratio);
                String hex = String.format("#%02x%02x%02x", red, green, blue);

                out.append(convertHexToMinecraft(hex));
                for (ChatColor fmt : activeFormats) out.append(fmt);

                out.append(c);
                seenVisible++;
            } else {
                out.append(c);
            }
            i++;
        }

        return out.toString();
    }

    private static void applyFormatCode(char code, Set<ChatColor> active) {
        switch (code) {
            case 'l': active.add(ChatColor.BOLD); break;
            case 'o': active.add(ChatColor.ITALIC); break;
            case 'n': active.add(ChatColor.UNDERLINE); break;
            case 'm': active.add(ChatColor.STRIKETHROUGH); break;
            case 'k': active.add(ChatColor.MAGIC); break;
            case 'r': active.clear(); break;
            default:
                if (isLegacyColorCode(code)) active.clear();
                break;
        }
    }

    private static boolean isLegacyColorCode(char code) {
        return "0123456789abcdef".indexOf(code) >= 0;
    }

    private static int countVisibleChars(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length()) {
                i += 2;
                continue;
            }
            count++;
            i++;
        }
        return count;
    }

    private static String convertHexToMinecraft(String hex) {
        if (supportsHexColors()) {
            return ChatColor.of(hex).toString();
        } else {
            return getClosestChatColor(hex).toString();
        }
    }

    private static boolean supportsHexColors() {
        try {
            String version = Bukkit.getServer().getBukkitVersion();
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1].split("-")[0]);
            return major > 1 || (major == 1 && minor >= 16);
        } catch (Exception e) {
            return false;
        }
    }

    private static ChatColor getClosestChatColor(String hex) {
        Color color = Color.decode(hex);
        ChatColor closest = ChatColor.WHITE;
        double minDistance = Double.MAX_VALUE;

        ChatColor[] colors = {
                BLACK, DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
                ChatColor.DARK_RED, ChatColor.DARK_PURPLE, GOLD, ChatColor.GRAY,
                ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
                ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
        };

        for (ChatColor chatColor : colors) {
            Color chatColorRGB = getChatColorRGB(chatColor);
            if (chatColorRGB != null) {
                double distance = getColorDistance(color, chatColorRGB);
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = chatColor;
                }
            }
        }
        return closest;
    }

    private static Color getChatColorRGB(ChatColor chatColor) {
        if (chatColor == ChatColor.BLACK) return new Color(0, 0, 0);
        if (chatColor == ChatColor.DARK_BLUE) return new Color(0, 0, 170);
        if (chatColor == ChatColor.DARK_GREEN) return new Color(0, 170, 0);
        if (chatColor == ChatColor.DARK_AQUA) return new Color(0, 170, 170);
        if (chatColor == ChatColor.DARK_RED) return new Color(170, 0, 0);
        if (chatColor == ChatColor.DARK_PURPLE) return new Color(170, 0, 170);
        if (chatColor == ChatColor.GOLD) return new Color(255, 170, 0);
        if (chatColor == ChatColor.GRAY) return new Color(170, 170, 170);
        if (chatColor == ChatColor.DARK_GRAY) return new Color(85, 85, 85);
        if (chatColor == ChatColor.BLUE) return new Color(85, 85, 255);
        if (chatColor == ChatColor.GREEN) return new Color(85, 255, 85);
        if (chatColor == ChatColor.AQUA) return new Color(85, 255, 255);
        if (chatColor == ChatColor.RED) return new Color(255, 85, 85);
        if (chatColor == ChatColor.LIGHT_PURPLE) return new Color(255, 85, 255);
        if (chatColor == ChatColor.YELLOW) return new Color(255, 255, 85);
        if (chatColor == ChatColor.WHITE) return new Color(255, 255, 255);
        return null;
    }

    private static double getColorDistance(Color c1, Color c2) {
        int dr = c1.getRed() - c2.getRed();
        int dg = c1.getGreen() - c2.getGreen();
        int db = c1.getBlue() - c2.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static String stripLegacyCodesKeepState(String s, StringBuilder out) {
        return s;
    }
}

