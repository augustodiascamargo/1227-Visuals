package net.m1227.hud;

import java.util.Collection;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/** Active potion effects with icon, level and time left (blinks in the last 10 s). */
public final class PotionHud extends HudModule {
    private static final ResourceLocation INV = new ResourceLocation("textures/gui/container/inventory.png");
    private float w = 100, h = 22;

    public PotionHud() {
        super("Potions", "Active effects and time left.", 0.80, 0.30);
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return h;
    }

    @Override
    protected boolean hasContent() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && !mc.thePlayer.getActivePotionEffects().isEmpty();
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        Collection<PotionEffect> list = mc.thePlayer == null ? null : mc.thePlayer.getActivePotionEffects();
        if ((list == null || list.isEmpty()) && editing) {
            java.util.List<PotionEffect> l = new java.util.ArrayList<PotionEffect>();
            l.add(new PotionEffect(Potion.moveSpeed.id, 2400, 1));
            l.add(new PotionEffect(Potion.fireResistance.id, 3600, 0));
            list = l;
        }
        if (list == null || list.isEmpty()) return;
        float maxW = 60;
        for (PotionEffect e : list) {
            maxW = Math.max(maxW, 28 + Gfx.textWidth(name(e)));
        }
        w = maxW + 6;
        h = list.size() * 22 + 2;
        panel(x, y, w, h);
        float cy = y + 2;
        for (PotionEffect e : list) {
            Potion p = Potion.potionTypes[e.getPotionID()];
            if (p != null && p.hasStatusIcon()) {
                int idx = p.getStatusIconIndex();
                GlStateManager.color(1f, 1f, 1f, 1f);
                GlStateManager.enableBlend();
                mc.getTextureManager().bindTexture(INV);
                mc.ingameGUI.drawTexturedModalRect((int) x + 3, (int) cy + 2, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18);
            }
            Gfx.text(name(e), x + 25, cy + 2, 0xFFFFFFFF);
            boolean blink = e.getDuration() < 200 && (System.currentTimeMillis() / 300) % 2 == 0;
            Gfx.text(Potion.getDurationString(e), x + 25, cy + 11, blink ? LABEL & 0x55FFFFFF : value());
            cy += 22;
        }
    }

    private static String name(PotionEffect e) {
        String n = I18n.format(e.getEffectName());
        int a = e.getAmplifier();
        if (a > 0 && a < 4) n += " " + new String[]{"II", "III", "IV"}[a - 1];
        else if (a >= 4) n += " " + (a + 1);
        return n;
    }
}
