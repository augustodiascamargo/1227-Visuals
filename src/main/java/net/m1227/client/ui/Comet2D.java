package net.m1227.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.m1227.module.HaloModule;
import net.m1227.ui.Gfx;

/** The Halo comet drawn in screen space (menu character and cosmetics preview). */
public final class Comet2D {
    private final List<float[]> tail = new ArrayList<float[]>();

    /** Core at (hx, hy); size = screen units per block. */
    public void draw(float hx, float hy, float size, float dt, float t) {
        int[] col = HaloModule.menuCometColors();
        float cx = hx + (float) Math.sin(t * 1.3) * size * 0.05f, cy = hy + (float) Math.cos(t * 1.1) * size * 0.04f;
        float want = dt * 90f;
        int count = (int) want + (Math.random() < want - (int) want ? 1 : 0);
        for (int i = 0; i < count; i++) {
            tail.add(new float[]{cx + (float) (Math.random() - 0.5) * size * 0.05f, cy, size * (0.18f + (float) Math.random() * 0.12f),
                    -size * (0.7f + (float) Math.random() * 0.4f), 0f});
        }
        for (int i = tail.size() - 1; i >= 0; i--) {
            float[] p = tail.get(i);
            p[4] += dt / 0.9f;
            if (p[4] >= 1f) {
                tail.remove(i);
                continue;
            }
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            p[2] += (float) Math.sin(t * 2 + i) * size * 0.08f * dt;
        }
        for (float[] p : tail) {
            float k = p[4];
            int c = Gfx.mix(0xFF000000 | col[0], 0xFF000000 | col[1], k);
            Gfx.glow(p[0], p[1], size * 0.16f * (1f - k * 0.8f), Gfx.alpha(c, 0.55f * (1f - k)));
        }
        Gfx.glow(cx, cy, size * 0.45f, Gfx.alpha(0xFF000000 | col[1], 0.45f));
        Gfx.glow(cx, cy, size * 0.25f, Gfx.alpha(0xFF000000 | col[0], 0.9f));
        Gfx.glow(cx, cy, size * 0.11f, 0xFFFFFFFF);
    }
}
