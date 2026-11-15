package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.m1227.weapons.WeaponMotion;
import org.junit.Test;

/** Weapon motion engine: frame-rate independence, swings return to rest, combo, critical, block. */
public class WeaponMotionTest {

    private static WeaponMotion.Input in(double t) {
        WeaponMotion.Input i = new WeaponMotion.Input();
        i.time = t;
        i.onGround = true;
        return i;
    }

    /** Scripted 2.5 s: walk, turn the camera, jump, land, swing twice; sampled at a given frame rate. */
    private static float[] run(float fps, WeaponMotion.Params p) {
        WeaponMotion m = new WeaponMotion();
        double dt = 1.0 / fps;
        float swing = 0f;
        for (double t = 0; t <= 2.5 + 1e-9; t += dt) {
            WeaponMotion.Input i = in(t);
            i.speed = t < 1.0 ? 0.2f : 0f;
            i.walkPhase = (float) (t * 1.8);
            i.yaw = (float) (t < 0.6 ? t * 300.0 : 180.0);
            i.pitch = (float) (Math.sin(t * 3) * 10);
            boolean air = t > 1.0 && t < 1.5;
            i.onGround = !air;
            i.motionY = air ? (t < 1.25 ? 0.3f : -0.6f) : 0f;
            i.fallDistance = air && t >= 1.25 ? 0.5f : 0f;
            // Minecraft's swing: 6 ticks long, started at 1.6 s and 2.0 s
            double[] starts = {1.6, 2.0};
            swing = 0f;
            for (double s0 : starts) if (t >= s0 && t < s0 + 0.3) swing = (float) ((t - s0) / 0.3 + 0.01);
            i.swingProgress = swing;
            m.update(i, p);
        }
        return m.matrix();
    }

    @Test
    public void sameMotionAtAnyFrameRate() {
        WeaponMotion.Params p = new WeaponMotion.Params();
        float[] a = run(30f, p), b = run(240f, p), c = run(144f, p);
        for (int k = 0; k < 16; k++) {
            assertEquals("30 vs 240 fps, element " + k, b[k], a[k], 0.02f);
            assertEquals("144 vs 240 fps, element " + k, b[k], c[k], 0.01f);
        }
    }

    @Test
    public void swingReturnsToRest() {
        WeaponMotion.Params p = new WeaponMotion.Params();
        p.idle = false;
        p.sway = 0f;
        for (String s : WeaponMotion.SWINGS) {
            if (s.equals("Per model")) continue;
            p.swing = s;
            WeaponMotion rest = new WeaponMotion(), m = new WeaponMotion();
            rest.update(in(0), p);
            float[] r0 = rest.matrix();
            boolean moved = false;
            for (int f = 0; f <= 120; f++) {
                WeaponMotion.Input i = in(f / 60.0);
                i.swingProgress = f >= 1 && f < 19 ? f / 18f : 0f;
                m.update(i, p);
                if (f == 10) moved = Math.abs(m.matrix()[12] - r0[12]) + Math.abs(m.matrix()[5] - r0[5]) + Math.abs(m.matrix()[0] - r0[0]) > 0.01f;
            }
            assertTrue(s + " moves the weapon", moved);
            float[] end = m.matrix();
            for (int k = 0; k < 16; k++) assertEquals(s + " back at rest", r0[k], end[k], 1e-3f);
        }
    }

    @Test
    public void comboCriticalAndAlternate() {
        WeaponMotion.Params p = new WeaponMotion.Params();
        p.combo = true;
        WeaponMotion m = new WeaponMotion();
        String[] seen = new String[4];
        double t = 0;
        for (int s = 0; s < 4; s++) {
            for (int f = 0; f < 6; f++, t += 0.05) {
                WeaponMotion.Input i = in(t);
                i.swingProgress = (f + 1) / 6f;
                m.update(i, p);
                if (f == 0) seen[s] = m.currentSwing();
            }
            for (int f = 0; f < 12; f++, t += 0.05) m.update(in(t), p); // let it finish, stay in the combo window
        }
        assertEquals("Classic", seen[0]);
        assertEquals("Horizontal slash", seen[1]);
        assertEquals("Vertical slash", seen[2]);
        assertEquals("Classic", seen[3]);
        // the slash angle follows the swing: the last one (classic) is diagonal, a vertical cut is ~upright
        assertEquals(35f, Math.abs(m.slashTiltDeg()), 1e-3f);
        // falling while swinging: critical
        WeaponMotion c = new WeaponMotion();
        WeaponMotion.Input i = in(10);
        i.onGround = false;
        i.motionY = -0.4f;
        i.fallDistance = 1f;
        i.swingProgress = 0.1f;
        c.update(i, new WeaponMotion.Params());
        assertEquals("Critical", c.currentSwing());
        assertTrue(c.lastSwingCritical());
    }

    @Test
    public void blockBlendsIntoMinecraftsBlockPose() {
        WeaponMotion.Params p = new WeaponMotion.Params();
        p.idle = false;
        p.sway = 0f;
        WeaponMotion m = new WeaponMotion();
        double t = 0;
        WeaponMotion.Input i = in(t);
        i.blocking = true;
        m.update(i, p);
        float first = m.matrix()[12];
        for (int f = 0; f < 60; f++) {
            i = in(t += 1 / 60.0);
            i.blocking = true;
            m.update(i, p);
        }
        // the vanilla chain: hold, yaw 45, scale 0.4, block transform
        float[] v = net.m1227.weapons.M4.identity();
        net.m1227.weapons.M4.translate(v, 0.56f, -0.52f, -0.72f);
        net.m1227.weapons.M4.rotate(v, 45f, 0f, 1f, 0f);
        net.m1227.weapons.M4.scale(v, 0.4f, 0.4f, 0.4f);
        net.m1227.weapons.M4.translate(v, -0.5f, 0.2f, 0f);
        net.m1227.weapons.M4.rotate(v, 30f, 0f, 1f, 0f);
        net.m1227.weapons.M4.rotate(v, -80f, 1f, 0f, 0f);
        net.m1227.weapons.M4.rotate(v, 60f, 0f, 1f, 0f);
        float[] got = m.matrix();
        for (int k = 0; k < 16; k++) assertEquals("element " + k, v[k], got[k], 2e-3f);
        assertTrue("smooth: not there on the first frame", Math.abs(first - v[12]) > 0.01f);
        p.smoothBlock = false;
        WeaponMotion n = new WeaponMotion();
        i = in(0);
        i.blocking = true;
        n.update(i, p);
        assertEquals(v[12], n.matrix()[12], 2e-3f);
    }
}
