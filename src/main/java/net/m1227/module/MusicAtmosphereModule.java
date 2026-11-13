package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.music.MusicAnalysis;
import net.m1227.music.NowPlaying;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Dynamic Music Atmosphere: the space around you reacts gently to the music you are hearing.
 * Soft rings spread on the ground on the beat, small lights drift up with the energy of the song
 * and a faint halo above you breathes with the bass. Visual only, on your screen; it needs real
 * audio data (the 1227 player, or an allowed capture device) and stays still otherwise.
 */
public final class MusicAtmosphereModule extends Module {
    private final Setting.Bool rings, motes, halo;
    private final Setting.Col color1, color2;
    private final Setting.Num intensity;
    private float wasBeat;

    public MusicAtmosphereModule() {
        super("Music Atmosphere", "Rings on the beat and lights that move with your music.", Category.MUSIC, false);
        rings = add(new Setting.Bool("Rings on the beat", true));
        motes = add(new Setting.Bool("Rising lights", true));
        halo = add(new Setting.Bool("Breathing halo", false));
        color1 = add(new Setting.Col("Color", 0x8B5CFF));
        color2 = add(new Setting.Col("Second color", 0x2DD4FF));
        intensity = add(new Setting.Num("Intensity", 0.6, 0.1, 1.0, 0.05, ""));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity v = mc.getRenderViewEntity();
        if (mc.theWorld == null || v == null || NowPlaying.get().source == NowPlaying.Source.NONE) return;
        MusicAnalysis an = MusicAnalysis.INSTANCE;
        an.update();
        if (an.source() == MusicAnalysis.Source.NONE) return;
        ParticleSystem ps = ParticleSystem.INSTANCE;
        float pt = e.partialTicks, k = intensity.f();
        double x = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt;
        double y = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt;
        double z = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        int c0 = color1.rgb(), c1 = color2.rgb();
        if (rings.on() && an.beat >= 0.99f && wasBeat < 0.99f) {
            Particle p = ps.glow(x, y + 0.05, z, 0f, 0f, 0f, 0.9f, 0.4f, 3.2f + 2f * k, c0, c1, 0.5f * k);
            if (p != null) {
                p.sprite = Sprite.RING;
                p.flat = true;
                p.fadePow = 1.5f;
            }
        }
        wasBeat = an.beat;
        if (motes.on() && Math.random() < an.energy * k * 0.9f) {
            double a = Math.random() * Math.PI * 2, r = 1.2 + Math.random() * 2.5;
            Particle p = ps.glow(x + Math.cos(a) * r, y + Math.random() * 0.4, z + Math.sin(a) * r, 0f, 0.3f + an.energy * 0.8f, 0f,
                    1.6f, 0.06f, 0.0f, c1, c0, 0.7f * k);
            if (p != null) {
                p.sway = 0.15f;
                p.swayFreq = 2f;
                p.swayPhase = (float) (Math.random() * 6.28);
            }
        }
        if (halo.on()) {
            float s = 0.5f + an.bass * 0.5f * k;
            Particle p = ps.frame(x, y + v.height + 0.35, z);
            if (p != null) {
                p.sprite = Sprite.HALO;
                p.flat = true;
                p.size0 = p.size1 = s;
                p.setColors(c0, c1);
                p.alpha = 0.35f + 0.4f * an.bass * k;
            }
        }
    }
}
