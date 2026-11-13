package net.m1227.client.menu;

import java.io.File;
import java.util.Arrays;
import net.m1227.client.Os;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Img;
import net.m1227.module.InterfaceModule;
import net.m1227.ui.Gfx;

/**
 * Background of the 1227 menus outside the world: Aurora (slow soft colour fields in the accent
 * colour), Constellation (drifting points joined by faint lines), Image (your own picture from
 * config/1227/backgrounds, cropped to fill) or Solid. Inside the world it only darkens the game.
 */
public final class MenuBackground {
    private MenuBackground() {}

    private static final int STARS = 70;
    private static final float[] SX = new float[STARS], SY = new float[STARS], SV = new float[STARS];

    static {
        java.util.Random r = new java.util.Random(1227);
        for (int i = 0; i < STARS; i++) {
            SX[i] = r.nextFloat();
            SY[i] = r.nextFloat();
            SV[i] = 0.3f + r.nextFloat();
        }
    }

    public static void openFolder() {
        Os.open(InterfaceModule.backgroundsDir());
    }

    /** The chosen background image, or null. */
    static File imageFile() {
        File dir = InterfaceModule.backgroundsDir();
        InterfaceModule m = InterfaceModule.INSTANCE;
        String want = m == null ? "" : m.image.get().trim();
        if (!want.isEmpty()) {
            File f = new File(dir, new File(want).getName()); // file name only: stays inside the folder
            if (f.isFile()) return f;
        }
        File[] all = dir.listFiles((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg");
        });
        if (all == null || all.length == 0) return null;
        Arrays.sort(all);
        return all[0];
    }

    /** Full-screen background. mx, my = mouse (for parallax). */
    public static void draw(int w, int h, int mx, int my, boolean inWorld) {
        InterfaceModule m = InterfaceModule.INSTANCE;
        if (inWorld) {
            Gfx.gradientH(0, 0, w, h, 0xD0080910, 0x70080910);
            return;
        }
        String mode = m == null ? "Aurora" : m.background.get();
        double t = System.nanoTime() / 1e9;
        float px = 0, py = 0;
        if (m == null || m.parallax.on()) {
            px = (mx / (float) Math.max(1, w) - 0.5f) * -10f;
            py = (my / (float) Math.max(1, h) - 0.5f) * -6f;
        }
        Gfx.rect(0, 0, w, h, DS.BG);
        int acc = DS.accent();
        if (mode.equals("Image")) {
            File f = imageFile();
            Img.Loaded l = f == null ? null : Img.file(f, 1920);
            if (l != null) {
                Img.cover(l, px - 8, py - 6, w + 16, h + 12, 1f);
            } else {
                aurora(w, h, t, px, py, acc);
                Gfx.textScaled("Put a .png or .jpg in config/1227/backgrounds", 8, h - 12, DS.T_SMALL, DS.FAINT);
            }
        } else if (mode.equals("Constellation")) {
            constellation(w, h, t, px, py, acc);
        } else if (mode.equals("Aurora")) {
            aurora(w, h, t, px, py, acc);
        }
        float dim = m == null ? 0.35f : m.dim.f();
        if (dim > 0f) Gfx.rect(0, 0, w, h, Gfx.alpha(0xFF000000, dim));
        // vignette on the left, where the menu sits
        Gfx.gradientH(0, 0, w * 0.55f, h, 0x90060709, 0x00060709);
    }

    private static void aurora(int w, int h, double t, float px, float py, int acc) {
        int second = Gfx.mix(acc, 0xFF38BDF8, 0.65f), third = Gfx.mix(acc, 0xFFFF4FD8, 0.55f);
        float r = Math.max(w, h);
        Gfx.glow(w * (0.72f + 0.08f * (float) Math.sin(t * 0.11)) + px * 2, h * (0.30f + 0.06f * (float) Math.cos(t * 0.13)) + py * 2, r * 0.55f, Gfx.alpha(acc, 0.38f));
        Gfx.glow(w * (0.92f + 0.05f * (float) Math.cos(t * 0.09)) + px, h * (0.85f + 0.05f * (float) Math.sin(t * 0.15)) + py, r * 0.45f, Gfx.alpha(second, 0.28f));
        Gfx.glow(w * (0.40f + 0.07f * (float) Math.sin(t * 0.07 + 1)) + px * 3, h * (1.05f) + py * 3, r * 0.5f, Gfx.alpha(third, 0.18f));
        for (int i = 0; i < STARS; i++) {
            float x = (SX[i] * w + px * SV[i] * 2 + w) % w, y = (SY[i] * h + py * SV[i] * 2 + h) % h;
            float tw = 0.35f + 0.35f * (float) Math.sin(t * 1.3 * SV[i] + i);
            Gfx.rect(x, y, 0.8f, 0.8f, Gfx.alpha(0xFFFFFFFF, tw * 0.6f));
        }
    }

    private static void constellation(int w, int h, double t, float px, float py, int acc) {
        Gfx.glow(w * 0.8f + px, h * 0.25f + py, Math.max(w, h) * 0.5f, Gfx.alpha(acc, 0.22f));
        float[] xs = new float[STARS], ys = new float[STARS];
        for (int i = 0; i < STARS; i++) {
            xs[i] = (float) ((SX[i] * w + Math.sin(t * 0.05 * SV[i] + i) * 40 + px * SV[i] * 3) % w + w) % w;
            ys[i] = (float) ((SY[i] * h + Math.cos(t * 0.04 * SV[i] + i * 2) * 30 + py * SV[i] * 3) % h + h) % h;
        }
        float maxD = Math.min(w, h) * 0.16f;
        for (int i = 0; i < STARS; i++) {
            for (int j = i + 1; j < STARS; j++) {
                float dx = xs[i] - xs[j], dy = ys[i] - ys[j];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < maxD) Gfx.line(xs[i], ys[i], xs[j], ys[j], 0.5f, Gfx.alpha(acc, 0.35f * (1f - d / maxD)));
            }
        }
        for (int i = 0; i < STARS; i++) Gfx.circle(xs[i], ys[i], 0.9f + SV[i] * 0.4f, Gfx.alpha(0xFFFFFFFF, 0.55f));
    }
}
