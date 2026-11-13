package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.AuraShapes;
import net.m1227.vfx.CosmeticMesh;
import net.m1227.vfx.MaterialShader;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Cosmetics above the head or around the body: halo, crown of stars, orbiting orbs. */
public final class HaloModule extends Module {
    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Num height;
    private final Setting.Bool others, rim;
    private final Setting.Mode material, shape;
    private final Setting.Num glow;
    private float trailAcc;

    public HaloModule() {
        super("Halo", "Halo, crown of stars, orbiting orbs or a china hat, with animated materials.", Category.COSMETICS, false);
        style = add(new Setting.Mode("Style", "Angel Halo", "Angel Halo", "Star Crown", "Orbiting Orbs", "China Hat"));
        fx = new FxSettings(this, 0xFFF4C2, 0xFFFFFF, 3, 1.0, 0.3, 0.35);
        height = add(new Setting.Num("Height above head", 0.28, 0.05, 0.8, 0.01, ""));
        material = add(new Setting.Mode("Material", "Gradient", net.m1227.vfx.MaterialShader.MATERIALS));
        shape = add(new Setting.Mode("China hat shape", "Cone", net.m1227.vfx.CosmeticMesh.HAT_SHAPES));
        rim = add(new Setting.Bool("Bright rim", true));
        glow = add(new Setting.Num("Edge glow", 0.5, 0.0, 1.0, 0.05, ""));
        others = add(new Setting.Bool("Show on other players", false));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.gameSettings.thirdPersonView != 0) draw(mc.thePlayer, e.partialTicks);
        if (others.on()) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer p = list.get(i);
                if (p != mc.thePlayer && !p.isInvisible()) draw(p, e.partialTicks);
            }
        }
    }

    private void draw(EntityPlayer p, float pt) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        double cx = ParticleSystem.lerp(p.lastTickPosX, p.posX, pt);
        double cy = ParticleSystem.lerp(p.lastTickPosY, p.posY, pt);
        double cz = ParticleSystem.lerp(p.lastTickPosZ, p.posZ, pt);
        if (!ps.inRange(cx, cy, cz)) return;
        double t = ps.time;
        float spd = fx.speed.f(), size = fx.size.f(), alpha = fx.alpha();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        double head = cy + p.height - (p.isSneaking() ? 0.2 : 0.0);
        double bob = Math.sin(t * 2.2 * spd) * 0.03;
        String st = style.get();

        if (st.equals("Angel Halo") && net.m1227.vfx.MaterialShader.index(material) > 0 && net.m1227.vfx.MaterialShader.available()) {
            double y = head + height.get() + bob;
            AuraShapes.sprite(ps, Sprite.GLOW, cx, y, cz, size * 1.35f, 0f, true, c0, alpha * 0.25f);
            float yawH = (float) Math.toRadians(p.prevRotationYawHead + (p.rotationYawHead - p.prevRotationYawHead) * pt);
            meshAt(cx, y, cz, yawH, 0f, (float) t, c0, c1, alpha, false, size);
        } else if (st.equals("Angel Halo")) {
            double y = head + height.get() + bob;
            AuraShapes.sprite(ps, Sprite.GLOW, cx, y, cz, size * 1.35f, 0f, true, c0, alpha * 0.35f);
            AuraShapes.sprite(ps, Sprite.RING, cx, y, cz, size, (float) (t * 0.6 * spd), true, c0, alpha);
            AuraShapes.sprite(ps, Sprite.RING, cx, y + 0.002, cz, size * 0.96f, 0f, true, c1, alpha * 0.6f);
        } else if (st.equals("China Hat")) {
            // flat cone resting on the head, tilting with the head's pitch, colours sweeping round
            float yawH = (float) Math.toRadians(p.prevRotationYawHead + (p.rotationYawHead - p.prevRotationYawHead) * pt);
            float pitch = (float) Math.toRadians(p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt);
            meshAt(cx, head + 0.02 + height.get() * 0.15, cz, yawH, pitch * 0.35f, (float) (t * spd), c0, c1, alpha, true, size * 2f);
        } else if (st.equals("Star Crown")) {
            int n = Math.max(3, Math.min(12, fx.amount.i() + 3));
            double y = head + height.get() * 0.6 + bob;
            for (int i = 0; i < n; i++) {
                double a = t * 1.2 * spd + i * (Math.PI * 2 / n);
                float tw = (float) (0.6 + 0.4 * Math.sin(t * 4 + i));
                AuraShapes.sprite(ps, Sprite.STAR, cx + Math.cos(a) * size * 1.1, y + Math.sin(t * 3 + i) * 0.03,
                        cz + Math.sin(a) * size * 1.1, size * 0.35f * tw, (float) (t + i), false,
                        ParticleSystem.mix(c0, c1, i / (float) n), alpha * tw);
            }
        } else {
            int n = Math.max(1, Math.min(6, fx.amount.i()));
            trailAcc += ps.dt * 40f * PerformanceModule.qualityScale();
            boolean emit = trailAcc >= 1f;
            if (emit) trailAcc = 0f;
            for (int i = 0; i < n; i++) {
                double a = t * 2.4 * spd + i * (Math.PI * 2 / n);
                double r = p.width * 0.5 + 0.5;
                double ox = cx + Math.cos(a) * r, oz = cz + Math.sin(a) * r;
                double oy = cy + p.height * (0.55 + 0.25 * Math.sin(t * 1.7 * spd + i * 2.1));
                int c = ParticleSystem.mix(c0, c1, i / (float) n);
                AuraShapes.sprite(ps, Sprite.GLOW, ox, oy, oz, size * 0.45f, 0f, false, 0xFFFFFF, alpha);
                AuraShapes.sprite(ps, Sprite.GLOW, ox, oy, oz, size * 0.9f, 0f, false, c, alpha * 0.6f);
                if (emit && ps.dt > 0f) {
                    Particle tr = ps.glow(ox, oy, oz, 0f, 0f, 0f, fx.duration.f(), size * 0.4f, 0f, c, c1, alpha * 0.7f);
                    if (tr != null) tr.fadePow = fx.fade.f();
                }
            }
        }
    }

    /** China hat (hat = true, radius r) or material halo ring, drawn with the chosen material. */
    private void meshAt(double x, double y, double z, float yaw, float tilt, float t, int c0, int c1, float a, boolean hat, float r) {
        Minecraft mc = Minecraft.getMinecraft();
        double vx = x - mc.getRenderManager().viewerPosX, vy = y - mc.getRenderManager().viewerPosY, vz = z - mc.getRenderManager().viewerPosZ;
        float lx = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX, ly = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(vx, vy, vz);
        GlStateManager.rotate((float) -Math.toDegrees(yaw), 0f, 1f, 0f);
        GlStateManager.rotate((float) Math.toDegrees(tilt), 1f, 0f, 0f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
        int mat = MaterialShader.index(material);
        boolean shaded = MaterialShader.begin(mat, ParticleSystem.INSTANCE.time * fx.speed.f(), c0, c1, hat ? 4f : 6f, glow.f(), hat && rim.on() ? 0.12f : 0f);
        if (hat) {
            int sh = 0;
            for (int i = 0; i < CosmeticMesh.HAT_SHAPES.length; i++) if (CosmeticMesh.HAT_SHAPES[i].equals(shape.get())) sh = i;
            CosmeticMesh.hat(sh, r, r * 0.42f, t, shaded, c0, c1, a);
        } else {
            CosmeticMesh.torus(r * 0.3f, r * 0.028f, shaded, c0, c1, a);
        }
        if (shaded) MaterialShader.end();
        if (hat && rim.on() && !shaded) {
            // bright rim for the plain gradient
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 64; i++) {
                double ang = Math.PI * 2 * i / 64;
                int c = ParticleSystem.mix(c0, c1, 0.5f + 0.5f * (float) Math.sin(ang + t * 2.0));
                GL11.glColor4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a * 0.9f);
                GL11.glVertex3f((float) Math.cos(ang) * r, 0f, (float) Math.sin(ang) * r); // every shape ends at y = 0 on the rim
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);
        }
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.popMatrix();
    }
}
