package net.m1227.client.menu;

import net.m1227.module.HaloModule;
import net.m1227.ui.Gfx;
import net.m1227.vfx.Puppet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Your character on the main menu: your own skin (loaded by Minecraft's skin manager from your
 * signed-in profile), walking in from the right edge, turning to face you and then following the
 * mouse with its head while it breathes. If the Halo module is on with Comet or Thorn Crown, the
 * character wears it. Drawn from the skin alone (no fake world or entity), so other mods never see
 * it.
 */
final class MenuCharacter {

    private float startT = -1f;
    private long last;
    private final Puppet.Pose pose = new Puppet.Pose();
    private final net.m1227.client.ui.Comet2D comet = new net.m1227.client.ui.Comet2D();
    private float headYaw, headPitch;

    private static final float WALK = 1.7f, TURN = 0.4f;

    /** Starts the walk (call when the menu becomes visible). */
    void start(boolean walkIn) {
        net.m1227.client.ui.LocalSkin.request();
        startT = now() - (walkIn ? 0f : WALK + TURN);
    }

    boolean started() {
        return startT >= 0f;
    }

    private static float now() {
        return (System.nanoTime() / 1000000L) / 1000f;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Draws the character with its feet at (feetX, feetY); size = screen units per block. */
    void draw(float feetX, float feetY, float size, int mx, int my, int screenW) {
        ResourceLocation skin = net.m1227.client.ui.LocalSkin.skin();
        boolean slim = net.m1227.client.ui.LocalSkin.slim();
        if (startT < 0f || skin == null) return;
        long n = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (n - last) / 1e9f);
        last = n;
        float t = now() - startT;
        float walk = clamp(t / WALK);
        float eased = 1f - (1f - walk) * (1f - walk);
        float startX = screenW + size * 0.8f;
        float x = startX + (feetX - startX) * eased;

        // pose: walking in profile, then a turn to face the camera, then idle with the head following the mouse
        pose.reset();
        float bodyYaw;
        double phase = t * 7.5;
        if (t < WALK) {
            float stride = 1f - walk * walk * walk; // the steps slow down as it arrives
            float sw = (float) Math.sin(phase) * 32f * Math.max(0.25f, stride);
            pose.rLeg[0] = -sw;
            pose.lLeg[0] = sw;
            pose.rArm[0] = sw * 0.8f;
            pose.lArm[0] = -sw * 0.8f;
            pose.lift = Math.abs((float) Math.cos(phase)) * 0.035f * stride;
            bodyYaw = 90f;
        } else {
            float turn = clamp((t - WALK) / TURN);
            float k = turn * turn * (3f - 2f * turn);
            bodyYaw = 90f * (1f - k);
            float breathe = (float) Math.sin(t * 1.8);
            pose.rArm[2] = 3f + breathe * 2f;
            pose.lArm[2] = 3f + breathe * 2f;
            pose.body[0] = breathe * 0.8f;
            // head towards the mouse (positive yaw looks to screen-right, positive pitch looks up)
            float hx = x, hy = feetY - size * 1.65f;
            float wantYaw = (float) Math.toDegrees(Math.atan((mx - hx) / (size * 1.6f))) * 0.9f * k;
            float wantPitch = (float) -Math.toDegrees(Math.atan((my - hy) / (size * 1.6f))) * 0.6f * k;
            float e = 1f - (float) Math.exp(-10f * dt);
            headYaw += (wantYaw - headYaw) * e;
            headPitch += (wantPitch - headPitch) * e;
            pose.head[1] = headYaw;
            pose.head[0] = headPitch;
            pose.body[1] = headYaw * 0.25f;
        }

        // the model, in a small 3D space of its own
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, feetY, 200f);
        GlStateManager.scale(size, -size, size);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        Puppet.render(skin, slim, 0, 0, 0, bodyYaw, pose, 1f);
        String cos = HaloModule.menuStyle();
        if (cos != null && !cos.equals("Comet")) crown(t, bodyYaw - headYaw * (t > WALK ? 1f : 0f), pose.lift);
        // forget the model's depth so 2D drawn afterwards (comet, intro, tooltips) is never hidden by it
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);

        if (cos != null && cos.startsWith("Comet")) comet.draw(x, feetY - size * (2.25f + pose.lift), size, dt, t);
    }

    private void crown(float t, float yaw, float lift) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0f, 1.8f + lift, 0f);
        GlStateManager.rotate(-yaw, 0f, 1f, 0f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        HaloModule.thornGeometry(t, HaloModule.menuCrownColor(), 1f);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

}
