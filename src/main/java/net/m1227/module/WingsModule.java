package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Wings on your back (third person, and optionally on other players): Angel feathers,
 * Demon membrane, Butterfly, Energy and Crystal. Built from code and flapping gently;
 * faster and wider while you are in the air. Purely visual and only on your screen.
 */
public final class WingsModule extends Module {
    public static final String[] STYLES = {"Angel", "Demon", "Butterfly", "Energy", "Crystal"};
    private final Setting.Mode style;
    private final Setting.Col color1, color2;
    private final Setting.Num size, flap, opacity;
    private final Setting.Bool others;

    public WingsModule() {
        super("Wings", "Angel, demon, butterfly, energy or crystal wings.", Category.COSMETICS, false);
        style = add(new Setting.Mode("Style", "Angel", STYLES));
        color1 = add(new Setting.Col("Color", 0xFFFFFF));
        color2 = add(new Setting.Col("Tip color", 0xB8C8FF));
        size = add(new Setting.Num("Size", 1.0, 0.5, 1.8, 0.05, "x"));
        flap = add(new Setting.Num("Flap speed", 1.0, 0.0, 3.0, 0.1, "x"));
        opacity = add(new Setting.Num("Opacity", 0.95, 0.2, 1.0, 0.05, ""));
        others = add(new Setting.Bool("Show on other players", false));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        boolean any = false;
        if (mc.gameSettings.thirdPersonView != 0 && !mc.thePlayer.isInvisible()) {
            begin();
            any = true;
            draw(mc, mc.thePlayer, e.partialTicks);
        }
        if (others.on()) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer p = list.get(i);
                if (p == mc.thePlayer || p.isInvisible() || p.getDistanceSqToEntity(mc.thePlayer) > 64 * 64) continue;
                if (!any) {
                    begin();
                    any = true;
                }
                draw(mc, p, e.partialTicks);
            }
        }
        if (any) end();
    }

    private float lx, ly;

    private void begin() {
        lx = OpenGlHelper.lastBrightnessX;
        ly = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        boolean glow = style.is("Energy") || style.is("Crystal");
        if (glow) {
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        } else {
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        }
    }

    private void end() {
        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.popMatrix();
    }

    private void draw(Minecraft mc, EntityPlayer p, float pt) {
        double rx = mc.getRenderManager().viewerPosX, ry = mc.getRenderManager().viewerPosY, rz = mc.getRenderManager().viewerPosZ;
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - rx;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - ry;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - rz;
        float yaw = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * pt;
        double yr = Math.toRadians(yaw);
        // facing (fx, fz), back = -facing, side = to the player's left
        float fx = (float) -Math.sin(yr), fz = (float) Math.cos(yr);
        float bx = -fx, bz = -fz, sx = (float) Math.cos(yr), sz = (float) Math.sin(yr);
        float sc = size.f() * (p.height / 1.8f);
        boolean air = !p.onGround;
        double t = System.nanoTime() / 1e9;
        float sp = flap.f() * (air ? 2.2f : 1f);
        float open = (float) Math.toRadians((air ? 34f : 22f) + (air ? 26f : 12f) * (float) Math.sin(t * 2.4 * sp + p.getEntityId()));
        double ox = x + bx * 0.16, oy = y + (p.isSneaking() ? 1.05 : 1.3) * (p.height / 1.8f), oz = z + bz * 0.16;
        if (p.isSneaking()) {
            ox += bx * 0.12;
            oz += bz * 0.12;
        }
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            // outward direction: sideways, swept back by the flap angle
            float c = (float) Math.cos(open), s = (float) Math.sin(open);
            float ux = sx * sgn * c + bx * s, uz = sz * sgn * c + bz * s;
            wing(ox + sx * sgn * 0.06, oy, oz + sz * sgn * 0.06, ux, uz, sc);
        }
    }

    /** One wing in the plane spanned by (ux, 0, uz) outward and +Y up. */
    private void wing(double x, double y, double z, float ux, float uz, float sc) {
        String st = style.get();
        int c0 = color1.rgb(), c1 = color2.rgb();
        float a = opacity.f();
        if (st.equals("Angel")) {
            // layered feathers fanning from up-out to down-out, longest in the middle
            for (int layer = 0; layer < 2; layer++) {
                int n = 9;
                for (int i = 0; i < n; i++) {
                    float k = i / (float) (n - 1);
                    double th = Math.toRadians(70 - 115 * k);
                    float len = sc * (layer == 0 ? 0.95f : 0.6f) * (0.55f + 0.45f * (float) Math.sin(Math.PI * (0.25 + 0.75 * k)));
                    float wdt = sc * (layer == 0 ? 0.075f : 0.09f);
                    float root = sc * (0.05f + 0.25f * (float) Math.sin(Math.PI * k * 0.5));
                    feather(x, y, z, ux, uz, th, root, len, wdt, layer == 0 ? c0 : mix(c0, 0xFFFFFF, 0.4f), c1, a, layer * 0.01f);
                }
            }
        } else if (st.equals("Demon")) {
            // bony fingers with a membrane stretched between them
            int n = 5;
            double[] tx = new double[n], ty = new double[n];
            for (int i = 0; i < n; i++) {
                float k = i / (float) (n - 1);
                double th = Math.toRadians(65 - 110 * k);
                float len = sc * (1.05f - 0.35f * k);
                tx[i] = Math.cos(th) * len;
                ty[i] = Math.sin(th) * len;
            }
            int mem = mix(c0, 0x000000, 0.35f);
            GL11.glBegin(GL11.GL_TRIANGLES);
            for (int i = 0; i + 1 < n; i++) {
                vtx(x, y, z, ux, uz, 0, 0, mem, a * 0.9f);
                vtx(x, y, z, ux, uz, tx[i], ty[i], c1, a * 0.85f);
                // scalloped edge: midpoint pulled inwards
                double mx = (tx[i] + tx[i + 1]) * 0.36, my = (ty[i] + ty[i + 1]) * 0.36;
                vtx(x, y, z, ux, uz, mx, my, mem, a * 0.9f);
                vtx(x, y, z, ux, uz, 0, 0, mem, a * 0.9f);
                vtx(x, y, z, ux, uz, mx, my, mem, a * 0.9f);
                vtx(x, y, z, ux, uz, tx[i + 1], ty[i + 1], c1, a * 0.85f);
            }
            GL11.glEnd();
            for (int i = 0; i < n; i++) feather(x, y, z, ux, uz, Math.atan2(ty[i], tx[i]), 0f, (float) Math.hypot(tx[i], ty[i]), sc * 0.03f, 0x1A1014, 0x2A1A20, a, 0.004f);
        } else if (st.equals("Butterfly")) {
            lobe(x, y, z, ux, uz, Math.toRadians(40), sc * 0.95f, sc * 0.55f, c0, c1, a);
            lobe(x, y, z, ux, uz, Math.toRadians(-35), sc * 0.65f, sc * 0.4f, mix(c0, c1, 0.5f), c1, a);
        } else if (st.equals("Energy")) {
            int n = 7;
            double t = System.nanoTime() / 1e9;
            for (int i = 0; i < n; i++) {
                float k = i / (float) (n - 1);
                double th = Math.toRadians(65 - 110 * k);
                float pulse = 0.75f + 0.25f * (float) Math.sin(t * 3 + i);
                float len = sc * (0.6f + 0.45f * (float) Math.sin(Math.PI * (0.2 + 0.8 * k))) * pulse;
                feather(x, y, z, ux, uz, th, sc * 0.05f, len, sc * 0.05f, c0, c1, a * 0.8f, 0f);
                feather(x, y, z, ux, uz, th, sc * 0.05f, len * 1.05f, sc * 0.12f, c1, c1, a * 0.25f, 0f);
            }
        } else { // Crystal
            int n = 6;
            for (int i = 0; i < n; i++) {
                float k = i / (float) (n - 1);
                double th = Math.toRadians(60 - 100 * k);
                float len = sc * (0.5f + 0.5f * (float) Math.sin(Math.PI * (0.25 + 0.75 * k)));
                shard(x, y, z, ux, uz, th, sc * 0.1f, len, sc * 0.09f, c0, c1, a * 0.7f);
            }
        }
    }

    private static int mix(int a, int b, float t) {
        return net.m1227.vfx.ParticleSystem.mix(a, b, t);
    }

    /** Point (u along the outward direction, v up) in world space. */
    private static void vtx(double x, double y, double z, float ux, float uz, double u, double v, int c, float a) {
        GL11.glColor4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a);
        GL11.glVertex3d(x + ux * u, y + v, z + uz * u);
    }

    /** A tapered feather from root distance r0 out to r0+len at angle th, width w. */
    private static void feather(double x, double y, double z, float ux, float uz, double th, float r0, float len, float w,
                                int c0, int c1, float a, float lift) {
        double dx = Math.cos(th), dy = Math.sin(th), px = -dy, py = dx;
        double ax = dx * r0, ay = dy * r0 + lift, bx = dx * (r0 + len), by = dy * (r0 + len) + lift;
        GL11.glBegin(GL11.GL_TRIANGLES);
        vtx(x, y, z, ux, uz, ax + px * w * 0.5, ay + py * w * 0.5, c0, a);
        vtx(x, y, z, ux, uz, ax - px * w * 0.5, ay - py * w * 0.5, c0, a);
        vtx(x, y, z, ux, uz, bx * 0.7 + ax * 0.3 + px * w, by * 0.7 + ay * 0.3 + py * w, c1, a);
        vtx(x, y, z, ux, uz, ax - px * w * 0.5, ay - py * w * 0.5, c0, a);
        vtx(x, y, z, ux, uz, bx, by, c1, a * 0.9f);
        vtx(x, y, z, ux, uz, bx * 0.7 + ax * 0.3 + px * w, by * 0.7 + ay * 0.3 + py * w, c1, a);
        GL11.glEnd();
    }

    private static void lobe(double x, double y, double z, float ux, float uz, double th, float len, float wid, int c0, int c1, float a) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        vtx(x, y, z, ux, uz, 0, 0, c0, a);
        for (int i = 0; i <= 20; i++) {
            double q = Math.PI * i / 20.0;
            double lx = Math.sin(q) * len, ly = (Math.cos(q)) * wid * 0.5 * Math.sin(q) * 2;
            double rx = lx * Math.cos(th) - ly * Math.sin(th), ry = lx * Math.sin(th) + ly * Math.cos(th);
            vtx(x, y, z, ux, uz, rx, ry, c1, a * 0.9f);
        }
        GL11.glEnd();
    }

    private static void shard(double x, double y, double z, float ux, float uz, double th, float r0, float len, float w, int c0, int c1, float a) {
        double dx = Math.cos(th), dy = Math.sin(th), px = -dy, py = dx;
        double ax = dx * r0, ay = dy * r0, mx = dx * (r0 + len * 0.45), my = dy * (r0 + len * 0.45), bx = dx * (r0 + len), by = dy * (r0 + len);
        GL11.glBegin(GL11.GL_TRIANGLES);
        vtx(x, y, z, ux, uz, ax, ay, c0, a);
        vtx(x, y, z, ux, uz, mx + px * w, my + py * w, c1, a);
        vtx(x, y, z, ux, uz, bx, by, 0xFFFFFF, a);
        vtx(x, y, z, ux, uz, ax, ay, c0, a);
        vtx(x, y, z, ux, uz, mx - px * w, my - py * w, c0, a);
        vtx(x, y, z, ux, uz, bx, by, 0xFFFFFF, a);
        GL11.glEnd();
    }
}
