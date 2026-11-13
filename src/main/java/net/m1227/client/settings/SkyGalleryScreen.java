package net.m1227.client.settings;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.m1227.Mod1227;
import net.m1227.client.menu.MenuBackground;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.ConfigManager;
import net.m1227.core.Setting;
import net.m1227.module.SkyModule;
import net.m1227.ui.Gfx;
import net.m1227.vfx.SkyPreset;
import net.minecraft.client.gui.GuiScreen;

/**
 * Sky gallery: all 58 skies as live thumbnails (gradient, stars, aurora, nebula, galaxy band,
 * sun or moon, grid), with search, collections (Night, Sunset, Space, Aurora, Neon, Favorites) and
 * the sky options on the side. Click a sky to use it; in a world you see it change behind the
 * window. Favorites are kept in config/1227/sky-favorites.txt.
 */
public final class SkyGalleryScreen extends UiScreen {
    private static final String[] TABS = {"All", "Favorites", "Night", "Sunset", "Space", "Aurora", "Neon"};
    private static int tab;
    private String search = "";
    private final Set<String> favs = new LinkedHashSet<String>();

    public SkyGalleryScreen(GuiScreen parent) {
        super(parent);
        loadFavs();
    }

    private static File favFile() {
        File d = new File(Mod1227.configDir, "1227");
        d.mkdirs();
        return new File(d, "sky-favorites.txt");
    }

    private void loadFavs() {
        try {
            File f = favFile();
            if (f.isFile()) for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) if (!l.trim().isEmpty()) favs.add(l.trim());
        } catch (Exception ignored) {
            // no favourites yet
        }
    }

    private void saveFavs() {
        try {
            Files.write(favFile().toPath(), new ArrayList<String>(favs), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not save sky favorites", e);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ConfigManager.save();
    }

    /** Collection a sky belongs to (see SkyPreset.collection). */
    static String kind(SkyPreset s) {
        return SkyPreset.collection(s);
    }

    private Setting.Mode skySetting(SkyModule m) {
        for (Setting<?> s : m.settings()) if (s.name.equals("Sky") && s instanceof Setting.Mode) return (Setting.Mode) s;
        return null;
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        SkyModule sky = SkyModule.INSTANCE;
        float ww = Math.min(700, w - 16), wh = Math.min(430, h - 16);
        float wx = (w - ww) / 2f, wy = (h - wh) / 2f;
        Gfx.roundedRect(wx, wy, ww, wh, DS.R_LG, mc.theWorld != null ? 0xC80E1016 : 0xF00E1016);
        Gfx.roundedOutline(wx, wy, ww, wh, DS.R_LG, 0.6f, DS.LINE_STRONG);
        ui.iconButton("back", wx + 10, wy + 10, 18, Glyph.BACK, "Back", true, this::back);
        Ui.textBold("Skies", wx + 34, wy + 13, DS.T_LABEL, DS.TEXT);
        Ui.text(mc.theWorld != null ? "Click a sky to use it, then look up" : "Click a sky to use it", wx + 34, wy + 26, DS.T_SMALL, DS.FAINT);
        if (sky == null) return;
        Setting.Mode current = skySetting(sky);
        ui.toggle("skyon", wx + 120, wy + 14, sky.isEnabled(), sky::toggle);
        Ui.text(sky.isEnabled() ? "Dynamic Skies on" : "Dynamic Skies off", wx + 148, wy + 16, DS.T_SMALL, DS.MUTED);

        float side = Math.min(220, ww * 0.33f);
        float gx = wx + 10, gw = ww - side - 30, gy = wy + 64, gh = wh - 74;
        // collections and search
        float tx = gx;
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            float tw = Ui.width(TABS[i], DS.T_SMALL) + 14;
            boolean on = tab == i, hv = ui.hover(tx, wy + 40, tw, 16);
            Gfx.roundedRect(tx, wy + 40, tw, 16, 8, on ? DS.accent() : hv ? DS.SURFACE_3 : DS.SURFACE_2);
            Ui.text(TABS[i], tx + 7, wy + 44.4f, DS.T_SMALL, on ? 0xFFFFFFFF : DS.MUTED);
            ui.click(tx, wy + 40, tw, 16, () -> {
                tab = k;
                ui.resetScroll("skies");
            });
            tx += tw + 4;
        }
        ui.textField("search", gx + gw - 110, wy + 10, 110, 16, search, "Search skies", 30, v -> search = v);

        List<SkyPreset> shown = new ArrayList<SkyPreset>();
        String q = search.trim().toLowerCase();
        for (SkyPreset s : SkyPreset.ALL) {
            if (!q.isEmpty() && !s.name.toLowerCase().contains(q)) continue;
            if (tab == 1 && !favs.contains(s.name)) continue;
            if (tab > 1 && !kind(s).equals(TABS[tab])) continue;
            shown.add(s);
        }
        int cols = Math.max(2, (int) (gw / 104));
        float cw = (gw - (cols - 1) * 6) / cols, ch = cw * 0.62f + 14;
        float off = ui.beginScroll("skies", gx, gy, gw, gh);
        float t = System.nanoTime() / 1e9f % 3600f;
        for (int i = 0; i < shown.size(); i++) {
            final SkyPreset s = shown.get(i);
            float x = gx + (i % cols) * (cw + 6), y = gy + (i / cols) * (ch + 6) - off;
            if (y + ch < gy || y > gy + gh) continue;
            boolean sel = current != null && current.is(s.name) && sky.isEnabled();
            boolean hv = ui.hover(x, y, cw, ch);
            float k = ui.anim("sky" + s.name, sel ? 1f : hv ? 0.5f : 0f);
            Gfx.roundedRect(x, y, cw, ch, DS.R_MD, Gfx.mix(DS.SURFACE_2, DS.SURFACE_3, k));
            thumb(s, x + 2, y + 2, cw - 4, cw * 0.62f - 4, t);
            if (sel) Gfx.roundedOutline(x, y, cw, ch, DS.R_MD, 1.2f, DS.accent());
            Ui.text(Gfx.trim(s.name, cw - 20), x + 5, y + ch - 11, 0.75f, sel ? DS.TEXT : DS.MUTED);
            final boolean fav = favs.contains(s.name);
            float sx = x + cw - 9, sy = y + ch - 7;
            boolean sh = ui.hover(sx - 6, sy - 6, 12, 12);
            Gfx.star(sx, sy, 3.6f, fav, fav ? DS.WARN : sh ? DS.TEXT : 0x40FFFFFF);
            ui.click(sx - 6, sy - 6, 12, 12, () -> {
                if (!favs.remove(s.name)) favs.add(s.name);
                saveFavs();
            });
            ui.click(x, y, cw, ch, () -> {
                if (current != null) current.set(s.name);
                if (!sky.isEnabled()) sky.setEnabled(true);
            });
        }
        if (shown.isEmpty()) Ui.textCenter(tab == 1 ? "Star a sky to keep it here." : "No sky matches.", gx + gw / 2f, gy + 40, DS.T_BODY, DS.FAINT);
        ui.endScroll(((shown.size() + cols - 1) / cols) * (ch + 6));

        // options of the sky module
        float sxp = wx + ww - side - 10, syp = wy + 40, shp = wh - 50;
        float o2 = ui.beginScroll("skyopts", sxp, syp, side, shp);
        Rows r = new Rows(ui, sxp, syp - o2, side - 6, syp, syp + shp, "");
        float start = r.y;
        r.section("Options");
        for (Setting<?> s : sky.settings()) if (!s.name.equals("Sky")) r.setting(s);
        ui.endScroll(r.y - start + 6);
    }

    /** A procedural thumbnail of a sky (same ingredients as the real one, flattened). */
    private void thumb(SkyPreset s, float x, float y, float w, float h, float t) {
        ui.clipTo(x, y, w, h);
        Gfx.gradient(x, y, w, h * 0.62f, 0xFF000000 | s.top, 0xFF000000 | s.horizon);
        Gfx.gradient(x, y + h * 0.62f, w, h * 0.38f, 0xFF000000 | s.horizon, 0xFF000000 | s.bottom);
        if (s.glow > 0) Gfx.glow(x + w * 0.5f, y + h * 0.62f, w * 0.6f * Math.min(1f, s.glow + 0.3f), Gfx.alpha(0xFF000000 | s.glowColor, 0.45f * Math.min(1f, s.glow)));
        if (s.nebula > 0) {
            Gfx.glow(x + w * 0.3f, y + h * 0.3f, w * 0.4f, Gfx.alpha(0xFF000000 | s.nebA, 0.35f * Math.min(1f, s.nebula)));
            Gfx.glow(x + w * 0.72f, y + h * 0.22f, w * 0.35f, Gfx.alpha(0xFF000000 | s.nebB, 0.35f * Math.min(1f, s.nebula)));
        }
        if (s.galaxy > 0) {
            for (int i = 0; i < 8; i++) Gfx.glow(x + w * (0.1f + i * 0.11f), y + h * (0.55f - i * 0.06f), w * 0.12f, Gfx.alpha(0xFF000000 | s.galaxyColor, 0.18f * Math.min(1f, s.galaxy)));
        }
        if (s.stars > 0) {
            java.util.Random r = new java.util.Random(s.name.hashCode());
            int n = (int) (14 + 40 * Math.min(1f, s.stars));
            for (int i = 0; i < n; i++) {
                float sx = x + r.nextFloat() * w, sy = y + r.nextFloat() * h * 0.6f;
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 2 + i);
                Gfx.rect(sx, sy, 0.8f, 0.8f, Gfx.alpha(0xFF000000 | s.starColor, 0.4f + 0.5f * tw));
            }
        }
        if (s.aurora > 0 || s.shader == 10) {
            int a = s.shA >= 0 ? s.shA : s.auroraA, b = s.shB >= 0 ? s.shB : s.auroraB;
            for (int band = 0; band < 3; band++) {
                float py = 0, px = 0;
                for (int i = 0; i <= 20; i++) {
                    float fx = x + w * i / 20f;
                    float fy = y + h * (0.25f + band * 0.08f) + (float) Math.sin(i * 0.6f + t * 0.8f + band) * h * 0.06f;
                    if (i > 0) Gfx.line(px, py, fx, fy, 3f - band * 0.6f, Gfx.alpha(0xFF000000 | (band == 1 ? b : a), 0.45f));
                    px = fx;
                    py = fy;
                }
            }
        }
        if (s.celestial == SkyPreset.SUN || s.celestial == SkyPreset.MOON || s.celestial == SkyPreset.PLANET || s.celestial == SkyPreset.ECLIPSE) {
            float cx = x + w * 0.72f, cy = y + h * 0.32f, r = h * (s.celestial == SkyPreset.PLANET ? 0.16f : 0.1f);
            Gfx.glow(cx, cy, r * 2.6f, Gfx.alpha(0xFF000000 | s.celColor, 0.35f));
            Gfx.circle(cx, cy, r, s.celestial == SkyPreset.ECLIPSE ? 0xFF050608 : 0xFF000000 | s.celColor);
            if (s.celestial == SkyPreset.ECLIPSE) Gfx.annulus(cx, cy, r, r + 1.2f, 0, Math.PI * 2, 0xFF000000 | s.celColor);
        }
        if (s.grid || s.celestial == SkyPreset.RETRO) {
            float hz = y + h * 0.62f;
            for (int i = 1; i <= 5; i++) Gfx.rect(x, hz + (h * 0.38f) * (i * i) / 25f, w, 0.6f, Gfx.alpha(0xFF000000 | s.lineColor, 0.5f));
            for (int i = -6; i <= 6; i++) Gfx.line(x + w / 2f + i * w * 0.04f, hz, x + w / 2f + i * w * 0.2f, y + h, 0.6f, Gfx.alpha(0xFF000000 | s.lineColor, 0.4f));
        }
        if (s.lines > 0 && !s.grid) {
            for (int i = 0; i < Math.min(4, s.lines); i++) {
                float py = 0, px = 0;
                for (int j = 0; j <= 16; j++) {
                    float fx = x + w * j / 16f, fy = y + h * (0.2f + i * 0.1f) + (float) Math.sin(j * 0.5f + t + i * 2) * h * 0.05f;
                    if (j > 0) Gfx.line(px, py, fx, fy, 0.8f, Gfx.alpha(0xFF000000 | s.lineColor, 0.6f));
                    px = fx;
                    py = fy;
                }
            }
        }
        ui.reclip();
    }
}
