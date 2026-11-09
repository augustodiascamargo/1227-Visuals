package net.m1227.ui;

/**
 * Small line icons for the sidebar, drawn from shapes so they stay crisp at
 * any GUI scale and share one stroke width. Index matches Category.icon.
 */
public final class Icons {
    private Icons() {}

    public static void draw(int icon, float cx, float cy, int c) {
        float w = 1.2f;
        switch (icon) {
            case 0: // sky: crescent + star
                Gfx.circle(cx - 1f, cy, 3.6f, c);
                Gfx.circle(cx + 0.6f, cy - 1.2f, 3f, 0xFF08090C);
                Gfx.circle(cx + 3f, cy - 3f, 0.8f, c);
                break;
            case 1: // ambient: three dots
                Gfx.circle(cx - 2.8f, cy + 1.5f, 1.1f, c);
                Gfx.circle(cx + 0.3f, cy - 2.2f, 1.3f, c);
                Gfx.circle(cx + 3f, cy + 2f, 0.9f, c);
                break;
            case 2: // player aura: head + ring
                Gfx.circle(cx, cy - 1.5f, 1.8f, c);
                ring(cx, cy + 2.2f, 3.8f, 1.4f, w, c);
                break;
            case 3: // cosmetics: crown
                Gfx.line(cx - 3.5f, cy + 2.5f, cx + 3.5f, cy + 2.5f, w, c);
                Gfx.line(cx - 3.5f, cy + 2.5f, cx - 3.5f, cy - 2f, w, c);
                Gfx.line(cx + 3.5f, cy + 2.5f, cx + 3.5f, cy - 2f, w, c);
                Gfx.line(cx - 3.5f, cy - 2f, cx - 1.2f, cy + 0.3f, w, c);
                Gfx.line(cx - 1.2f, cy + 0.3f, cx, cy - 3f, w, c);
                Gfx.line(cx, cy - 3f, cx + 1.2f, cy + 0.3f, w, c);
                Gfx.line(cx + 1.2f, cy + 0.3f, cx + 3.5f, cy - 2f, w, c);
                break;
            case 4: // movement: speed lines
                Gfx.line(cx - 3.5f, cy - 2.5f, cx + 3f, cy - 2.5f, w, c);
                Gfx.line(cx - 1.5f, cy, cx + 3.5f, cy, w, c);
                Gfx.line(cx - 3f, cy + 2.5f, cx + 2f, cy + 2.5f, w, c);
                break;
            case 5: // hit: burst
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 4;
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    Gfx.line(cx - dx * 4f, cy - dy * 4f, cx + dx * 4f, cy + dy * 4f, w, c);
                }
                break;
            case 6: // shockwave: concentric rings
                ring(cx, cy, 4f, 4f, w, c);
                ring(cx, cy, 1.8f, 1.8f, w, c);
                break;
            case 7: // particles: sparkle
                Gfx.line(cx, cy - 4f, cx, cy + 4f, w, c);
                Gfx.line(cx - 4f, cy, cx + 4f, cy, w, c);
                Gfx.circle(cx, cy, 1.4f, c);
                break;
            case 8: // hit sounds: speaker + wave
                Gfx.rect(cx - 4f, cy - 1.5f, 2f, 3f, c);
                Gfx.line(cx - 2f, cy - 1.5f, cx + 0.5f, cy - 3.5f, w, c);
                Gfx.line(cx - 2f, cy + 1.5f, cx + 0.5f, cy + 3.5f, w, c);
                Gfx.line(cx + 0.5f, cy - 3.5f, cx + 0.5f, cy + 3.5f, w, c);
                Gfx.line(cx + 2.4f, cy - 1.8f, cx + 2.4f, cy + 1.8f, w, c);
                Gfx.line(cx + 4f, cy - 3f, cx + 4f, cy + 3f, w, c);
                break;
            case 10: // weapons: sword
                Gfx.line(cx - 3.5f, cy + 3.5f, cx + 3.5f, cy - 3.5f, w, c);
                Gfx.line(cx - 3.8f, cy + 1.2f, cx - 1.2f, cy + 3.8f, w, c);
                Gfx.line(cx + 3.5f, cy - 3.5f, cx + 1.8f, cy - 3.6f, w, c);
                break;
            case 11: // animations: play triangle in a frame
                Gfx.line(cx - 4f, cy - 3f, cx + 4f, cy - 3f, w, c);
                Gfx.line(cx - 4f, cy + 3f, cx + 4f, cy + 3f, w, c);
                Gfx.line(cx - 1.2f, cy - 1.6f, cx - 1.2f, cy + 1.6f, w, c);
                Gfx.line(cx - 1.2f, cy - 1.6f, cx + 1.8f, cy, w, c);
                Gfx.line(cx - 1.2f, cy + 1.6f, cx + 1.8f, cy, w, c);
                break;
            default: // hud: rounded panel
                Gfx.line(cx - 4f, cy - 2.5f, cx + 4f, cy - 2.5f, w, c);
                Gfx.line(cx - 4f, cy + 2.5f, cx + 4f, cy + 2.5f, w, c);
                Gfx.line(cx - 4f, cy - 2.5f, cx - 4f, cy + 2.5f, w, c);
                Gfx.line(cx + 4f, cy - 2.5f, cx + 4f, cy + 2.5f, w, c);
                Gfx.circle(cx - 1.5f, cy, 0.9f, c);
                break;
        }
    }

    /** Ellipse outline from line segments (rx, ry radii). */
    private static void ring(float cx, float cy, float rx, float ry, float w, int c) {
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Gfx.line(cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry,
                    cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry, w, c);
        }
    }

    /** Magnifier for the search field. */
    public static void search(float cx, float cy, int c) {
        float r = 3.2f;
        for (int k = 0; k < 16; k++) {
            double a0 = k * Math.PI * 2 / 16, a1 = (k + 1) * Math.PI * 2 / 16;
            Gfx.line(cx - 1f + (float) Math.cos(a0) * r, cy - 1f + (float) Math.sin(a0) * r,
                    cx - 1f + (float) Math.cos(a1) * r, cy - 1f + (float) Math.sin(a1) * r, 1.1f, c);
        }
        Gfx.line(cx + 1.4f, cy + 1.4f, cx + 3.8f, cy + 3.8f, 1.3f, c);
    }
}
