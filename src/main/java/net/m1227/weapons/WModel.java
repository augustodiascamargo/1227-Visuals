package net.m1227.weapons;

import java.util.ArrayList;
import java.util.List;

/**
 * A weapon model read from a file (.bbmodel, .gltf/.glb, .fbx, .obj): bones, textured triangle
 * batches, textures (with animated strips) and animation clips. Pure Java: no Minecraft or GL,
 * so the importers and the animation sampling are unit tested. The game side uploads it
 * (WeaponGpu) and draws it with the bone matrices from {@link #boneMatrices}.
 */
public final class WModel {
    public String name = "", format = "", credit = "";
    public final List<Bone> bones = new ArrayList<>();
    public final List<Part> parts = new ArrayList<>();
    public final List<Tex> textures = new ArrayList<>();
    public final List<Clip> clips = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    /** Model units per block (16 for Blockbench pixels, 1 for glTF metres). */
    public float unit = 16f;
    /** Java item model first-person display scale (0 when the file has none). */
    public float displayScale;

    public WModel() {
        Bone root = new Bone();
        root.name = "root";
        bones.add(root);
    }

    /** A node of the hierarchy. Euler bones follow Blockbench, the others glTF/FBX (TRS + quaternion). */
    public static final class Bone {
        public String name = "";
        public int parent = -1;
        /** Rotation / scale pivot (model units). */
        public float px, py, pz;
        /** Rest translation (glTF / FBX local position). */
        public float tx, ty, tz;
        /** Rest rotation, degrees, order ZYX (Blockbench groups). */
        public float rx, ry, rz;
        /** Rest rotation as a quaternion (glTF / FBX). */
        public float qx, qy, qz, qw = 1f;
        public float sx = 1f, sy = 1f, sz = 1f;
        public boolean euler;
        /** Extra fixed matrix applied after the TRS (FBX pre-rotation), or null. */
        public float[] post;
    }

    /** Triangles that share a bone and a texture. v: x y z u v nx ny nz per vertex, 3 vertices per triangle. */
    public static final class Part {
        public int bone, tex = -1;
        public boolean emissive, shade = true, translucent;
        public int color = 0xFFFFFFFF;
        public String name = "";
        public float[] v = new float[0];
        public int verts;

        public void add(float x, float y, float z, float u, float vv, float nx, float ny, float nz) {
            if ((verts + 1) * 8 > v.length) v = java.util.Arrays.copyOf(v, Math.max(64, v.length * 2));
            int o = verts * 8;
            v[o] = x;
            v[o + 1] = y;
            v[o + 2] = z;
            v[o + 3] = u;
            v[o + 4] = vv;
            v[o + 5] = nx;
            v[o + 6] = ny;
            v[o + 7] = nz;
            verts++;
        }

        /** Adds triangle a b c with a flat normal (no-op for a degenerate triangle). */
        public void tri(float[] a, float[] b, float[] c, float[] ua, float[] ub, float[] uc) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9f) return;
            nx /= l;
            ny /= l;
            nz /= l;
            add(a[0], a[1], a[2], ua[0], ua[1], nx, ny, nz);
            add(b[0], b[1], b[2], ub[0], ub[1], nx, ny, nz);
            add(c[0], c[1], c[2], uc[0], uc[1], nx, ny, nz);
        }
    }

    /** ARGB pixels. Animated strips: 'frames' frames stacked vertically, uv v covers frame 0. */
    public static final class Tex {
        public String name = "";
        public int w, h;
        public int[] argb;
        public int frames = 1;
        /** Game ticks per frame. */
        public int frameTicks = 1;
        /** Frame sequence (indices), or null for 0..frames-1. */
        public int[] order;
        public boolean emissive, additive, translucent;

        public int frameAt(long ticks) {
            if (frames <= 1) return 0;
            int n = order != null && order.length > 0 ? order.length : frames;
            int k = (int) ((ticks / Math.max(1, frameTicks)) % n);
            return order != null && order.length > 0 ? Math.max(0, Math.min(frames - 1, order[k])) : k;
        }
    }

    public static final int ROT = 0, POS = 1, SCALE = 2;
    public static final int LINEAR = 0, CATMULL = 1, STEP = 2, BEZIER = 3;

    /** One keyframe; value expressions per axis (pre = arriving, post = leaving), or a quaternion. */
    public static final class Key {
        public float t;
        public int interp;
        public Molang.Expr[] pre, post;
        /** glTF rotation keys: x y z w, already final (not added to the rest pose). */
        public float[] quat;
    }

    public static final class Track {
        public int bone, channel;
        public final List<Key> keys = new ArrayList<>();
    }

    public static final class Clip {
        public String name = "";
        public float length;
        /** 0 once, 1 loop, 2 hold the last frame. */
        public int loop;
        public final List<Track> tracks = new ArrayList<>();
    }

    /**
     * A sampled pose: per bone, Euler degrees added to the rest pose (Blockbench), a translation
     * added to it, a scale multiplier, or (glTF) an absolute quaternion / translation / scale.
     */
    public static final class Pose {
        public final float[] rot, pos, scl, quat;
        public final boolean[] hasQuat, absPos, absScl;

        public Pose(int bones) {
            rot = new float[bones * 3];
            pos = new float[bones * 3];
            scl = new float[bones * 3];
            quat = new float[bones * 4];
            hasQuat = new boolean[bones];
            absPos = new boolean[bones];
            absScl = new boolean[bones];
            clear();
        }

        public void clear() {
            java.util.Arrays.fill(rot, 0f);
            java.util.Arrays.fill(pos, 0f);
            java.util.Arrays.fill(scl, 1f);
            java.util.Arrays.fill(quat, 0f);
            for (int i = 0; i < hasQuat.length; i++) quat[i * 4 + 3] = 1f;
            java.util.Arrays.fill(hasQuat, false);
            java.util.Arrays.fill(absPos, false);
            java.util.Arrays.fill(absScl, false);
        }

        public void copy(Pose o) {
            System.arraycopy(o.rot, 0, rot, 0, rot.length);
            System.arraycopy(o.pos, 0, pos, 0, pos.length);
            System.arraycopy(o.scl, 0, scl, 0, scl.length);
            System.arraycopy(o.quat, 0, quat, 0, quat.length);
            System.arraycopy(o.hasQuat, 0, hasQuat, 0, hasQuat.length);
            System.arraycopy(o.absPos, 0, absPos, 0, absPos.length);
            System.arraycopy(o.absScl, 0, absScl, 0, absScl.length);
        }

        /** this = lerp(this, o, k): additive channels lerp, quaternions slerp. */
        public void blend(Pose o, float k, WModel m) {
            if (k <= 0f) return;
            if (k >= 1f) {
                copy(o);
                return;
            }
            for (int i = 0; i < rot.length; i++) {
                rot[i] += (o.rot[i] - rot[i]) * k;
                pos[i] += (o.pos[i] - pos[i]) * k;
                scl[i] += (o.scl[i] - scl[i]) * k;
            }
            for (int b = 0; b < hasQuat.length; b++) {
                if (hasQuat[b] || o.hasQuat[b]) {
                    Bone bn = m.bones.get(b);
                    float[] rest = {bn.qx, bn.qy, bn.qz, bn.qw};
                    float[] a = hasQuat[b] ? new float[]{quat[b * 4], quat[b * 4 + 1], quat[b * 4 + 2], quat[b * 4 + 3]} : rest;
                    float[] c = o.hasQuat[b] ? new float[]{o.quat[b * 4], o.quat[b * 4 + 1], o.quat[b * 4 + 2], o.quat[b * 4 + 3]} : rest;
                    M4.slerp(a, 0, c, 0, k, quat, b * 4);
                    hasQuat[b] = true;
                }
                absPos[b] |= o.absPos[b];
                absScl[b] |= o.absScl[b];
            }
        }
    }

    // ------------------------------------------------------------------ sampling

    /** Writes clip c at time t (seconds; looping / holding applied here) into out (cleared first). */
    public void sample(Clip c, float t, float lifeTime, Pose out) {
        out.clear();
        if (c == null) return;
        float len = Math.max(1e-4f, c.length);
        if (c.loop == 1) t = ((t % len) + len) % len;
        else t = Math.max(0f, Math.min(len, t));
        for (Track tr : c.tracks) {
            if (tr.keys.isEmpty() || tr.bone < 0 || tr.bone >= bones.size()) continue;
            int b = tr.bone;
            if (tr.keys.get(0).quat != null && tr.channel == ROT) {
                quatAt(tr, t, out.quat, b * 4);
                out.hasQuat[b] = true;
                continue;
            }
            float[] v = new float[3];
            valueAt(tr, t, lifeTime, v);
            boolean gltf = !bones.get(b).euler;
            if (tr.channel == ROT) {
                out.rot[b * 3] = v[0];
                out.rot[b * 3 + 1] = v[1];
                out.rot[b * 3 + 2] = v[2];
            } else if (tr.channel == POS) {
                out.pos[b * 3] = v[0];
                out.pos[b * 3 + 1] = v[1];
                out.pos[b * 3 + 2] = v[2];
                if (gltf) out.absPos[b] = true;
            } else {
                out.scl[b * 3] = v[0];
                out.scl[b * 3 + 1] = v[1];
                out.scl[b * 3 + 2] = v[2];
                if (gltf) out.absScl[b] = true;
            }
        }
    }

    private static void eval(Molang.Expr[] e, float t, float life, float[] out) {
        for (int i = 0; i < 3; i++) out[i] = e != null && i < e.length && e[i] != null ? e[i].eval(t, life) : 0f;
    }

    static void valueAt(Track tr, float t, float life, float[] out) {
        List<Key> k = tr.keys;
        if (t <= k.get(0).t) {
            eval(k.get(0).pre, t, life, out);
            return;
        }
        Key last = k.get(k.size() - 1);
        if (t >= last.t) {
            eval(last.post, t, life, out);
            return;
        }
        int i = 0;
        while (i < k.size() - 2 && k.get(i + 1).t <= t) i++;
        Key a = k.get(i), b = k.get(i + 1);
        float span = Math.max(1e-6f, b.t - a.t), u = (t - a.t) / span;
        float[] va = new float[3], vb = new float[3];
        eval(a.post, t, life, va);
        eval(b.pre, t, life, vb);
        if (a.interp == STEP) {
            System.arraycopy(va, 0, out, 0, 3);
            return;
        }
        if (a.interp == CATMULL || b.interp == CATMULL) {
            float[] v0 = new float[3], v3 = new float[3];
            if (i > 0) eval(k.get(i - 1).post, t, life, v0);
            else System.arraycopy(va, 0, v0, 0, 3);
            if (i + 2 < k.size()) eval(k.get(i + 2).pre, t, life, v3);
            else System.arraycopy(vb, 0, v3, 0, 3);
            float u2 = u * u, u3 = u2 * u;
            for (int j = 0; j < 3; j++) {
                out[j] = 0.5f * ((2f * va[j]) + (-v0[j] + vb[j]) * u + (2f * v0[j] - 5f * va[j] + 4f * vb[j] - v3[j]) * u2
                        + (-v0[j] + 3f * va[j] - 3f * vb[j] + v3[j]) * u3);
            }
            return;
        }
        if (a.interp == BEZIER) u = u * u * (3f - 2f * u); // no handles in the file format we read: ease in-out
        for (int j = 0; j < 3; j++) out[j] = va[j] + (vb[j] - va[j]) * u;
    }

    static void quatAt(Track tr, float t, float[] out, int o) {
        List<Key> k = tr.keys;
        if (t <= k.get(0).t) {
            System.arraycopy(k.get(0).quat, 0, out, o, 4);
            return;
        }
        Key last = k.get(k.size() - 1);
        if (t >= last.t) {
            System.arraycopy(last.quat, 0, out, o, 4);
            return;
        }
        int i = 0;
        while (i < k.size() - 2 && k.get(i + 1).t <= t) i++;
        Key a = k.get(i), b = k.get(i + 1);
        if (a.interp == STEP) {
            System.arraycopy(a.quat, 0, out, o, 4);
            return;
        }
        float u = (t - a.t) / Math.max(1e-6f, b.t - a.t);
        M4.slerp(a.quat, 0, b.quat, 0, u, out, o);
    }

    // ------------------------------------------------------------------ bones

    /**
     * World matrix of every bone for the pose (null = rest), in model units, parents first.
     * Blockbench bones turn around their pivot (child positions are absolute in the file), and an
     * animation's rotation adds to the rest angles with X and Y negated and its position moves
     * the bone with X negated, as Blockbench does. glTF / FBX bones use their local TRS.
     */
    public float[][] boneMatrices(Pose p) {
        int n = bones.size();
        float[][] w = new float[n][];
        for (int i = 0; i < n; i++) {
            Bone b = bones.get(i);
            float[] m = b.parent >= 0 && b.parent < i ? w[b.parent].clone() : M4.identity();
            if (b.euler) {
                float ax = 0f, ay = 0f, az = 0f, mx = 0f, my = 0f, mz = 0f, sx = 1f, sy = 1f, sz = 1f;
                if (p != null) {
                    ax = -p.rot[i * 3];
                    ay = -p.rot[i * 3 + 1];
                    az = p.rot[i * 3 + 2];
                    mx = -p.pos[i * 3];
                    my = p.pos[i * 3 + 1];
                    mz = p.pos[i * 3 + 2];
                    sx = p.scl[i * 3];
                    sy = p.scl[i * 3 + 1];
                    sz = p.scl[i * 3 + 2];
                }
                M4.translate(m, mx, my, mz);
                M4.translate(m, b.px, b.py, b.pz);
                M4.rotateZYX(m, b.rx + ax, b.ry + ay, b.rz + az);
                M4.scale(m, sx, sy, sz);
                M4.translate(m, -b.px, -b.py, -b.pz);
            } else {
                float tx = b.tx, ty = b.ty, tz = b.tz, sx = b.sx, sy = b.sy, sz = b.sz;
                float qx = b.qx, qy = b.qy, qz = b.qz, qw = b.qw;
                if (p != null) {
                    if (p.absPos[i]) {
                        tx = p.pos[i * 3];
                        ty = p.pos[i * 3 + 1];
                        tz = p.pos[i * 3 + 2];
                    } else {
                        tx += p.pos[i * 3];
                        ty += p.pos[i * 3 + 1];
                        tz += p.pos[i * 3 + 2];
                    }
                    if (p.absScl[i]) {
                        sx = p.scl[i * 3];
                        sy = p.scl[i * 3 + 1];
                        sz = p.scl[i * 3 + 2];
                    } else {
                        sx *= p.scl[i * 3];
                        sy *= p.scl[i * 3 + 1];
                        sz *= p.scl[i * 3 + 2];
                    }
                    if (p.hasQuat[i]) {
                        qx = p.quat[i * 4];
                        qy = p.quat[i * 4 + 1];
                        qz = p.quat[i * 4 + 2];
                        qw = p.quat[i * 4 + 3];
                    }
                }
                M4.translate(m, tx, ty, tz);
                M4.translate(m, b.px, b.py, b.pz);
                M4.rotateQuat(m, qx, qy, qz, qw);
                if (p != null && !p.hasQuat[i]) M4.rotateZYX(m, p.rot[i * 3], p.rot[i * 3 + 1], p.rot[i * 3 + 2]);
                M4.scale(m, sx, sy, sz);
                M4.translate(m, -b.px, -b.py, -b.pz);
                if (b.post != null) M4.mul(m, b.post, m);
            }
            w[i] = m;
        }
        return w;
    }

    public Clip clip(String name) {
        if (name == null) return null;
        for (Clip c : clips) if (c.name.equalsIgnoreCase(name)) return c;
        return null;
    }

    public int triangles() {
        int n = 0;
        for (Part p : parts) n += p.verts / 3;
        return n;
    }

    /** Finds or creates the part for (bone, texture, emissive). */
    public Part part(int bone, int tex, boolean emissive) {
        for (Part p : parts) if (p.bone == bone && p.tex == tex && p.emissive == emissive) return p;
        Part p = new Part();
        p.bone = bone;
        p.tex = tex;
        p.emissive = emissive;
        if (tex >= 0 && tex < textures.size()) p.translucent = textures.get(tex).translucent;
        parts.add(p);
        return p;
    }

    /** Marks textures that have semi-transparent pixels (drawn in a second, depth-read-only pass). */
    public static void classify(Tex t) {
        if (t.argb == null) return;
        int partial = 0, opaque = 0;
        for (int c : t.argb) {
            int a = c >>> 24;
            if (a > 8 && a < 240) partial++;
            else if (a >= 240) opaque++;
        }
        t.translucent = partial > Math.max(4, (partial + opaque) / 50);
    }
}
