package net.m1227.client.settings;

import net.m1227.client.menu.MenuBackground;
import net.m1227.client.ui.Comet2D;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.LocalSkin;
import net.m1227.client.ui.Stage3D;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.module.EnergyShellModule;
import net.m1227.module.HaloModule;
import net.m1227.module.WingsModule;
import net.m1227.ui.Gfx;
import net.m1227.vfx.Puppet;
import net.minecraft.client.gui.GuiScreen;

/**
 * Cosmetics: your character in 3D wearing what is turned on (halo / china hat / comet / thorn
 * crown / orbs, wings, energy shell), turned with the mouse, next to the list of cosmetics. Each
 * one switches on and off here and opens its options inline; the preview updates live.
 */
public final class CosmeticsScreen extends UiScreen {
    private Module open;
    private float yaw = -25f, pitch = 6f, spinVel;
    private boolean auto = true;
    private long last, dragUntil;
    private float lastDragX = -1;
    private final Comet2D comet = new Comet2D();
    private final Puppet.Pose pose = new Puppet.Pose();

    public CosmeticsScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        super.initGui();
        LocalSkin.request();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ConfigManager.save();
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        long n = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (n - last) / 1e9f);
        last = n;
        float t = n / 1e9f % 3600f;

        float ww = Math.min(680, w - 16), wh = Math.min(420, h - 16);
        float wx = (w - ww) / 2f, wy = (h - wh) / 2f;
        Gfx.roundedRect(wx, wy, ww, wh, DS.R_LG, 0xF00E1016);
        Gfx.roundedOutline(wx, wy, ww, wh, DS.R_LG, 0.6f, DS.LINE_STRONG);
        ui.iconButton("back", wx + 10, wy + 10, 18, Glyph.BACK, "Back", true, this::back);
        Ui.textBold("Cosmetics", wx + 34, wy + 13, DS.T_LABEL, DS.TEXT);
        Ui.text("Drag your character to turn it", wx + 34, wy + 26, DS.T_SMALL, DS.FAINT);

        // ---- preview stage
        float sw = ww * 0.46f, sx = wx + 10, sy = wy + 42, sh = wh - 52;
        Gfx.roundedRect(sx, sy, sw, sh, DS.R_LG, 0xFF0A0B10);
        Gfx.glow(sx + sw / 2f, sy + sh * 0.45f, sh * 0.6f, Gfx.alpha(DS.accent(), 0.12f));
        float size = sh * 0.28f, feetX = sx + sw / 2f, feetY = sy + sh * 0.86f;
        Stage3D.floor(feetX, feetY, size * 1.6f, DS.accent());
        // drag to turn; it keeps turning slowly by itself
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
            yaw += spinVel * dt + (auto ? 14f * dt : 0f);
        }
        pose.reset();
        float breathe = (float) Math.sin(t * 1.8);
        pose.rArm[2] = 3f + breathe * 2f;
        pose.lArm[2] = 3f + breathe * 2f;
        pose.body[0] = breathe * 0.8f;
        boolean cometOn = false;
        Stage3D.begin(feetX, feetY, size, yaw, pitch);
        if (LocalSkin.skin() != null) Puppet.render(LocalSkin.skin(), LocalSkin.slim(), 0, 0, 0, 0f, pose, 1f);
        Module wings = ModuleManager.byName("Wings"), shell = ModuleManager.byName("Energy Shell");
        if (wings instanceof WingsModule) ((WingsModule) wings).renderPreview(t);
        if (HaloModule.INSTANCE != null) cometOn = HaloModule.INSTANCE.renderPreview(t, 0f);
        if (shell instanceof EnergyShellModule) ((EnergyShellModule) shell).renderPreview();
        Stage3D.end();
        if (cometOn) comet.draw(feetX, feetY - size * 2.25f, size, dt, t);
        ui.iconButton("spin", sx + sw - 24, sy + 6, 18, auto ? Glyph.PAUSE : Glyph.PLAY, auto ? "Stop turning" : "Turn by itself", true, () -> auto = !auto);
        Ui.text("Halo, Wings and Energy Shell show here; the others show in game.", sx + 8, sy + sh - 12, 0.72f, DS.FAINT);

        // ---- list
        float lx = sx + sw + 10, lw = wx + ww - 10 - lx, ly = wy + 42, lh = wh - 52;
        float off = ui.beginScroll("cos", lx, ly, lw, lh);
        Rows r = new Rows(ui, lx + 2, ly - off, lw - 8, ly, ly + lh, "");
        float start = r.y;
        for (final Module m : ModuleManager.inCategory(Category.COSMETICS)) {
            r.module(m, () -> open = open == m ? null : m);
            if (open == m) {
                for (net.m1227.core.Setting<?> s : m.settings()) r.setting(s);
                r.space(6);
            }
        }
        r.section("Weapons");
        r.button("Weapon editor", "Model, finish, colours and size of your sword, with a 3D preview", "Open", Ui.PRIMARY,
                () -> mc.displayGuiScreen(new WeaponEditorScreen(this)));
        ui.endScroll(r.y - start + 6);
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (Rows.capturing != null) {
            Rows.capturing.set(key == org.lwjgl.input.Keyboard.KEY_ESCAPE || key == org.lwjgl.input.Keyboard.KEY_BACK ? 0 : key);
            Rows.capturing = null;
            return;
        }
        super.keyTyped(c, key);
    }
}
