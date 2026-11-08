package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.Arcs;
import net.m1227.vfx.AuraShapes;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Lines;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 16 trail styles. Particle trails spawn behind you; "path" trails (Ribbon,
 * Waves, Geometric, Energy) keep a short history of your positions and draw
 * luminous lines through it every frame, fading with age.
 */
public final class TrailsModule extends Module {
    public static final String[] STYLES = {
            "Smoke", "Glow", "Sparkle", "Comet", "Speed Lines", "Footprints", "Ribbon", "Waves",
            "Geometric", "Energy", "Stars", "Magic", "Petals", "Embers", "Bubbles", "Snow"
    };
    private static final int HIST = 64;

    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Bool rainbow, firstPerson;
    private final Setting.Num width;
    private float acc;
    private double stepDist;
    private boolean leftFoot;

    // position history (ring buffer) for path trails
    private final double[] hx = new double[HIST], hy = new double[HIST], hz = new double[HIST];
    private final double[] ht = new double[HIST];
    private int head, size;
    private double lastSample;

    public TrailsModule() {
        super("Trails", "16 trail styles that follow your movement.", Category.MOVEMENT, false);
        style = add(new Setting.Mode("Style", "Smoke", STYLES));
        fx = new FxSettings(this, 0x2DD4FF, 0x8B5CFF, 2, 1.0, 0.16, 0.9);
        width = add(new Setting.Num("Path width", 0.35, 0.1, 1.0, 0.05, ""));
        rainbow = add(new Setting.Bool("Rainbow", false));
        firstPerson = add(new Setting.Bool("Show in first person", true));
    }

    @Override
    protected void onDisable() {
        size = 0;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (mc.theWorld == null || p == null) return;
        if (!firstPerson.on() && mc.gameSettings.thirdPersonView == 0) return;
        String st = style.get();
        double x = ParticleSystem.lerp(p.lastTickPosX, p.posX, e.partialTicks);
        double y = ParticleSystem.lerp(p.lastTickPosY, p.posY, e.partialTicks);
        double z = ParticleSystem.lerp(p.lastTickPosZ, p.posZ, e.partialTicks);
        double mx = p.posX - p.lastTickPosX, mz = p.posZ - p.lastTickPosZ;
        boolean moving = mx * mx + mz * mz >= 0.0025;

        if (st.equals("Ribbon") || st.equals("Waves") || st.equals("Geometric") || st.equals("Energy")) {
            sample(ps, x, y, z, moving);
            drawPath(ps, st, x, y, z);
            return;
        }
        if (ps.dt <= 0f || !moving) return;
        if (st.equals("Footprints")) {
            footprints(ps, p, mx, mz);
            return;
        }

        acc += ps.dt * fx.amount.f() * 30f * PerformanceModule.qualityScale();
        int whole = (int) acc;
        acc -= whole;
        int m = Math.min(whole, 12);
        double t = ps.time;
        float sz = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f();
        float len = (float) Math.sqrt(mx * mx + mz * mz);
        float dx = (float) (mx / len), dz = (float) (mz / len);
        for (int j = 0; j < m; j++) {
            int c0 = rainbow.on() ? ParticleSystem.rainbow((float) (t * 0.25)) : fx.color1.rgb();
            int c1 = rainbow.on() ? ParticleSystem.rainbow((float) (t * 0.25 + 0.25)) : fx.color2.rgb();
            particle(ps, st, x, y, z, dx, dz, mx, mz, sz, life, spd, c0, c1);
        }
    }

    private void particle(ParticleSystem ps, String st, double x, double y, double z, float dx, float dz,
                          double mx, double mz, float sz, float life, float spd, int c0, int c1) {
        Particle part;
        float a = fx.alpha(), fade = fx.fade.f();
        if (st.equals("Smoke")) {
            part = ps.glow(x + ps.rand(-0.15f, 0.15f), y + 0.12, z + ps.rand(-0.15f, 0.15f), (float) (-mx * 3.0),
                    ps.rand(0.1f, 0.35f), (float) (-mz * 3.0), life * ps.rand(0.8f, 1.25f), sz * 1.4f, sz * 5f,
                    ParticleSystem.mix(0xD7DCE2, c0, 0.12f), 0x8A929C, a * 0.4f);
            if (part == null) return;
            part.sprite = Sprite.SMOKE;
            part.alphaBlend = true;
            part.rot = ps.rand(0f, 6.28f);
            part.rotVel = ps.rand(-0.6f, 0.6f);
            part.drag = 1.8f;
            part.fadePow = 1.2f * fade;
            return;
        } else if (st.equals("Sparkle")) {
            part = ps.glow(x + ps.rand(-0.25f, 0.25f), y + ps.rand(0.05f, 0.9f), z + ps.rand(-0.25f, 0.25f),
                    0f, ps.rand(0.2f, 0.7f) * spd, 0f, life * ps.rand(0.6f, 1f), sz * 0.45f, 0f, c0, c1, a);
            if (part != null) part.gravity = -0.4f;
        } else if (st.equals("Speed Lines")) {
            float side = ps.rand(-0.45f, 0.45f);
            part = ps.glow(x - dz * side, y + ps.rand(0.15f, 1.7f), z + dx * side, -dx * 9f * spd, 0f, -dz * 9f * spd,
                    life * 0.25f, sz * 0.35f, sz * 0.2f, 0xFFFFFF, c1, a * 0.8f);
            if (part != null) {
                part.stretch = 0.09f;
                part.drag = 2.5f;
            }
        } else if (st.equals("Comet")) {
            part = ps.glow(x, y + 0.9, z, (float) -mx * 6f, 0f, (float) -mz * 6f, life * 0.8f, sz * 1.4f, 0f, c0, c1, a);
            if (part != null) part.drag = 1.5f;
        } else if (st.equals("Stars")) {
            part = ps.glow(x + ps.rand(-0.3f, 0.3f), y + ps.rand(0.1f, 1.2f), z + ps.rand(-0.3f, 0.3f),
                    0f, ps.rand(0f, 0.3f), 0f, life * 1.2f, sz * ps.rand(0.6f, 1.1f), 0f, c0, c1, a);
            if (part != null) {
                part.sprite = Sprite.STAR;
                part.rotVel = ps.rand(-3f, 3f);
                part.pulse = 0.5f;
                part.pulseFreq = ps.rand(8f, 14f);
            }
        } else if (st.equals("Magic")) {
            boolean rune = ps.rng.nextFloat() < 0.15f;
            part = ps.glow(x + ps.rand(-0.2f, 0.2f), rune ? y + 0.04 : y + ps.rand(0.1f, 1f), z + ps.rand(-0.2f, 0.2f),
                    0f, rune ? 0f : ps.rand(0.3f, 0.8f), 0f, life * (rune ? 1.6f : 1f),
                    rune ? sz * 2f : sz * 0.5f, rune ? sz * 3f : 0f, c0, c1, a);
            if (part != null && rune) {
                part.sprite = Sprite.RUNE;
                part.flat = true;
                part.rotVel = 1.5f;
            }
        } else if (st.equals("Petals")) {
            part = ps.glow(x + ps.rand(-0.3f, 0.3f), y + ps.rand(0.4f, 1.4f), z + ps.rand(-0.3f, 0.3f),
                    -dx * 0.6f, ps.rand(-0.4f, -0.1f), -dz * 0.6f, life * 1.8f, sz * 0.9f, sz * 0.7f, c0, c1, a);
            if (part != null) {
                part.sprite = Sprite.PETAL;
                part.rot = ps.rand(0f, 6.28f);
                part.rotVel = ps.rand(-3f, 3f);
                part.sway = 0.5f;
                part.swayFreq = ps.rand(2f, 4f);
            }
        } else if (st.equals("Embers")) {
            part = ps.glow(x + ps.rand(-0.2f, 0.2f), y + 0.1, z + ps.rand(-0.2f, 0.2f), 0f, ps.rand(0.6f, 1.4f) * spd, 0f,
                    life * 1.4f, sz * 0.5f, 0f, ParticleSystem.mix(0xFF8A3D, c0, 0.3f), 0xFFC36B, a);
            if (part != null) {
                part.sway = 0.3f;
                part.swayFreq = ps.rand(2f, 4f);
                part.pulse = 0.5f;
                part.pulseFreq = 10f;
            }
        } else if (st.equals("Bubbles")) {
            part = ps.glow(x + ps.rand(-0.25f, 0.25f), y + ps.rand(0.1f, 0.6f), z + ps.rand(-0.25f, 0.25f),
                    0f, ps.rand(0.3f, 0.7f), 0f, life * 1.5f, sz * 0.6f, sz * 1.1f, c0, c1, a * 0.9f);
            if (part != null) {
                part.sprite = Sprite.RING;
                part.sway = 0.3f;
                part.swayFreq = 3f;
            }
        } else if (st.equals("Snow")) {
            part = ps.glow(x + ps.rand(-0.3f, 0.3f), y + ps.rand(0.6f, 1.6f), z + ps.rand(-0.3f, 0.3f),
                    0f, -0.4f, 0f, life * 2f, sz * 0.5f, sz * 0.4f, 0xFFFFFF, c1, a);
            if (part != null) {
                part.sway = 0.25f;
                part.swayFreq = 2f;
            }
        } else { // Glow
            part = ps.glow(x + ps.rand(-0.08f, 0.08f), y + 0.12, z + ps.rand(-0.08f, 0.08f), 0f, 0.1f * spd, 0f,
                    life, sz, sz * 0.2f, c0, c1, a);
        }
        if (part != null) {
            part.swayPhase = ps.rand(0f, 6.28f);
            part.fadePow = fade;
        }
    }

    // ------------------------------------------------------- path trails

    private void sample(ParticleSystem ps, double x, double y, double z, boolean moving) {
        if (!moving || ps.time - lastSample < 1.0 / 30.0) return;
        lastSample = ps.time;
        head = (head + 1) % HIST;
        hx[head] = x;
        hy[head] = y;
        hz[head] = z;
        ht[head] = ps.time;
        if (size < HIST) size++;
    }

    private void drawPath(ParticleSystem ps, String st, double x, double y, double z) {
        double maxAge = fx.duration.f() * 1.2;
        double t = ps.time;
        Lines L = Lines.INSTANCE;
        float w = width.f(), a0 = fx.alpha();
        double px = x, py = y, pz = z, pAge = 0;
        int seg = 0;
        for (int k = 0; k < size; k++) {
            int i = (head - k + HIST) % HIST;
            double age = t - ht[i];
            if (age > maxAge) break;
            float f = 1f - (float) (age / maxAge);
            float pf = 1f - (float) (pAge / maxAge);
            int col = rainbow.on() ? ParticleSystem.rainbow((float) (t * 0.2 + k * 0.02)) : ParticleSystem.mix(fx.color1.rgb(), fx.color2.rgb(), 1f - f);
            double qx = hx[i], qy = hy[i], qz = hz[i];
            double dx = qx - px, dz = qz - pz, l = Math.sqrt(dx * dx + dz * dz);
            double nx = l > 1e-4 ? -dz / l : 0, nz = l > 1e-4 ? dx / l : 0;
            float alpha = a0 * f;
            if (st.equals("Ribbon")) {
                for (int s = -1; s <= 1; s += 2) {
                    L.add(px + nx * w * s * pf * 0.5, py + 0.9, pz + nz * w * s * pf * 0.5,
                            qx + nx * w * s * f * 0.5, qy + 0.9, qz + nz * w * s * f * 0.5, col, alpha);
                }
                L.add(px, py + 0.9, pz, qx, qy + 0.9, qz, 0xFFFFFF, alpha * 0.6f);
            } else if (st.equals("Waves")) {
                double y0 = py + 0.6 + Math.sin(t * 6 * fx.speed.f() + seg * 0.7) * w * 0.6;
                double y1 = qy + 0.6 + Math.sin(t * 6 * fx.speed.f() + (seg + 1) * 0.7) * w * 0.6;
                L.add(px, y0, pz, qx, y1, qz, col, alpha);
                L.add(px, y0 + 0.35, pz, qx, y1 + 0.35, qz, col, alpha * 0.5f);
            } else if (st.equals("Geometric")) {
                double s0 = (seg & 1) == 0 ? 1 : -1;
                L.add(px + nx * w * s0, py + 0.08, pz + nz * w * s0, qx - nx * w * s0, qy + 0.08, qz - nz * w * s0, col, alpha);
                if (seg % 4 == 0) L.add(qx - nx * w, qy + 0.08, qz - nz * w, qx + nx * w, qy + 0.08, qz + nz * w, col, alpha * 0.6f);
            } else { // Energy
                L.add(px, py + 1.0, pz, qx, qy + 1.0, qz, col, alpha * 0.7f);
                if (seg == 3 && ps.rng.nextFloat() < ps.dt * 12f) {
                    Arcs.INSTANCE.spawn(px, py + 1.0, pz, qx, qy + 0.2, qz, 0.12f, 0.15f, col, a0);
                }
                if (seg % 6 == 0) AuraShapes.sprite(ps, Sprite.GLOW, qx, qy + 1.0, qz, fx.size.f() * f, 0f, false, col, alpha);
            }
            px = qx;
            py = qy;
            pz = qz;
            pAge = age;
            seg++;
        }
    }

    /** Glowing footprints left and right of the path, one per step. */
    private void footprints(ParticleSystem ps, EntityPlayerSP p, double mx, double mz) {
        if (!p.onGround) return;
        stepDist += Math.sqrt(mx * mx + mz * mz) * ps.dt * 20.0;
        if (stepDist < 0.62) return;
        stepDist = 0;
        leftFoot = !leftFoot;
        float len = (float) Math.sqrt(mx * mx + mz * mz);
        float dx = (float) (mx / len), dz = (float) (mz / len);
        float side = leftFoot ? 0.16f : -0.16f;
        float sz = fx.size.f();
        Particle f = ps.glow(p.posX - dz * side, p.posY + 0.03, p.posZ + dx * side, 0f, 0f, 0f,
                fx.duration.f() * 2.5f, sz * 1.1f, sz * 0.9f, fx.color1.rgb(), fx.color2.rgb(), fx.alpha() * 0.9f);
        if (f != null) {
            f.sprite = Sprite.PETAL;
            f.flat = true;
            f.rot = (float) Math.atan2(-dx, dz);
            f.fadePow = 0.7f * fx.fade.f();
        }
    }
}
