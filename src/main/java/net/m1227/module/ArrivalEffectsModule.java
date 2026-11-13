package net.m1227.module;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.CubeBurst;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Arrival Effects: an effect where a player appears (joins, respawns, comes into view), where
 * one disappears (leaves or goes out of view) and at both ends of a teleport (moving more than a
 * few blocks in one tick), you included. Styles: Beam, Portal, Smoke, Pixels and Rift. Only on
 * your screen; it reacts to what Minecraft already shows you.
 */
public final class ArrivalEffectsModule extends Module {
    private final Setting.Mode style;
    private final Setting.Bool join, leave, teleport, self;
    private final Setting.Col color1, color2;
    private final Setting.Num size;

    private final Map<Integer, double[]> last = new HashMap<Integer, double[]>();
    private Object world;
    private EntityPlayer selfEntity;
    private int quietTicks;

    public ArrivalEffectsModule() {
        super("Arrival Effects", "Effects when players appear, leave or teleport.", Category.MOVEMENT, false);
        style = add(new Setting.Mode("Style", "Beam", "Beam", "Portal", "Smoke", "Pixels", "Rift"));
        color1 = add(new Setting.Col("Color", 0x8B5CFF));
        color2 = add(new Setting.Col("Second color", 0x3DF5FF));
        size = add(new Setting.Num("Size", 1.0, 0.5, 2.0, 0.05, "x"));
        join = add(new Setting.Bool("When a player appears", true));
        leave = add(new Setting.Bool("When a player leaves", true));
        teleport = add(new Setting.Bool("On teleports", true));
        self = add(new Setting.Bool("Also for you (join, respawn, teleport)", true));
        add(new Setting.Action("Preview", () -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            play(mc.thePlayer.posX - Math.sin(yaw) * 3, mc.thePlayer.posY, mc.thePlayer.posZ + Math.cos(yaw) * 3, true);
        }));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            last.clear();
            world = null;
            return;
        }
        if (mc.theWorld != world) {
            // new world: everyone already there appears at once, so only you get an effect
            world = mc.theWorld;
            last.clear();
            quietTicks = 40;
            if (self.on() && join.on()) play(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true);
            selfEntity = mc.thePlayer;
        } else if (mc.thePlayer != selfEntity) {
            selfEntity = mc.thePlayer; // respawn
            if (self.on() && join.on()) play(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true);
        }
        if (quietTicks > 0) quietTicks--;
        Set<Integer> seen = new HashSet<Integer>();
        int budget = 6; // never more than a few effects per tick (crowds loading in)
        List<EntityPlayer> players = mc.theWorld.playerEntities;
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer p = players.get(i);
            if (p.isInvisible() && p != mc.thePlayer) continue;
            seen.add(p.getEntityId());
            double[] prev = last.get(p.getEntityId());
            if (prev == null) {
                if (quietTicks == 0 && join.on() && p != mc.thePlayer && budget-- > 0) play(p.posX, p.posY, p.posZ, true);
            } else if (teleport.on() && (p != mc.thePlayer || self.on())) {
                double dx = p.posX - prev[0], dy = p.posY - prev[1], dz = p.posZ - prev[2];
                if (dx * dx + dy * dy + dz * dz > 8 * 8 && budget-- > 0) {
                    play(prev[0], prev[1], prev[2], false);
                    play(p.posX, p.posY, p.posZ, true);
                }
            }
            last.put(p.getEntityId(), new double[]{p.posX, p.posY, p.posZ, p.isDead || p.getHealth() <= 0 ? 1 : 0});
        }
        // players gone since last tick (not dead: kill effects handle deaths)
        java.util.Iterator<Map.Entry<Integer, double[]>> it = last.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, double[]> en = it.next();
            if (seen.contains(en.getKey())) continue;
            double[] p = en.getValue();
            if (leave.on() && p[3] == 0 && quietTicks == 0 && budget-- > 0) play(p[0], p[1], p[2], false);
            it.remove();
        }
    }

    /** Plays the effect at feet position (x, y, z); in = appearing (true) or leaving (false). */
    public void play(double x, double y, double z, boolean in) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        float s = size.f();
        int c0 = in ? color1.rgb() : color2.rgb(), c1 = in ? color2.rgb() : color1.rgb();
        String st = style.get();
        if (st.equals("Beam")) {
            for (int i = 0; i < 14; i++) {
                Particle p = ps.glow(x, y + i * 0.22 * s, z, 0f, in ? -0.4f : 1.4f, 0f, 0.6f, 0.9f * s, 0.2f, c0, c1, 0.75f);
                if (p != null) p.fadePow = 1.6f;
            }
            ring(ps, x, y + 0.05, z, 0.2f, 2.4f * s, c0, c1);
            for (int i = 0; i < 24; i++) {
                double a = Math.random() * Math.PI * 2, r = Math.random() * 0.5 * s;
                Particle p = ps.glow(x + Math.cos(a) * r, y + Math.random() * 0.4, z + Math.sin(a) * r, 0f, 1.5f + (float) Math.random() * 2f, 0f,
                        0.9f, 0.18f * s, 0.02f, c1, c0, 0.9f);
                if (p != null) p.drag = 1.2f;
            }
        } else if (st.equals("Portal")) {
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48, r = 1.1 * s, h = 0.1 + Math.random() * 1.8;
                float vx = (float) (-Math.cos(a) * 1.6 - Math.sin(a) * 1.2), vz = (float) (-Math.sin(a) * 1.6 + Math.cos(a) * 1.2);
                if (!in) {
                    vx = -vx;
                    vz = -vz;
                }
                double sx = in ? x + Math.cos(a) * r : x, sz = in ? z + Math.sin(a) * r : z;
                Particle p = ps.glow(sx, y + h, sz, vx * s, 0f, vz * s, 0.65f, 0.22f * s, 0.04f, c0, c1, 0.9f);
                if (p != null) p.fadePow = 1.3f;
            }
            ring(ps, x, y + 1.0, z, 1.6f * s, 0.2f, c0, c1);
        } else if (st.equals("Smoke")) {
            for (int i = 0; i < 26; i++) {
                double a = Math.random() * Math.PI * 2;
                Particle p = ps.glow(x + Math.cos(a) * 0.3, y + Math.random() * 1.6, z + Math.sin(a) * 0.3,
                        (float) Math.cos(a) * 0.9f, 0.3f + (float) Math.random() * 0.4f, (float) Math.sin(a) * 0.9f, 1.4f, 0.5f * s, 1.3f * s, 0xC8CCD6, c0, 0.45f);
                if (p != null) {
                    p.sprite = Sprite.SMOKE;
                    p.alphaBlend = true;
                    p.drag = 1.6f;
                }
            }
        } else if (st.equals("Pixels")) {
            CubeBurst.INSTANCE.spawn(x, y + 0.3, z, (int) (36 * s), s, 0.7f, c0, c1);
            ps.glow(x, y + 1, z, 0f, 0f, 0f, 0.3f, 2.5f * s, 5f * s, c1, c0, 0.5f);
        } else { // Rift: a bright vertical slit that opens and closes
            for (int i = 0; i < 20; i++) {
                double h = i / 19.0 * 2.0 * s;
                float wdt = (float) Math.sin(i / 19.0 * Math.PI) * 0.7f * s;
                Particle p = ps.glow(x, y + h, z, 0f, 0f, 0f, 0.55f, wdt * 0.2f, wdt, 0xFFFFFF, c0, 0.9f);
                if (p != null) p.fadePow = 1.2f;
            }
            ring(ps, x, y + 1.0 * s, z, 0.3f, 2.2f * s, c0, c1);
        }
    }

    private static void ring(ParticleSystem ps, double x, double y, double z, float from, float to, int c0, int c1) {
        Particle p = ps.glow(x, y, z, 0f, 0f, 0f, 0.55f, from, to, c0, c1, 0.85f);
        if (p != null) {
            p.sprite = Sprite.RING;
            p.flat = true;
            p.fadePow = 1.4f;
        }
    }
}
