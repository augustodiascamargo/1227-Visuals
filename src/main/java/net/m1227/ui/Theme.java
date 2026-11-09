package net.m1227.ui;

import net.m1227.module.MenuStyleModule;

/** Colours and flags derived from Menu Style, used by the menu, HUD panels and the island. */
public final class Theme {
    private Theme() {}

    private static MenuStyleModule m() {
        return MenuStyleModule.INSTANCE;
    }

    public static String style() {
        return m() == null ? "Glass" : m().style.get();
    }

    public static boolean is(String s) {
        return style().equals(s);
    }

    /** Frosted background (Minecraft's blur shader + glass copy behind panels). */
    public static boolean blur() {
        return (is("Glass") || is("Frosted") || liquid()) && blurAmount() > 0.01f;
    }

    /** Apple-style liquid glass: very clear, magnified background, bright specular rim. */
    public static boolean liquid() {
        return is("Liquid Glass");
    }

    public static float blurAmount() {
        return m() == null ? 1f : m().blur.f();
    }

    public static float opacity() {
        return m() == null ? 1f : m().opacity.f();
    }

    public static float radius() {
        return m() == null ? 10f : m().radius.f();
    }

    public static float dim() {
        return m() == null ? 1f : m().dim.f();
    }

    public static float border() {
        return m() == null ? 1f : m().border.f();
    }

    public static boolean sheen() {
        return m() == null || m().sheen.on();
    }

    public static boolean hudFollows() {
        return m() == null || m().hudFollows.on();
    }

    public static int tint() {
        return 0xFF000000 | (m() == null ? 0x0E1018 : m().tint.rgb());
    }

    private static int a(int c, float alpha) {
        return Gfx.alpha(c, Math.min(1f, alpha * opacity()));
    }

    /** Main window fill (drawn over the glass copy when blurred). */
    public static int window() {
        String s = style();
        if (s.equals("Liquid Glass")) return a(tint(), 0.22f);
        if (s.equals("Glass")) return a(tint(), 0.42f);
        if (s.equals("Frosted")) return a(tint(), 0.72f);
        if (s.equals("Transparent")) return a(tint(), 0.55f);
        if (s.equals("Minimal")) return a(tint(), 0.0f);
        return a(tint(), 0.97f); // Solid, Neon
    }

    public static int sidebar() {
        String s = style();
        if (s.equals("Liquid Glass")) return a(0xFFFFFFFF, 0.05f);
        if (s.equals("Glass")) return a(0xFFFFFFFF, 0.035f);
        if (s.equals("Frosted")) return a(0xFF000000, 0.18f);
        if (s.equals("Transparent")) return a(0xFF000000, 0.22f);
        if (s.equals("Minimal")) return a(tint(), 0.55f);
        return a(Gfx.mix(tint(), 0xFF000000, 0.35f), 1f);
    }

    public static int card(float hover) {
        String s = style();
        if (s.equals("Liquid Glass")) return a(0xFFFFFFFF, 0.07f + 0.05f * hover);
        if (s.equals("Glass")) return a(0xFFFFFFFF, 0.045f + 0.04f * hover);
        if (s.equals("Frosted")) return a(0xFFFFFFFF, 0.035f + 0.035f * hover);
        if (s.equals("Transparent")) return a(Gfx.mix(tint(), 0xFF2A2E3A, 0.5f), 0.45f + 0.15f * hover);
        if (s.equals("Minimal")) return a(0xFFFFFFFF, 0.02f + 0.05f * hover);
        return a(Gfx.mix(Gfx.mix(tint(), 0xFF1E2230, 0.6f), 0xFF262A38, hover), 1f);
    }

    /** 0 = no outline. */
    public static int cardBorder(int accent) {
        String s = style();
        float b = border();
        if (s.equals("Liquid Glass")) return Gfx.alpha(0xFFFFFFFF, 0.16f * b);
        if (s.equals("Glass")) return Gfx.alpha(0xFFFFFFFF, 0.09f * b);
        if (s.equals("Frosted")) return Gfx.alpha(0xFFFFFFFF, 0.06f * b);
        if (s.equals("Minimal")) return Gfx.alpha(0xFFFFFFFF, 0.12f * b);
        if (s.equals("Neon")) return Gfx.alpha(accent, 0.35f * b);
        return 0;
    }

    public static int windowBorder(int accent) {
        String s = style();
        float b = border();
        if (s.equals("Liquid Glass")) return Gfx.alpha(0xFFFFFFFF, 0.30f * b);
        if (s.equals("Glass")) return Gfx.alpha(0xFFFFFFFF, 0.18f * b);
        if (s.equals("Frosted")) return Gfx.alpha(0xFFFFFFFF, 0.12f * b);
        if (s.equals("Neon")) return Gfx.alpha(accent, 0.9f * Math.min(1f, b));
        if (s.equals("Minimal")) return Gfx.alpha(0xFFFFFFFF, 0.10f * b);
        return Gfx.alpha(accent, 0.45f * Math.min(1f, b));
    }

    /** Glass parameters for HUD panels. */
    public static int hudTint() {
        return hudFollows() ? tint() : 0xFF0B0C11;
    }

    public static float hudBlurPx() {
        return 7f * (hudFollows() ? Math.max(0.3f, blurAmount()) : 1f);
    }
}
