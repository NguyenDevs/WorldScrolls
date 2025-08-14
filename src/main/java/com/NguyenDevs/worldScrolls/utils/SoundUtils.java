package com.NguyenDevs.worldScrolls.utils;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundUtils {
    
    // GUI Sounds
    public static final float GUI_CLICK_VOLUME = 0.5f;
    public static final float GUI_CLICK_PITCH = 1.05f;
    
    public static final Sound GUI_OPEN_SOUND = Sound.BLOCK_CHEST_OPEN;
    public static final float GUI_OPEN_VOLUME = 0.3f;
    public static final float GUI_OPEN_PITCH = 1.2f;
    
    public static final Sound GUI_CLOSE_SOUND = Sound.BLOCK_CHEST_CLOSE;
    public static final float GUI_CLOSE_VOLUME = 0.3f;
    public static final float GUI_CLOSE_PITCH = 0.9f;
    
    public static final Sound GUI_PAGE_SOUND = Sound.ITEM_BOOK_PAGE_TURN;
    public static final float GUI_PAGE_VOLUME = 0.4f;
    public static final float GUI_PAGE_PITCH = 1.1f;
    
    // Permission/Error Sounds
    public static final Sound PERMISSION_DENIED_SOUND = Sound.BLOCK_NOTE_BLOCK_BASS;
    public static final float PERMISSION_DENIED_VOLUME = 0.8f;
    public static final float PERMISSION_DENIED_PITCH = 0.5f;
    
    public static final Sound ERROR_SOUND = Sound.ENTITY_VILLAGER_NO;
    public static final float ERROR_VOLUME = 0.6f;
    public static final float ERROR_PITCH = 0.8f;
    
    // Success Sounds
    public static final Sound SUCCESS_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    public static final float SUCCESS_VOLUME = 0.5f;
    public static final float SUCCESS_PITCH = 1.3f;
    
    public static final Sound RELOAD_SOUND = Sound.ENTITY_PLAYER_LEVELUP;
    public static final float RELOAD_VOLUME = 0.4f;
    public static final float RELOAD_PITCH = 1.0f;
    
    // Scroll Sounds (for future use)
    public static final Sound SCROLL_USE_SOUND = Sound.ENTITY_ENDER_EYE_LAUNCH;
    public static final float SCROLL_USE_VOLUME = 0.7f;
    public static final float SCROLL_USE_PITCH = 1.2f;
    

    public static void playGUIClickSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, GUI_CLICK_VOLUME, GUI_CLICK_PITCH);
        }
    }

    public static void playGUIOpenSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), GUI_OPEN_SOUND, GUI_OPEN_VOLUME, GUI_OPEN_PITCH);
        }
    }

    public static void playGUICloseSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), GUI_CLOSE_SOUND, GUI_CLOSE_VOLUME, GUI_CLOSE_PITCH);
        }
    }

    public static void playPageTurnSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, GUI_PAGE_VOLUME, GUI_PAGE_PITCH);
        }
    }

    public static void playPermissionDeniedSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), PERMISSION_DENIED_SOUND, PERMISSION_DENIED_VOLUME, PERMISSION_DENIED_PITCH);
        }
    }

    public static void playErrorSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), ERROR_SOUND, ERROR_VOLUME, ERROR_PITCH);
        }
    }
    

    public static void playSuccessSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), SUCCESS_SOUND, SUCCESS_VOLUME, SUCCESS_PITCH);
        }
    }
    

    public static void playReloadSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), RELOAD_SOUND, RELOAD_VOLUME, RELOAD_PITCH);
        }
    }

    public static void playScrollUseSound(Player player) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), SCROLL_USE_SOUND, SCROLL_USE_VOLUME, SCROLL_USE_PITCH);
        }
    }
    

    public static void playCustomSound(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline()) {
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            pitch = Math.max(0.5f, Math.min(2.0f, pitch));
            
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public static boolean shouldPlaySounds(Player player) {
        return player != null && player.isOnline();
    }
}