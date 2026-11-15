package net.m1227.weapons;

import java.util.ArrayList;
import java.util.List;

/**
 * First-person weapon motion, in layers that blend instead of snapping:
 * <ol>
 * <li>hold pose (per weapon style) and the equip / draw rise;</li>
 * <li>locomotion: idle breathing, walking, sprint pose, jump and fall poses, landing recovery;</li>
 * <li>camera inertia: the weapon lags behind fast turns and settles back (a spring);</li>
 * <li>swing: a library of swings drawn from keyframe curves (classic, fast, heavy, diagonal,
 * horizontal, vertical, thrust, flourish, twin, two-handed), alternate sides, a three-hit combo
 * and a critical overhead swing; a new swing crossfades from the current pose;</li>
 * <li>block: the sword-blocking pose blends in and out (smooth) or switches at once (instant),
 * with or without blockhit.</li>
 * </ol>
 * Time is real seconds and springs are integrated in fixed small steps, so speed and duration
 * are the same at any frame rate. It only moves the drawing: attacks, timing and reach are
 * Minecraft's own. Pure: inputs in, a list of GL operations out.
 */
public final class WeaponMotion {

    // ------------------------------------------------------------------ inputs and settings

    /** What the player is doing this frame. */
    public static final class Input {
        /** Seconds (any clock that only moves forward). */
        public double time;
        /** Minecraft's swing progress (0 when not swinging). */
        public float swingProgress;
        /** Equip progress from Minecraft (0 = fully up). */
        public float equip;
        public boolean sprinting, onGround, blocking, inWater;
        /** Horizontal speed (blocks / tick), vertical motion (blocks / tick, + up), fall distance. */
        public float speed, motionY, fallDistance;
        /** Walk cycle (Minecraft's distanceWalkedModified, interpolated). */
        public float walkPhase;
        /** View angles in degrees (interpolated), for the inertia. */
        public float yaw, pitch;
        /** Draw progress 0..1 (1 = done) and whether the draw rises from below. */
        public float draw = 1f;
        public boolean drawRise = true, drawOvershoot = true;
    }

    /** User settings (all multipliers default to 1). */
    public static final class Params {
        /** "Per model" uses the weapon style; or one of {@link #SWINGS}. */
        public String swing = "Per model";
        /** Weapon hold style: Classic, Two-handed, Heavy, Thrust, Flourish, Twin. */
        public String style = "Classic";
        public float swingSpeed = 1f, speed = 1f, intensity = 1f, sway = 1f, move = 1f, jump = 1f;
        public boolean alternate = true, combo = false, critical = true, idle = true, sprintPose = true;
        public boolean smoothBlock = true, blockhit = true;
    }

    public static final String[] SWINGS = {"Per model", "Classic", "Fast", "Heavy", "Diagonal slash", "Horizontal slash",
            "Vertical slash", "Thrust", "Flourish", "Twin", "Two-handed"};

    // ------------------------------------------------------------------ output

    /** GL operations, in order: {0, x, y, z} translate, {1, deg, ax, ay, az} rotate, {2, x, y, z} scale. */
    public final List<float[]> ops = new ArrayList<>();

    private void t(float x, float y, float z) {
        if (x != 0f || y != 0f || z != 0f) ops.add(new float[]{0, x, y, z, 0});
    }

    private void r(float deg, float x, float y, float z) {
        if (Math.abs(deg) > 1e-4f) ops.add(new float[]{1, deg, x, y, z});
    }

    private void s(float k) {
        ops.add(new float[]{2, k, k, k, 0});
    }

    // ------------------------------------------------------------------ state

    private double last = Double.NaN;
    private boolean wasSwinging;
    private float lastSwingP;
    /** Current swing. */
    private String swingName = "";
    private double swingStart = -1;
    private float swingDur = 0.42f, swingSide = 1f;
    private int count, comboIndex;
    private double lastSwingEnd = -10;
    /** Pose the swing started from (for the crossfade) and when. */
    private final float[] from = new float[6], cur = new float[6];
    private double fadeStart = -10;
    private boolean lastCrit;

    private final Spring sprint = new Spring(7f, 1f), moving = new Spring(6f, 1f), air = new Spring(9f, 0.9f);
    private final Spring land = new Spring(16f, 0.45f), lagYaw = new Spring(14f, 0.7f), lagPitch = new Spring(14f, 0.7f);
    private final Spring block = new Spring(18f, 1f);
    private float prevYaw = Float.NaN, prevPitch, prevMotionY;
    private boolean prevGround = true;

    /** True when the last swing started was a critical one (for effects). */
    public boolean lastSwingCritical() {
        return lastCrit;
    }

    /** How far the sword-block pose is blended in (0..1). */
    public float blockWeight() {
        return block.pos;
    }

    public int swings() {
        return count;
    }

    /**
     * Angle of the current (or last) swing's cut, in degrees around the facing direction:
     * about 0 = horizontal, 90 = vertical, the sign gives the side. For slash effects.
     */
    public float slashTiltDeg() {
        String n = swingName.isEmpty() ? lastName : swingName;
        float side = swingName.isEmpty() ? lastSide : swingSide;
        switch (n) {
            case "Horizontal slash":
                return 8f * side;
            case "Vertical slash":
            case "Critical":
                return 85f;
            case "Heavy":
                return side > 0 ? 75f : -40f;
            case "Thrust":
                return 15f * side;
            case "Flourish":
                return -30f * side;
            case "Twin":
                return 25f * side;
            case "Two-handed":
                return 12f * side;
            case "Diagonal slash":
                return 40f * side;
            default:
                return 35f * side;
        }
    }

    private String lastName = "Classic";
    private float lastSide = 1f;

    /** Name of the swing being drawn ("" when none). */
    public String currentSwing() {
        return swingName;
    }

    // ------------------------------------------------------------------ update

    public void update(Input in, Params p) {
        ops.clear();
        double now = in.time;
        float dt = Double.isNaN(last) ? 0f : (float) Math.max(0.0, Math.min(0.1, now - last));
        if (!Double.isNaN(last) && now - last > 0.5) reset(); // came back after a pause: no leftover motion
        last = now;
        float sp = Math.max(0.1f, p.speed);
        float I = Math.max(0f, p.intensity);

        // --- swing timing: starts when Minecraft starts a swing (or restarts it)
        boolean started = in.swingProgress > 0f && (!wasSwinging || in.swingProgress < lastSwingP - 0.2f);
        wasSwinging = in.swingProgress > 0f;
        lastSwingP = in.swingProgress;
        float prog = swingStart < 0 ? 1f : (float) ((now - swingStart) / swingDur);
        if (started && (prog > 0.6f || swingStart < 0)) begin(now, in, p, prog < 1f);
        prog = swingStart < 0 ? 1f : (float) ((now - swingStart) / swingDur);
        if (prog >= 1f && swingStart >= 0) {
            swingStart = -1;
            lastSwingEnd = now;
            swingName = "";
        }

        // --- locomotion targets
        boolean moving2 = in.speed > 0.02f && in.onGround;
        sprint.target = p.sprintPose && in.sprinting && in.onGround ? 1f : 0f;
        moving.target = moving2 ? Math.min(1f, in.speed / 0.22f) : 0f;
        air.target = in.onGround || in.inWater ? 0f : in.motionY > 0.02f ? 1f : -Math.min(1f, -in.motionY * 1.6f);
        if (in.onGround && !prevGround && prevMotionY < -0.25f && !in.inWater) {
            land.vel -= Math.min(1.6f, -prevMotionY * 2.2f) * 60f; // impulse: the weapon dips and recovers
        }
        prevGround = in.onGround;
        prevMotionY = in.motionY;
        // camera inertia: the weapon trails fast turns
        if (Float.isNaN(prevYaw) || dt <= 0f) {
            prevYaw = in.yaw;
            prevPitch = in.pitch;
        }
        float dy = wrap(in.yaw - prevYaw), dp = in.pitch - prevPitch;
        prevYaw = in.yaw;
        prevPitch = in.pitch;
        float yawRate = dt > 0f ? dy / dt : 0f, pitchRate = dt > 0f ? dp / dt : 0f;
        lagYaw.target = clamp(-yawRate * 0.012f, -10f, 10f);
        lagPitch.target = clamp(-pitchRate * 0.010f, -8f, 8f);
        block.target = in.blocking ? 1f : 0f;
        if (!p.smoothBlock) {
            block.pos = block.target;
            block.vel = 0f;
        }
        for (Spring s : new Spring[]{sprint, moving, air, land, lagYaw, lagPitch, block}) s.step(dt * (s == block ? 1f : sp));
        if (!p.smoothBlock) block.pos = block.target;

        // --- swing pose (weapon space, after the hold yaw)
        float b = 0f;
        java.util.Arrays.fill(cur, 0f);
        if (swingStart >= 0) {
            prog = Math.max(0f, Math.min(1f, (float) ((now - swingStart) / swingDur)));
            pose(swingName, prog, swingSide, cur);
            b = (float) Math.sin(prog * Math.PI);
            float k = I * (in.blocking && !p.blockhit ? 0f : 1f);
            for (int i = 0; i < 6; i++) cur[i] *= k;
        }
        float fade = (float) ((now - fadeStart) / 0.07);
        if (fade < 1f) {
            float w = smooth(fade);
            for (int i = 0; i < 6; i++) cur[i] = from[i] + (cur[i] - from[i]) * w;
        }
        float rest = 1f - Math.min(1f, b * 1.4f); // poses that fade out while swinging

        // --- build the transform
        float x = 0.56f, y = -0.52f, z = -0.72f, yaw = 45f, holdRoll = 0f;
        if (p.style.equals("Two-handed")) {
            x = 0.42f;
            y = -0.50f;
            yaw = 38f;
            holdRoll = -12f;
        }
        t(x, y, z);
        t(0f, in.equip * -0.6f, 0f);
        if (in.draw < 1f && in.drawRise) {
            float d = 1f - (in.drawOvershoot ? easeOutBack(in.draw) : smooth(in.draw));
            t(0.06f * d, -0.45f * d, 0.08f * d);
            r(-40f * d, 1f, 0f, 0f);
        }
        // sprint: lowered and tilted back, with a running sway in step with the walk cycle
        float sk = sprint.pos * rest * p.move;
        if (Math.abs(sk) > 1e-3f) {
            float run = (float) Math.sin(in.walkPhase * Math.PI);
            t(0.03f * sk, -0.07f * sk, 0.05f * sk);
            r(16f * sk + 3f * run * sk, 1f, 0f, 0f);
            r(-10f * sk, 0f, 1f, 0f);
            r(4f * run * sk, 0f, 0f, 1f);
        }
        // walking: a slight forward lean and a sway with each step
        float wk = moving.pos * (1f - sprint.pos) * rest * p.move;
        if (Math.abs(wk) > 1e-3f) {
            float step = (float) Math.sin(in.walkPhase * Math.PI);
            t(0.008f * step * wk, 0f, 0f);
            r(3f * wk, 1f, 0f, 0f);
            r(1.6f * step * wk, 0f, 0f, 1f);
        }
        // jump / fall: raised on the way up, lifted back while falling
        float ak = air.pos * p.jump * rest;
        if (Math.abs(ak) > 1e-3f) {
            if (ak > 0f) {
                t(0f, -0.025f * ak, 0.01f * ak);
                r(-5f * ak, 1f, 0f, 0f);
            } else {
                t(0f, 0.035f * -ak, 0f);
                r(8f * -ak, 1f, 0f, 0f);
            }
        }
        // landing: a dip that springs back
        float lk = land.pos * p.jump;
        if (Math.abs(lk) > 1e-3f) {
            t(0f, 0.012f * lk, 0f);
            r(1.5f * lk, 1f, 0f, 0f); // lk < 0 after a landing: down and tipped forward, then back
        }
        // idle breathing
        if (p.idle) {
            float ti = (float) (now % 3600.0) * sp;
            float k = rest * (1f - sprint.pos) * (1f - moving.pos * 0.6f) * I;
            t(0f, 0.008f * (float) Math.sin(ti * 1.6f) * k, 0f);
            r(1.6f * (float) Math.sin(ti * 1.1f) * k, 1f, 0f, 0f);
            r(1.2f * (float) Math.sin(ti * 0.8f + 1f) * k, 0f, 0f, 1f);
        }
        // camera inertia
        float sw = p.sway;
        if (sw > 0f) {
            t(lagYaw.pos * 0.0035f * sw, -lagPitch.pos * 0.003f * sw, 0f);
            r(lagYaw.pos * sw, 0f, 1f, 0f);
            r(-lagPitch.pos * 0.8f * sw, 1f, 0f, 0f);
            r(lagYaw.pos * 0.6f * sw, 0f, 0f, 1f);
        }
        r(yaw, 0f, 1f, 0f);
        r(holdRoll, 0f, 0f, 1f);
        // swing: translate, then Y, Z, X
        t(cur[0], cur[1], cur[2]);
        r(cur[3], 0f, 1f, 0f);
        r(cur[4], 0f, 0f, 1f);
        r(cur[5], 1f, 0f, 0f);
        s(0.4f);
        // sword block (Minecraft's own block pose, blended)
        float bk = block.pos;
        if (bk > 1e-3f) {
            t(-0.5f * bk, 0.2f * bk, 0f);
            r(30f * bk, 0f, 1f, 0f);
            r(-80f * bk, 1f, 0f, 0f);
            r(60f * bk, 0f, 1f, 0f);
        }
    }

    private void begin(double now, Input in, Params p, boolean interrupted) {
        // crossfade from where the weapon is now
        System.arraycopy(cur, 0, from, 0, 6);
        fadeStart = interrupted ? now : -10;
        boolean crit = p.critical && in.fallDistance > 0f && !in.onGround && !in.inWater && in.motionY < 0f;
        lastCrit = crit;
        String base = p.swing.equals("Per model") ? styleSwing(p.style) : p.swing;
        String name = base;
        if (p.combo && now - lastSwingEnd < 0.6 && !crit) {
            comboIndex = (comboIndex + 1) % 3;
            if (comboIndex == 1) name = "Horizontal slash";
            else if (comboIndex == 2) name = "Vertical slash";
        } else {
            comboIndex = 0;
        }
        if (crit) name = "Critical";
        count++;
        swingSide = p.alternate && (count & 1) == 0 ? -1f : 1f;
        swingName = name;
        lastName = name;
        lastSide = swingSide;
        swingDur = duration(name) / Math.max(0.3f, p.swingSpeed * p.speed);
        swingStart = now;
    }

    private static String styleSwing(String style) {
        switch (style) {
            case "Two-handed":
            case "Heavy":
            case "Thrust":
            case "Flourish":
            case "Twin":
                return style;
            default:
                return "Classic";
        }
    }

    static float duration(String n) {
        switch (n) {
            case "Fast":
                return 0.28f;
            case "Heavy":
                return 0.55f;
            case "Vertical slash":
            case "Two-handed":
            case "Flourish":
                return 0.5f;
            case "Critical":
                return 0.55f;
            case "Thrust":
                return 0.38f;
            case "Diagonal slash":
            case "Horizontal slash":
            case "Twin":
                return 0.45f;
            default:
                return 0.42f;
        }
    }

    // ------------------------------------------------------------------ swing library

    /** Keyframes: time, then tx ty tz ry rz rx, then easing into the key (0 smooth, 1 out = fast start, 2 in). */
    private static final float[][] DIAGONAL = {
            {0.18f, 0.04f, 0.06f, 0f, 18f, 25f, 10f, 0},
            {0.52f, -0.10f, -0.06f, -0.10f, -40f, -45f, -70f, 1},
            {1f, 0, 0, 0, 0, 0, 0, 0}};
    private static final float[][] HORIZONTAL = {
            {0.16f, 0.05f, 0.01f, 0f, 28f, -55f, -15f, 0},
            {0.52f, -0.16f, 0f, -0.08f, -62f, -68f, -22f, 1},
            {1f, 0, 0, 0, 0, 0, 0, 0}};
    private static final float[][] VERTICAL = {
            {0.2f, 0f, 0.08f, 0.03f, 0f, 0f, 25f, 0},
            {0.52f, 0f, -0.08f, -0.10f, -6f, -6f, -100f, 1},
            {1f, 0, 0, 0, 0, 0, 0, 0}};
    private static final float[][] CRITICAL = {
            {0.22f, 0f, 0.12f, 0.05f, 0f, 10f, 40f, 0},
            {0.5f, -0.02f, -0.10f, -0.12f, -10f, -14f, -125f, 1},
            {0.62f, -0.02f, -0.09f, -0.11f, -10f, -14f, -118f, 0},
            {1f, 0, 0, 0, 0, 0, 0, 0}};

    /** Pose of a swing at progress 0..1: tx ty tz, rotY rotZ rotX (degrees). */
    static void pose(String n, float sw, float side, float[] o) {
        float bell = (float) Math.sin(sw * Math.PI);
        float lead = (float) Math.sin(Math.sqrt(sw) * Math.PI);
        float f = (float) Math.sin(sw * sw * Math.PI);
        boolean alt = side < 0f;
        java.util.Arrays.fill(o, 0f);
        switch (n) {
            case "Two-handed":
                set(o, -0.18f * bell * side, 0.04f * bell, -0.08f * bell, -55f * lead * side, -14f * bell, -28f * bell);
                return;
            case "Heavy":
                if (!alt) set(o, 0f, 0.06f * bell, -0.06f * bell, -15f * lead, 0f, -85f * lead);
                else set(o, -0.06f * bell, -0.04f * bell, -0.08f * bell, 35f * lead, 28f * lead, -55f * lead);
                return;
            case "Thrust":
                set(o, -0.08f * bell, (alt ? -0.02f : 0.03f) * bell, -0.28f * bell, 10f * bell * side, 0f, (alt ? -12f : -22f) * bell);
                return;
            case "Flourish":
                set(o, 0f, 0f, 0f, -40f * lead * side, -30f * lead * side, -55f * lead);
                return;
            case "Twin": {
                float two = (float) Math.sin(sw * Math.PI * 2.0);
                set(o, 0f, 0f, 0f, 30f * two * side, 0f, -45f * bell);
                return;
            }
            case "Fast":
                if (!alt) set(o, 0f, 0f, 0f, -16f * f, -16f * lead, -62f * lead);
                else set(o, 0f, 0f, 0f, 18f * f, 22f * lead, -52f * lead);
                return;
            case "Diagonal slash":
                keys(DIAGONAL, sw, o, side);
                return;
            case "Horizontal slash":
                keys(HORIZONTAL, sw, o, side);
                return;
            case "Vertical slash":
                keys(VERTICAL, sw, o, 1f);
                return;
            case "Critical":
                keys(CRITICAL, sw, o, 1f);
                return;
            default: // Classic: Minecraft's curve over the longer visual time; backhand on alternate swings
                if (!alt) set(o, 0f, 0f, 0f, -20f * f, -20f * lead, -80f * lead);
                else set(o, 0f, 0f, 0f, 22f * f, 28f * lead, -65f * lead);
        }
    }

    private static void set(float[] o, float tx, float ty, float tz, float ry, float rz, float rx) {
        o[0] = tx;
        o[1] = ty;
        o[2] = tz;
        o[3] = ry;
        o[4] = rz;
        o[5] = rx;
    }

    /** Evaluates keyframes from the rest pose at t = 0; side mirrors x, yaw and roll. */
    static void keys(float[][] k, float t, float[] o, float side) {
        float t0 = 0f;
        float[] a = new float[6];
        for (float[] key : k) {
            if (t <= key[0]) {
                float u = (t - t0) / Math.max(1e-5f, key[0] - t0);
                int ease = (int) key[7];
                float e = ease == 1 ? 1f - (1f - u) * (1f - u) * (1f - u) : ease == 2 ? u * u * u : smooth(u);
                for (int i = 0; i < 6; i++) o[i] = a[i] + (key[i + 1] - a[i]) * e;
                o[0] *= side;
                o[3] *= side;
                o[4] *= side;
                return;
            }
            t0 = key[0];
            for (int i = 0; i < 6; i++) a[i] = key[i + 1];
        }
        System.arraycopy(a, 0, o, 0, 6);
    }

    /** The ops multiplied into one matrix (for tests and previews). */
    public float[] matrix() {
        float[] m = M4.identity();
        for (float[] o : ops) {
            if (o[0] == 0f) M4.translate(m, o[1], o[2], o[3]);
            else if (o[0] == 1f) M4.rotate(m, o[1], o[2], o[3], o[4]);
            else M4.scale(m, o[1], o[2], o[3]);
        }
        return m;
    }

    // ------------------------------------------------------------------ helpers

    private void reset() {
        swingStart = -1;
        swingName = "";
        fadeStart = -10;
        java.util.Arrays.fill(cur, 0f);
        for (Spring s : new Spring[]{sprint, moving, air, land, lagYaw, lagPitch}) s.pos = s.vel = 0f;
        prevYaw = Float.NaN;
    }

    static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    static float easeOutBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f;
        float x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    private static float wrap(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /** Damped spring towards 'target', integrated in fixed 1/240 s steps (frame-rate independent). */
    static final class Spring {
        final float w, z;
        float pos, vel, target;
        private float acc;

        Spring(float omega, float damping) {
            w = omega;
            z = damping;
        }

        void step(float dt) {
            acc += dt;
            final float h = 1f / 240f;
            while (acc >= h) {
                float a = w * w * (target - pos) - 2f * z * w * vel;
                vel += a * h;
                pos += vel * h;
                acc -= h;
            }
        }
    }
}
