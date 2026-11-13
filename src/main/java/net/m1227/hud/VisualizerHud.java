package net.m1227.hud;

import java.util.List;
import net.m1227.core.Setting;
import net.m1227.module.ClientModule;
import net.m1227.music.CaptureSource;
import net.m1227.music.MusicAnalysis;
import net.m1227.music.NowPlaying;
import net.m1227.ui.Gfx;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Audio visualizer: Bars, Wave, Mirror, Dots, Ring, Circular Wave, Spectrum and Particles.
 *
 * It reacts to the real samples of the 1227 player (exactly what you hear). For Spotify /
 * YouTube it can use a capture device such as Stereo Mix, only after you allow it below; when
 * there is no audio data it either hides or shows a clearly labelled simulated animation.
 */
public final class VisualizerHud extends HudModule {
    private final Setting.Mode style, noData, colors, captureDevice;
    private final Setting.Num bars, sensitivity, speed, vHeight, vWidth, thickness, alpha;
    private final Setting.Col color1, color2;
    private final Setting.Bool capture, consent;
    private final float[] shown = new float[MusicAnalysis.BANDS];
    private final float[] px = new float[48], py = new float[48], pv = new float[48], pl = new float[48];
    private long last;
    private float t;

    public VisualizerHud() {
        super("Audio Visualizer", "Bars, waves, rings and particles that move with your music.", 0.35, 0.86);
        style = add(new Setting.Mode("Style", "Bars", "Bars", "Wave", "Mirror", "Dots", "Ring", "Circular Wave", "Spectrum", "Particles"));
        colors = add(new Setting.Mode("Colors", "Gradient", "Gradient", "Accent", "Single", "Rainbow"));
        color1 = add(new Setting.Col("Color", 0x8B5CFF)).showIf(() -> !colors.is("Accent") && !colors.is("Rainbow"));
        color2 = add(new Setting.Col("Color 2", 0x2DD4FF)).showIf(() -> colors.is("Gradient"));
        bars = add(new Setting.Num("Bars", 32, 8, 64, 1, ""));
        sensitivity = add(new Setting.Num("Sensitivity", 1.0, 0.3, 3.0, 0.05, "x"));
        speed = add(new Setting.Num("Smoothing", 0.5, 0.0, 0.95, 0.05, ""));
        vWidth = add(new Setting.Num("Width", 160, 60, 360, 2, ""));
        vHeight = add(new Setting.Num("Height", 36, 12, 140, 1, ""));
        thickness = add(new Setting.Num("Thickness", 0.7, 0.2, 1.0, 0.05, ""));
        alpha = add(new Setting.Num("Opacity", 0.9, 0.1, 1.0, 0.05, ""));
        noData = add(new Setting.Mode("Without audio data", "Simulated (labelled)", "Simulated (labelled)", "Hide"));
        capture = add(new Setting.Bool("Use a capture device for Spotify / YouTube", false));
        consent = add(new Setting.Bool("I allow 1227 to read that device (stays in memory)", false)).showIf(() -> capture.on());
        captureDevice = add(new Setting.Mode("Capture device", "(none found)", "(none found)")).showIf(() -> capture.on());
        add(new Setting.Action("Find capture devices", this::findDevices)).showIf(() -> capture.on());
        background.set("None");
    }

    private void findDevices() {
        List<String> d = CaptureSource.devices();
        if (d.isEmpty()) {
            captureDevice.setOptions(new String[]{"(none found)"});
            net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Visualizer",
                    "No Stereo Mix / loopback device. Enable \"Stereo Mix\" in Windows sound settings.", net.m1227.core.Notifier.WARN, 4000);
        } else {
            captureDevice.setOptions(d.toArray(new String[0]));
            net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Visualizer", d.size() + " device(s) found", net.m1227.core.Notifier.GOOD, 2000);
        }
    }

    @Override
    public float width() {
        return vWidth.f();
    }

    @Override
    public float height() {
        String s = style.get();
        return s.equals("Ring") || s.equals("Circular Wave") ? Math.max(vHeight.f() * 1.6f, 40) : vHeight.f();
    }

    private boolean simulated;

    @Override
    protected boolean hasContent() {
        NowPlaying.Info np = NowPlaying.get();
        if (np.source == NowPlaying.Source.NONE) return false;
        if (capture.on() && consent.on() && np.source != NowPlaying.Source.LOCAL && !captureDevice.is("(none found)")) {
            CaptureSource.want(captureDevice.get());
        } else if (CaptureSource.active()) {
            CaptureSource.stopNow();
        }
        MusicAnalysis.INSTANCE.update();
        simulated = MusicAnalysis.INSTANCE.source() == MusicAnalysis.Source.NONE;
        return !simulated || noData.is("Simulated (labelled)");
    }

    private int color(float f) {
        String c = colors.get();
        if (c.equals("Accent")) return ClientModule.accent();
        if (c.equals("Single")) return color1.rgb();
        if (c.equals("Rainbow")) return net.m1227.vfx.ParticleSystem.rainbow(f * 0.8f + t * 0.05f);
        return Gfx.mix(0xFF000000 | color1.rgb(), 0xFF000000 | color2.rgb(), f) & 0xFFFFFF;
    }

    private int col(float f, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | color(f);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        t += dt;
        MusicAnalysis an = MusicAnalysis.INSTANCE;
        boolean sim = editing || simulated;
        int n = bars.i();
        float sens = sensitivity.f();
        float keep = speed.f();
        for (int i = 0; i < n; i++) {
            float f = i / (float) Math.max(1, n - 1);
            float v;
            if (sim) {
                // gentle made-up motion, so the widget can be placed and styled without music
                v = 0.25f + 0.2f * (float) Math.sin(t * 2.1f + i * 0.45f) + 0.12f * (float) Math.sin(t * 3.7f + i * 1.3f);
                v *= 1f - 0.4f * f;
            } else {
                v = an.band(f) * sens;
                v = v * v * (3 - 2 * Math.min(1f, v)); // expand the middle of the range
            }
            v = Math.max(0f, Math.min(1f, v));
            shown[i] = shown[i] * keep + v * (1f - keep);
        }
        panel(x, y, width(), height());
        float w = width(), h = height(), a = alpha.f();
        String s = style.get();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        if (s.equals("Bars") || s.equals("Mirror") || s.equals("Spectrum") || s.equals("Dots")) {
            float slot = (w - 4) / n, bw = Math.max(1f, slot * thickness.f());
            for (int i = 0; i < n; i++) {
                float f = i / (float) Math.max(1, n - 1);
                float v = shown[i];
                float bx = x + 2 + i * slot + (slot - bw) / 2f;
                if (s.equals("Bars")) {
                    float bh = Math.max(1f, v * (h - 4));
                    Gfx.roundedRect(bx, y + h - 2 - bh, bw, bh, Math.min(bw / 2f, 1.5f), col(f, a));
                } else if (s.equals("Mirror")) {
                    float bh = Math.max(1f, v * (h - 4) / 2f);
                    Gfx.roundedRect(bx, y + h / 2f - bh, bw, bh * 2, Math.min(bw / 2f, 1.5f), col(f, a));
                } else if (s.equals("Spectrum")) {
                    float bh = Math.max(1f, v * (h - 4));
                    Gfx.gradient(bx, y + h - 2 - bh, bw, bh, col(f, a), col(f, a * 0.15f));
                    Gfx.rect(bx, y + h - 2 - bh, bw, 1f, col(f, Math.min(1f, a * 1.2f)));
                } else {
                    int dots = Math.max(1, (int) (v * 8));
                    for (int k = 0; k < dots; k++) Gfx.circle(bx + bw / 2f, y + h - 4 - k * (h - 6) / 8f, Math.max(0.8f, bw * 0.45f), col(f, a * (0.5f + 0.5f * k / 8f)));
                }
            }
        } else if (s.equals("Wave")) {
            float[] wave = an.wave;
            GL11.glLineWidth(Math.max(1f, 2.5f * thickness.f()));
            GL11.glBegin(GL11.GL_LINE_STRIP);
            int pts = 96;
            for (int i = 0; i < pts; i++) {
                float f = i / (float) (pts - 1);
                float v = sim ? (float) Math.sin(t * 3 + f * 12) * 0.35f * (float) Math.sin(f * Math.PI) : wave[(int) (f * (wave.length - 1))] * sens;
                setColor(col(f, a));
                GL11.glVertex2f(x + 2 + f * (w - 4), y + h / 2f - Math.max(-1f, Math.min(1f, v)) * (h / 2f - 2));
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);
        } else if (s.equals("Ring") || s.equals("Circular Wave")) {
            float cx = x + w / 2f, cy = y + h / 2f, r0 = Math.min(w, h) * 0.24f, r1 = Math.min(w, h) * 0.48f;
            if (s.equals("Ring")) {
                for (int i = 0; i < n; i++) {
                    float f = i / (float) n;
                    double an0 = Math.PI * 2 * f - Math.PI / 2, an1 = an0 + Math.PI * 2 / n * thickness.f();
                    float len = r0 + (r1 - r0) * shown[i];
                    Gfx.annulus(cx, cy, r0, len, an0, an1, col(f, a));
                }
                Gfx.circle(cx, cy, r0 * (0.8f + 0.2f * an.beat), col(0f, a * 0.25f));
            } else {
                GL11.glLineWidth(Math.max(1f, 2f * thickness.f()));
                GL11.glBegin(GL11.GL_LINE_LOOP);
                for (int i = 0; i < 128; i++) {
                    float f = i / 128f;
                    float v = shown[(int) (Math.abs(f * 2 - 1) * (n - 1))];
                    float rr = r0 + (r1 - r0) * v;
                    double ang = Math.PI * 2 * f - Math.PI / 2;
                    setColor(col(f, a));
                    GL11.glVertex2f(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr);
                }
                GL11.glEnd();
                GL11.glLineWidth(1f);
            }
        } else { // Particles: dots thrown up by the bass, falling back
            float bass = sim ? 0.4f + 0.3f * (float) Math.sin(t * 4) : an.bass * sens;
            for (int i = 0; i < px.length; i++) {
                if (pl[i] <= 0f) {
                    if (Math.random() < bass * dt * 30f) {
                        px[i] = (float) Math.random();
                        py[i] = 0f;
                        pv[i] = 0.6f + bass * 1.6f * (float) Math.random();
                        pl[i] = 1f;
                    }
                    continue;
                }
                pv[i] -= dt * 2.2f;
                py[i] += pv[i] * dt;
                pl[i] -= dt * 0.8f;
                if (py[i] < 0) pl[i] = 0;
                Gfx.circle(x + 2 + px[i] * (w - 4), y + h - 2 - py[i] * (h - 4), 1.2f + 1.5f * thickness.f(), col(px[i], a * pl[i]));
            }
        }
        GlStateManager.enableTexture2D();
        if (sim && !editing) Gfx.textScaled("simulated", x + 3, y + 2, 0.6f, 0x80FFFFFF);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void setColor(int argb) {
        GL11.glColor4f(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
