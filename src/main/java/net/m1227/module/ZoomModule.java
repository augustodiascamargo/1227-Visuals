package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

/** Hold-to-zoom with smooth easing, scroll to change the zoom, optional cinematic camera. */
public final class ZoomModule extends Module {
    private final Setting.Key key;
    private final Setting.Num factor;
    private final Setting.Bool scroll, cinematic, lowerSens;
    private float zoom = 1f, extra = 1f;
    private boolean wasZoom, oldSmooth;
    private float oldSens = -1f;

    public ZoomModule() {
        super("Zoom", "Hold a key to zoom; scroll adjusts it.", Category.CAMERA, true);
        key = add(new Setting.Key("Zoom key", Keyboard.KEY_C));
        factor = add(new Setting.Num("Zoom", 4.0, 1.5, 10.0, 0.5, "x"));
        scroll = add(new Setting.Bool("Scroll to adjust", true));
        cinematic = add(new Setting.Bool("Cinematic camera while zoomed", false));
        lowerSens = add(new Setting.Bool("Lower sensitivity while zoomed", true));
    }

    private boolean held() {
        Minecraft mc = Minecraft.getMinecraft();
        int k = key.code();
        return k != 0 && mc.currentScreen == null && net.m1227.core.InputManager.isDown(k);
    }

    /**
     * FOVUpdateEvent fires every tick while Minecraft computes its FOV
     * multiplier; vanilla then eases the camera towards the new value, which
     * gives the zoom its smooth in/out. Vanilla limits the multiplier to
     * 0.1, so the strongest zoom is 10x.
     */
    @SubscribeEvent
    public void onFov(FOVUpdateEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean z = held();
        if (!z) extra = 1f;
        zoom = z ? Math.min(10f, factor.f() * extra) : 1f;
        if (z != wasZoom) {
            if (z) {
                oldSmooth = mc.gameSettings.smoothCamera;
                if (cinematic.on()) mc.gameSettings.smoothCamera = true;
                if (lowerSens.on()) {
                    oldSens = mc.gameSettings.mouseSensitivity;
                }
            } else {
                mc.gameSettings.smoothCamera = oldSmooth;
                if (oldSens >= 0f) mc.gameSettings.mouseSensitivity = oldSens;
                oldSens = -1f;
            }
            wasZoom = z;
        }
        if (z && oldSens >= 0f) mc.gameSettings.mouseSensitivity = oldSens / (float) Math.sqrt(zoom);
        if (zoom > 1.001f) e.newfov = e.newfov / zoom;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (!scroll.on() || e.dwheel == 0 || !held()) return;
        extra = Math.max(0.4f, Math.min(3f, extra * (e.dwheel > 0 ? 1.15f : 1f / 1.15f)));
        e.setCanceled(true);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (wasZoom) {
            mc.gameSettings.smoothCamera = oldSmooth;
            if (oldSens >= 0f) mc.gameSettings.mouseSensitivity = oldSens;
        }
        wasZoom = false;
        oldSens = -1f;
        zoom = 1f;
    }
}
