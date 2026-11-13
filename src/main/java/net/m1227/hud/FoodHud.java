package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FoodStats;

/**
 * Food and saturation: your hunger, the hidden saturation that is spent before hunger drops, and
 * (optionally) the exhaustion building up towards the next point. Food and saturation come from
 * the server; exhaustion is the client's own count, so on servers it is only an estimate.
 */
public final class FoodHud extends HudModule {
    private final Setting.Bool numbers, exhaustion;

    public FoodHud() {
        super("Saturation", "Hunger, hidden saturation and exhaustion.", 0.52, 0.86);
        numbers = add(new Setting.Bool("Numbers", true));
        exhaustion = add(new Setting.Bool("Exhaustion bar", false));
    }

    @Override
    public float width() {
        return 84;
    }

    @Override
    public float height() {
        return exhaustion.on() ? 30 : 24;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        float food = 17, sat = 6.4f, exh = 1.6f;
        if (mc.thePlayer != null) {
            FoodStats f = mc.thePlayer.getFoodStats();
            food = f.getFoodLevel();
            sat = f.getSaturationLevel();
            exh = exhaustionOf(f);
        }
        float w = width();
        panel(x, y, w, height());
        float bx = x + 5, bw = w - 10;
        Gfx.text("Food", bx, y + 3, LABEL);
        if (numbers.on()) {
            String v = (int) food + " / " + String.format(java.util.Locale.ROOT, "%.1f", sat);
            Gfx.text(v, x + w - 5 - Gfx.textWidth(v), y + 3, value());
        }
        float by = y + 14;
        Gfx.roundedRect(bx, by, bw, 4, 2, 0x50000000);
        Gfx.roundedRect(bx, by, bw * Math.min(1f, food / 20f), 4, 2, 0xFFE0A050);
        // saturation drawn on top of the food bar (it can never be higher than food)
        Gfx.roundedRect(bx, by + 1, bw * Math.min(1f, sat / 20f), 2, 1, 0xFFFFE08A);
        if (exhaustion.on()) {
            Gfx.roundedRect(bx, by + 8, bw, 2, 1, 0x50000000);
            Gfx.roundedRect(bx, by + 8, bw * Math.min(1f, exh / 4f), 2, 1, 0xFFB0B4C0);
        }
    }

    private static java.lang.reflect.Field exField;
    private static boolean exFailed;

    /** foodExhaustionLevel is private in 1.8.9: found once by type (the 2nd float field), 0 if not. */
    private static float exhaustionOf(FoodStats f) {
        if (exFailed) return 0f;
        try {
            if (exField == null) {
                int n = 0;
                for (java.lang.reflect.Field fl : FoodStats.class.getDeclaredFields()) {
                    if (fl.getType() == float.class && !java.lang.reflect.Modifier.isStatic(fl.getModifiers())) {
                        n++;
                        // order in 1.8.9: foodSaturationLevel, foodExhaustionLevel
                        if (n == 2) {
                            exField = fl;
                            exField.setAccessible(true);
                            break;
                        }
                    }
                }
                if (exField == null) {
                    exFailed = true;
                    return 0f;
                }
            }
            return exField.getFloat(f);
        } catch (Throwable t) {
            exFailed = true;
            return 0f;
        }
    }
}
