package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/** Horizontal compass strip: cardinal and intercardinal marks scroll with your view. */
public final class CompassHud extends HudModule {
    private static final String[] MARKS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private final Setting.Num span;
    private final Setting.Bool degrees;

    public CompassHud() {
        super("Compass", "Scrolling compass with your heading.", 0.42, 0.01);
        span = add(new Setting.Num("Field of view", 180, 90, 300, 10, "°"));
        degrees = add(new Setting.Bool("Show degrees", true));
    }

    @Override
    public float width() {
        return 160;
    }

    @Override
    public float height() {
        return degrees.on() ? 26 : 18;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        panel(x, y, width(), height());
        Entity e = Minecraft.getMinecraft().getRenderViewEntity();
        float yaw = e == null ? 0f : e.rotationYaw;
        yaw = ((yaw % 360f) + 360f) % 360f;
        float half = span.f() / 2f, cx = x + width() / 2f, pxPerDeg = (width() - 12) / span.f();
        Gfx.scissorOn(x + 2, y, width() - 4, height());
        for (int d = 0; d < 360; d += 15) {
            float diff = d - yaw;
            if (diff > 180) diff -= 360;
            if (diff < -180) diff += 360;
            if (Math.abs(diff) > half) continue;
            float px = cx + diff * pxPerDeg;
            float fade = 1f - Math.abs(diff) / half * 0.7f;
            if (d % 45 == 0) {
                String m = MARKS[d / 45];
                boolean north = m.equals("N");
                Gfx.text(m, px - Gfx.textWidth(m) / 2f, y + 5, Gfx.alpha(north ? 0xFFFF6B6B : (m.length() == 1 ? 0xFFFFFFFF : LABEL), fade));
            } else {
                Gfx.rect(px - 0.5f, y + 7, 1, 4, Gfx.alpha(0xFFFFFFFF, 0.35f * fade));
            }
        }
        Gfx.scissorOff();
        Gfx.rect(cx - 0.5f, y + 1, 1, 3, value());
        if (degrees.on()) {
            String s = Math.round(yaw) % 360 + "°";
            Gfx.text(s, cx - Gfx.textWidth(s) / 2f, y + 15, value());
        }
    }
}
