package net.m1227.hud;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.module.ClientModule;
import net.m1227.ui.Gfx;
import net.m1227.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Base for movable HUD elements. Position is stored as a fraction of the
 * screen (so it survives resolution and GUI-scale changes) and edited in
 * the HUD editor (/1227 hud, or Client > Edit HUD layout).
 */
public abstract class HudModule extends Module {
    public final Setting.Num posX, posY, scale;
    /** Locked elements can't be dragged, resized or reset in the editor (middle-click toggles it there). */
    public final Setting.Bool locked;
    protected final Setting.Mode background;
    protected final Setting.Num bgOpacity;
    protected final Setting.Bool accentText;
    private final double defX, defY;

    protected HudModule(String name, String description, double x, double y) {
        super(name, description, Category.HUD, false);
        defX = x;
        defY = y;
        posX = add(new Setting.Num("x", x, 0, 1, 0.0001, "")).showIf(() -> false);
        posY = add(new Setting.Num("y", y, 0, 1, 0.0001, "")).showIf(() -> false);
        scale = add(new Setting.Num("Scale", 1.0, 0.5, 2.5, 0.05, "x"));
        background = add(new Setting.Mode("Background", "Liquid Glass", "Liquid Glass", "Glass", "Solid", "Accent bar", "Outline", "Gradient", "None"));
        bgOpacity = add(new Setting.Num("Background opacity", 0.55, 0.0, 1.0, 0.05, "")).showIf(() -> !background.is("None"));
        accentText = add(new Setting.Bool("Accent colored values", true));
        locked = add(new Setting.Bool("Lock position", false));
    }

    /** Unscaled size in GUI pixels. */
    public abstract float width();

    public abstract float height();

    /** Draws at (x, y) in the already-scaled space. editing = drawn inside the HUD editor (show sample data). */
    protected abstract void draw(float x, float y, boolean editing);

    /** Hidden when there is nothing to show (e.g. Target HUD without a target), except in the editor. */
    protected boolean hasContent() {
        return true;
    }

    public void resetPosition() {
        posX.set(defX);
        posY.set(defY);
        scale.set(1.0);
    }

    public float screenX(ScaledResolution sr) {
        float w = width() * scale.f();
        return Math.max(0f, Math.min(sr.getScaledWidth() - w, posX.f() * sr.getScaledWidth()));
    }

    public float screenY(ScaledResolution sr) {
        float h = height() * scale.f();
        return Math.max(0f, Math.min(sr.getScaledHeight() - h, posY.f() * sr.getScaledHeight()));
    }

    public void render(ScaledResolution sr, boolean editing) {
        if (!editing && !hasContent()) return;
        float s = scale.f();
        GlStateManager.pushMatrix();
        GlStateManager.scale(s, s, 1f);
        draw(screenX(sr) / s, screenY(sr) / s, editing);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo || mc.currentScreen instanceof HudEditorScreen) return;
        if (net.m1227.module.CinematicModule.hidesHud(this)) return;
        render(e.resolution, false);
    }

    // ------------------------------------------------------------ helpers

    protected void panel(float x, float y, float w, float h) {
        String b = background.get();
        if (b.equals("None")) return;
        if (b.equals("Liquid Glass")) {
            // clear, slightly magnifying glass with a bright rim (same look as the Liquid Glass menu)
            int sf = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor();
            Glass.panel(x, y, w, h, 5f, scale.f(), net.m1227.ui.Theme.hudTint(), bgOpacity.f() * 0.38f,
                    net.m1227.ui.Theme.hudBlurPx() * scale.f() * sf * 0.4f, 0.03f);
        } else if (b.equals("Glass")) {
            int sf = new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor();
            Glass.panel(x, y, w, h, 4f, scale.f(), net.m1227.ui.Theme.hudTint(), bgOpacity.f() * 0.75f,
                    net.m1227.ui.Theme.hudBlurPx() * scale.f() * sf * 0.5f);
            Gfx.roundedOutline(x, y, w, h, 4f, 0.6f, Gfx.alpha(0xFFFFFFFF, 0.12f * net.m1227.ui.Theme.border()));
        } else if (b.equals("Accent bar")) {
            Gfx.roundedRect(x, y, w, h, 4f, Gfx.alpha(0xFF0B0C11, bgOpacity.f()));
            Gfx.roundedRect(x, y + 2f, 1.6f, h - 4f, 0.8f, accent());
        } else if (b.equals("Outline")) {
            Gfx.roundedRect(x, y, w, h, 4f, Gfx.alpha(0xFF0B0C11, bgOpacity.f() * 0.45f));
            Gfx.roundedOutline(x, y, w, h, 4f, 0.8f, Gfx.alpha(accent(), 0.85f));
        } else if (b.equals("Gradient")) {
            int alt = 0xFF000000 | ClientModule.accentAlt();
            Gfx.roundedRect(x, y, w, h, 4f, Gfx.alpha(0xFF0B0C11, bgOpacity.f()));
            Gfx.gradientH(x + 2f, y + 0.5f, w - 4f, 1f, accent(), alt);
            Gfx.roundedOutline(x, y, w, h, 4f, 0.5f, Gfx.alpha(0xFFFFFFFF, 0.08f));
        } else {
            Gfx.roundedRect(x, y, w, h, 4f, Gfx.alpha(0xFF0B0C11, bgOpacity.f()));
        }
    }

    protected int accent() {
        return 0xFF000000 | ClientModule.accent();
    }

    protected int value() {
        return accentText.on() ? accent() : 0xFFFFFFFF;
    }

    protected static final int LABEL = 0xFFA9ADBA;
}
