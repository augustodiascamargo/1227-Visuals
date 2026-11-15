package net.m1227.weapons;

/**
 * Works out how an imported model sits in the hand, from its shape alone: the blade axis
 * (principal axis of the geometry, snapped to the model's axes when close), which end is the
 * tip, the handle (the narrowest section near the bottom, up to the guard) and a size. The
 * result maps model space to "weapon space": origin in the middle of the grip, +X towards the
 * tip, +Y across the blade, +Z through it, in item units (the built-in swords are 1.3 long).
 * Pure.
 */
public final class WeaponFit {
    /** Model-space unit axes. */
    public final float[] a = new float[3], n = new float[3], t = new float[3];
    /** Grip centre (model space), length along the axis (model units), auto size factor, hold point 0..1. */
    public final float[] grip = new float[3];
    public float length, autoSize = 1f, gripAt;
    public float a0;
    /** Centre and the A/N/T extents for the hold-point slider. */
    final float[] c = new float[3];
    float nMid, tMid;

    /** Built-in sword length in item units. */
    public static final float TARGET = 1.30f;

    public static WeaponFit compute(WModel m, boolean flip, float gripOverride) {
        WeaponFit f = new WeaponFit();
        float[][] bm = m.boneMatrices(null);
        // glow / effect planes (e.g. an "fx" overlay) do not describe the weapon's shape
        boolean solid = false;
        for (WModel.Part p : m.parts) solid |= !p.emissive && p.verts > 0;
        int total = 0;
        for (WModel.Part p : m.parts) if (!solid || !p.emissive) total += p.verts;
        if (total == 0) {
            f.a[1] = 1f;
            f.n[0] = 1f;
            f.t[2] = 1f;
            f.length = 1f;
            return f;
        }
        float[] P = new float[total * 3];
        int k = 0;
        float[] tmp = new float[3];
        for (WModel.Part p : m.parts) {
            if (solid && p.emissive) continue;
            float[] mat = bm[Math.max(0, Math.min(bm.length - 1, p.bone))];
            for (int i = 0; i < p.verts; i++) {
                M4.point(mat, p.v[i * 8], p.v[i * 8 + 1], p.v[i * 8 + 2], tmp);
                P[k++] = tmp[0];
                P[k++] = tmp[1];
                P[k++] = tmp[2];
            }
        }
        int nv = total;
        for (int i = 0; i < nv; i++) for (int j = 0; j < 3; j++) f.c[j] += P[i * 3 + j] / nv;
        double[][] cov = new double[3][3];
        for (int i = 0; i < nv; i++) {
            double x = P[i * 3] - f.c[0], y = P[i * 3 + 1] - f.c[1], z = P[i * 3 + 2] - f.c[2];
            double[] d = {x, y, z};
            for (int r = 0; r < 3; r++) for (int s = 0; s < 3; s++) cov[r][s] += d[r] * d[s];
        }
        double[][] ev = jacobi(cov);
        // ev[0..2] sorted by eigenvalue (largest first): blade, width, thickness
        float[] A = snap(toF(ev[0])), N = toF(ev[1]);
        float dn = dot(N, A);
        for (int j = 0; j < 3; j++) N[j] -= dn * A[j];
        norm(N);
        N = snap(N);
        dn = dot(N, A);
        for (int j = 0; j < 3; j++) N[j] -= dn * A[j];
        norm(N);
        // which end is the tip
        if (Math.abs(A[1]) > 0.5f) {
            if (A[1] < 0) neg(A);
        } else {
            float[] o = "bbmodel".equals(m.format) && m.displayScale > 0f ? new float[]{8f, 8f, 8f} : new float[]{0f, 0f, 0f};
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int i = 0; i < nv; i++) {
                float s = (P[i * 3] - f.c[0]) * A[0] + (P[i * 3 + 1] - f.c[1]) * A[1] + (P[i * 3 + 2] - f.c[2]) * A[2];
                lo = Math.min(lo, s);
                hi = Math.max(hi, s);
            }
            float os = (o[0] - f.c[0]) * A[0] + (o[1] - f.c[1]) * A[1] + (o[2] - f.c[2]) * A[2];
            if (Math.abs(hi - os) < Math.abs(lo - os)) neg(A);
        }
        if (flip) neg(A);
        int dom = Math.abs(N[2]) >= Math.abs(N[0]) && Math.abs(N[2]) >= Math.abs(N[1]) ? 2 : Math.abs(N[0]) >= Math.abs(N[1]) ? 0 : 1;
        if (N[dom] < 0) neg(N);
        float[] T = {A[1] * N[2] - A[2] * N[1], A[2] * N[0] - A[0] * N[2], A[0] * N[1] - A[1] * N[0]};
        System.arraycopy(A, 0, f.a, 0, 3);
        System.arraycopy(N, 0, f.n, 0, 3);
        System.arraycopy(T, 0, f.t, 0, 3);

        // section profile along the axis
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        float[] sa = new float[nv], sn = new float[nv], st = new float[nv];
        for (int i = 0; i < nv; i++) {
            float x = P[i * 3] - f.c[0], y = P[i * 3 + 1] - f.c[1], z = P[i * 3 + 2] - f.c[2];
            sa[i] = x * A[0] + y * A[1] + z * A[2];
            sn[i] = x * N[0] + y * N[1] + z * N[2];
            st[i] = x * T[0] + y * T[1] + z * T[2];
            lo = Math.min(lo, sa[i]);
            hi = Math.max(hi, sa[i]);
        }
        float L = Math.max(1e-4f, hi - lo);
        f.length = L;
        f.a0 = lo;
        final int B = 48;
        float[] bmin = new float[B], bmax = new float[B], tmin = new float[B], tmax = new float[B];
        java.util.Arrays.fill(bmin, Float.MAX_VALUE);
        java.util.Arrays.fill(bmax, -Float.MAX_VALUE);
        java.util.Arrays.fill(tmin, Float.MAX_VALUE);
        java.util.Arrays.fill(tmax, -Float.MAX_VALUE);
        for (int i = 0; i + 2 < nv; i += 3) {
            float amin = Math.min(sa[i], Math.min(sa[i + 1], sa[i + 2])), amax = Math.max(sa[i], Math.max(sa[i + 1], sa[i + 2]));
            float nmin = Math.min(sn[i], Math.min(sn[i + 1], sn[i + 2])), nmax = Math.max(sn[i], Math.max(sn[i + 1], sn[i + 2]));
            float tmn = Math.min(st[i], Math.min(st[i + 1], st[i + 2])), tmx = Math.max(st[i], Math.max(st[i + 1], st[i + 2]));
            int b0 = Math.max(0, Math.min(B - 1, (int) ((amin - lo) / L * B))), b1 = Math.max(0, Math.min(B - 1, (int) ((amax - lo) / L * B)));
            for (int b = b0; b <= b1; b++) {
                bmin[b] = Math.min(bmin[b], nmin);
                bmax[b] = Math.max(bmax[b], nmax);
                tmin[b] = Math.min(tmin[b], tmn);
                tmax[b] = Math.max(tmax[b], tmx);
            }
        }
        float[] w = new float[B];
        for (int b = 0; b < B; b++) w[b] = bmax[b] >= bmin[b] ? bmax[b] - bmin[b] : -1f;
        // the guard: the first section (in the lower half) at least twice as wide as what is below it
        int lim = B * 55 / 100, guard = -1;
        for (int i = 2; i < lim && guard < 0; i++) {
            if (w[i] <= 0f) continue;
            float med = median(w, 0, i);
            if (med > 0f && w[i] >= 2f * med + 0.01f * L) guard = i;
        }
        int hi0 = guard > 0 ? guard : lim;
        // the grip: the longest stretch below it whose width stays close to the typical handle
        // width (a pommel, end cap or rings do not count)
        float med = median(w, 0, hi0);
        int bestI = -1, bestJ = -2;
        for (int i = 0; i < hi0; i++) {
            if (!(w[i] >= 0.75f * med && w[i] <= 1.3f * med + 0.005f * L)) continue;
            int j = i;
            while (j + 1 < hi0 && w[j + 1] >= 0.75f * med && w[j + 1] <= 1.3f * med + 0.005f * L) j++;
            if (j - i > bestJ - bestI) {
                bestI = i;
                bestJ = j;
            }
            i = j;
        }
        float g = lo + 0.12f * L;
        if (bestI >= 0) {
            float bot = lo + bestI * L / B, top = lo + (bestJ + 1) * L / B;
            if (guard > 0 && hi0 * L / B > 0.3f * L) g = lo + 0.12f * L;  // long haft (axe, spear): hold it low
            else g = (bot + top) * 0.5f;
        }
        float hold = gripOverride >= 0f ? lo + Math.min(1f, gripOverride) * L : g;
        f.gripAt = (hold - lo) / L;
        // centre of the cross-section at the hold point (so the hand wraps the handle)
        int hbI = Math.max(0, Math.min(B - 1, (int) ((hold - lo) / L * B)));
        f.nMid = bmax[hbI] >= bmin[hbI] ? (bmin[hbI] + bmax[hbI]) * 0.5f : 0f;
        f.tMid = tmax[hbI] >= tmin[hbI] ? (tmin[hbI] + tmax[hbI]) * 0.5f : 0f;
        for (int j = 0; j < 3; j++) f.grip[j] = f.c[j] + A[j] * hold + N[j] * f.nMid + T[j] * f.tMid;

        // size: Java item models carry their in-hand scale; everything else matches the built-in length
        if (m.displayScale > 0f) {
            float blocks = L / 16f * m.displayScale;
            f.autoSize = Math.max(0.8f, Math.min(1.2f, blocks / 0.96f));
        }
        return f;
    }

    /**
     * Model space to weapon space with the profile applied: scale to the target length, axes,
     * then size, twist / turn edge, roll, tilt and offsets around the grip.
     */
    public float[] matrix(WeaponProfile p) {
        float k = TARGET * autoSize / Math.max(1e-4f, length);
        float[] w = M4.identity();
        M4.translate(w, p.along, p.across, p.depth);
        M4.rotate(w, p.tilt, 0f, 0f, 1f);
        M4.rotate(w, p.roll, 0f, 1f, 0f);
        M4.rotate(w, p.twist + (p.turnEdge ? 180f : 0f), 1f, 0f, 0f);
        float s = k * p.size;
        M4.scale(w, s, s, s);
        float[] basis = {a[0], n[0], t[0], 0f, a[1], n[1], t[1], 0f, a[2], n[2], t[2], 0f, 0f, 0f, 0f, 1f};
        M4.mul(w, basis, w);
        M4.translate(w, -grip[0], -grip[1], -grip[2]);
        return w;
    }

    /** Weapon-space length of the model with this profile (item units). */
    public float lengthWith(WeaponProfile p) {
        return TARGET * autoSize * p.size;
    }

    // ------------------------------------------------------------------ maths

    private static float median(float[] w, int from, int to) {
        float[] t = new float[Math.max(0, to - from)];
        int n = 0;
        for (int i = from; i < to; i++) if (w[i] > 0f) t[n++] = w[i];
        if (n == 0) return 0f;
        java.util.Arrays.sort(t, 0, n);
        return t[n / 2];
    }

    private static float[] toF(double[] d) {
        float[] f = {(float) d[0], (float) d[1], (float) d[2]};
        norm(f);
        return f;
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void neg(float[] a) {
        a[0] = -a[0];
        a[1] = -a[1];
        a[2] = -a[2];
    }

    private static void norm(float[] a) {
        float l = (float) Math.sqrt(dot(a, a));
        if (l < 1e-9f) {
            a[0] = 1f;
            a[1] = a[2] = 0f;
            return;
        }
        a[0] /= l;
        a[1] /= l;
        a[2] /= l;
    }

    /** Snaps a direction to the nearest model axis when within ~12 degrees. */
    static float[] snap(float[] v) {
        int i = Math.abs(v[0]) >= Math.abs(v[1]) && Math.abs(v[0]) >= Math.abs(v[2]) ? 0 : Math.abs(v[1]) >= Math.abs(v[2]) ? 1 : 2;
        if (Math.abs(v[i]) > 0.978f) {
            float[] r = new float[3];
            r[i] = Math.signum(v[i]);
            return r;
        }
        return v;
    }

    /** Eigenvectors of a symmetric 3x3 matrix, sorted by eigenvalue, largest first. */
    static double[][] jacobi(double[][] a0) {
        double[][] a = {a0[0].clone(), a0[1].clone(), a0[2].clone()};
        double[][] v = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int it = 0; it < 50; it++) {
            int p = 0, q = 1;
            if (Math.abs(a[0][2]) > Math.abs(a[p][q])) {
                p = 0;
                q = 2;
            }
            if (Math.abs(a[1][2]) > Math.abs(a[p][q])) {
                p = 1;
                q = 2;
            }
            if (Math.abs(a[p][q]) < 1e-12 * (Math.abs(a[0][0]) + Math.abs(a[1][1]) + Math.abs(a[2][2]) + 1e-30)) break;
            double th = 0.5 * Math.atan2(2 * a[p][q], a[q][q] - a[p][p]);
            double c = Math.cos(th), s = Math.sin(th);
            for (int k = 0; k < 3; k++) {
                double akp = a[k][p], akq = a[k][q];
                a[k][p] = c * akp - s * akq;
                a[k][q] = s * akp + c * akq;
            }
            for (int k = 0; k < 3; k++) {
                double apk = a[p][k], aqk = a[q][k];
                a[p][k] = c * apk - s * aqk;
                a[q][k] = s * apk + c * aqk;
            }
            for (int k = 0; k < 3; k++) {
                double vkp = v[k][p], vkq = v[k][q];
                v[k][p] = c * vkp - s * vkq;
                v[k][q] = s * vkp + c * vkq;
            }
        }
        final double[] ev = {a[0][0], a[1][1], a[2][2]};
        Integer[] idx = {0, 1, 2};
        java.util.Arrays.sort(idx, (x, y) -> Double.compare(ev[y], ev[x]));
        double[][] out = new double[3][3];
        for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++) out[i][k] = v[k][idx[i]];
        return out;
    }
}
