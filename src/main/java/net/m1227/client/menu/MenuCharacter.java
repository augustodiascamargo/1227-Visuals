package net.m1227.client.menu;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.ArrayList;
import java.util.List;
import net.m1227.module.HaloModule;
import net.m1227.ui.Gfx;
import net.m1227.vfx.Puppet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
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
    private static ResourceLocation skin;
    private static boolean slim, requested;

    private float startT = -1f;
    private long last;
    private final Puppet.Pose pose = new Puppet.Pose();
    private final List<float[]> tail = new ArrayList<float[]>();
    private float headYaw, headPitch;

    private static final float WALK = 1.7f, TURN = 0.4f;

    /** Starts the skin download once per game session (falls back to the default skin). */
    private static void requestSkin() {
        if (requested) return;
        requested = true;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            GameProfile profile = mc.getSession().getProfile();
            skin = DefaultPlayerSkin.getDefaultSkin(profile.getId());
            slim = "slim".equals(DefaultPlayerSkin.getSkinType(profile.getId()));
            mc.getSkinManager().loadProfileTextures(profile, new SkinManager.SkinAvailableCallback() {
                @Override
                public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture tex) {
                    if (type != MinecraftProfileTexture.Type.SKIN) return;
                    skin = location;
                    String model = tex == null ? null : tex.getMetadata("model");
                    slim = "slim".equals(model);
                }
            }, false);
        } catch (Throwable t) {
            skin = DefaultPlayerSkin.getDefaultSkinLegacy();
            slim = false;
        }
    }

    /** Starts the walk (call when the menu becomes visible). */
    void start(boolean walkIn) {
        requestSkin();
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

        if (cos != null && cos.startsWith("Comet")) comet(x, feetY - size * (2.25f + pose.lift), size, dt, t);
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

    /** The comet in screen space: a bright core over the head and a tail streaming up and back. */
    private void comet(float hx, float hy, float size, float dt, float t) {
        int[] col = HaloModule.menuCometColors();
        float cx = hx + (float) Math.sin(t * 1.3) * size * 0.05f, cy = hy + (float) Math.cos(t * 1.1) * size * 0.04f;
        // emit
        int count = (int) (dt * 90f) + (Math.random() < dt * 90f % 1f ? 1 : 0);
        for (int i = 0; i < count; i++) {
            tail.add(new float[]{cx + (float) (Math.random() - 0.5) * size * 0.05f, cy, size * (0.18f + (float) Math.random() * 0.12f),
                    -size * (0.7f + (float) Math.random() * 0.4f), 0f});
        }
        for (int i = tail.size() - 1; i >= 0; i--) {
            float[] p = tail.get(i);
            p[4] += dt / 0.9f;
            if (p[4] >= 1f) {
                tail.remove(i);
                continue;
            }
            p[0] += p[2] * dt;
            p[1] += p[3] * dt;
            p[2] += (float) Math.sin(t * 2 + i) * size * 0.08f * dt; // a little turbulence
        }
        for (float[] p : tail) {
            float k = p[4];
            int c = Gfx.mix(0xFF000000 | col[0], 0xFF000000 | col[1], k);
            Gfx.glow(p[0], p[1], size * 0.16f * (1f - k * 0.8f), Gfx.alpha(c, 0.55f * (1f - k)));
        }
        Gfx.glow(cx, cy, size * 0.45f, Gfx.alpha(0xFF000000 | col[1], 0.45f));
        Gfx.glow(cx, cy, size * 0.25f, Gfx.alpha(0xFF000000 | col[0], 0.9f));
        Gfx.glow(cx, cy, size * 0.11f, 0xFFFFFFFF);
    }
}
