package net.m1227.combat;

import net.m1227.client.perf.Stats;

/**
 * What happened in combat, in order: the Combat Timeline, the Hit Timing Analyzer data and the
 * W-Tap Trainer data. Only events that were actually observed are logged; each says whether it is
 * a client event, a packet we sent, a packet from the server, or an inference.
 * Main thread only. Fixed-size rings, nothing grows.
 */
public final class CombatLog {
    public static final CombatLog INSTANCE = new CombatLog();

    public enum Kind {
        ATTEMPT("Attack attempt", "client"),
        SENT("Attack sent", "packet you sent"),
        HURT("Damage feedback", "server packet"),
        CRIT("Critical", "server packet"),
        KNOCKBACK("Knockback", "server packet / movement"),
        HEALTH("Health drop", "server data"),
        RESULT("Classified", "inference"),
        SELF_HURT("You were hit", "server packet"),
        COMBO("Combo", "inference"),
        SPRINT_STOP("Sprint stop sent", "packet you sent"),
        SPRINT_START("Sprint start sent", "packet you sent"),
        KILL("Kill", "server packet / inference");

        public final String label, source;

        Kind(String label, String source) {
            this.label = label;
            this.source = source;
        }
    }

    public static final int N = 160;
    private final long[] t = new long[N];
    private final Kind[] kind = new Kind[N];
    private final String[] text = new String[N];
    private final int[] color = new int[N];
    private int head, size;

    // Hit Timing Analyzer
    public final Stats.Series attemptGaps = new Stats.Series(256), hitGaps = new Stats.Series(256);
    /** Histogram of attempt-to-attempt gaps: 0-50, 50-100, ... 450-500, 500-1000, 1000+ ms. */
    public final int[] gapBuckets = new int[12];
    private long lastAttempt, lastHit;

    // W-Tap Trainer
    public static final class Reset {
        public long at;
        public String grade;
        public int color;
        /** ms: sprint stop sent -> start sent; start sent -> attack sent; -1 = n/a. */
        public float resetMs = -1, startToAttackMs = -1;
        public boolean sprintSent;
    }

    public final Reset[] resets = new Reset[24];
    private int resetHead, resetCount;
    private boolean sentSprint, assumedServerReset;
    private long stopAt, startAt, lastSprintAttack;
    public int sprintAttacks, attacks, perfect;

    private CombatLog() {
        for (int i = 0; i < resets.length; i++) resets[i] = new Reset();
    }

    public void add(long nanos, Kind k, String s, int argb) {
        t[head] = nanos;
        kind[head] = k;
        text[head] = s;
        color[head] = argb;
        head = (head + 1) % N;
        if (size < N) size++;
    }

    public int size() {
        return size;
    }

    /** i = 0 is the newest. */
    public long time(int i) {
        return t[(head - 1 - i + N) % N];
    }

    public Kind kind(int i) {
        return kind[(head - 1 - i + N) % N];
    }

    public String text(int i) {
        return text[(head - 1 - i + N) % N];
    }

    public int color(int i) {
        return color[(head - 1 - i + N) % N];
    }

    public void clear() {
        size = 0;
        head = 0;
        attemptGaps.clear();
        hitGaps.clear();
        java.util.Arrays.fill(gapBuckets, 0);
        resetCount = 0;
        sprintAttacks = attacks = perfect = 0;
        lastAttempt = lastHit = 0L;
    }

    // ------------------------------------------------------------- timing

    public void attempt(long now) {
        if (lastAttempt != 0L) {
            float g = (now - lastAttempt) / 1e6f;
            if (g < 5000f) {
                attemptGaps.add(now, g);
                int b = g < 500f ? (int) (g / 50f) : g < 1000f ? 10 : 11;
                gapBuckets[b]++;
            }
        }
        lastAttempt = now;
    }

    public void hit(long now) {
        if (lastHit != 0L) {
            float g = (now - lastHit) / 1e6f;
            if (g < 5000f) hitGaps.add(now, g);
        }
        lastHit = now;
    }

    // ------------------------------------------------------------- sprint (packets you sent)

    /** C0B start / stop, in the order they were written to the connection. */
    public void sprintPacket(long now, boolean start) {
        if (start) {
            startAt = now;
            if (!sentSprint) assumedServerReset = false; // a fresh start reaches the server
        } else {
            stopAt = now;
        }
        sentSprint = start;
        add(now, start ? Kind.SPRINT_START : Kind.SPRINT_STOP, "", 0xFF9AA0AE);
    }

    /**
     * C02 attack, in send order relative to the sprint packets. Grades the reset the same way as the
     * W-Tap Indicator, but from what was actually sent. Whether the server counted it as a sprint
     * hit is an inference: the server ends your sprint after a sprint hit that lands, and only a new
     * "start sprinting" packet turns it back on.
     */
    public Reset attackSent(long now) {
        attacks++;
        Reset r = resets[resetHead];
        resetHead = (resetHead + 1) % resets.length;
        if (resetCount < resets.length) resetCount++;
        r.at = now;
        r.sprintSent = sentSprint;
        boolean effective = sentSprint && !assumedServerReset;
        boolean chained = lastSprintAttack != 0L && now - lastSprintAttack < 1_500_000_000L;
        r.resetMs = stopAt != 0L && startAt > stopAt && chained ? (startAt - stopAt) / 1e6f : -1f;
        r.startToAttackMs = startAt != 0L && sentSprint ? (now - startAt) / 1e6f : -1f;
        if (effective) {
            sprintAttacks++;
            if (!chained) {
                r.grade = "Sprint hit";
                r.color = 0xFF3DDC97;
            } else if (r.resetMs >= 0 && r.resetMs <= 110) {
                perfect++;
                r.grade = "Perfect";
                r.color = 0xFF3DDC97;
            } else if (r.resetMs >= 0 && r.resetMs <= 260) {
                r.grade = "Good";
                r.color = 0xFFB6E35A;
            } else {
                r.grade = "Slow";
                r.color = 0xFFFFC24A;
            }
            lastSprintAttack = now;
            assumedServerReset = true;
        } else if (sentSprint) {
            r.grade = "No reset";
            r.color = 0xFFFF6B6B;
        } else {
            r.grade = "No sprint";
            r.color = 0xFF9AA0AE;
        }
        return r;
    }

    public int resetCount() {
        return resetCount;
    }

    /** i = 0 newest. */
    public Reset reset(int i) {
        return resets[(resetHead - 1 - i + resets.length * 2) % resets.length];
    }

    /** Standard deviation of the reset length over the kept history (ms), NaN if < 3. */
    public double resetConsistency() {
        float[] v = new float[resets.length];
        int n = 0;
        for (int i = 0; i < resetCount; i++) if (reset(i).resetMs >= 0) v[n++] = reset(i).resetMs;
        return n < 3 ? Double.NaN : Stats.stddev(v, n);
    }
}
