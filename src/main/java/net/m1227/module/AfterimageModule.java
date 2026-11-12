package net.m1227.module;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.render.Silhouette;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ghost trail: translucent copies of the player left behind at their last positions, each in
 * the pose it had there (body and head rotation, walking stride, arm swing), fading out.
 * Drawn only on your screen and depth-tested (never through walls).
 */
public final class AfterimageModule extends Module {
    private final Setting.Mode who, colorMode;
    private final Setting.Col color1, color2;
    private final Setting.Num count, spacing, life, opacity;
    private final Setting.Bool firstPerson;

    /** One ghost: where the player was and how they stood. */
    private static final class Snap {
        double x, y, z;
        float body, head, pitch, limb, limbAmt, swing;
        long born;
    }

    private final Map<Integer, ArrayDeque<Snap>> trails = new HashMap<Integer, ArrayDeque<Snap>>();

    public AfterimageModule() {
        super("Afterimage", "Ghost copies of the player left at their last positions.", Category.MOVEMENT, false);
        who = add(new Setting.Mode("Players", "Self", "Self", "Self and target", "Everyone"));
        colorMode = add(new Setting.Mode("Color", "Gradient", "Single", "Gradient", "Rainbow"));
        color1 = add(new Setting.Col("Color", 0x8B5CFF));
        color2 = add(new Setting.Col("Fade-to color", 0x2DD4FF)).showIf(() -> colorMode.is("Gradient"));
        count = add(new Setting.Num("Ghosts", 6, 2, 14, 1, ""));
        spacing = add(new Setting.Num("Spacing", 0.45, 0.15, 1.5, 0.05, "m"));
        life = add(new Setting.Num("Lifetime", 0.6, 0.2, 2.0, 0.05, "s"));
        opacity = add(new Setting.Num("Opacity", 0.45, 0.1, 0.9, 0.05, ""));
        firstPerson = add(new Setting.Bool("Show yours in first person", false));
    }

    @Override
    protected void onDisable() {
        trails.clear();
    }

    private boolean tracked(Minecraft mc, EntityPlayer p) {
        if (p == mc.thePlayer) return true;
        if (who.is("Everyone")) return !p.isInvisible();
        if (who.is("Self and target")) return p == CombatTracker.target(5000L);
        return false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            trails.clear();
            return;
        }
        long now = System.currentTimeMillis();
        long maxAge = (long) (life.f() * 1000f);
        List<EntityPlayer> list = mc.theWorld.playerEntities;
        java.util.Set<Integer> alive = new java.util.HashSet<Integer>();
        for (int i = 0; i < list.size(); i++) {
            EntityPlayer p = list.get(i);
            if (!tracked(mc, p) || p.isDead) continue;
            alive.add(p.getEntityId());
            ArrayDeque<Snap> q = trails.get(p.getEntityId());
            if (q == null) trails.put(p.getEntityId(), q = new ArrayDeque<Snap>());
            Snap last = q.peekLast();
            double d = last == null ? 99 : Math.sqrt(sq(p.posX - last.x) + sq(p.posY - last.y) + sq(p.posZ - last.z));
            if (d >= spacing.f()) {
                Snap s = new Snap();
                s.x = p.posX;
                s.y = p.posY;
                s.z = p.posZ;
                s.body = p.renderYawOffset;
                s.head = p.rotationYawHead;
                s.pitch = p.rotationPitch;
                s.limb = p.limbSwing;
                s.limbAmt = p.limbSwingAmount;
                s.swing = p.swingProgress;
                s.born = now;
                q.addLast(s);
            }
            while (q.size() > count.i() || (!q.isEmpty() && now - q.peekFirst().born > maxAge)) q.pollFirst();
        }
        trails.keySet().retainAll(alive);
    }

    private static double sq(double v) {
        return v * v;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || trails.isEmpty()) return;
        long now = System.currentTimeMillis();
        float maxAge = life.f() * 1000f;
        Silhouette.entityState(true);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        for (Map.Entry<Integer, ArrayDeque<Snap>> en : trails.entrySet()) {
            net.minecraft.entity.Entity ent = mc.theWorld.getEntityByID(en.getKey());
            if (!(ent instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) ent;
            boolean self = p == mc.thePlayer;
            if (self && mc.gameSettings.thirdPersonView == 0 && !firstPerson.on()) continue;
            int n = en.getValue().size(), k = 0;
            Iterator<Snap> it = en.getValue().iterator();
            while (it.hasNext()) {
                Snap s = it.next();
                k++;
                // skip the newest ghost while it still overlaps the player
                if (k == n && Math.sqrt(sq(p.posX - s.x) + sq(p.posZ - s.z)) < 0.3) continue;
                float age = Math.min(1f, (now - s.born) / maxAge);
                float a = opacity.f() * (1f - age) * (0.4f + 0.6f * k / (float) n);
                if (a < 0.02f) continue;
                ghost(mc, p, s, colorAt(k / (float) n, age), a, e.partialTicks);
            }
        }
        Silhouette.end();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        Silhouette.entityState(false);
    }

    private int colorAt(float order, float age) {
        if (colorMode.is("Rainbow")) return ParticleSystem.rainbow(order * 0.6f + (float) (ParticleSystem.INSTANCE.time * 0.1));
        if (colorMode.is("Gradient")) return ParticleSystem.mix(color2.rgb(), color1.rgb(), order);
        return color1.rgb();
    }

    /** Renders the player as a silhouette at a snapshot, with the snapshot's pose, then restores. */
    private void ghost(Minecraft mc, EntityPlayer p, Snap s, int rgb, float a, float pt) {
        float pb = p.prevRenderYawOffset, rb = p.renderYawOffset, ph = p.prevRotationYawHead, rh = p.rotationYawHead;
        float pp = p.prevRotationPitch, rp = p.rotationPitch, pl = p.prevLimbSwingAmount, rl = p.limbSwingAmount, ls = p.limbSwing;
        float psw = p.prevSwingProgress, sw = p.swingProgress;
        try {
            p.prevRenderYawOffset = p.renderYawOffset = s.body;
            p.prevRotationYawHead = p.rotationYawHead = s.head;
            p.prevRotationPitch = p.rotationPitch = s.pitch;
            p.prevLimbSwingAmount = p.limbSwingAmount = s.limbAmt;
            p.limbSwing = s.limb;
            p.prevSwingProgress = p.swingProgress = s.swing;
            Silhouette.begin(rgb, a);
            double x = s.x - mc.getRenderManager().viewerPosX, y = s.y - mc.getRenderManager().viewerPosY, z = s.z - mc.getRenderManager().viewerPosZ;
            Silhouette.renderAt(p, x, y, z, s.body, pt);
        } finally {
            p.prevRenderYawOffset = pb;
            p.renderYawOffset = rb;
            p.prevRotationYawHead = ph;
            p.rotationYawHead = rh;
            p.prevRotationPitch = pp;
            p.rotationPitch = rp;
            p.prevLimbSwingAmount = pl;
            p.limbSwingAmount = rl;
            p.limbSwing = ls;
            p.prevSwingProgress = psw;
            p.swingProgress = sw;
        }
    }
}
