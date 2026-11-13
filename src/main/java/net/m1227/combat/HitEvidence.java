package net.m1227.combat;

/**
 * How an attack attempt is classified, from what the client could observe. Plain Java, no
 * Minecraft classes, so the rules are unit-tested.
 *
 * <h3>What counts as evidence</h3>
 * <ul>
 * <li><b>Hurt signal</b>: the server's "entity hurt" status (or hurt animation) for the target,
 * arriving inside the attempt's window. The server sends it only when it accepted damage, but it
 * does not say who caused the damage.</li>
 * <li><b>Health drop</b>: the target's health went down (only when the server shares real health
 * values; many do not).</li>
 * <li><b>Knockback</b>: the server's velocity packet for the target, or the target's position
 * moving away from you faster than before.</li>
 * <li><b>Critical</b>: the server's critical-hit animation for the target, after an attempt that
 * met the critical conditions on your side (the server sends it only for an accepted critical
 * attack).</li>
 * </ul>
 *
 * <h3>What lowers confidence</h3>
 * Another player swinging near the target in the same window, an arrow or other projectile near
 * it, fire / lava / poison / wither on the target, or a hurt signal that arrived faster than any
 * round trip measured to this server (it cannot be the answer to your attack).
 *
 * <h3>Levels</h3>
 * <ul>
 * <li>STRONG: hurt signal + at least one more independent sign, nothing else could explain it.</li>
 * <li>MODERATE: hurt signal alone with nothing else that could explain it; or a health drop
 * without a hurt signal (a stronger hit during the target's hit immunity) with nothing else.</li>
 * <li>WEAK: there was damage, but something else may have caused it; or the network monitor was
 * not available and only the client-side hurt animation was seen.</li>
 * <li>NONE (unconfirmed): the window passed with no sign of damage although the target could take
 * damage. Not "rejected": the server may still have counted it in ways the client cannot see.</li>
 * <li>UNKNOWN: not enough data: the target was in its hit immunity (after a hit the server
 * ignores weaker hits for 10 ticks), it disappeared, or the world changed.</li>
 * </ul>
 * "Likely registered" = STRONG or MODERATE.
 */
public final class HitEvidence {
    private HitEvidence() {}

    public enum Level {
        STRONG("Strong evidence"), MODERATE("Moderate evidence"), WEAK("Weak evidence"), NONE("No confirmation"), UNKNOWN("Unknown");

        public final String label;

        Level(String label) {
            this.label = label;
        }

        public boolean likely() {
            return this == STRONG || this == MODERATE;
        }
    }

    /** Facts about one attempt, filled in as they are observed. Times in ms (any common origin). */
    public static final class Facts {
        public boolean hurt, healthDrop, knockback, critical;
        public boolean otherAttacker, projectile, environment, tooFast;
        public boolean cooldown, targetGone, observer = true, clientHurtOnly;
    }

    public static final class Result {
        public final Level level;
        public final String reason;

        Result(Level level, String reason) {
            this.level = level;
            this.reason = reason;
        }
    }

    /**
     * How long after an attempt a reply can still belong to it: 1.5x the round trip plus 120 ms
     * (the server handles attacks on its next tick, up to 50 ms, plus jitter), 150..1000 ms. Unknown
     * round trip: 600 ms.
     */
    public static double windowMs(double rttMs) {
        if (Double.isNaN(rttMs) || rttMs <= 0) return 600;
        return Math.max(150, Math.min(1000, rttMs * 1.5 + 120));
    }

    /**
     * Earliest time after an attempt a reply to it can arrive: 60% of the lowest round trip seen,
     * minus 10 ms. 0 when the lowest round trip is not known.
     */
    public static double floorMs(double minRttMs) {
        if (Double.isNaN(minRttMs) || minRttMs <= 0) return 0;
        return Math.max(0, minRttMs * 0.6 - 10);
    }

    /**
     * True when the attempt reached the server while the target was still immune from the previous
     * accepted hit (10 ticks = 500 ms on the server, +50 ms tick margin). lastHurtMs = when the last
     * hurt signal arrived here (negative = none).
     */
    public static boolean inCooldown(double attemptMs, double lastHurtMs, double rttMs) {
        if (lastHurtMs < 0) return false;
        double rtt = Double.isNaN(rttMs) ? 100 : Math.min(400, Math.max(0, rttMs));
        // the previous hurt happened on the server ~one-way earlier than we saw it, and our attempt
        // reaches the server ~one-way later than we sent it: together about one round trip
        return attemptMs - lastHurtMs < 550 - rtt;
    }

    public static Result classify(Facts f) {
        boolean competing = f.otherAttacker || f.projectile || f.environment || f.tooFast;
        String why = competing ? (f.tooFast ? "arrived faster than a round trip" : f.otherAttacker ? "another player was attacking it"
                : f.projectile ? "a projectile was near it" : "fire, lava or poison on the target") : "";
        if (!f.observer) {
            if (f.clientHurtOnly) return new Result(Level.WEAK, "only the hurt animation was seen (network monitor off)");
            return new Result(Level.UNKNOWN, "network monitor off");
        }
        if (f.hurt) {
            if (competing) return new Result(Level.WEAK, "damage seen, but " + why);
            int more = (f.healthDrop ? 1 : 0) + (f.knockback ? 1 : 0) + (f.critical ? 1 : 0);
            if (more > 0) {
                StringBuilder b = new StringBuilder("hurt signal");
                if (f.healthDrop) b.append(" + health drop");
                if (f.knockback) b.append(" + knockback");
                if (f.critical) b.append(" + critical");
                return new Result(Level.STRONG, b.toString());
            }
            return new Result(Level.MODERATE, "hurt signal in the window");
        }
        if (f.healthDrop) {
            if (competing) return new Result(Level.WEAK, "health dropped, but " + why);
            return new Result(Level.MODERATE, "health dropped without a hurt signal (hit during immunity)");
        }
        if (f.targetGone) return new Result(Level.UNKNOWN, "the target disappeared");
        if (f.cooldown) return new Result(Level.UNKNOWN, "the target was immune from the previous hit");
        return new Result(Level.NONE, "no sign of damage in the window");
    }

    /**
     * Estimated Hit Feedback Rate: likely registered / (likely registered + unconfirmed). Weak and
     * unknown attempts are left out on purpose. NaN with fewer than 5 counted attempts.
     */
    public static double feedbackRate(int strong, int moderate, int none) {
        int d = strong + moderate + none;
        if (d < 5) return Double.NaN;
        return (strong + moderate) / (double) d;
    }
}
