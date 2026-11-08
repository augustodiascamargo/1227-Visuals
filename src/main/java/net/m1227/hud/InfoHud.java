package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;

/** One compact pill per stat: FPS, ping, CPS and movement speed, in a row or column. */
public final class InfoHud extends HudModule {
    private final Setting.Bool fps, ping, cps, speed;
    private final Setting.Mode layout;
    private float w = 60, h = 14;

    public InfoHud() {
        super("Info Bar", "FPS, ping, CPS and speed.", 0.01, 0.01);
        fps = add(new Setting.Bool("FPS", true));
        ping = add(new Setting.Bool("Ping", true));
        cps = add(new Setting.Bool("CPS", true));
        speed = add(new Setting.Bool("Speed (blocks/s)", false));
        layout = add(new Setting.Mode("Layout", "Row", "Row", "Column"));
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return h;
    }

    public static int ping() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.getNetHandler() == null) return -1;
        if (mc.isSingleplayer()) return 0;
        NetworkPlayerInfo i = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return i == null ? -1 : i.getResponseTime();
    }

    public static double speed(EntityPlayer p) {
        double dx = p.posX - p.prevPosX, dz = p.posZ - p.prevPosZ;
        return Math.sqrt(dx * dx + dz * dz) * 20.0;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        String[] lbl = new String[4], val = new String[4];
        int n = 0;
        if (fps.on()) {
            lbl[n] = "FPS";
            val[n++] = String.valueOf(Minecraft.getDebugFPS());
        }
        if (ping.on()) {
            int p = ping();
            lbl[n] = "PING";
            val[n++] = p < 0 ? "-" : p + "ms";
        }
        if (cps.on()) {
            lbl[n] = "CPS";
            val[n++] = ClickCounter.cps(0) + " | " + ClickCounter.cps(1);
        }
        if (speed.on() && mc.thePlayer != null) {
            lbl[n] = "BPS";
            val[n++] = String.format(java.util.Locale.ROOT, "%.1f", speed(mc.thePlayer));
        }
        if (n == 0) {
            w = 30;
            h = 14;
            return;
        }
        boolean row = layout.is("Row");
        float cx = x, cy = y, maxW = 0;
        for (int i = 0; i < n; i++) {
            float pw = Gfx.textWidth(lbl[i]) + Gfx.textWidth(val[i]) + 14;
            panel(cx, cy, pw, 14);
            Gfx.text(lbl[i], cx + 5, cy + 3.5f, LABEL);
            Gfx.text(val[i], cx + 9 + Gfx.textWidth(lbl[i]), cy + 3.5f, value());
            maxW = Math.max(maxW, pw);
            if (row) cx += pw + 3;
            else cy += 17;
        }
        w = row ? cx - x - 3 : maxW;
        h = row ? 14 : cy - y - 3;
    }
}
