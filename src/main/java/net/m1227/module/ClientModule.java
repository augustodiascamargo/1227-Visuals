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
    public final Setting.Num menuScale, animSpeed;
    public final Setting.Bool glass;

    public ClientModule() {
        super("Client", "Menu key, accent colour, menu size and reset.", Category.SETTINGS, true);
        INSTANCE = this;
        self = this;
        menuKey = add(new Setting.Key("Menu key", Keyboard.KEY_RCONTROL));
        accent = add(new Setting.Col("Accent color", 0x8B5CFF));
        menuScale = add(new Setting.Num("Menu scale", 1.0, 0.75, 1.25, 0.05, "x"));
        animSpeed = add(new Setting.Num("Animation speed", 1.0, 0.5, 2.0, 0.1, "x"));
        glass = add(new Setting.Bool("Glass effects (blur behind menus)", true));
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

    public static int menuKey() {
        return INSTANCE == null ? Keyboard.KEY_RCONTROL : INSTANCE.menuKey.code();
    }

    public static int accent() {
        return INSTANCE == null ? 0x8B5CFF : INSTANCE.accent.rgb();
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
