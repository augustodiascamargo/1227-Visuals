package net.m1227.module;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Floating numbers showing health lost (in hearts or HP), read from the
 * health the server reports for each entity. Depth-tested, so they are
 * hidden behind walls. Critical hits from you show in a separate colour.
 */
public final class DamageNumbersModule extends Module {
    private static final int MAX = 48;
    private final Setting.Mode who, unit;
    private final Setting.Col color, critColor, healColor;
    private final Setting.Num scale, duration;
    private final Setting.Bool heals;

    private final Map<Integer, Float> health = new HashMap<Integer, Float>();
    private final double[] nx = new double[MAX], ny = new double[MAX], nz = new double[MAX];
    private final float[] nAge = new float[MAX], nVal = new float[MAX], nDrift = new float[MAX];
    private final int[] nCol = new int[MAX];
    private int n;
    private long lastCrit;
    private int critTarget = -1;

    public DamageNumbersModule() {
        super("Damage Numbers", "Floating numbers for damage and healing.", Category.COMBAT, false);
        who = add(new Setting.Mode("Show for", "Your targets", "Your targets", "All players", "All living"));
        unit = add(new Setting.Mode("Unit", "Hearts", "Hearts", "HP"));
        color = add(new Setting.Col("Damage color", 0xFF5A6A));
        critColor = add(new Setting.Col("Critical color", 0xFFC857));
        heals = add(new Setting.Bool("Show healing", true));
        healColor = add(new Setting.Col("Heal color", 0x3DDC84));
        scale = add(new Setting.Num("Size", 1.0, 0.5, 2.0, 0.05, "x"));
        duration = add(new Setting.Num("Duration", 1.0, 0.4, 2.5, 0.1, "s"));
    }

    @SubscribeEvent
    public void onHit(CombatTracker.HitEvent e) {
        if (e.critical) {
            lastCrit = System.currentTimeMillis();
            critTarget = e.target.getEntityId();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            health.clear();
            n = 0;
            return;
        }
        String w = who.get();
        EntityLivingBase target = CombatTracker.target(6000L);
        List<Entity> list = mc.theWorld.loadedEntityList;
        Map<Integer, Float> seen = new HashMap<Integer, Float>();
        for (int i = 0; i < list.size(); i++) {
            Entity en = list.get(i);
            if (!(en instanceof EntityLivingBase) || en == mc.thePlayer) continue;
            if (en.getDistanceSqToEntity(mc.thePlayer) > 48 * 48) continue;
            EntityLivingBase l = (EntityLivingBase) en;
            boolean wanted = w.equals("All living") || (w.equals("All players") && en instanceof net.minecraft.entity.player.EntityPlayer)
                    || (w.equals("Your targets") && l == target);
            float hp = l.getHealth();
            Float before = health.get(en.getEntityId());
            seen.put(en.getEntityId(), hp);
            if (!wanted || before == null) continue;
            float d = before - hp;
            if (d > 0.05f) {
                boolean crit = en.getEntityId() == critTarget && System.currentTimeMillis() - lastCrit < 700;
                add(l, d, crit ? critColor.rgb() : color.rgb());
            } else if (d < -0.05f && heals.on() && hp < l.getMaxHealth() + 0.01f) {
                add(l, d, healColor.rgb());
            }
        }
        health.clear();
        health.putAll(seen);
    }

    private void add(EntityLivingBase l, float amount, int c) {
        if (n >= MAX) return;
        int i = n++;
        nx[i] = l.posX + (Math.random() - 0.5) * 0.6;
        ny[i] = l.posY + l.height + 0.2;
        nz[i] = l.posZ + (Math.random() - 0.5) * 0.6;
        nAge[i] = 0f;
        nVal[i] = amount;
        nDrift[i] = (float) (Math.random() - 0.5) * 0.4f;
        nCol[i] = c;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (n == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        ParticleSystem ps = ParticleSystem.INSTANCE;
        RenderManager rm = mc.getRenderManager();
        FontRenderer fr = mc.fontRendererObj;
        float life = duration.f();
        boolean hearts = unit.is("Hearts");
        for (int i = 0; i < n; ) {
            nAge[i] += ps.dt;
            if (nAge[i] >= life) {
                int l = --n;
                nx[i] = nx[l]; ny[i] = ny[l]; nz[i] = nz[l];
                nAge[i] = nAge[l]; nVal[i] = nVal[l]; nDrift[i] = nDrift[l]; nCol[i] = nCol[l];
                continue;
            }
            float t = nAge[i] / life;
            float v = Math.abs(nVal[i]) / (hearts ? 2f : 1f);
            String s = (nVal[i] < 0 ? "+" : "-") + String.format(Locale.ROOT, v >= 10 ? "%.0f" : "%.1f", v) + (hearts ? "❤" : "");
            float pop = t < 0.15f ? 0.6f + t / 0.15f * 0.6f : 1.2f - Math.min(0.2f, (t - 0.15f));
            float sc = 0.026f * scale.f() * pop;
            float alpha = t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f;
            GlStateManager.pushMatrix();
            GlStateManager.translate((float) (nx[i] - ps.rX) + nDrift[i] * t, (float) (ny[i] - ps.rY) + t * 0.6f, (float) (nz[i] - ps.rZ));
            GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
            GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
            GlStateManager.scale(-sc, -sc, sc);
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            int a = Math.max(8, (int) (alpha * 255f));
            float x = -fr.getStringWidth(s) / 2f;
            fr.drawString(s, x + 1f, 1f, (a << 24), false);
            fr.drawString(s, x, 0f, (a << 24) | nCol[i], false);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            i++;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
