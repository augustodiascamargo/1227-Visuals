package net.m1227.client.ui;

import net.m1227.module.ClientModule;

/**
 * 1227 design system tokens. Every client screen (main menu, pause, servers, settings, profiles,
 * screenshots) reads its colours, sizes and motion from here, so they look like one product.
 * See docs/DESIGN.md for the rules.
 */
public final class DS {
    private DS() {}

    // ---- colour (ARGB)
    public static final int BG = 0xFF0B0C11;
    public static final int SURFACE = 0xFF12141B;
    public static final int SURFACE_2 = 0xFF181B24;
    public static final int SURFACE_3 = 0xFF20232E;
    public static final int LINE = 0x16FFFFFF;
    public static final int LINE_STRONG = 0x2AFFFFFF;
    public static final int TEXT = 0xFFECEEF4;
    public static final int MUTED = 0xFF9AA0AE;
    public static final int FAINT = 0xFF646978;
    public static final int GOOD = 0xFF3DDC97;
    public static final int WARN = 0xFFFFC24A;
    public static final int BAD = 0xFFFF6B6B;
    public static final int SCRIM = 0xB0080910;

    /** The user's accent colour (Settings › Client), opaque. */
    public static int accent() {
        return 0xFF000000 | ClientModule.accent();
    }

    // ---- type (scale factors for Gfx.textScaled; 1.0 = body text, about 8.6 GUI units tall)
    public static final float T_SMALL = 0.82f;
    public static final float T_BODY = 1.0f;
    public static final float T_LABEL = 1.1f;
    public static final float T_TITLE = 1.55f;
    public static final float T_DISPLAY = 2.6f;

    // ---- shape and space (GUI units)
    public static final float R_SM = 4f;
    public static final float R_MD = 6f;
    public static final float R_LG = 10f;
    public static final float S1 = 4f, S2 = 8f, S3 = 12f, S4 = 16f, S5 = 24f;
    public static final float BUTTON_H = 22f;
    public static final float ROW_H = 26f;

    // ---- motion
    /** Response rate of hover / press / open animations (higher = snappier). */
    public static final float EASE_RATE = 14f;
    /** Screen fade-in duration, seconds. */
    public static final float OPEN_TIME = 0.22f;
}
