package net.m1227.weapons;

/** Column-major 4x4 float matrices (the OpenGL layout), as plain float[16]. Pure, no allocation in hot paths. */
public final class M4 {
    private M4() {}

    public static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    public static void setIdentity(float[] m) {
        java.util.Arrays.fill(m, 0f);
        m[0] = m[5] = m[10] = m[15] = 1f;
    }

    /** out = a * b (out may be a or b). */
    public static void mul(float[] a, float[] b, float[] out) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int rr = 0; rr < 4; rr++) {
                r[c * 4 + rr] = a[rr] * b[c * 4] + a[4 + rr] * b[c * 4 + 1] + a[8 + rr] * b[c * 4 + 2] + a[12 + rr] * b[c * 4 + 3];
            }
        }
        System.arraycopy(r, 0, out, 0, 16);
    }

    public static void translate(float[] m, float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
    }

    public static void scale(float[] m, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
    }

    /** m = m * R(axis, deg). */
    public static void rotate(float[] m, float deg, float ax, float ay, float az) {
        if (deg == 0f) return;
        float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (l < 1e-9f) return;
        ax /= l;
        ay /= l;
        az /= l;
        double r = Math.toRadians(deg);
        float c = (float) Math.cos(r), s = (float) Math.sin(r), t = 1f - c;
        float[] q = {
                t * ax * ax + c, t * ax * ay + s * az, t * ax * az - s * ay, 0f,
                t * ax * ay - s * az, t * ay * ay + c, t * ay * az + s * ax, 0f,
                t * ax * az + s * ay, t * ay * az - s * ax, t * az * az + c, 0f,
                0f, 0f, 0f, 1f};
        mul(m, q, m);
    }

    /** m = m * Rz * Ry * Rx: Blockbench / three.js Euler order "ZYX" (X is applied to the point first). */
    public static void rotateZYX(float[] m, float xDeg, float yDeg, float zDeg) {
        rotate(m, zDeg, 0f, 0f, 1f);
        rotate(m, yDeg, 0f, 1f, 0f);
        rotate(m, xDeg, 1f, 0f, 0f);
    }

    /** m = m * R(quaternion x, y, z, w). */
    public static void rotateQuat(float[] m, float x, float y, float z, float w) {
        float n = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (n < 1e-9f) return;
        x /= n;
        y /= n;
        z /= n;
        w /= n;
        float[] q = {
                1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
                2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
                2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
                0f, 0f, 0f, 1f};
        mul(m, q, m);
    }

    public static void point(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    public static void dir(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z;
        out[1] = m[1] * x + m[5] * y + m[9] * z;
        out[2] = m[2] * x + m[6] * y + m[10] * z;
    }

    /** Slerp between unit quaternions a and b (x, y, z, w), result into out. */
    public static void slerp(float[] a, int ai, float[] b, int bi, float t, float[] out, int oi) {
        float ax = a[ai], ay = a[ai + 1], az = a[ai + 2], aw = a[ai + 3];
        float bx = b[bi], by = b[bi + 1], bz = b[bi + 2], bw = b[bi + 3];
        float d = ax * bx + ay * by + az * bz + aw * bw;
        if (d < 0f) {
            d = -d;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        float k0, k1;
        if (d > 0.9995f) {
            k0 = 1f - t;
            k1 = t;
        } else {
            double th = Math.acos(d), sn = Math.sin(th);
            k0 = (float) (Math.sin((1 - t) * th) / sn);
            k1 = (float) (Math.sin(t * th) / sn);
        }
        float x = ax * k0 + bx * k1, y = ay * k0 + by * k1, z = az * k0 + bz * k1, w = aw * k0 + bw * k1;
        float n = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (n < 1e-9f) n = 1f;
        out[oi] = x / n;
        out[oi + 1] = y / n;
        out[oi + 2] = z / n;
        out[oi + 3] = w / n;
    }
}
