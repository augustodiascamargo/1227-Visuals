package net.m1227.hud;

import java.util.Locale;
import net.m1227.core.CombatTracker;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

/**
 * The player you are fighting: head, name, animated health bar (with a
 * trailing "damage taken" bar), distance, armour and win/lose hint.
 */
public final class TargetHud extends HudModule {
    private final Setting.Num keep;
    private final Setting.Bool aimed, hint, head;
    private final Setting.Mode barColor;
    private float shown = -1f, trail = -1f, appear;
    private int lastId = -1;
    private long last;
    private EntityLivingBase current;

    public TargetHud() {
        super("Target HUD", "Health, distance and info of your target.", 0.55, 0.55);
        keep = add(new Setting.Num("Keep after last hit", 3, 1, 10, 0.5, "s"));
        aimed = add(new Setting.Bool("Also show the player you aim at", true));
        head = add(new Setting.Bool("Player head", true));
        hint = add(new Setting.Bool("Winning / losing hint", true));
        barColor = add(new Setting.Mode("Bar color", "Health", "Health", "Accent", "Gradient"));
    }

    @Override
    public float width() {
        return 138;
    }

    @Override
    public float height() {
        return 40;
    }

    private EntityLivingBase find() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase t = CombatTracker.target((long) (keep.f() * 1000));
        if (t == null && aimed.on() && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityPlayer) {
            t = (EntityLivingBase) mc.objectMouseOver.entityHit;
        }
        if (t != null && (t.isDead || t.worldObj != mc.theWorld)) t = null;
        return t;
    }

    @Override
    protected boolean hasContent() {
        current = find();
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        appear += ((current != null ? 1f : 0f) - appear) * Math.min(1f, dt * 12f);
        return current != null || appear > 0.02f;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase t = current;
        if (editing && t == null) t = mc.thePlayer;
        if (t == null) return;
        if (t.getEntityId() != lastId) {
            lastId = t.getEntityId();
            shown = trail = net.m1227.core.Health.of(t);
        }
        float a = editing ? 1f : appear;
        float max = Math.max(1f, t.getMaxHealth()), hp = Math.min(max, net.m1227.core.Health.of(t));
        shown += (hp - shown) * 0.25f;
        trail += (shown - trail) * (trail > shown ? 0.05f : 1f);
        GlStateManager.pushMatrix();
        float s = 0.9f + 0.1f * a;
        GlStateManager.translate(x + width() / 2f, y + height() / 2f, 0f);
        GlStateManager.scale(s, s, 1f);
        GlStateManager.translate(-(x + width() / 2f), -(y + height() / 2f), 0f);
        if (a > 0.95f) panel(x, y, width(), height());
        else Gfx.roundedRect(x, y, width(), height(), 4f, Gfx.alpha(0xFF0B0C11, bgOpacity.f() * a));
        float tx = x + 6;
        if (head.on() && t instanceof AbstractClientPlayer) {
            AbstractClientPlayer p = (AbstractClientPlayer) t;
            mc.getTextureManager().bindTexture(p.getLocationSkin());
            float hurt = t.hurtTime > 0 ? t.hurtTime / 10f : 0f;
            GlStateManager.enableBlend();
            GlStateManager.color(1f, 1f - hurt * 0.6f, 1f - hurt * 0.6f, a);
            Gui.drawScaledCustomSizeModalRect((int) x + 5, (int) y + 5, 8, 8, 8, 8, 30, 30, 64, 64);
            Gui.drawScaledCustomSizeModalRect((int) x + 5, (int) y + 5, 40, 8, 8, 8, 30, 30, 64, 64);
            GlStateManager.color(1f, 1f, 1f, 1f);
            tx = x + 40;
        }
        int alpha = (int) (a * 255) << 24;
        String name = t.getName();
        if (Gfx.textWidth(name) > x + width() - tx - 6) name = mc.fontRendererObj.trimStringToWidth(name, (int) (x + width() - tx - 12)) + "..";
        Gfx.text(name, tx, y + 6, alpha | 0xFFFFFF);
        float dist = mc.thePlayer == null ? 0f : mc.thePlayer.getDistanceToEntity(t);
        String info = String.format(Locale.ROOT, "%.1f❤  %.1fm", hp / 2f, dist);
        if (t instanceof EntityPlayer) info += "  " + ((EntityPlayer) t).getTotalArmorValue() + "⛨";
        Gfx.text(info, tx, y + 17, alpha | (LABEL & 0xFFFFFF));
        float bw = x + width() - 6 - tx, by = y + 29;
        Gfx.roundedRect(tx, by, bw, 5, 2.5f, Gfx.alpha(0xFF000000, 0.45f * a));
        float ft = Math.max(0f, Math.min(1f, trail / max)), fs = Math.max(0f, Math.min(1f, shown / max));
        if (ft > fs) Gfx.roundedRect(tx, by, bw * ft, 5, 2.5f, Gfx.alpha(0xFFFFFFFF, 0.55f * a));
        int c;
        if (barColor.is("Accent")) c = accent();
        else if (barColor.is("Gradient")) c = Gfx.mix(0xFFFF5A6A, accent(), fs);
        else c = fs > 0.5f ? Gfx.mix(0xFFFFC857, 0xFF3DDC84, (fs - 0.5f) * 2f) : Gfx.mix(0xFFFF5A6A, 0xFFFFC857, fs * 2f);
        if (fs > 0.01f) Gfx.roundedRect(tx, by, Math.max(5f, bw * fs), 5, 2.5f, Gfx.alpha(c, a));
        if (hint.on() && mc.thePlayer != null && t != mc.thePlayer) {
            float mine = mc.thePlayer.getHealth();
            String h = mine > hp + 1f ? "WIN" : mine < hp - 1f ? "LOSE" : "EVEN";
            int hc = h.equals("WIN") ? 0x3DDC84 : h.equals("LOSE") ? 0xFF5A6A : 0xFFC857;
            Gfx.textScaled(h, x + width() - 6 - Gfx.textWidth(h) * 0.7f, y + 6.5f, 0.7f, alpha | hc);
        }
        GlStateManager.popMatrix();
    }
}
