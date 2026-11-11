package net.m1227.vfx;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import net.m1227.Mod1227;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Loads the 3D models baked by tools/ (from the FBX / .blend files you gave):
 * an animated character as vertex-animation frames (M127) and a multi-part
 * rigid model with per-frame part matrices (K127, the butterfly knife).
 * Files are loaded lazily the first time they are drawn.
 */
public final class MeshAssets {
    private MeshAssets() {}

    private static DataInputStream open(String name) throws Exception {
        InputStream in = MeshAssets.class.getResourceAsStream("/assets/m1227/models/" + name);
        if (in == null) throw new IllegalStateException("missing model " + name);
        return new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(in), 65536));
    }

    // =============================================================== character

    /** Vertex-animated, textured character (positions per frame, uv per corner). */
    public static final class Animated {
        public final int frames;
        public final float fps;
        final int[] cornerPos;
        final float[] cornerUv;
        final int[] tris;
        final float[][] pos;      // [frame][vertex*3]
        final float[][] normals;  // lazily per frame
        final ResourceLocation texture;

        private Animated(DataInputStream in, ResourceLocation tex) throws Exception {
            byte[] magic = new byte[4];
            in.readFully(magic);
            int nv = in.readInt(), nc = in.readInt(), nt = in.readInt();
            frames = in.readInt();
            fps = in.readFloat();
            float[] lo = {in.readFloat(), in.readFloat(), in.readFloat()};
            float[] hi = {in.readFloat(), in.readFloat(), in.readFloat()};
            cornerPos = new int[nc];
            for (int i = 0; i < nc; i++) cornerPos[i] = in.readUnsignedShort();
            cornerUv = new float[nc * 2];
            for (int i = 0; i < nc * 2; i++) {
                float f = in.readFloat();
                cornerUv[i] = (i & 1) == 1 ? 1f - f : f; // FBX v goes up, textures go down
            }
            tris = new int[nt];
            for (int i = 0; i < nt; i++) tris[i] = in.readUnsignedShort();
            pos = new float[frames][nv * 3];
            for (int f = 0; f < frames; f++) {
                for (int i = 0; i < nv * 3; i++) {
                    int k = i % 3;
                    pos[f][i] = lo[k] + (in.readShort() + 32768) / 65535f * (hi[k] - lo[k]);
                }
            }
            normals = new float[frames][];
            texture = tex;
        }

        private float[] normals(int f) {
            float[] n = normals[f];
            if (n != null) return n;
            float[] p = pos[f];
            n = new float[p.length];
            for (int t = 0; t < tris.length; t += 3) {
                int a = cornerPos[tris[t]] * 3, b = cornerPos[tris[t + 1]] * 3, c = cornerPos[tris[t + 2]] * 3;
                float ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
                float vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                for (int q : new int[]{a, b, c}) {
                    n[q] += nx;
                    n[q + 1] += ny;
                    n[q + 2] += nz;
                }
            }
            for (int i = 0; i < n.length; i += 3) {
                float l = (float) Math.sqrt(n[i] * n[i] + n[i + 1] * n[i + 1] + n[i + 2] * n[i + 2]);
                if (l > 1e-9f) {
                    n[i] /= l;
                    n[i + 1] /= l;
                    n[i + 2] /= l;
                }
            }
            normals[f] = n;
            return n;
        }

        /**
         * Draws the pose at time t (seconds, clamped) with height h blocks, feet at (x, y, z),
         * facing (fx, fz). light 0..1, alpha 0..1.
         */
        public void draw(float t, double x, double y, double z, float fx, float fz, float h, float light, float alpha) {
            float ft = Math.max(0f, Math.min(frames - 1.001f, t * fps));
            int f0 = (int) ft, f1 = Math.min(frames - 1, f0 + 1);
            float k = ft - f0;
            float[] p0 = pos[f0], p1 = pos[f1], n0 = normals(f0);
            float len = (float) Math.sqrt(fx * fx + fz * fz);
            if (len < 1e-4f) {
                fx = 0f;
                fz = 1f;
            } else {
                fx /= len;
                fz /= len;
            }
            // model +z -> facing, model +x -> (fz, -fx)
            float rxX = fz, rxZ = -fx;
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager.enableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(alpha > 0.98f);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GL11.glBegin(GL11.GL_TRIANGLES);
            for (int i = 0; i < tris.length; i++) {
                int c = tris[i], v = cornerPos[c] * 3;
                float mx = p0[v] + (p1[v] - p0[v]) * k, my = p0[v + 1] + (p1[v + 1] - p0[v + 1]) * k, mz = p0[v + 2] + (p1[v + 2] - p0[v + 2]) * k;
                float nx = n0[v], ny = n0[v + 1], nz = n0[v + 2];
                float wnx = nx * rxX + nz * fx, wnz = nx * rxZ + nz * fz;
                // soft key light from above-front plus ambient
                float d = Math.max(0f, wnx * 0.35f + ny * 0.8f + wnz * 0.45f);
                float s = (0.5f + 0.5f * d) * light;
                GL11.glColor4f(s, s, s, alpha);
                GL11.glTexCoord2f(cornerUv[c * 2], cornerUv[c * 2 + 1]);
                GL11.glVertex3d(x + (mx * rxX + mz * fx) * h, y + my * h, z + (mx * rxZ + mz * fz) * h);
            }
            GL11.glEnd();
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.depthMask(true);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    // ================================================================== rigid

    /** Rigid parts animated by a 3x4 matrix per part per frame (the butterfly knife). */
    public static final class Rigid {
        public final int frames;
        public final float fps;
        final float[][] v, n;
        final int[][] t, mat;
        final float[][][] m; // [frame][part][12]

        private Rigid(DataInputStream in) throws Exception {
            byte[] magic = new byte[4];
            in.readFully(magic);
            int parts = in.readInt();
            frames = in.readInt();
            fps = in.readFloat();
            v = new float[parts][];
            n = new float[parts][];
            t = new int[parts][];
            mat = new int[parts][];
            for (int p = 0; p < parts; p++) {
                int nv = in.readInt(), nt = in.readInt();
                v[p] = new float[nv * 3];
                n[p] = new float[nv * 3];
                for (int i = 0; i < nv * 3; i++) v[p][i] = in.readFloat();
                for (int i = 0; i < nv * 3; i++) n[p][i] = in.readFloat();
                t[p] = new int[nt * 3];
                for (int i = 0; i < nt * 3; i++) t[p][i] = in.readUnsignedShort();
                mat[p] = new int[nt];
                for (int i = 0; i < nt; i++) mat[p][i] = in.readUnsignedByte();
            }
            m = new float[frames][parts][12];
            for (int f = 0; f < frames; f++) {
                for (int p = 0; p < parts; p++) {
                    for (int i = 0; i < 12; i++) m[f][p][i] = in.readFloat();
                }
            }
        }

        public int parts() {
            return v.length;
        }

        /**
         * Emits the model at frame time ft (fractional frame) through a caller-supplied mapping:
         * map(x, y, z, out) converts model space to the caller's space. colors[part][material].
         */
        public void draw(float ft, Mapper map, int[][] colors, boolean[] emissive, float alpha) {
            ft = Math.max(0f, Math.min(frames - 1.001f, ft));
            int f0 = (int) ft, f1 = f0 + 1;
            float k = ft - f0;
            float[] o = new float[3], q = new float[12];
            GlStateManager.disableTexture2D();
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            for (int p = 0; p < v.length; p++) {
                float[] a = m[f0][p], b = m[f1][p];
                for (int i = 0; i < 12; i++) q[i] = a[i] + (b[i] - a[i]) * k;
                boolean glow = emissive != null && emissive[p];
                GL11.glBegin(GL11.GL_TRIANGLES);
                float[] vv = v[p], nn = n[p];
                int[] tt = t[p];
                for (int i = 0; i < tt.length; i++) {
                    int vi = tt[i] * 3;
                    float x = vv[vi], y = vv[vi + 1], z = vv[vi + 2];
                    float nx = q[0] * nn[vi] + q[1] * nn[vi + 1] + q[2] * nn[vi + 2];
                    float ny = q[4] * nn[vi] + q[5] * nn[vi + 1] + q[6] * nn[vi + 2];
                    float nz = q[8] * nn[vi] + q[9] * nn[vi + 1] + q[10] * nn[vi + 2];
                    float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (nl > 1e-6f) {
                        nx /= nl;
                        ny /= nl;
                        nz /= nl;
                    }
                    int[] cs = colors[Math.min(p, colors.length - 1)];
                    int col = cs[Math.min(mat[p][i / 3], cs.length - 1)];
                    // two-sided key light + a bright rim so steel reads as metal
                    float d = Math.abs(nx * 0.3f + ny * 0.25f + nz * 0.9f);
                    float s = glow ? 1f : 0.42f + 0.58f * d + 0.25f * (float) Math.pow(Math.abs(ny), 8);
                    GL11.glColor4f(Math.min(1f, ((col >> 16) & 255) / 255f * s), Math.min(1f, ((col >> 8) & 255) / 255f * s),
                            Math.min(1f, (col & 255) / 255f * s), alpha);
                    float wx = q[0] * x + q[1] * y + q[2] * z + q[3];
                    float wy = q[4] * x + q[5] * y + q[6] * z + q[7];
                    float wz = q[8] * x + q[9] * y + q[10] * z + q[11];
                    map.map(wx, wy, wz, o);
                    GL11.glVertex3f(o[0], o[1], o[2]);
                }
                GL11.glEnd();
            }
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    public interface Mapper {
        void map(float x, float y, float z, float[] out);
    }

    // ================================================================= cache

    private static Animated sahur;
    private static Rigid knife;
    private static boolean sahurFailed, knifeFailed;

    public static Animated sahur() {
        if (sahur == null && !sahurFailed) {
            try {
                DataInputStream in = open("sahur.m127");
                sahur = new Animated(in, new ResourceLocation("m1227", "textures/sahur.png"));
                in.close();
            } catch (Throwable t) {
                sahurFailed = true;
                Mod1227.LOGGER.warn("1227: could not load the Sahur model", t);
            }
        }
        return sahur;
    }

    public static Rigid knife() {
        if (knife == null && !knifeFailed) {
            try {
                DataInputStream in = open("knife.k127");
                knife = new Rigid(in);
                in.close();
            } catch (Throwable t) {
                knifeFailed = true;
                Mod1227.LOGGER.warn("1227: could not load the butterfly knife model", t);
            }
        }
        return knife;
    }
}
