package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;

/** Java memory in use (with a bar) and the number of loaded chunks / entities rendered. */
public final class SystemHud extends HudModule {
    private final Setting.Bool bar, entities;

    public SystemHud() {
        super("Memory", "Java memory use, and entities rendered.", 0.86, 0.16);
        bar = add(new Setting.Bool("Usage bar", true));
        entities = add(new Setting.Bool("Entities rendered", false));
    }

    @Override
    public float width() {
        return 96;
    }

    @Override
    public float height() {
        return 6 + 10 + (bar.on() ? 6 : 0) + (entities.on() ? 10 : 0);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        panel(x, y, width(), height());
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory(), used = rt.totalMemory() - rt.freeMemory();
        float f = max <= 0 ? 0f : used / (float) max;
        String v = (used >> 20) + " / " + (max >> 20) + " MB";
        Gfx.text("RAM", x + 5, y + 4, LABEL);
        Gfx.text(v, x + width() - 5 - Gfx.textWidth(v), y + 4, f > 0.85f ? 0xFFFF6B6B : value());
        float ry = y + 14;
        if (bar.on()) {
            Gfx.roundedRect(x + 5, ry + 1, width() - 10, 3, 1.5f, 0x40FFFFFF);
            Gfx.roundedRect(x + 5, ry + 1, (width() - 10) * Math.min(1f, f), 3, 1.5f, f > 0.85f ? 0xFFFF6B6B : value());
            ry += 6;
        }
        if (entities.on()) {
            Minecraft mc = Minecraft.getMinecraft();
            String info = mc.renderGlobal == null ? "-" : mc.renderGlobal.getDebugInfoEntities();
            // "E: 12/40, B: 0, I: 3" -> keep the "rendered/total" part
            int c = info.indexOf(',');
            String e = info.startsWith("E: ") ? info.substring(3, c > 0 ? c : info.length()) : info;
            Gfx.text("Entities", x + 5, ry, LABEL);
            Gfx.text(e, x + width() - 5 - Gfx.textWidth(e), ry, value());
        }
    }
}
