package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Reach Display: the distance of your last hit, from your eyes to the target's hitbox (measured
 * the way Minecraft measures reach). Information only.
 */
public final class ReachHud extends HudModule {
    private double last = -1;
    private long at;
    private final Setting.Bool fade;

    public ReachHud() {
        super("Reach Display", "Distance of your last hit.", 0.02, 0.62);
        fade = add(new Setting.Bool("Fade after 3 s", false));
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onAttack(AttackEntityEvent e) {
                Minecraft mc = Minecraft.getMinecraft();
                if (!isEnabled() || e.entityPlayer != mc.thePlayer || !(e.target instanceof EntityLivingBase)) return;
                Vec3 eye = mc.thePlayer.getPositionEyes(1f);
                EntityLivingBase t = (EntityLivingBase) e.target;
                float b = t.getCollisionBorderSize();
                AxisAlignedBB bb = t.getEntityBoundingBox().expand(b, b, b);
                double cx = Math.max(bb.minX, Math.min(eye.xCoord, bb.maxX)), cy = Math.max(bb.minY, Math.min(eye.yCoord, bb.maxY)),
                        cz = Math.max(bb.minZ, Math.min(eye.zCoord, bb.maxZ));
                last = Math.sqrt((cx - eye.xCoord) * (cx - eye.xCoord) + (cy - eye.yCoord) * (cy - eye.yCoord) + (cz - eye.zCoord) * (cz - eye.zCoord));
                at = System.currentTimeMillis();
            }
        });
    }

    @Override
    public float width() {
        return 64;
    }

    @Override
    public float height() {
        return 16;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        double v = editing && last < 0 ? 2.87 : last;
        if (!editing && fade.on() && System.currentTimeMillis() - at > 3000) v = -1;
        panel(x, y, width(), height());
        String s = v < 0 ? "-- m" : String.format("%.2f m", v);
        Gfx.text("Reach", x + 5, y + 4, LABEL);
        Gfx.text(s, x + width() - 5 - Gfx.textWidth(s), y + 4, 0xFFFFFFFF);
    }
}
