package net.m1227.module;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.render.ScreenOffset;
import net.m1227.render.Silhouette;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

/**
 * Player Glow: an outline only on the edges of the player as the camera sees them, with an
 * optional soft glow outside it, plus body materials (glass, ghost, hologram, shadow, tint,
 * rainbow) for the chosen players.
 *
 * How the outline stays on the edge: the player is drawn again as a flat-colour silhouette,
 * several times, each copy moved a few screen pixels in a different direction and pushed a
 * little deeper. The real body (already in the depth buffer) hides every copy where the body
 * is, so only the ring around the silhouette is left: never colour over the middle of the body,
 * the same thickness at every distance, and depth-tested (nothing through walls).
 *
 * Body materials replace how the chosen players are drawn: a depth pre-pass (so only the front
 * surface shows), then the skin tinted and see-through. All of it is only on your screen.
 */
public final class PlayerGlowModule extends Module {
    private final Setting.Mode who, colorMode, style, body;
    private final Setting.Col color, tint;
    private final Setting.Num intensity, pulse, thickness, glowSize, bodyAlpha;
    private final Setting.Bool bodyOutline;
    private final List<EntityLivingBase> drawn = new ArrayList<EntityLivingBase>();
    private boolean rendering;
    private Method renderName;
    private boolean nameFailed;

    public PlayerGlowModule() {
        super("Player Glow", "Edge outline and glow around players, and body materials like glass.", Category.AURAS, false);
        style = add(new Setting.Mode("Style", "Outline + Glow", "Outline + Glow", "Outline", "Glow", "Off"));
        who = add(new Setting.Mode("Players", "Target", "Target", "Self (3rd person)", "Target and self", "Everyone"));
        colorMode = add(new Setting.Mode("Color", "Team color", "Team color", "Custom", "Health", "Rainbow"));
        color = add(new Setting.Col("Custom color", 0x8B5CFF));
        thickness = add(new Setting.Num("Outline thickness", 2, 1, 6, 0.5, "px")).showIf(() -> style.get().contains("Outline"));
        glowSize = add(new Setting.Num("Glow size", 6, 2, 14, 1, "px")).showIf(() -> style.get().contains("Glow"));
        intensity = add(new Setting.Num("Intensity", 0.8, 0.1, 1.0, 0.05, ""));
        pulse = add(new Setting.Num("Pulse", 0.2, 0.0, 1.0, 0.05, ""));
        body = add(new Setting.Mode("Body", "Normal", "Normal", "Glass", "Ghost", "Hologram", "Shadow", "Tint", "Rainbow"));
        tint = add(new Setting.Col("Body color", 0x9FD8FF)).showIf(() -> body.is("Glass") || body.is("Tint") || body.is("Hologram"));
        bodyAlpha = add(new Setting.Num("Body opacity", 0.35, 0.05, 1.0, 0.05, "")).showIf(() -> !body.is("Normal"));
        bodyOutline = add(new Setting.Bool("Bright edges on glass", true)).showIf(() -> body.is("Glass"));
    }

    // ------------------------------------------------------------------ who

    private boolean applies(Minecraft mc, Entity e) {
        if (!(e instanceof EntityLivingBase) || e.isInvisible()) return false;
        String w = who.get();
        if (e == mc.thePlayer) {
            return mc.gameSettings.thirdPersonView != 0 && (w.equals("Self (3rd person)") || w.equals("Target and self") || w.equals("Everyone"));
        }
        if (w.equals("Everyone")) return e instanceof EntityPlayer;
        if (w.equals("Self (3rd person)")) return false;
        return e == CombatTracker.target(5000L);
    }

    private int colorFor(Minecraft mc, EntityLivingBase e, double time) {
        String m = colorMode.get();
        if (m.equals("Custom")) return color.rgb();
        if (m.equals("Rainbow")) return ParticleSystem.rainbow((float) (time * 0.12));
        if (m.equals("Health")) {
            float f = e.getHealth() / Math.max(1f, e.getMaxHealth());
            return f > 0.5f ? ParticleSystem.mix(0xFFC857, 0x3DDC84, (f - 0.5f) * 2f) : ParticleSystem.mix(0xFF4D5E, 0xFFC857, f * 2f);
        }
        String name = e.getDisplayName().getFormattedText();
        for (int i = 0; i + 1 < name.length(); i++) {
            if (name.charAt(i) == '§') {
                char c = Character.toLowerCase(name.charAt(i + 1));
                if ("0123456789abcdef".indexOf(c) >= 0) return mc.fontRendererObj.getColorCode(c) & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
    }

    // ------------------------------------------------------------------ body materials

    /** Players with a body material are not drawn by Minecraft; we draw them in onRender. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPre(RenderLivingEvent.Pre<EntityLivingBase> e) {
        if (rendering || Silhouette.active() || body.is("Normal")) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!applies(mc, e.entity)) return;
        e.setCanceled(true);
        if (!drawn.contains(e.entity)) drawn.add(e.entity);
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            drawn.clear();
            return;
        }
        float pt = e.partialTicks;
        double t = ParticleSystem.INSTANCE.time;
        // 1. bodies with a material (the ones Minecraft skipped this frame)
        for (EntityLivingBase b : drawn) {
            if (!b.isDead) drawBody(mc, b, pt, t);
        }
        drawn.clear();
        // 2. outline and glow
        if (style.is("Off")) return;
        List<EntityLivingBase> list = new ArrayList<EntityLivingBase>();
        if (applies(mc, mc.thePlayer)) list.add(mc.thePlayer);
        if (who.is("Everyone")) {
            for (EntityPlayer p : mc.theWorld.playerEntities) {
                if (p != mc.thePlayer && applies(mc, p)) list.add(p);
            }
        } else {
            EntityLivingBase tg = CombatTracker.target(5000L);
            if (tg != null && tg != mc.thePlayer && applies(mc, tg)) list.add(tg);
        }
        for (EntityLivingBase p : list) {
            if (mc.thePlayer.getDistanceToEntity(p) > 64) continue;
            float a = intensity.f() * (1f - pulse.f() * 0.4f * (1f + (float) Math.sin(t * 3)));
            edge(mc, p, pt, colorFor(mc, p, t), a, style.get().contains("Outline") ? thickness.f() : 0f,
                    style.get().contains("Glow") ? glowSize.f() : 0f);
        }
    }

    /**
     * Ring around the silhouette as the camera sees it: copies moved by a few pixels, behind the
     * body. outlinePx = solid ring width (0 = none), glowPx = soft fading ring outside it.
     */
    private void edge(Minecraft mc, EntityLivingBase e, float pt, int rgb, float alpha, float outlinePx, float glowPx) {
        double d = Math.sqrt(e.getDistanceSqToEntity(mc.getRenderViewEntity()));
        float z = ScreenOffset.behind(d);
        Silhouette.entityState(true);
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        ScreenOffset.begin();
        rendering = true;
        try {
            if (glowPx > 0f) {
                // soft glow: rings from the outside in, faint and additive
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                int rings = 3;
                for (int r = rings; r >= 1; r--) {
                    float rad = outlinePx + glowPx * r / rings;
                    Silhouette.begin(rgb, alpha * 0.16f * (1.2f - r / (float) (rings + 1)));
                    ring(e, pt, rad, z, 8, r * 0.4f, true);
                }
            }
            if (outlinePx > 0f) {
                Silhouette.begin(rgb, Math.min(1f, alpha));
                // two rings so thick outlines have no gaps between the 8 directions
                ring(e, pt, outlinePx, z, outlinePx > 3f ? 16 : 8, 0f, false);
                if (outlinePx > 1.5f) ring(e, pt, outlinePx * 0.55f, z, 8, 0.39f, false);
            }
        } finally {
            rendering = false;
            Silhouette.end();
            ScreenOffset.end();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(true);
            Silhouette.entityState(false);
        }
    }

    private static void ring(EntityLivingBase e, float pt, float px, float z, int n, float phase, boolean additive) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + phase;
            // set again every copy: the enchanted-armour shine resets blending and depth writes
            if (additive) GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            else GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.depthMask(false);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            ScreenOffset.shift((float) Math.cos(a) * px, (float) Math.sin(a) * px, z);
            Silhouette.render(e, pt, 0, 0, 0);
        }
    }

    private void drawBody(Minecraft mc, EntityLivingBase e, float pt, double t) {
        String m = body.get();
        float a = bodyAlpha.f();
        int c = tint.rgb();
        Silhouette.entityState(true);
        GlStateManager.enableBlend();
        rendering = true;
        try {
            if (m.equals("Shadow")) {
                // solid dark figure (no pre-pass needed)
                GlStateManager.depthMask(true);
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                Silhouette.begin(0x07060C, Math.max(0.6f, a + 0.5f));
                Silhouette.render(e, pt, 0, 0, 0);
            } else {
                // depth pre-pass: only the nearest surface of the body will be coloured
                GL11.glColorMask(false, false, false, false);
                GlStateManager.depthMask(true);
                Silhouette.render(e, pt, 0, 0, 0);
                GL11.glColorMask(true, true, true, true);
                GlStateManager.depthMask(false);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
                if (m.equals("Glass")) {
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    Silhouette.beginTint(c, a);
                    Silhouette.render(e, pt, 0, 0, 0);
                    // a cool sheen on top
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    Silhouette.begin(0xFFFFFF, 0.08f + 0.04f * (float) Math.sin(t * 2));
                    Silhouette.render(e, pt, 0, 0, 0);
                } else if (m.equals("Ghost")) {
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    float flick = 0.85f + 0.15f * (float) Math.sin(t * 7 + e.getEntityId());
                    Silhouette.beginTint(0xE6ECFF, a * 0.7f * flick);
                    Silhouette.render(e, pt, 0, 0, 0);
                } else if (m.equals("Hologram")) {
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    float flick = Math.random() < 0.03 ? 0.4f : 1f;
                    Silhouette.beginTint(c, a * flick);
                    Silhouette.render(e, pt, 0, 0, 0);
                    // a brighter scan line travelling up the body
                    double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - mc.getRenderManager().viewerPosY;
                    double band = y + ((t * 0.8) % 1.0) * e.height;
                    clipBand(band, 0.12);
                    Silhouette.begin(c, Math.min(1f, a * 2f));
                    Silhouette.render(e, pt, 0, 0, 0);
                    GL11.glDisable(GL11.GL_CLIP_PLANE0);
                    GL11.glDisable(GL11.GL_CLIP_PLANE1);
                } else if (m.equals("Rainbow")) {
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    Silhouette.beginTint(ParticleSystem.rainbow((float) (t * 0.15)), Math.max(a, 0.6f));
                    Silhouette.render(e, pt, 0, 0, 0);
                } else { // Tint
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    Silhouette.beginTint(c, Math.max(a, 0.6f));
                    Silhouette.render(e, pt, 0, 0, 0);
                }
            }
        } finally {
            Silhouette.end();
            rendering = false;
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Silhouette.entityState(false);
        }
        if (m.equals("Glass") && bodyOutline.on()) edge(mc, e, pt, 0xFFFFFF, 0.85f, 1.2f, 4f);
        name(mc, e, pt);
    }

    private final java.nio.DoubleBuffer plane = org.lwjgl.BufferUtils.createDoubleBuffer(4);

    private void clipBand(double y, double half) {
        plane.clear();
        plane.put(0).put(1).put(0).put(-(y - half)).flip();
        GL11.glClipPlane(GL11.GL_CLIP_PLANE0, plane);
        plane.clear();
        plane.put(0).put(-1).put(0).put(y + half).flip();
        GL11.glClipPlane(GL11.GL_CLIP_PLANE1, plane);
        GL11.glEnable(GL11.GL_CLIP_PLANE0);
        GL11.glEnable(GL11.GL_CLIP_PLANE1);
    }

    /** The name tag Minecraft would have drawn (skipped with the cancelled normal render). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void name(Minecraft mc, EntityLivingBase e, float pt) {
        if (nameFailed || e == mc.thePlayer) return;
        try {
            Render r = mc.getRenderManager().getEntityRenderObject(e);
            if (r == null) return;
            if (renderName == null) {
                renderName = ReflectionHelper.findMethod(Render.class, r, new String[]{"renderName", "func_177067_a"},
                        Entity.class, double.class, double.class, double.class);
            }
            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
            double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - mc.getRenderManager().viewerPosY;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;
            renderName.invoke(r, e, x, y, z);
        } catch (Throwable t) {
            nameFailed = true;
            net.m1227.Mod1227.LOGGER.warn("1227 Player Glow: could not draw name tags of players with a body material", t);
        }
    }
}
