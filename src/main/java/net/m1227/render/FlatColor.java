package net.m1227.render;

import org.lwjgl.opengl.GL11;

/**
 * Lets procedural models be drawn as a flat-colour silhouette (for glow and outline copies)
 * without a second copy of their drawing code: while active, every colour the model asks for is
 * replaced by one colour, keeping only the model's own alpha. Main thread only.
 */
public final class FlatColor {
    private FlatColor() {}

    private static boolean active;
    private static float r, g, b, a;

    public static void begin(int rgb, float alpha) {
        active = true;
        r = ((rgb >> 16) & 255) / 255f;
        g = ((rgb >> 8) & 255) / 255f;
        b = (rgb & 255) / 255f;
        a = alpha;
    }

    public static void end() {
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static void color(float cr, float cg, float cb, float ca) {
        if (active) GL11.glColor4f(r, g, b, a * Math.min(1f, ca));
        else GL11.glColor4f(cr, cg, cb, ca);
    }
}
