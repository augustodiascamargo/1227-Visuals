package net.m1227.core;

/**
 * The one notification shown by the Dynamic Island. Anything can post:
 * module toggles, presets, kills, combos, player state changes. A newer
 * post replaces the current one. Nothing is posted until startup finishes,
 * so loading the config stays silent.
 */
public final class Notifier {
    public static final int GOOD = 0x3DDC84, BAD = 0xFF5A6A, INFO = 0x4DA3FF, WARN = 0xFFC857, ACCENT = 0x8B5CFF;

    public static volatile boolean ready;
    public static volatile String title = "";
    public static volatile String detail = "";
    public static volatile int color = INFO;
    public static volatile int duration = 1700;
    public static volatile long time;
    /** Per-kind switches, set by the Dynamic Island settings. */
    public static volatile boolean allowToggles = true, allowCombat = true, allowState = true, allowSystem = true;

    public enum Kind { TOGGLE, COMBAT, STATE, SYSTEM }

    private Notifier() {}

    public static void post(Kind kind, String t, String d, int rgb, int ms) {
        if (!ready) return;
        switch (kind) {
            case TOGGLE: if (!allowToggles) return; break;
            case COMBAT: if (!allowCombat) return; break;
            case STATE: if (!allowState) return; break;
            default: if (!allowSystem) return; break;
        }
        title = t;
        detail = d;
        color = rgb;
        duration = ms;
        time = System.currentTimeMillis();
    }

    public static void moduleToggled(String name, boolean on) {
        post(Kind.TOGGLE, name, on ? "Enabled" : "Disabled", on ? GOOD : BAD, 1600);
    }

    public static boolean active(long now) {
        return time > 0 && now - time < duration;
    }
}
