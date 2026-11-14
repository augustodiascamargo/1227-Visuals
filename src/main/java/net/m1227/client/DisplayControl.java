package net.m1227.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.m1227.Mod1227;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Window size and fullscreen mode for the Display settings, done the way Minecraft 1.8.9 does it
 * itself in toggleFullscreen(): LWJGL's Display.setDisplayMode, then Minecraft's own resize
 * (framebuffer, screen layout, shaders), so HUDs and menus are laid out again for the new size.
 * The GUI scale is a separate setting and is not touched.
 *
 * Safety: sizes are clamped to 640x480 .. the desktop size for a window; fullscreen only uses modes
 * the monitor reports; every change is made from a snapshot that {@link #revert} puts back, and the
 * settings screen asks to confirm within 12 s, reverting by itself otherwise.
 */
public final class DisplayControl {
    private DisplayControl() {}

    public static final int MIN_W = 640, MIN_H = 480;
    private static Method resize;

    /** What the display was before a change. */
    public static final class Snapshot {
        public final boolean fullscreen;
        public final int w, h, freq;

        Snapshot(boolean fullscreen, int w, int h, int freq) {
            this.fullscreen = fullscreen;
            this.w = w;
            this.h = h;
            this.freq = freq;
        }
    }

    public static Snapshot snapshot() {
        DisplayMode m = Display.getDisplayMode();
        return new Snapshot(Display.isFullscreen(), m.getWidth(), m.getHeight(), m.getFrequency());
    }

    /** Minecraft's private resize(int, int): the only private void method taking two ints. */
    private static void mcResize(int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (resize == null) {
                for (Method m : Minecraft.class.getDeclaredMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (m.getReturnType() == void.class && p.length == 2 && p[0] == int.class && p[1] == int.class
                            && java.lang.reflect.Modifier.isPrivate(m.getModifiers())) {
                        if (resize != null) {
                            resize = null; // not unique: do not guess
                            break;
                        }
                        resize = m;
                    }
                }
                if (resize != null) resize.setAccessible(true);
            }
            if (resize != null) {
                resize.invoke(mc, w, h);
                return;
            }
        } catch (Throwable t) {
            Mod1227.LOGGER.warn("1227 display: Minecraft's resize could not be called", t);
        }
        // fallback: Minecraft notices the new window size by itself on the next frame
        mc.displayWidth = Math.max(1, w);
        mc.displayHeight = Math.max(1, h);
    }

    public static DisplayMode desktop() {
        return Display.getDesktopDisplayMode();
    }

    /** Clamped windowed size. */
    public static int[] clampWindow(int w, int h) {
        DisplayMode d = desktop();
        return new int[]{Math.max(MIN_W, Math.min(d.getWidth(), w)), Math.max(MIN_H, Math.min(d.getHeight(), h))};
    }

    /** Windowed at w x h (leaves fullscreen first, through Minecraft's own toggle). */
    public static boolean setWindow(int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        int[] c = clampWindow(w, h);
        try {
            if (Display.isFullscreen()) mc.toggleFullscreen();
            Display.setDisplayMode(new DisplayMode(c[0], c[1]));
            mcResize(c[0], c[1]);
            return true;
        } catch (Exception e) {
            Mod1227.LOGGER.warn("1227 display: could not set the window to " + c[0] + "x" + c[1], e);
            return false;
        }
    }

    /** Fullscreen in one of the monitor's modes; null = the desktop mode (Minecraft's own). */
    public static boolean setFullscreen(DisplayMode mode) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (!Display.isFullscreen()) mc.toggleFullscreen(); // Minecraft's state becomes "fullscreen"
            if (mode != null && mode.isFullscreenCapable() && !sameMode(mode, Display.getDisplayMode())) {
                Display.setDisplayModeAndFullscreen(mode);
                mcResize(mode.getWidth(), mode.getHeight());
            }
            return Display.isFullscreen();
        } catch (Exception e) {
            Mod1227.LOGGER.warn("1227 display: could not switch to fullscreen " + describe(mode), e);
            return false;
        }
    }

    public static void revert(Snapshot s) {
        if (s == null) return;
        if (s.fullscreen) setFullscreen(find(s.w, s.h, s.freq));
        else setWindow(s.w, s.h);
    }

    public static boolean sameMode(DisplayMode a, DisplayMode b) {
        return a != null && b != null && a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight() && a.getFrequency() == b.getFrequency();
    }

    /** Fullscreen modes of this monitor: 32-bit, at least 640x480, one per size and refresh rate, biggest first. */
    public static List<DisplayMode> fullscreenModes() {
        List<DisplayMode> out = new ArrayList<DisplayMode>();
        try {
            for (DisplayMode m : Display.getAvailableDisplayModes()) {
                if (!m.isFullscreenCapable() || m.getBitsPerPixel() < 24 || m.getWidth() < MIN_W || m.getHeight() < MIN_H) continue;
                boolean dup = false;
                for (DisplayMode o : out) if (sameMode(o, m)) dup = true;
                if (!dup) out.add(m);
            }
        } catch (Exception e) {
            Mod1227.LOGGER.warn("1227 display: could not list display modes", e);
        }
        Collections.sort(out, new Comparator<DisplayMode>() {
            @Override
            public int compare(DisplayMode a, DisplayMode b) {
                int d = b.getWidth() * b.getHeight() - a.getWidth() * a.getHeight();
                return d != 0 ? d : b.getFrequency() - a.getFrequency();
            }
        });
        return out;
    }

    public static DisplayMode find(int w, int h, int freq) {
        DisplayMode best = null;
        for (DisplayMode m : fullscreenModes()) {
            if (m.getWidth() != w || m.getHeight() != h) continue;
            if (best == null || Math.abs(m.getFrequency() - freq) < Math.abs(best.getFrequency() - freq)) best = m;
        }
        return best;
    }

    /** "1920x1080@144" -> mode, or null. */
    public static DisplayMode parse(String s) {
        try {
            String[] a = s.split("[x@]");
            return find(Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()), a.length > 2 ? Integer.parseInt(a[2].trim()) : 0);
        } catch (Exception e) {
            return null;
        }
    }

    public static String describe(DisplayMode m) {
        return m == null ? "desktop" : m.getWidth() + "x" + m.getHeight() + "@" + m.getFrequency();
    }

    /** Common window sizes that fit this desktop. */
    public static List<int[]> windowPresets() {
        int[][] all = {{854, 480}, {1024, 768}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160}};
        DisplayMode d = desktop();
        List<int[]> out = new ArrayList<int[]>();
        for (int[] s : all) if (s[0] <= d.getWidth() && s[1] <= d.getHeight()) out.add(s);
        return out;
    }
}
