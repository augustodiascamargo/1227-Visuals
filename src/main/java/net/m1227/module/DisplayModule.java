package net.m1227.module;

import net.m1227.client.DisplayControl;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Display: window size and fullscreen resolution from inside 1227 (Display settings screen).
 * Only confirmed choices are saved. With "Use with F11", Minecraft's own fullscreen key switches
 * to the chosen fullscreen mode instead of the desktop one; leaving fullscreen goes back to the
 * saved window size. The GUI scale is a separate Minecraft setting and is never changed here.
 */
public final class DisplayModule extends Module {
    public static DisplayModule INSTANCE;
    public final Setting.Num windowW, windowH;
    public final Setting.Text fullscreenMode;
    public final Setting.Bool applyAtStart, useWithF11;
    private boolean startDone, wasFullscreen;

    public DisplayModule() {
        super("Display", "Window size and fullscreen resolution, with a safe confirm.", Category.SETTINGS, true);
        INSTANCE = this;
        windowW = add(new Setting.Num("Window width", 0, 0, 7680, 1, " px")).showIf(() -> false);
        windowH = add(new Setting.Num("Window height", 0, 0, 4320, 1, " px")).showIf(() -> false);
        fullscreenMode = add(new Setting.Text("Fullscreen mode", "", 24, "desktop")).showIf(() -> false);
        applyAtStart = add(new Setting.Bool("Use the saved window size at start-up", false));
        useWithF11 = add(new Setting.Bool("Use the chosen fullscreen mode with F11", true));
        add(new Setting.Action("Open display settings", () -> net.m1227.client.menu.DisplaySettingsScreen.open()));
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    public DisplayMode chosenFullscreen() {
        return fullscreenMode.get().isEmpty() ? null : DisplayControl.parse(fullscreenMode.get());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean fs = Display.isFullscreen();
        if (!startDone) {
            startDone = true;
            wasFullscreen = fs;
            if (applyAtStart.on() && !fs && windowW.i() >= DisplayControl.MIN_W && windowH.i() >= DisplayControl.MIN_H
                    && (mc.displayWidth != windowW.i() || mc.displayHeight != windowH.i())) {
                DisplayControl.setWindow(windowW.i(), windowH.i());
            }
            return;
        }
        if (fs != wasFullscreen) {
            wasFullscreen = fs;
            // F11 (Minecraft's toggle) just happened
            if (fs && useWithF11.on()) {
                DisplayMode m = chosenFullscreen();
                if (m != null && !DisplayControl.sameMode(m, Display.getDisplayMode())) DisplayControl.setFullscreen(m);
            } else if (!fs && windowW.i() >= DisplayControl.MIN_W && windowH.i() >= DisplayControl.MIN_H
                    && (mc.displayWidth != windowW.i() || mc.displayHeight != windowH.i())) {
                DisplayControl.setWindow(windowW.i(), windowH.i());
            }
        }
    }
}
