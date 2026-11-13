package net.m1227.vfx;

import org.lwjgl.opengl.GL11;

/**
 * Small procedural meshes for cosmetics, with texture coordinates (u around, v from the centre or
 * bottom to the edge or top) and normals for {@link MaterialShader}. When shaded is false the mesh
 * colours itself with a c0 to c1 gradient instead (the fallback without shaders).
 * Everything is drawn around the current origin; the caller translates and rotates first.
 */
public final class CosmeticMesh {
    public static final String[] HAT_SHAPES = {"Cone", "Bell", "Spiked", "Two-tier", "Flat disc"};

    private CosmeticMesh() {}

    private static void col(boolean shaded, int c0, int c1, float k, float a) {
        if (shaded) {
            GL11.glColor4f(1f, 1f, 1f, a);
            return;
        }
        int c = ParticleSystem.mix(c0, c1, Math.max(0f, Math.min(1f, k)));
        GL11.glColor4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a);
    }

    /** Height of the hat surface at radial fraction v (0 = apex, 1 = rim). */
    private static float hatY(int shape, float v, float apex) {
        switch (shape) {
            case 1: // bell: steep crown, flat wide brim
                return apex * (float) Math.pow(1f - v, 2.4);
            case 3: // two tiers: small cone on top of a wider, flatter one
                return v < 0.45f ? apex * (1f - v / 0.45f * 0.55f) : apex * 0.45f * (1f - (v - 0.45f) / 0.55f);
            case 4: // flat disc, slightly domed
                return apex * 0.18f * (1f - v * v);
            default:
                return apex * (1f - v);
        }
    }

    private static float hatR(int shape, float v, float u, float r, float t) {
        if (shape != 2) return r * v;
        // 12 spikes at the rim, slowly turning
        double s = Math.abs(Math.sin(u * Math.PI * 12 + t * 0.6));
        return r * v * (1f + 0.22f * (float) Math.pow(s, 6) * v * v * v);
    }

    /** A hat of radius r with apex height apex. t = time (spike spin). */
    public static void hat(int shape, float r, float apex, float t, boolean shaded, int c0, int c1, float a) {
        int seg = shape == 2 ? 144 : 64, rings = 12;
        GL11.glBegin(GL11.GL_QUADS);
        for (int j = 0; j < rings; j++) {
            float v0 = j / (float) rings, v1 = (j + 1) / (float) rings;
            for (int i = 0; i < seg; i++) {
                float u0 = i / (float) seg, u1 = (i + 1) / (float) seg;
                hatV(shape, u0, v0, r, apex, t, shaded, c0, c1, a);
                hatV(shape, u0, v1, r, apex, t, shaded, c0, c1, a);
                hatV(shape, u1, v1, r, apex, t, shaded, c0, c1, a);
                hatV(shape, u1, v0, r, apex, t, shaded, c0, c1, a);
            }
        }
        GL11.glEnd();
    }

    private static void hatV(int shape, float u, float v, float r, float apex, float t, boolean shaded, int c0, int c1, float a) {
        double ang = u * Math.PI * 2;
        float rad = hatR(shape, v, u, r, t), y = hatY(shape, v, apex);
        float dv = 0.01f;
        float dr = hatR(shape, Math.min(1f, v + dv), u, r, t) - hatR(shape, Math.max(0f, v - dv), u, r, t);
        float dy = hatY(shape, Math.min(1f, v + dv), apex) - hatY(shape, Math.max(0f, v - dv), apex);
        float slope = Math.abs(dr) < 1e-5f ? 0f : -dy / dr;
        float nx = (float) Math.cos(ang) * slope, nz = (float) Math.sin(ang) * slope;
        float nl = (float) Math.sqrt(nx * nx + 1f + nz * nz);
        GL11.glNormal3f(nx / nl, 1f / nl, nz / nl);
        GL11.glTexCoord2f(u, v);
        col(shaded, c0, c1, 0.5f + 0.5f * (float) Math.sin(ang + t * 2.0), a * (shaded ? 1f : 0.35f + 0.25f * v));
        GL11.glVertex3f((float) Math.cos(ang) * rad, y, (float) Math.sin(ang) * rad);
    }

    /** Ring (torus) of radius big in the XZ plane, tube radius tube. */
    public static void torus(float big, float tube, boolean shaded, int c0, int c1, float a) {
        int seg = 72, sides = 10;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < seg; i++) {
            float u0 = i / (float) seg, u1 = (i + 1) / (float) seg;
            for (int j = 0; j < sides; j++) {
                float v0 = j / (float) sides, v1 = (j + 1) / (float) sides;
                torusV(u0, v0, big, tube, shaded, c0, c1, a);
                torusV(u1, v0, big, tube, shaded, c0, c1, a);
                torusV(u1, v1, big, tube, shaded, c0, c1, a);
                torusV(u0, v1, big, tube, shaded, c0, c1, a);
            }
        }
        GL11.glEnd();
    }

    private static void torusV(float u, float v, float big, float tube, boolean shaded, int c0, int c1, float a) {
        double au = u * Math.PI * 2, av = v * Math.PI * 2;
        float cu = (float) Math.cos(au), su = (float) Math.sin(au), cv = (float) Math.cos(av), sv = (float) Math.sin(av);
        GL11.glNormal3f(cu * cv, sv, su * cv);
        // v for the shader: 1 on the outer equator (bright edge), 0 on the inner side
        GL11.glTexCoord2f(u, 0.5f + 0.5f * cv);
        col(shaded, c0, c1, 0.5f + 0.5f * (float) Math.sin(au * 2), a);
        GL11.glVertex3f(cu * (big + tube * cv), tube * sv, su * (big + tube * cv));
    }

    /** Capsule from y = 0 to y = h (radius r), for body shells. */
    public static void capsule(float r, float h, boolean shaded, int c0, int c1, float a) {
        int seg = 40, rings = 18;
        GL11.glBegin(GL11.GL_QUADS);
        for (int j = 0; j < rings; j++) {
            float v0 = j / (float) rings, v1 = (j + 1) / (float) rings;
            for (int i = 0; i < seg; i++) {
                float u0 = i / (float) seg, u1 = (i + 1) / (float) seg;
                capV(u0, v0, r, h, shaded, c0, c1, a);
                capV(u1, v0, r, h, shaded, c0, c1, a);
                capV(u1, v1, r, h, shaded, c0, c1, a);
                capV(u0, v1, r, h, shaded, c0, c1, a);
            }
        }
        GL11.glEnd();
    }

    private static void capV(float u, float v, float r, float h, boolean shaded, int c0, int c1, float a) {
        // v maps along the profile: bottom cap (0..0.2), cylinder (0.2..0.8), top cap (0.8..1)
        double au = u * Math.PI * 2;
        float y, rad, ny;
        if (v < 0.2f) {
            double el = -Math.PI / 2 + (v / 0.2f) * Math.PI / 2;
            rad = r * (float) Math.cos(el);
            y = r + r * (float) Math.sin(el);
            ny = (float) Math.sin(el);
        } else if (v > 0.8f) {
            double el = ((v - 0.8f) / 0.2f) * Math.PI / 2;
            rad = r * (float) Math.cos(el);
            y = h - r + r * (float) Math.sin(el);
            ny = (float) Math.sin(el);
        } else {
            rad = r;
            y = r + (h - 2 * r) * (v - 0.2f) / 0.6f;
            ny = 0f;
        }
        float nr = (float) Math.sqrt(Math.max(0f, 1f - ny * ny));
        GL11.glNormal3f((float) Math.cos(au) * nr, ny, (float) Math.sin(au) * nr);
        GL11.glTexCoord2f(u, v);
        col(shaded, c0, c1, v, a * (shaded ? 1f : 0.25f));
        GL11.glVertex3f((float) Math.cos(au) * rad, y, (float) Math.sin(au) * rad);
    }
}
