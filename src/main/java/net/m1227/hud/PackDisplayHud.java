package net.m1227.hud;

import java.util.ArrayList;
import java.util.List;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.ResourcePackRepository;

/** Pack Display: the resource pack(s) you are using, with their icon. */
public final class PackDisplayHud extends HudModule {
    private final Setting.Bool icon, all;
    private List<ResourcePackRepository.Entry> packs = new ArrayList<ResourcePackRepository.Entry>();
    private long at;

    public PackDisplayHud() {
        super("Pack Display", "The resource pack you are using, with its icon.", 0.01, 0.62);
        icon = add(new Setting.Bool("Pack icon", true));
        all = add(new Setting.Bool("All active packs", false));
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - at < 1000L) return;
        at = now;
        List<ResourcePackRepository.Entry> l = new ArrayList<ResourcePackRepository.Entry>(Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries());
        java.util.Collections.reverse(l); // the pack that wins first
        packs = all.on() ? l : l.isEmpty() ? l : l.subList(0, 1);
    }

    private static String name(ResourcePackRepository.Entry e) {
        String n = e.getResourcePackName();
        return n.endsWith(".zip") ? n.substring(0, n.length() - 4) : n;
    }

    @Override
    public float width() {
        refresh();
        float w = 60;
        for (ResourcePackRepository.Entry e : packs) w = Math.max(w, Gfx.textWidth(name(e)) + (icon.on() ? 26 : 10));
        return Math.min(220, w);
    }

    @Override
    public float height() {
        return Math.max(1, packs.size()) * (icon.on() ? 20 : 11) + 4;
    }

    @Override
    protected boolean hasContent() {
        refresh();
        return !packs.isEmpty();
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        refresh();
        float w = width();
        panel(x, y, w, height());
        if (packs.isEmpty()) {
            Gfx.text("Default", x + 5, y + 4, 0xFFFFFFFF);
            return;
        }
        float ry = y + 2;
        for (ResourcePackRepository.Entry e : packs) {
            float tx = x + 5;
            if (icon.on()) {
                try {
                    e.bindTexturePackIcon(Minecraft.getMinecraft().getTextureManager());
                    GlStateManager.enableTexture2D();
                    GlStateManager.color(1f, 1f, 1f, 1f);
                    Gui.drawModalRectWithCustomSizedTexture((int) x + 3, (int) ry + 1, 0, 0, 18, 18, 18, 18);
                } catch (Throwable ignored) {
                    // no icon
                }
                tx = x + 25;
            }
            Gfx.text(Gfx.trim(name(e), x + w - tx - 4), tx, ry + (icon.on() ? 6 : 2), 0xFFFFFFFF);
            ry += icon.on() ? 20 : 11;
        }
    }
}
