package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/** Position, facing direction and biome. */
public final class CoordsHud extends HudModule {
    private static final String[] DIR = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private final Setting.Bool facing, biome;

    public CoordsHud() {
        super("Coordinates", "XYZ, facing and biome.", 0.01, 0.08);
        facing = add(new Setting.Bool("Facing", true));
        biome = add(new Setting.Bool("Biome", false));
    }

    @Override
    public float width() {
        return 92;
    }

    @Override
    public float height() {
        return 6 + 10 * (3 + (biome.on() ? 1 : 0));
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity e = mc.getRenderViewEntity();
        panel(x, y, width(), height());
        double px = e == null ? 0 : e.posX, py = e == null ? 64 : e.getEntityBoundingBox().minY, pz = e == null ? 0 : e.posZ;
        int d = e == null ? 4 : MathHelper.floor_double(e.rotationYaw * 8.0 / 360.0 + 0.5) & 7;
        row(x, y + 4, "X", String.format(java.util.Locale.ROOT, "%.1f", px), facing.on() ? DIR[d] : null);
        row(x, y + 14, "Y", String.format(java.util.Locale.ROOT, "%.1f", py), null);
        row(x, y + 24, "Z", String.format(java.util.Locale.ROOT, "%.1f", pz), null);
        if (biome.on()) {
            String b = "-";
            if (mc.theWorld != null && e != null) b = mc.theWorld.getBiomeGenForCoords(new BlockPos(e)).biomeName;
            row(x, y + 34, "B", b, null);
        }
    }

    private void row(float x, float y, String l, String v, String extra) {
        Gfx.text(l, x + 5, y, LABEL);
        Gfx.text(v, x + 16, y, 0xFFFFFFFF);
        if (extra != null) Gfx.text(extra, x + width() - 5 - Gfx.textWidth(extra), y, value());
    }
}
