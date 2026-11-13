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

    public static HaloModule INSTANCE;

    public HaloModule() {
        super("Halo", "Halo, china hat, comet, thorn crown, orbiting orbs or badges, with animated materials.", Category.COSMETICS, false);
        style = add(new Setting.Mode("Style", "Angel Halo", "Angel Halo", "Star Crown", "Orbiting Orbs", "China Hat", "Comet", "Thorn Crown",
                "Comet + Crown", "Orbiting Badges"));
        fx = new FxSettings(this, 0xFFF4C2, 0xFFFFFF, 3, 1.0, 0.3, 0.35);
        height = add(new Setting.Num("Height above head", 0.28, 0.05, 0.8, 0.01, ""));
        material = add(new Setting.Mode("Material", "Gradient", net.m1227.vfx.MaterialShader.MATERIALS));
        shape = add(new Setting.Mode("China hat shape", "Cone", net.m1227.vfx.CosmeticMesh.HAT_SHAPES));
        rim = add(new Setting.Bool("Bright rim", true));
        glow = add(new Setting.Num("Edge glow", 0.5, 0.0, 1.0, 0.05, ""));
        cometColor = add(new Setting.Mode("Comet colors", "Blue", "Blue", "Red", "Violet", "Gold", "Custom"))
                .showIf(() -> style.get().startsWith("Comet"));
        tail = add(new Setting.Num("Comet tail length", 1.2, 0.4, 2.5, 0.1, "x")).showIf(() -> style.get().startsWith("Comet"));
        others = add(new Setting.Bool("Show on other players", false));
        INSTANCE = this;
    }

    private final Setting.Mode cometColor;
    private final Setting.Num tail;

    /** Core and tail colours of the comet. */
    private int[] cometColors() {
        String c = cometColor.get();
        if (c.equals("Red")) return new int[]{0xFF6A6A, 0xB0102A};
        if (c.equals("Violet")) return new int[]{0xD9A6FF, 0x5A2DD6};
        if (c.equals("Gold")) return new int[]{0xFFE7A0, 0xD9741A};
        if (c.equals("Custom")) return new int[]{fx.color1.rgb(), fx.color2.rgb()};
        return new int[]{0x8FE3FF, 0x1E4BFF};
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
        } else if (st.startsWith("Comet") || st.equals("Thorn Crown")) {
            float yawB = (float) Math.toRadians(p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * pt);
            float yawH = (float) Math.toRadians(p.prevRotationYawHead + (p.rotationYawHead - p.prevRotationYawHead) * pt);
            if (!st.equals("Comet")) thornCrown(cx, head + 0.1 + height.get() * 0.2, cz, yawH, (float) t, c0, alpha);
            if (st.startsWith("Comet")) comet(ps, p, cx, head + 0.5 + height.get() * 0.6 + bob, cz, yawB, t, size, alpha);
        } else if (st.equals("Orbiting Badges")) {
            int n = Math.max(1, Math.min(6, fx.amount.i()));
            for (int i = 0; i < n; i++) {
                double a = t * 1.4 * spd + i * (Math.PI * 2 / n);
                double r = p.width * 0.5 + 0.45;
                badge(cx + Math.cos(a) * r, cy + p.height * 0.52 + Math.sin(t * 1.9 + i) * 0.05, cz + Math.sin(a) * r,
                        0.13f * (0.6f + size), (float) Math.cos(t * 2.6 * spd + i * 1.3), ParticleSystem.mix(c0, c1, i / (float) n), alpha);
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

    /**
     * Comet: a bright core above and slightly behind the head with a long tail. The tail is made
     * of particles left in the world with a drift up and backwards, so it streams and swings as you
     * move and turn.
     */
    private void comet(ParticleSystem ps, EntityPlayer p, double cx, double y, double cz, float yaw, double t, float size, float alpha) {
        int[] col = cometColors();
        float bx = (float) Math.sin(yaw), bz = (float) -Math.cos(yaw); // behind the player
        double x = cx + bx * 0.18 + Math.sin(t * 1.3) * 0.05, z = cz + bz * 0.18 + Math.cos(t * 1.1) * 0.05;
        float k = 0.6f + size;
        AuraShapes.sprite(ps, Sprite.GLOW, x, y, z, 0.42f * k, 0f, false, 0xFFFFFF, alpha);
        AuraShapes.sprite(ps, Sprite.GLOW, x, y, z, 0.95f * k, 0f, false, col[0], alpha * 0.7f);
        AuraShapes.sprite(ps, Sprite.GLOW, x, y, z, 1.7f * k, 0f, false, col[1], alpha * 0.25f);
        if (ps.dt <= 0f) return;
        // per player: whole particles for this frame plus a random chance for the remainder
        float want = ps.dt * 110f * PerformanceModule.qualityScale();
        int count = (int) want + (Math.random() < want - (int) want ? 1 : 0);
        float len = tail.f();
        for (int c = 0; c < count; c++) {
            float jx = (float) (Math.random() - 0.5) * 0.06f, jy = (float) (Math.random() - 0.5) * 0.06f, jz = (float) (Math.random() - 0.5) * 0.06f;
            Particle q = ps.glow(x + jx, y + jy, z + jz, bx * 0.55f * len + jx * 3, 0.5f * len + jy * 2, bz * 0.55f * len + jz * 3,
                    0.55f * len, 0.36f * k, 0.03f, col[0], col[1], alpha * 0.85f);
            if (q != null) q.fadePow = 1.4f;
        }
    }

    /** Dark crown of thorns resting on the head; the tips catch the colour. */
    private void thornCrown(double x, double y, double z, float yaw, float t, int c0, float a) {
        Minecraft mc = Minecraft.getMinecraft();
        double vx = x - mc.getRenderManager().viewerPosX, vy = y - mc.getRenderManager().viewerPosY, vz = z - mc.getRenderManager().viewerPosZ;
        float lx = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX, ly = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(vx, vy, vz);
        GlStateManager.rotate((float) -Math.toDegrees(yaw), 0f, 1f, 0f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
        thornGeometry(t, c0, a);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.popMatrix();
    }

    /** The thorn crown around the origin (ring in the XZ plane); caller sets the GL state. */
    public static void thornGeometry(float t, int c0, float a) {
        float cr = ((c0 >> 16) & 255) / 255f, cg = ((c0 >> 8) & 255) / 255f, cb = (c0 & 255) / 255f;
        int n = 14;
        float r = 0.34f;
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n, am = (a0 + a1) / 2;
            float hgt = (i % 2 == 0 ? 0.24f : 0.15f) * (1f + 0.04f * (float) Math.sin(t * 2 + i));
            float out = 1.25f;
            GL11.glColor4f(0.07f, 0.07f, 0.09f, a);
            GL11.glVertex3d(Math.cos(a0) * r, 0, Math.sin(a0) * r);
            GL11.glVertex3d(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            GL11.glColor4f(cr * 0.8f, cg * 0.8f, cb * 0.8f, a);
            GL11.glVertex3d(Math.cos(am) * r * out, hgt, Math.sin(am) * r * out);
        }
        GL11.glEnd();
        // band and bright edges on the thorns
        GL11.glLineWidth(1.6f);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n, am = (a0 + a1) / 2;
            float hgt = i % 2 == 0 ? 0.24f : 0.15f;
            GL11.glColor4f(0.15f, 0.15f, 0.18f, a);
            GL11.glVertex3d(Math.cos(a0) * r, 0, Math.sin(a0) * r);
            GL11.glVertex3d(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            GL11.glColor4f(cr, cg, cb, a * 0.9f);
            GL11.glVertex3d(Math.cos(a0) * r, 0, Math.sin(a0) * r);
            GL11.glVertex3d(Math.cos(am) * r * 1.25, hgt, Math.sin(am) * r * 1.25);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
    }

    /**
     * What the main-menu character should wear from this module: "Comet", "Thorn Crown",
     * "Comet + Crown" or null when the Halo is off or another style is chosen.
     */
    public static String menuStyle() {
        HaloModule h = INSTANCE;
        if (h == null || !h.isEnabled()) return null;
        String s = h.style.get();
        return s.startsWith("Comet") || s.equals("Thorn Crown") ? s : null;
    }

    public static int[] menuCometColors() {
        return INSTANCE == null ? new int[]{0x8FE3FF, 0x1E4BFF} : INSTANCE.cometColors();
    }

    public static int menuCrownColor() {
        return INSTANCE == null ? 0xFFFFFF : INSTANCE.fx.color1.rgb();
    }

    private static final net.minecraft.util.ResourceLocation LOGO = new net.minecraft.util.ResourceLocation("m1227", "textures/logo.png");

    /** A spinning medal with the 1227 symbol, always facing the camera (flip = cos of its spin). */
    private void badge(double x, double y, double z, float s, float flip, int c, float a) {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
        float lx = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX, ly = net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x - rm.viewerPosX, y - rm.viewerPosY, z - rm.viewerPosZ);
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(Math.max(0.08f, Math.abs(flip)), 1f, 1f);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.disableTexture2D();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        // coin body and rim
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r * 0.35f, g * 0.35f, b * 0.35f, a);
        GL11.glVertex3f(0, 0, 0);
        for (int i = 0; i <= 28; i++) {
            double an = Math.PI * 2 * i / 28;
            GL11.glColor4f(r * 0.7f, g * 0.7f, b * 0.7f, a);
            GL11.glVertex3f((float) Math.cos(an) * s, (float) Math.sin(an) * s, 0);
        }
        GL11.glEnd();
        GL11.glLineWidth(2f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(Math.min(1f, r + 0.3f), Math.min(1f, g + 0.3f), Math.min(1f, b + 0.3f), a);
        for (int i = 0; i < 28; i++) {
            double an = Math.PI * 2 * i / 28;
            GL11.glVertex3f((float) Math.cos(an) * s, (float) Math.sin(an) * s, 0);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        // the 1227 symbol on the face
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(LOGO);
        GlStateManager.color(1f, 1f, 1f, a);
        float q = s * 0.78f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(-q, -q, -0.002f);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(q, -q, -0.002f);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(q, q, -0.002f);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(-q, q, -0.002f);
        GL11.glEnd();
        GlStateManager.enableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        net.minecraft.client.renderer.OpenGlHelper.setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.popMatrix();
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
