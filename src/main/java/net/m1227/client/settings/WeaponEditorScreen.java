package net.m1227.client.settings;

import net.m1227.client.menu.MenuBackground;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Stage3D;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.ConfigManager;
import net.m1227.core.Setting;
import net.m1227.module.WeaponModelsModule;
import net.m1227.weapons.WeaponLibrary;
import net.m1227.weapons.WeaponProfile;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;

/**
 * Weapon editor: the sword model you hold, in 3D, turning (drag to turn it yourself), next to a
 * grid of all models (built-in and your own from config/1227/weapons) and every option of
 * Weapon Models. For your own models it also sets how the model sits in the hand (size,
 * offsets, angles, tip, hold point), which of its animations play for idle / swing / inspect /
 * draw (with buttons to play each one here), and opens or re-reads the folder. Every change
 * shows at once; the same settings are used in game.
 */
public final class WeaponEditorScreen extends UiScreen {
    private float yaw = 30f, spinVel;
    private boolean auto = true;
    private long last, dragUntil;
    private float lastDragX = -1;

    public WeaponEditorScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ConfigManager.save();
        if (WeaponModelsModule.INSTANCE != null) WeaponModelsModule.INSTANCE.setPreviewModel(null);
        for (WeaponLibrary.Entry e : WeaponLibrary.entries()) if (e.ready()) e.anim.play(null, 0f);
        WeaponLibrary.tick();
        flushSaves();
    }

    private static void flushSaves() {
        for (WeaponLibrary.Entry e : WeaponLibrary.entries()) if (dirty.remove(e)) e.saveNow();
    }

    private static final java.util.Set<WeaponLibrary.Entry> dirty = new java.util.HashSet<>();

    private static float now() {
        return (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        WeaponModelsModule wm = WeaponModelsModule.INSTANCE;
        long n = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (n - last) / 1e9f);
        last = n;

        float ww = Math.min(680, w - 16), wh = Math.min(420, h - 16);
        float wx = (w - ww) / 2f, wy = (h - wh) / 2f;
        Gfx.roundedRect(wx, wy, ww, wh, DS.R_LG, 0xF00E1016);
        Gfx.roundedOutline(wx, wy, ww, wh, DS.R_LG, 0.6f, DS.LINE_STRONG);
        ui.iconButton("back", wx + 10, wy + 10, 18, Glyph.BACK, "Back", true, this::back);
        Ui.textBold("Weapon editor", wx + 34, wy + 13, DS.T_LABEL, DS.TEXT);
        Ui.text("Applies to swords you hold. Drag the weapon to turn it", wx + 34, wy + 26, DS.T_SMALL, DS.FAINT);
        if (wm == null) return;
        WeaponLibrary.tick();
        wm.setPreviewModel(wm.modelSetting().get());
        WeaponLibrary.Entry im = WeaponModelsModule.imported(wm.modelSetting().get());

        // ---- preview
        float sw = ww * 0.46f, sx = wx + 10, sy = wy + 42, sh = wh - 52;
        Gfx.roundedRect(sx, sy, sw, sh, DS.R_LG, 0xFF0A0B10);
        Gfx.glow(sx + sw / 2f, sy + sh / 2f, sh * 0.55f, Gfx.alpha(DS.accent(), 0.14f));
        if (ui.hover(sx, sy, sw, sh) || lastDragX >= 0) {
            ui.drag(sx, sy, sw, sh, (px, py) -> {
                if (lastDragX >= 0) {
                    float d = px - lastDragX;
                    yaw += d * 1.2f;
                    spinVel = d * 1.2f / Math.max(0.008f, dt);
                }
                lastDragX = px;
                dragUntil = System.currentTimeMillis() + 150;
            });
        }
        if (!ui.dragging()) lastDragX = -1;
        if (System.currentTimeMillis() > dragUntil) {
            spinVel *= (float) Math.exp(-4f * dt);
            yaw += spinVel * dt + (auto ? 22f * dt : 0f);
        }
        float scale = Math.min(sw, sh) * 0.62f;
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        Stage3D.begin(sx + sw / 2f, sy + sh / 2f, scale, yaw, 12f);
        // stand the blade up: the model runs along the item's diagonal
        net.minecraft.client.renderer.GlStateManager.rotate(45f, 0f, 0f, 1f);
        wm.renderPreview();
        Stage3D.end();
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        if (im != null && !im.ready()) {
            Ui.textCenter(im.error != null ? "Could not read this model" : "Reading the model\u2026", sx + sw / 2f, sy + sh / 2f, DS.T_SMALL,
                    im.error != null ? DS.WARN : DS.MUTED);
        }
        ui.iconButton("spin", sx + sw - 24, sy + 6, 18, auto ? Glyph.PAUSE : Glyph.PLAY, auto ? "Stop turning" : "Turn by itself", true, () -> auto = !auto);
        if (!wm.isEnabled()) {
            Ui.textCenter("Weapon Models is off: your normal sword shows in game", sx + sw / 2f, sy + sh - 14, DS.T_SMALL, DS.WARN);
        }

        // ---- options
        float lx = sx + sw + 10, lw = wx + ww - 10 - lx, ly = wy + 42, lh = wh - 52;
        float off = ui.beginScroll("weap", lx, ly, lw, lh);
        Rows r = new Rows(ui, lx + 2, ly - off, lw - 8, ly, ly + lh, "");
        float start = r.y;
        r.toggle("Use custom weapon models", null, wm.isEnabled(), wm::toggle);
        r.section("Model");
        Setting.Mode model = wm.modelSetting();
        r.y = chips(model, WeaponModelsModule.MODELS, lx + 2, r.y, lw - 8, ly, ly + lh);
        java.util.List<WeaponLibrary.Entry> lib = WeaponLibrary.entries();
        java.util.List<String> inc = new java.util.ArrayList<>(), own = new java.util.ArrayList<>();
        for (WeaponLibrary.Entry e : lib) (e.included ? inc : own).add(e.name);
        r.section("3D models (textured)");
        if (inc.isEmpty()) r.note(WeaponLibrary.scanning() ? "Loading\u2026" : "Not found: press Reload.");
        else r.y = chips(model, inc.toArray(new String[0]), lx + 2, r.y, lw - 8, ly, ly + lh);
        r.section("Your models");
        if (own.isEmpty()) {
            r.note(WeaponLibrary.scanning() ? "Reading config/1227/weapons\u2026"
                    : "Put .bbmodel, .gltf / .glb, .fbx or .obj files in config/1227/weapons, then Reload.");
        } else {
            r.y = chips(model, own.toArray(new String[0]), lx + 2, r.y, lw - 8, ly, ly + lh);
        }
        r.buttons("Weapons folder", "Your model files and their settings", new String[]{"Open", "Reload"}, new int[]{Ui.SECONDARY, Ui.SECONDARY},
                new Runnable[]{() -> net.m1227.client.Os.open(WeaponLibrary.dir()), () -> {
                    flushSaves();
                    WeaponLibrary.scan(null);
                }});
        for (WeaponLibrary.Entry e : lib) if (e.error != null) r.note(e.name + ": " + e.error);
        if (im != null && im.ready()) imported(r, im);
        r.section("Per sword type");
        for (int i = 0; i < WeaponModelsModule.TIERS.length; i++) r.setting(wm.tierSetting(i));
        r.section("Look");
        for (Setting<?> s : wm.settings()) {
            if (s == model || s.name.endsWith(" model") || s.name.endsWith(" model file") || s.name.equals("Model file")) continue;
            if (im != null && (s.name.equals("Finish") || s.name.equals("Blade color") || s.name.equals("Hilt color") || s.name.equals("Length")
                    || s.name.equals("Thickness") || s.name.equals("Blade width") || s.name.equals("Glowing parts"))) continue;
            r.setting(s);
        }
        r.space(6);
        r.button("Reset weapon", "Every option back to its default", "Reset", Ui.DANGER, wm::reset);
        ui.endScroll(r.y - start + 6);
    }

    /** How an imported model sits in the hand, its animations and its info. */
    private void imported(Rows r, final WeaponLibrary.Entry e) {
        final WeaponProfile p = e.profile;
        net.m1227.weapons.WModel m = e.model;
        r.section(e.name);
        String info = m.format + " \u00b7 " + m.triangles() + " triangles \u00b7 " + m.textures.size() + " texture" + (m.textures.size() == 1 ? "" : "s")
                + " \u00b7 " + (m.clips.isEmpty() ? "no animations" : m.clips.size() + " animation" + (m.clips.size() == 1 ? "" : "s"));
        r.note(info);
        if (!m.credit.isEmpty()) r.note("Credit: " + m.credit);
        for (int i = 0; i < Math.min(2, m.warnings.size()); i++) r.note(m.warnings.get(i));
        slider(r, e, "Size", 0.3f, 3f, p.size, "x", v -> p.size = v);
        slider(r, e, "Along the blade", -0.6f, 0.6f, p.along, "", v -> p.along = v);
        slider(r, e, "Across", -0.4f, 0.4f, p.across, "", v -> p.across = v);
        slider(r, e, "Depth", -0.3f, 0.3f, p.depth, "", v -> p.depth = v);
        slider(r, e, "Twist", -180f, 180f, p.twist, "\u00b0", v -> p.twist = Math.round(v));
        slider(r, e, "Tilt", -90f, 90f, p.tilt, "\u00b0", v -> p.tilt = Math.round(v));
        slider(r, e, "Roll", -90f, 90f, p.roll, "\u00b0", v -> p.roll = Math.round(v));
        float hold = p.grip >= 0f ? p.grip : e.fit.gripAt;
        r.slider("Hold point", p.grip >= 0f ? "Where your hand holds it, from the handle end" : "Found automatically; drag to change",
                Math.round(hold * 100f) + "%", hold, v -> {
                    p.grip = Math.round(v * 100f) / 100f;
                    changed(e);
                });
        r.toggle("Swap tip and handle", null, p.flip, () -> {
            p.flip = !p.flip;
            changed(e);
        });
        r.toggle("Turn the edge around", null, p.turnEdge, () -> {
            p.turnEdge = !p.turnEdge;
            changed(e);
        });
        r.toggle("Glowing parts are full-bright", "Parts named glow / fx / emissive, or set to glow in the file", p.glow, () -> {
            p.glow = !p.glow;
            changed(e);
        });
        r.toggle("Show this model in third person", null, p.thirdPerson, () -> {
            p.thirdPerson = !p.thirdPerson;
            changed(e);
        });
        final String[] styles = {"Auto", "Classic", "Two-handed", "Heavy", "Thrust", "Flourish", "Twin"};
        r.cycle("Hold & swing", "Auto picks from the model's length", p.style, () -> {
            p.style = next(styles, p.style, 1);
            changed(e);
        }, () -> {
            p.style = next(styles, p.style, -1);
            changed(e);
        });
        // animation slots
        final String[] clipNames = new String[m.clips.size() + 2];
        clipNames[0] = "Auto";
        clipNames[1] = "None";
        for (int i = 0; i < m.clips.size(); i++) clipNames[i + 2] = m.clips.get(i).name;
        for (int s = 0; s < 4; s++) {
            final int slot = s;
            String cur = slotValue(p, s);
            String shown = cur.isEmpty() ? "Auto" + (e.anim.clip(s) != null ? " (" + e.anim.clip(s).name + ")" : " (none)") : cur.equals("-") ? "None" : cur;
            if (m.clips.isEmpty() && s > 0) continue;
            r.cycle(net.m1227.weapons.WeaponAnimator.SLOT_NAMES[s] + " animation", s == 0 ? (m.clips.isEmpty() ? "This model has no animations: the built-in motion is used" : null) : null,
                    shown, () -> setSlot(e, slot, next(clipNames, label(slotValue(p, slot)), 1)), () -> setSlot(e, slot, next(clipNames, label(slotValue(p, slot)), -1)));
        }
        if (!m.clips.isEmpty()) {
            int n = Math.min(4, m.clips.size());
            String[] t = new String[n];
            int[] st = new int[n];
            Runnable[] run = new Runnable[n];
            for (int i = 0; i < n; i++) {
                final net.m1227.weapons.WModel.Clip c = m.clips.get(i);
                t[i] = c.name;
                st[i] = Ui.SECONDARY;
                run[i] = () -> e.anim.play(c, now());
            }
            r.buttons("Play", "Plays one of the model's animations here", t, st, run);
        }
        r.button("Reset placement", "Size, offsets, angles and hold point back to automatic", "Reset", Ui.SECONDARY, () -> {
            p.size = 1f;
            p.along = p.across = p.depth = 0f;
            p.twist = p.tilt = p.roll = 0f;
            p.flip = p.turnEdge = false;
            p.grip = -1f;
            changed(e);
        });
    }

    private interface FloatSink {
        void set(float v);
    }

    private void slider(Rows r, final WeaponLibrary.Entry e, String label, final float min, final float max, float value, String suffix, final FloatSink sink) {
        String txt = (max - min) >= 60f ? Math.round(value) + suffix : String.format("%.2f", value) + suffix;
        r.slider(label, null, txt, (value - min) / (max - min), v -> {
            sink.set(min + v * (max - min));
            changed(e);
        });
    }

    private static void changed(WeaponLibrary.Entry e) {
        e.changed();
        dirty.add(e);
    }

    private static String slotValue(WeaponProfile p, int s) {
        return s == 0 ? p.idle : s == 1 ? p.swing : s == 2 ? p.inspect : p.draw;
    }

    private static String label(String v) {
        return v.isEmpty() ? "Auto" : v.equals("-") ? "None" : v;
    }

    private static void setSlot(WeaponLibrary.Entry e, int s, String shown) {
        String v = shown.equals("Auto") ? "" : shown.equals("None") ? "-" : shown;
        WeaponProfile p = e.profile;
        if (s == 0) p.idle = v;
        else if (s == 1) p.swing = v;
        else if (s == 2) p.inspect = v;
        else p.draw = v;
        changed(e);
    }

    private static String next(String[] list, String cur, int dir) {
        int i = 0;
        for (int k = 0; k < list.length; k++) if (list[k].equals(cur)) i = k;
        return list[(i + dir + list.length) % list.length];
    }

    /** Grid of model names; the selected one is highlighted. Returns the y after the grid. */
    private float chips(final Setting.Mode model, String[] names, float x, float y, float w, float top, float bottom) {
        float cx = x, ch = 18;
        for (final String m : names) {
            float cw = Ui.width(m, DS.T_SMALL) + 14;
            if (cx + cw > x + w) {
                cx = x;
                y += ch + 4;
            }
            boolean on = model.is(m);
            if (y + ch > top && y < bottom) {
                boolean hv = ui.hover(cx, y, cw, ch);
                Gfx.roundedRect(cx, y, cw, ch, 9, on ? DS.accent() : hv ? DS.SURFACE_3 : DS.SURFACE_2);
                Ui.text(m, cx + 7, y + 5, DS.T_SMALL, on ? 0xFFFFFFFF : DS.MUTED);
                ui.click(cx, y, cw, ch, () -> model.set(m));
            }
            cx += cw + 4;
        }
        return y + ch + 8;
    }
}
