package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Cinematic / Photo mode, for recording videos and taking screenshots: hides Minecraft's HUD
 * (hotbar, health, food, crosshair, chat), your hand and the 1227 HUD, while the lyrics, the Now
 * Playing widget and the visualizer can stay. Optional smooth camera (Minecraft's own cinematic
 * camera). Photo mode hides everything, lyrics included. Bind a key to it in the module list.
 */
public final class CinematicModule extends Module {
    public static CinematicModule INSTANCE;
    private final Setting.Bool hideVanilla, hideCrosshair, hideChat, hideHand, hide1227, keepMusic, hideNotes, smoothCam, photo;
    private boolean savedSmooth, changedSmooth;

    public CinematicModule() {
        super("Cinematic Mode", "Hide the HUD for videos and screenshots; keep lyrics and the music widget.", Category.CAMERA, false);
        INSTANCE = this;
        photo = add(new Setting.Bool("Photo mode (hide everything)", false));
        hideVanilla = add(new Setting.Bool("Hide hotbar, health and food", true)).showIf(() -> !photo.on());
        hideCrosshair = add(new Setting.Bool("Hide crosshair", true)).showIf(() -> !photo.on());
        hideChat = add(new Setting.Bool("Hide chat", true)).showIf(() -> !photo.on());
        hideHand = add(new Setting.Bool("Hide hand", true)).showIf(() -> !photo.on());
        hide1227 = add(new Setting.Bool("Hide 1227 HUD", true)).showIf(() -> !photo.on());
        keepMusic = add(new Setting.Bool("Keep Now Playing and visualizer", true)).showIf(() -> !photo.on() && hide1227.on());
        hideNotes = add(new Setting.Bool("Hide notifications", true)).showIf(() -> !photo.on());
        smoothCam = add(new Setting.Bool("Smooth camera", false));
    }

    private static boolean on() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    /** True when this HUD element should not be drawn right now. */
    public static boolean hidesHud(HudModule h) {
        if (!on()) return false;
        if (INSTANCE.photo.on()) return true;
        if (!INSTANCE.hide1227.on()) return false;
        return !(INSTANCE.keepMusic.on() && (h instanceof net.m1227.hud.MusicHud || h instanceof net.m1227.hud.VisualizerHud));
    }

    public static boolean hideLyrics() {
        return on() && INSTANCE.photo.on();
    }

    public static boolean hideNotifications() {
        return on() && (INSTANCE.photo.on() || INSTANCE.hideNotes.on());
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (smoothCam.on()) {
            savedSmooth = mc.gameSettings.smoothCamera;
            mc.gameSettings.smoothCamera = true;
            changedSmooth = true;
        }
    }

    @Override
    protected void onDisable() {
        if (changedSmooth) {
            Minecraft.getMinecraft().gameSettings.smoothCamera = savedSmooth;
            changedSmooth = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlay(RenderGameOverlayEvent.Pre e) {
        boolean all = photo.on();
        switch (e.type) {
            case HOTBAR:
            case HEALTH:
            case FOOD:
            case ARMOR:
            case AIR:
            case EXPERIENCE:
            case HEALTHMOUNT:
            case JUMPBAR:
                if (all || hideVanilla.on()) e.setCanceled(true);
                break;
            case CROSSHAIRS:
                if (all || hideCrosshair.on()) e.setCanceled(true);
                break;
            case CHAT:
                if (all || hideChat.on()) e.setCanceled(true);
                break;
            case BOSSHEALTH:
            case PLAYER_LIST:
                if (all) e.setCanceled(true);
                break;
            default:
                break;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHand(RenderHandEvent e) {
        if (photo.on() || hideHand.on()) e.setCanceled(true);
    }
}
