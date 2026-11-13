package net.m1227.hud;

import java.util.ArrayList;
import java.util.List;
import net.m1227.core.InputManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;

/**
 * Keybinds HUD: the 1227 modules you have bound to a key, with the key and whether each one is
 * on right now. Optionally every enabled module too (an "array list" of what is active).
 */
public final class KeybindsHud extends HudModule {
    private final Setting.Bool onlyBound, onlyActive;
    private final Setting.Num maxRows;
    private final List<Module> rows = new ArrayList<Module>();
    private long at;

    public KeybindsHud() {
        super("Keybinds", "Your bound modules and their keys, with on/off state.", 0.01, 0.30);
        onlyBound = add(new Setting.Bool("Only modules with a key", true));
        onlyActive = add(new Setting.Bool("Only active modules", false));
        maxRows = add(new Setting.Num("Max rows", 10, 3, 30, 1, ""));
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - at < 300L) return;
        at = now;
        rows.clear();
        for (Module m : ModuleManager.all()) {
            if (!m.toggleable() || m instanceof HudModule) continue;
            if (onlyBound.on() && m.key == 0) continue;
            if (onlyActive.on() && !m.isEnabled()) continue;
            if (!onlyBound.on() && !onlyActive.on() && !m.isEnabled() && m.key == 0) continue;
            rows.add(m);
            if (rows.size() >= maxRows.i()) break;
        }
    }

    @Override
    public float width() {
        refresh();
        float w = 70;
        for (Module m : rows) w = Math.max(w, Gfx.textWidth(m.name) + Gfx.textWidth(key(m)) + 26);
        return w;
    }

    @Override
    public float height() {
        return Math.max(1, rows.size()) * 11 + 6;
    }

    @Override
    protected boolean hasContent() {
        refresh();
        return !rows.isEmpty();
    }

    private static String key(Module m) {
        return m.key == 0 ? "" : InputManager.keyName(m.key);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        refresh();
        float w = width();
        panel(x, y, w, height());
        if (rows.isEmpty() && editing) {
            Gfx.text("Zoom", x + 10, y + 4, 0xFFFFFFFF);
            Gfx.text("C", x + w - 12, y + 4, value());
            return;
        }
        float yy = y + 4;
        for (Module m : rows) {
            Gfx.circle(x + 6, yy + 3.5f, 1.8f, m.isEnabled() ? 0xFF3DDC84 : 0xFF5A5F6E);
            Gfx.text(m.name, x + 11, yy, m.isEnabled() ? 0xFFFFFFFF : LABEL);
            String k = key(m);
            if (!k.isEmpty()) Gfx.text(k, x + w - 5 - Gfx.textWidth(k), yy, value());
            yy += 11;
        }
    }
}
