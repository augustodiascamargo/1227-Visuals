package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.InputManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import org.lwjgl.input.Keyboard;

/**
 * Client-wide settings: the menu key (stored in 1227's own config, never in
 * Minecraft's Controls), accent colour, menu scale and animation speed.
 * The default menu key is Right Control, which Lunar and most PvP clients
 * leave free; "/1227" in chat always opens the menu, even with no key.
 */
public final class ClientModule extends Module {
    private static ClientModule self;
    private Setting.Mode handMode;

    /**
     * Compatibility = never replace Minecraft's first-person hand drawing (for clients / mods
     * that patch the hand themselves). Weapon effects and glow still work; 1.7 poses and the
     * custom 3D swords are then off.
     */
    public static boolean handCompat() {
        return self != null && self.handMode != null && self.handMode.is("Compatibility");
    }

    public static ClientModule INSTANCE;

    public final Setting.Key menuKey;
    public final Setting.Col accent;
    public final Setting.Mode accentMode;
    public final Setting.Col accent2;
    public final Setting.Num accentSpeed;
    public final Setting.Num menuScale, animSpeed;
    public final Setting.Bool glass;

    public ClientModule() {
        super("Client", "Menu key, accent colour, menu size and reset.", Category.SETTINGS, true);
        INSTANCE = this;
        self = this;
        menuKey = add(new Setting.Key("Menu key", Keyboard.KEY_RCONTROL));
        add(new Setting.Action("Open Minecraft Controls", new Runnable() {
            @Override
            public void run() {
                // the menu key is also listed under the "1227" category there; Done returns to this menu
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiControls(mc.currentScreen, mc.gameSettings));
            }
        }));
        accent = add(new Setting.Col("Accent color", 0x8B5CFF));
        accentMode = add(new Setting.Mode("Accent mode", "Static", "Static", "Two-tone", "Rainbow", "Pulse"));
        accent2 = add(new Setting.Col("Second accent", 0x38BDF8)).showIf(() -> accentMode.is("Two-tone"));
        accentSpeed = add(new Setting.Num("Accent animation speed", 1.0, 0.2, 3.0, 0.1, "x")).showIf(() -> !accentMode.is("Static"));
        menuScale = add(new Setting.Num("Menu scale", 1.0, 0.75, 1.25, 0.05, "x"));
        animSpeed = add(new Setting.Num("Animation speed", 1.0, 0.5, 2.0, 0.1, "x"));
        glass = add(new Setting.Bool("Glass effects (blur behind menus)", true)).showIf(() -> false); // moved to Menu Style
        handMode = add(new Setting.Mode("First-person hand", "1227", "1227", "Compatibility"));
        add(new Setting.Action("Edit HUD layout", new Runnable() {
            @Override
            public void run() {
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new net.m1227.hud.HudEditorScreen());
            }
        }));
        add(new Setting.Action("Copy config to clipboard", new Runnable() {
            @Override
            public void run() {
                net.m1227.core.ConfigManager.exportToClipboard();
            }
        }));
        add(new Setting.Action("Load config from clipboard", new Runnable() {
            @Override
            public void run() {
                net.m1227.core.ConfigManager.importFromClipboard();
            }
        }));
        add(new Setting.Action("Reset every module to defaults", new Runnable() {
            @Override
            public void run() {
                for (Module m : ModuleManager.all()) m.reset();
                Notifier.post(Notifier.Kind.SYSTEM, "Settings", "All reset", Notifier.WARN, 1800);
            }
        }));
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    public Setting.Key menuKeySetting() {
        return menuKey;
    }

    public static int menuKey() {
        return INSTANCE == null ? Keyboard.KEY_RCONTROL : INSTANCE.menuKey.code();
    }

    /**
     * The accent colour used by the menu, HUD and effects. Static returns the picked colour;
     * Two-tone drifts between the two accents, Rainbow cycles the hue (keeping the picked
     * saturation and brightness) and Pulse breathes the brightness.
     */
    public static int accent() {
        if (INSTANCE == null) return 0x8B5CFF;
        ClientModule c = INSTANCE;
        String m = c.accentMode.get();
        if (m.equals("Static")) return c.accent.rgb();
        double t = System.nanoTime() / 1e9 * c.accentSpeed.f();
        if (m.equals("Two-tone")) {
            float k = 0.5f + 0.5f * (float) Math.sin(t * 1.4);
            return net.m1227.ui.Gfx.mix(0xFF000000 | c.accent.rgb(), 0xFF000000 | c.accent2.rgb(), k) & 0xFFFFFF;
        }
        if (m.equals("Rainbow")) {
            float h = (float) ((c.accent.hue + t * 0.12) % 1.0);
            return java.awt.Color.HSBtoRGB(h, Math.max(0.45f, c.accent.sat), Math.max(0.6f, c.accent.bri)) & 0xFFFFFF;
        }
        // Pulse
        float b = c.accent.bri * (0.72f + 0.28f * (0.5f + 0.5f * (float) Math.sin(t * 3.0)));
        return java.awt.Color.HSBtoRGB(c.accent.hue, c.accent.sat, b) & 0xFFFFFF;
    }

    /** Second colour for gradients: the second accent in Two-tone, otherwise a hue-shifted accent. */
    public static int accentAlt() {
        if (INSTANCE == null) return 0x38BDF8;
        ClientModule c = INSTANCE;
        if (c.accentMode.is("Two-tone")) {
            double t = System.nanoTime() / 1e9 * c.accentSpeed.f();
            float k = 0.5f + 0.5f * (float) Math.sin(t * 1.4);
            return net.m1227.ui.Gfx.mix(0xFF000000 | c.accent2.rgb(), 0xFF000000 | c.accent.rgb(), k) & 0xFFFFFF;
        }
        int a = accent();
        float[] hsb = java.awt.Color.RGBtoHSB((a >> 16) & 255, (a >> 8) & 255, a & 255, null);
        return java.awt.Color.HSBtoRGB((hsb[0] + 0.14f) % 1f, hsb[1], hsb[2]) & 0xFFFFFF;
    }

    public static float menuScale() {
        return INSTANCE == null ? 1f : INSTANCE.menuScale.f();
    }

    public static boolean glass() {
        return INSTANCE == null || INSTANCE.glass.on();
    }

    public static float animSpeed() {
        return INSTANCE == null ? 1f : INSTANCE.animSpeed.f();
    }

    /** Text shown under the menu key row when another binding uses the same key. */
    public static String conflictText(Setting.Key k) {
        List<String> c = InputManager.conflicts(k.code(), k);
        if (c.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("Also used by: ");
        for (int i = 0; i < c.size() && i < 3; i++) {
            if (i > 0) sb.append(", ");
            sb.append(c.get(i));
        }
        if (c.size() > 3) sb.append(" +").append(c.size() - 3);
        return sb.toString();
    }
}
