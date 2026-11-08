package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * 12 ambience styles. Each one changes the fog colour and how close the fog
 * starts, grades the screen with a light colour tint and vignette (drawn
 * under the HUD, so text stays readable), and fills the air with its own
 * particles. Fog changes are skipped under water or lava.
 */
public final class AmbienceModule extends Module {
    private static final class Style {
        final String name;
        final int fog, tint, p0, p1;
        final float fogDensity, tintAlpha, vignette;
        final int particle; // 0 none, 1 motes, 2 embers, 3 snow, 4 stars, 5 fireflies, 6 petals, 7 ash, 8 neon dust
        final float rate;

        Style(String name, int fog, float fogDensity, int tint, float tintAlpha, float vignette, int particle, int p0, int p1, float rate) {
            this.name = name;
            this.fog = fog;
            this.fogDensity = fogDensity;
            this.tint = tint;
            this.tintAlpha = tintAlpha;
            this.vignette = vignette;
            this.particle = particle;
            this.p0 = p0;
            this.p1 = p1;
            this.rate = rate;
        }
    }

    private static final Style[] STYLES = {
            new Style("Dark", 0x07080B, 0.45f, 0x000000, 0.12f, 0.55f, 1, 0xC9CED8, 0x6B7280, 10),
            new Style("Neon", 0x14062A, 0.35f, 0xFF2DB0, 0.06f, 0.45f, 8, 0xFF2DB0, 0x2DF6FF, 16),
            new Style("Fantasy", 0x2A1F4D, 0.3f, 0xC77DFF, 0.07f, 0.35f, 5, 0xFFF06B, 0xC77DFF, 14),
            new Style("Cosmic", 0x05030F, 0.4f, 0x5C2DFF, 0.08f, 0.5f, 4, 0xFFFFFF, 0xB8A6FF, 18),
            new Style("Dream", 0xF0D8F0, 0.3f, 0xFFB8F0, 0.08f, 0.25f, 6, 0xFFD6E8, 0xFFFFFF, 12),
            new Style("Cyberpunk", 0x0A1A24, 0.4f, 0x00E5FF, 0.06f, 0.5f, 8, 0xFFE600, 0x00E5FF, 16),
            new Style("Mystical", 0x0A1F1A, 0.45f, 0x3DFFD0, 0.06f, 0.45f, 5, 0x9AFFE0, 0x3DFF8A, 14),
            new Style("Minimal", 0xB8BEC8, 0.0f, 0xFFFFFF, 0.0f, 0.15f, 0, 0, 0, 0),
            new Style("Aurora", 0x06231F, 0.35f, 0x3DFFA0, 0.06f, 0.4f, 4, 0x3DFFA0, 0x2DD4FF, 12),
            new Style("Sunset", 0x8A4A3D, 0.3f, 0xFF7A3D, 0.08f, 0.35f, 1, 0xFFC36B, 0xFF8A5C, 10),
            new Style("Frozen", 0xC8E4F0, 0.5f, 0x9AE8FF, 0.07f, 0.35f, 3, 0xFFFFFF, 0xBFEFFF, 26),
            new Style("Infernal", 0x2A0500, 0.55f, 0xFF3D00, 0.09f, 0.55f, 2, 0xFF7A2E, 0xFFC36B, 22)
    };

    private final Setting.Mode style;
    private final Setting.Num intensity, fogAmount, particles, area;
    private final Setting.Bool fog, grading;
    private float acc;

    public AmbienceModule() {
        super("Ambience", "12 atmospheres: fog, colour grading and ambient particles.", Category.AMBIENCE, false);
        String[] names = new String[STYLES.length];
        for (int i = 0; i < names.length; i++) names[i] = STYLES[i].name;
        style = add(new Setting.Mode("Style", "Dark", names));
        intensity = add(new Setting.Num("Intensity", 1.0, 0.0, 1.5, 0.05, "x"));
        fog = add(new Setting.Bool("Change fog", true));
        fogAmount = add(new Setting.Num("Fog closeness", 1.0, 0.0, 2.0, 0.05, "x"));
        grading = add(new Setting.Bool("Colour grading and vignette", true));
        particles = add(new Setting.Num("Particles", 1.0, 0.0, 3.0, 0.1, "x"));
        area = add(new Setting.Num("Particle area", 18, 8, 40, 1, "m"));
    }

    private Style cur() {
        String n = style.get();
        for (Style s : STYLES) {
            if (s.name.equals(n)) return s;
        }
        return STYLES[0];
    }

    // low priority: runs after the sky's fog tint, so the ambience wins
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onFogColor(EntityViewRenderEvent.FogColors e) {
        if (!fog.on() || underFluid(e.block.getMaterial())) return;
        Style s = cur();
        float k = Math.min(1f, 0.85f * intensity.f());
        e.red = e.red + (((s.fog >> 16) & 255) / 255f - e.red) * k;
        e.green = e.green + (((s.fog >> 8) & 255) / 255f - e.green) * k;
        e.blue = e.blue + ((s.fog & 255) / 255f - e.blue) * k;
    }

    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.RenderFogEvent e) {
        if (!fog.on() || underFluid(e.block.getMaterial())) return;
        float d = Math.min(1f, cur().fogDensity * fogAmount.f() * intensity.f());
        if (d <= 0.01f) return;
        float far = e.farPlaneDistance;
        GlStateManager.setFog(GL11.GL_LINEAR);
        GlStateManager.setFogStart(e.fogMode == -1 ? 0f : far * (0.75f - 0.62f * d));
        GlStateManager.setFogEnd(far * (1f - 0.42f * d));
    }

    private static boolean underFluid(Material m) {
        return m == Material.water || m == Material.lava;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !grading.on()) return;
        Style s = cur();
        float k = intensity.f();
        float w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
        if (s.tintAlpha > 0f) Gfx.rect(0, 0, w, h, Gfx.alpha(0xFF000000 | s.tint, Math.min(0.25f, s.tintAlpha * k)));
        float v = Math.min(0.8f, s.vignette * k);
        if (v > 0.01f) vignette(w, h, v);
    }

    /** Darkened edges: four gradient bands. */
    private static void vignette(float w, float h, float a) {
        int dark = Gfx.alpha(0xFF000000, a), clear = 0x00000000;
        float bandY = h * 0.28f;
        Gfx.gradient(0, 0, w, bandY, dark, clear);
        Gfx.gradient(0, h - bandY, w, bandY, clear, dark);
        // sides: draw rotated by swapping roles with thin vertical strips
        int steps = 24;
        float bandX = w * 0.22f;
        for (int i = 0; i < steps; i++) {
            float f = 1f - i / (float) steps;
            int c = Gfx.alpha(0xFF000000, a * f * f);
            Gfx.rect(i * bandX / steps, 0, bandX / steps + 0.5f, h, c);
            Gfx.rect(w - (i + 1) * bandX / steps, 0, bandX / steps + 0.5f, h, c);
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (ps.dt <= 0f || Minecraft.getMinecraft().thePlayer == null) return;
        Style s = cur();
        if (s.particle == 0) return;
        acc += ps.dt * s.rate * particles.f() * PerformanceModule.qualityScale();
        int m = Math.min((int) acc, 24);
        acc -= (int) acc;
        float ar = area.f();
        for (int i = 0; i < m; i++) {
            double x = ps.camX + ps.rand(-ar, ar), y = ps.camY + ps.rand(-4f, 7f), z = ps.camZ + ps.rand(-ar, ar);
            spawn(ps, s, x, y, z);
        }
    }

    private void spawn(ParticleSystem ps, Style s, double x, double y, double z) {
        int c = ParticleSystem.mix(s.p0, s.p1, ps.rng.nextFloat());
        float a = Math.min(1f, intensity.f());
        Particle p;
        switch (s.particle) {
            case 2: // embers rise and flicker
                p = ps.glow(x, y - 3, z, 0.1f, ps.rand(0.3f, 0.8f), 0f, ps.rand(3f, 5f), ps.rand(0.05f, 0.11f), 0.02f, c, s.p1, a);
                if (p == null) return;
                p.pulse = 0.5f;
                p.pulseFreq = ps.rand(6f, 12f);
                p.sway = 0.35f;
                p.swayFreq = ps.rand(1.5f, 3f);
                break;
            case 3: // snow
                p = ps.glow(x, y + 4, z, 0.2f, ps.rand(-1f, -0.6f), 0f, ps.rand(5f, 8f), ps.rand(0.05f, 0.09f), 0.04f, 0xFFFFFF, c, a * 0.9f);
                if (p == null) return;
                p.sway = 0.35f;
                p.swayFreq = ps.rand(1f, 2f);
                break;
            case 4: // twinkling stars
                p = ps.glow(x, y, z, 0f, ps.rand(-0.05f, 0.05f), 0f, ps.rand(3f, 5f), ps.rand(0.08f, 0.16f), 0.02f, c, s.p1, a);
                if (p == null) return;
                p.sprite = Sprite.STAR;
                p.rotVel = ps.rand(-1f, 1f);
                p.pulse = 0.8f;
                p.pulseFreq = ps.rand(3f, 6f);
                break;
            case 5: // fireflies
                p = ps.glow(x, y - 2, z, 0f, ps.rand(-0.05f, 0.1f), 0f, ps.rand(4f, 7f), 0.08f, 0.08f, c, c, a);
                if (p == null) return;
                p.sway = 0.6f;
                p.swayFreq = ps.rand(0.8f, 1.6f);
                p.pulse = 0.9f;
                p.pulseFreq = ps.rand(2f, 4f);
                break;
            case 6: // petals
                p = ps.glow(x, y + 3, z, 0.4f, ps.rand(-0.6f, -0.3f), 0.2f, ps.rand(5f, 8f), 0.11f, 0.09f, c, s.p1, a);
                if (p == null) return;
                p.sprite = Sprite.PETAL;
                p.rotVel = ps.rand(-2.5f, 2.5f);
                p.sway = 0.5f;
                p.swayFreq = ps.rand(1.5f, 3f);
                break;
            case 8: // neon dust: slow drifting coloured motes
                p = ps.glow(x, y, z, ps.rand(-0.2f, 0.2f), ps.rand(-0.1f, 0.15f), ps.rand(-0.2f, 0.2f), ps.rand(3f, 5f),
                        ps.rand(0.05f, 0.1f), 0.02f, c, s.p1, a);
                if (p == null) return;
                p.pulse = 0.4f;
                p.pulseFreq = ps.rand(4f, 8f);
                break;
            default: // soft motes
                p = ps.glow(x, y, z, ps.rand(-0.1f, 0.1f), ps.rand(-0.05f, 0.1f), ps.rand(-0.1f, 0.1f), ps.rand(3f, 6f),
                        ps.rand(0.04f, 0.08f), 0.02f, c, s.p1, a * 0.7f);
                if (p == null) return;
                p.sway = 0.2f;
                p.swayFreq = 1f;
        }
        p.swayPhase = ps.rand(0f, 6.28f);
        p.pulsePhase = ps.rand(0f, 6.28f);
    }
}
