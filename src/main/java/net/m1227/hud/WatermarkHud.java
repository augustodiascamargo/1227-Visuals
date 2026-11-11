package net.m1227.hud;

import java.text.SimpleDateFormat;
import java.util.Date;
import net.m1227.Mod1227;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;

/** The 1227 mark on screen, in a few quiet styles, optionally with FPS / time / server. */
public final class WatermarkHud extends HudModule {
    private final Setting.Mode style;
    private final Setting.Bool fps, time, server, version, logo;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm");
    private float w = 60, h = 16;

    public WatermarkHud() {
        super("Watermark", "The 1227 mark with optional info.", 0.01, 0.01);
        style = add(new Setting.Mode("Style", "Pill", "Pill", "Minimal", "Card", "Bracket", "Gradient bar"));
        fps = add(new Setting.Bool("FPS", true));
        time = add(new Setting.Bool("Clock", true));
        server = add(new Setting.Bool("Server", false));
        version = add(new Setting.Bool("Version", false));
        logo = add(new Setting.Bool("1227 symbol", true));
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return h;
    }

    private String info() {
        Minecraft mc = Minecraft.getMinecraft();
        StringBuilder sb = new StringBuilder();
        if (version.on()) sb.append("v").append(Mod1227.VERSION);
        if (fps.on()) sep(sb).append(Minecraft.getDebugFPS()).append(" fps");
        if (server.on()) sep(sb).append(mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "singleplayer");
        if (time.on()) sep(sb).append(clock.format(new Date()));
        return sb.toString();
    }

    private static StringBuilder sep(StringBuilder sb) {
        if (sb.length() > 0) sb.append("  ·  ");
        return sb;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        String in = info();
        String st = style.get();
        int ac = accent();
        float tw = Gfx.textWidth("1227") * 1.25f;
        float iw = in.isEmpty() ? 0 : Gfx.textWidth(in) + 10;
        double t = System.currentTimeMillis() / 1000.0;
        if (st.equals("Minimal")) {
            w = tw + iw + 4;
            h = 12;
            Gfx.textScaled("1227", x, y, 1.25f, 0xFFFFFFFF);
            Gfx.rect(x, y + 11, 8, 1, ac);
            if (!in.isEmpty()) Gfx.text(in, x + tw + 8, y + 2, LABEL);
        } else if (st.equals("Card")) {
            w = Math.max(tw + 16, Gfx.textWidth(in) + 16);
            h = in.isEmpty() ? 20 : 30;
            panel(x, y, w, h);
            Gfx.rect(x + 6, y + 4, 2, 12, ac);
            Gfx.textScaled("1227", x + 11, y + 5, 1.25f, 0xFFFFFFFF);
            if (!in.isEmpty()) Gfx.text(in, x + 8, y + 19, LABEL);
        } else if (st.equals("Bracket")) {
            w = tw + iw + 16;
            h = 16;
            panel(x, y, w, h);
            Gfx.text("[", x + 4, y + 4, ac);
            Gfx.textScaled("1227", x + 9, y + 3, 1.25f, 0xFFFFFFFF);
            Gfx.text("]", x + 10 + tw, y + 4, ac);
            if (!in.isEmpty()) Gfx.text(in, x + tw + 20, y + 4, LABEL);
        } else if (st.equals("Gradient bar")) {
            w = tw + iw + 16;
            h = 17;
            panel(x, y, w, h);
            float p = (float) (t * 0.25 % 1.0);
            for (int i = 0; i < 24; i++) {
                float f = i / 24f;
                int c = Gfx.mix(ac, 0xFFFFFFFF, 0.5f + 0.5f * (float) Math.sin((f + p) * Math.PI * 2));
                Gfx.rect(x + 3 + (w - 6) * f, y, (w - 6) / 24f + 0.5f, 1.5f, c);
            }
            Gfx.textScaled("1227", x + 6, y + 5, 1.25f, 0xFFFFFFFF);
            if (!in.isEmpty()) Gfx.text(in, x + tw + 14, y + 5.5f, LABEL);
        } else { // Pill
            float lo = logo.on() ? 10f : 0f;
            w = tw + iw + 18 + lo;
            h = 16;
            panel(x, y, w, h);
            if (logo.on()) Gfx.logo(x + 2.5f, y + 0.5f, 15f, ac, 0.6f + 0.4f * (float) Math.sin(t * 2));
            else Gfx.circle(x + 7, y + 8, 2.2f, Gfx.alpha(ac, 0.75f + 0.25f * (float) Math.sin(t * 3)));
            Gfx.textScaled("1227", x + 12 + lo, y + 3.5f, 1.25f, 0xFFFFFFFF);
            if (!in.isEmpty()) {
                Gfx.rect(x + tw + 16 + lo, y + 4, 1, 8, Gfx.alpha(0xFFFFFFFF, 0.2f));
                Gfx.text(in, x + tw + 21 + lo, y + 4.5f, LABEL);
            }
        }
    }
}
