package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/**
 * Look of the 1227 menu (and, optionally, of HUD panels): Glass, Frosted,
 * Solid, Transparent, Minimal and Neon, each with its own tint, border,
 * blur and opacity. Read by ui.Theme every frame, so changes are live.
 */
public final class MenuStyleModule extends Module {
    public static MenuStyleModule INSTANCE;
    public static final String[] STYLES = {"Liquid Glass", "Glass", "Frosted", "Aurora", "Gradient", "Solid", "Midnight", "OLED",
            "Transparent", "Minimal", "Neon"};

    public final Setting.Mode style;
    public final Setting.Num opacity, blur, radius, dim, border;
    public final Setting.Col tint;
    public final Setting.Bool hudFollows, sheen, motion;

    private boolean ui() {
        return style.is("Glass") || style.is("Frosted") || style.is("Liquid Glass") || style.is("Aurora") || style.is("Gradient");
    }

    public MenuStyleModule() {
        super("Menu Style", "11 menu styles: liquid glass, aurora, gradient, midnight, OLED, neon and more.", Category.SETTINGS, false);
        INSTANCE = this;
        style = add(new Setting.Mode("Style", "Liquid Glass", STYLES));
        tint = add(new Setting.Col("Tint", 0x0E1018));
        opacity = add(new Setting.Num("Panel opacity", 1.0, 0.2, 1.5, 0.05, "x"));
        blur = add(new Setting.Num("Blur strength", 1.0, 0.0, 2.0, 0.1, "x"))
                .showIf(() -> ui());
        motion = add(new Setting.Bool("Animated background (Aurora, Gradient, Midnight)", true))
                .showIf(() -> style.is("Aurora") || style.is("Gradient") || style.is("Midnight"));
        border = add(new Setting.Num("Border", 1.0, 0.0, 2.0, 0.1, "x"));
        radius = add(new Setting.Num("Corner radius", 10, 2, 18, 1, "px"));
        dim = add(new Setting.Num("Background dim", 1.0, 0.0, 2.0, 0.1, "x"));
        sheen = add(new Setting.Bool("Light sheen on top edge", true));
        hudFollows = add(new Setting.Bool("HUD glass uses this style", true));
    }

    @Override
    public boolean toggleable() {
        return false;
    }
}
