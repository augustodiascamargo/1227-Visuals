package net.m1227.hud;

import java.text.SimpleDateFormat;
import java.util.Date;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;

/** Real-world clock, optional date, session time, and the in-game day and time of day. */
public final class ClockHud extends HudModule {
    private static final long START = System.currentTimeMillis();
    private final Setting.Mode format;
    private final Setting.Bool seconds, date, session, gameDay;

    public ClockHud() {
        super("Clock", "Local time, date and session time.", 0.86, 0.01);
        format = add(new Setting.Mode("Format", "24h", "24h", "12h"));
        seconds = add(new Setting.Bool("Seconds", false));
        date = add(new Setting.Bool("Date", false));
        session = add(new Setting.Bool("Session time", true));
        gameDay = add(new Setting.Bool("In-game day and time", false));
    }

    private int rows() {
        return 1 + (date.on() ? 1 : 0) + (session.on() ? 1 : 0) + (gameDay.on() ? 1 : 0);
    }

    @Override
    public float width() {
        return 74;
    }

    @Override
    public float height() {
        return 6 + 10 * rows();
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        panel(x, y, width(), height());
        Date now = new Date();
        String pat = (format.is("12h") ? "h:mm" : "HH:mm") + (seconds.on() ? ":ss" : "") + (format.is("12h") ? " a" : "");
        float ry = y + 4;
        row(x, ry, "Time", new SimpleDateFormat(pat).format(now));
        if (date.on()) {
            ry += 10;
            row(x, ry, "Date", new SimpleDateFormat("dd/MM").format(now));
        }
        if (session.on()) {
            ry += 10;
            long s = (System.currentTimeMillis() - START) / 1000L;
            row(x, ry, "Session", String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
        }
        if (gameDay.on()) {
            ry += 10;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            // the world clock as shown (the World module can change the time of day you see); day 1 = tick 0
            long t = mc.theWorld == null ? 0 : mc.theWorld.getWorldTime();
            long day = t / 24000L + 1;
            int tod = (int) ((t + 6000L) % 24000L); // tick 0 = 06:00
            row(x, ry, "Day " + day, String.format(java.util.Locale.ROOT, "%02d:%02d", tod / 1000, (tod % 1000) * 60 / 1000));
        }
    }

    private void row(float x, float y, String l, String v) {
        Gfx.text(l, x + 5, y, LABEL);
        Gfx.text(v, x + width() - 5 - Gfx.textWidth(v), y, value());
    }
}
