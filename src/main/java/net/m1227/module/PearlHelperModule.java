package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Pearl Helper: while you hold an ender pearl (optionally snowballs and eggs), draws where it
 * would fly if thrown now, using the same numbers as Minecraft 1.8.9's EntityThrowable:
 * spawn 0.1 below the eyes and 0.16 to the side, speed 1.5, gravity 0.03 per tick, drag 0.99
 * in air / 0.8 in water, and the first block it would hit. The real throw has a tiny random
 * spread, so the marker is the expected landing point. Purely visual: nothing is thrown or aimed.
 */
public final class PearlHelperModule extends Module {
    private final Setting.Col color, landColor;
    private final Setting.Num thickness, opacity;
    private final Setting.Bool snowballs, marker, info;

    public PearlHelperModule() {
        super("Pearl Helper", "Shows the ender pearl's flight path and landing spot.", Category.COMBAT, false);
        color = add(new Setting.Col("Path color", 0xB06CFF));
        landColor = add(new Setting.Col("Landing color", 0x5CFFF0));
        thickness = add(new Setting.Num("Line thickness", 2.5, 1.0, 6.0, 0.5, "px"));
        opacity = add(new Setting.Num("Opacity", 0.85, 0.2, 1.0, 0.05, ""));
        marker = add(new Setting.Bool("Landing marker", true));
        info = add(new Setting.Bool("Distance and flight time", true));
        snowballs = add(new Setting.Bool("Also snowballs and eggs", false));
    }

    private boolean holding(EntityPlayerSP p) {
        ItemStack s = p.getHeldItem();
        if (s == null) return false;
        Item i = s.getItem();
        return i == Items.ender_pearl || (snowballs.on() && (i == Items.snowball || i == Items.egg));
    }

    // simulated path (camera-relative points are computed at draw time)
    private final double[] px = new double[301], py = new double[301], pz = new double[301];
    private int count;
    private MovingObjectPosition hit;

    /** Runs the 1.8.9 throwable physics from the player's current aim. */
    private void simulate(Minecraft mc, EntityPlayerSP p, float pt) {
        float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;
        float pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt + p.getEyeHeight() - 0.1;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        float yr = yaw / 180f * (float) Math.PI, pr = pitch / 180f * (float) Math.PI;
        x -= MathHelper.cos(yr) * 0.16f;
        z -= MathHelper.sin(yr) * 0.16f;
        double mx = -MathHelper.sin(yr) * MathHelper.cos(pr) * 0.4f;
        double mz = MathHelper.cos(yr) * MathHelper.cos(pr) * 0.4f;
        double my = -MathHelper.sin(pr) * 0.4f;
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        mx = mx / len * 1.5;
        my = my / len * 1.5;
        mz = mz / len * 1.5;
        count = 0;
        hit = null;
        for (int tick = 0; tick < 300; tick++) {
            px[count] = x;
            py[count] = y;
            pz[count] = z;
            count++;
            Vec3 from = new Vec3(x, y, z), to = new Vec3(x + mx, y + my, z + mz);
            MovingObjectPosition m = mc.theWorld.rayTraceBlocks(from, to);
            if (m != null && m.hitVec != null) {
                px[count] = m.hitVec.xCoord;
                py[count] = m.hitVec.yCoord;
                pz[count] = m.hitVec.zCoord;
                count++;
                hit = m;
                return;
            }
            x += mx;
            y += my;
            z += mz;
            if (y < -64) return;
            boolean water = mc.theWorld.getBlockState(new net.minecraft.util.BlockPos(x, y, z)).getBlock().getMaterial()
                    == net.minecraft.block.material.Material.water;
            double drag = water ? 0.8 : 0.99;
            mx *= drag;
            my *= drag;
            mz *= drag;
            my -= 0.03;
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null || !holding(p)) return;
        simulate(mc, p, e.partialTicks);
        if (count < 2) return;
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableCull();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(thickness.f());
        int c = color.rgb();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f, a = opacity.f();
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 1; i < count; i++) { // skip the first point: it starts inside your view
            float fade = Math.min(1f, i / 4f);
            GL11.glColor4f(r, g, b, a * fade);
            GL11.glVertex3d(px[i] - vx, py[i] - vy, pz[i] - vz);
        }
        GL11.glEnd();
        if (marker.on() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) landing(hit, vx, vy, vz, a);
        GL11.glLineWidth(1f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
        if (info.on() && hit != null) label(mc, p, e.partialTicks);
    }

    /** Ring + filled disc lying on the face that would be hit. */
    private void landing(MovingObjectPosition m, double vx, double vy, double vz, float a) {
        EnumFacing f = m.sideHit;
        double cx = m.hitVec.xCoord - vx, cy = m.hitVec.yCoord - vy, cz = m.hitVec.zCoord - vz;
        // two axes spanning the face
        double[] u, v;
        if (f == EnumFacing.UP || f == EnumFacing.DOWN) {
            u = new double[]{1, 0, 0};
            v = new double[]{0, 0, 1};
        } else if (f == EnumFacing.NORTH || f == EnumFacing.SOUTH) {
            u = new double[]{1, 0, 0};
            v = new double[]{0, 1, 0};
        } else {
            u = new double[]{0, 0, 1};
            v = new double[]{0, 1, 0};
        }
        double off = 0.01;
        cx += f.getFrontOffsetX() * off;
        cy += f.getFrontOffsetY() * off;
        cz += f.getFrontOffsetZ() * off;
        int c = landColor.rgb();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        double pulse = 0.4 + 0.08 * Math.sin(System.nanoTime() / 1e9 * 5.0);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, a * 0.35f);
        GL11.glVertex3d(cx, cy, cz);
        GL11.glColor4f(r, g, b, a * 0.05f);
        for (int i = 0; i <= 32; i++) {
            double t = Math.PI * 2 * i / 32;
            double ox = Math.cos(t) * pulse, oy = Math.sin(t) * pulse;
            GL11.glVertex3d(cx + u[0] * ox + v[0] * oy, cy + u[1] * ox + v[1] * oy, cz + u[2] * ox + v[2] * oy);
        }
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, a);
        for (int i = 0; i < 32; i++) {
            double t = Math.PI * 2 * i / 32;
            double ox = Math.cos(t) * pulse, oy = Math.sin(t) * pulse;
            GL11.glVertex3d(cx + u[0] * ox + v[0] * oy, cy + u[1] * ox + v[1] * oy, cz + u[2] * ox + v[2] * oy);
        }
        GL11.glEnd();
    }

    /** "18.4 m · 1.2 s" floating above the landing point, always facing you. */
    private void label(Minecraft mc, EntityPlayerSP p, float pt) {
        double hx = hit.hitVec.xCoord, hy = hit.hitVec.yCoord, hz = hit.hitVec.zCoord;
        double dist = Math.sqrt(p.getDistanceSq(hx, hy, hz));
        String s = String.format(java.util.Locale.ROOT, "%.1f m  ·  %.1f s", dist, (count - 1) / 20f);
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.translate(hx - vx, hy - vy + 0.6, hz - vz);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1f, 0f, 0f);
        float sc = (float) Math.max(0.02, dist * 0.0022);
        GlStateManager.scale(-sc, -sc, sc);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        int w = mc.fontRendererObj.getStringWidth(s);
        mc.fontRendererObj.drawStringWithShadow(s, -w / 2f, 0, 0xFFFFFFFF);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
