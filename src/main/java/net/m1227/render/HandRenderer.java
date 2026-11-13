package net.m1227.render;

import java.lang.reflect.Field;
import net.m1227.Mod1227;
import net.m1227.module.AnimationsModule;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/**
 * Draws the first-person hand when 1227 needs to: for the 1.7 animations,
 * and for effects that live on the held item (blade glow, swing trail,
 * item glow). It repeats vanilla 1.8.9 ItemRenderer's steps exactly, so with
 * nothing enabled it looks identical; when nothing needs it, vanilla draws
 * the hand as usual. Maps always fall back to vanilla.
 */
public final class HandRenderer {
    public static final HandRenderer INSTANCE = new HandRenderer();

    private Field fItem, fEquip, fPrevEquip;
    private boolean reflectionFailed, warned;

    private HandRenderer() {}

    private boolean reflect() {
        if (fItem != null) return true;
        if (reflectionFailed) return false;
        try {
            fItem = ReflectionHelper.findField(ItemRenderer.class, "itemToRender", "field_78453_b");
            fEquip = ReflectionHelper.findField(ItemRenderer.class, "equippedProgress", "field_78454_c");
            fPrevEquip = ReflectionHelper.findField(ItemRenderer.class, "prevEquippedProgress", "field_78451_d");
            return true;
        } catch (Throwable t) {
            reflectionFailed = true;
            Mod1227.LOGGER.warn("1227 hand renderer unavailable (could not access ItemRenderer): " + t);
            return false;
        }
    }

    /** True when Minecraft's own hand drawing must be replaced (1.7 poses, custom 3D sword). */
    private static boolean takeover() {
        if (net.m1227.module.ClientModule.handCompat()) return false;
        AnimationsModule a = AnimationsModule.INSTANCE;
        net.m1227.module.WeaponModelsModule wm = net.m1227.module.WeaponModelsModule.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        boolean model = wm != null && mc.thePlayer != null && wm.replaces(mc.thePlayer.getHeldItem());
        return (a != null && a.needsTakeover()) || model;
    }

    @SubscribeEvent
    public void onHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.anaglyph) return;
        boolean take = takeover(), fx = HandFx.wantsHand();
        if ((!take && !fx) || !reflect()) return;
        ItemStack item;
        try {
            item = (ItemStack) fItem.get(mc.getItemRenderer());
        } catch (Exception ex) {
            return;
        }
        if (item != null && item.getItem() instanceof ItemMap) return;
        if (take) {
            e.setCanceled(true);
            renderHand(mc, e.partialTicks, item, true);
        } else {
            // Effects only: Minecraft still draws the hand itself, right after this. The glow,
            // trail and particles are drawn first (no depth writes) so the real item covers them,
            // and nothing about the vanilla hand pass is replaced.
            renderHand(mc, e.partialTicks, item, false);
        }
    }

    private final java.nio.FloatBuffer savedProjection = org.lwjgl.BufferUtils.createFloatBuffer(16);

    private void renderHand(Minecraft mc, float pt, ItemStack item, boolean full) {
        if (full) GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        // The projection stack is only guaranteed to be 2 deep; Minecraft, OptiFine and shader
        // passes already use it, so pushing here could overflow and later pop someone else's
        // matrix. Save and restore the matrix by value instead.
        savedProjection.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, savedProjection);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        Project.gluPerspective(handFov(mc, pt), (float) mc.displayWidth / (float) mc.displayHeight, 0.05F,
                mc.gameSettings.renderDistanceChunks * 16 * 2.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            Entity view = mc.getRenderViewEntity();
            boolean sleeping = view instanceof EntityLivingBase && ((EntityLivingBase) view).isPlayerSleeping();
            boolean draw = mc.gameSettings.thirdPersonView == 0 && !sleeping && !mc.gameSettings.hideGUI && !mc.playerController.isSpectator();
            GlStateManager.pushMatrix();
            hurtCamera(mc, pt);
            if (mc.gameSettings.viewBobbing) bobbing(mc, pt);
            if (draw) {
                mc.entityRenderer.enableLightmap();
                firstPerson(mc, pt, item, full);
                mc.entityRenderer.disableLightmap();
            }
            GlStateManager.popMatrix();
            if (draw) HandFx.INSTANCE.renderCameraSpace(mc);
            if (full && mc.gameSettings.thirdPersonView == 0 && !sleeping) mc.getItemRenderer().renderOverlays(pt);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Mod1227.LOGGER.warn("1227 hand render failed, falling back to vanilla", t);
            }
            reflectionFailed = true;
            fItem = null;
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            savedProjection.rewind();
            GL11.glLoadMatrix(savedProjection);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }

    private static float handFov(Minecraft mc, float pt) {
        float f = 70.0F;
        Entity v = mc.getRenderViewEntity();
        if (v instanceof EntityLivingBase && ((EntityLivingBase) v).getHealth() <= 0.0F) {
            float d = ((EntityLivingBase) v).deathTime + pt;
            f /= (1.0F - 500.0F / (d + 500.0F)) * 2.0F + 1.0F;
        }
        Block b = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, v, pt);
        if (b.getMaterial() == Material.water) f = f * 60.0F / 70.0F;
        return f;
    }

    private static void hurtCamera(Minecraft mc, float pt) {
        if (!(mc.getRenderViewEntity() instanceof EntityLivingBase)) return;
        EntityLivingBase e = (EntityLivingBase) mc.getRenderViewEntity();
        float f = e.hurtTime - pt;
        if (e.getHealth() <= 0.0F) {
            float d = e.deathTime + pt;
            GlStateManager.rotate(40.0F - 8000.0F / (d + 200.0F), 0.0F, 0.0F, 1.0F);
        }
        if (f < 0.0F) return;
        f /= (float) e.maxHurtTime;
        f = MathHelper.sin(f * f * f * f * (float) Math.PI);
        float yaw = e.attackedAtYaw;
        GlStateManager.rotate(-yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-f * 14.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
    }

    private static void bobbing(Minecraft mc, float pt) {
        if (!(mc.getRenderViewEntity() instanceof EntityPlayer)) return;
        EntityPlayer p = (EntityPlayer) mc.getRenderViewEntity();
        float f = p.distanceWalkedModified - p.prevDistanceWalkedModified;
        float f1 = -(p.distanceWalkedModified + f * pt);
        float f2 = p.prevCameraYaw + (p.cameraYaw - p.prevCameraYaw) * pt;
        float f3 = p.prevCameraPitch + (p.cameraPitch - p.prevCameraPitch) * pt;
        GlStateManager.translate(MathHelper.sin(f1 * (float) Math.PI) * f2 * 0.5F, -Math.abs(MathHelper.cos(f1 * (float) Math.PI) * f2), 0.0F);
        GlStateManager.rotate(MathHelper.sin(f1 * (float) Math.PI) * f2 * 3.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(Math.abs(MathHelper.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f3, 1.0F, 0.0F, 0.0F);
    }

    private void firstPerson(Minecraft mc, float pt, ItemStack item, boolean full) throws IllegalAccessException {
        AnimationsModule an = full ? AnimationsModule.INSTANCE : null;
        ItemRenderer ir = mc.getItemRenderer();
        float eq = fEquip.getFloat(ir), prev = fPrevEquip.getFloat(ir);
        float equip = 1.0F - (prev + (eq - prev) * pt);
        EntityPlayerSP p = mc.thePlayer;
        float swingP = p.getSwingProgress(pt);
        float pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.popMatrix();

        int light = mc.theWorld.getCombinedLight(new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) (light & 65535), (float) (light >> 16));

        float armPitch = p.prevRenderArmPitch + (p.renderArmPitch - p.prevRenderArmPitch) * pt;
        float armYaw = p.prevRenderArmYaw + (p.renderArmYaw - p.prevRenderArmYaw) * pt;
        GlStateManager.rotate((p.rotationPitch - armPitch) * 0.1F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate((p.rotationYaw - armYaw) * 0.1F, 0.0F, 1.0F, 0.0F);

        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        if (an != null) GlStateManager.translate(an.offX(), an.offY(), an.offZ());
        if (item != null) {
            if (p.getItemInUseCount() > 0) {
                EnumAction action = item.getItemUseAction();
                if (action == EnumAction.EAT || action == EnumAction.DRINK) {
                    drinking(p, pt, item);
                    transform(equip, an != null && an.eating() ? swingP : 0.0F);
                } else if (action == EnumAction.BLOCK) {
                    transform(equip, an != null && an.blockhit() ? swingP : 0.0F);
                    blockTransform();
                } else if (action == EnumAction.BOW) {
                    transform(equip, an != null && an.bow() ? swingP : 0.0F);
                    bowTransform(pt, p, item);
                } else {
                    transform(equip, 0.0F);
                }
            } else {
                net.m1227.module.WeaponModelsModule wm = net.m1227.module.WeaponModelsModule.INSTANCE;
                if (wm != null && wm.replaces(item) && !wm.style().equals("Classic")) {
                    // each custom sword has its own way of being held and swung
                    wm.holdTransform(equip, swingP);
                } else {
                    itemUsed(swingP);
                    transform(equip, swingP);
                }
            }
            float s = an != null ? an.itemScale() : 1f;
            if (s != 1.0F) GlStateManager.scale(s, s, s);
            if (an != null && an.rod() && item.getItem() instanceof ItemFishingRod) {
                // 1.7 drew rods with the regular tool pose turned 180 degrees around Y
                GlStateManager.translate(0.0F, 0.25F, 0.125F);
                GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(25.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.scale(1.7F, 1.7F, 1.7F);
                GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
                if (full) ir.renderItem(p, item, ItemCameraTransforms.TransformType.NONE);
            } else {
                HandFx.INSTANCE.beforeItem(mc, p, item); // glow behind the item: the item drawn next covers its middle
                if (full) {
                    net.m1227.module.WeaponModelsModule wm = net.m1227.module.WeaponModelsModule.INSTANCE;
                    if (wm != null && wm.replaces(item)) wm.renderFirstPerson(mc, item);
                    else ir.renderItem(p, item, ItemCameraTransforms.TransformType.FIRST_PERSON);
                }
                HandFx.INSTANCE.afterItem(mc, p, item);
            }
        } else if (full && !p.isInvisible()) {
            arm(mc, p, equip, swingP);
        }
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
    }

    // vanilla 1.8.9 ItemRenderer transforms (same numbers); 1.7 behaviour comes from the swing values passed in

    private static void transform(float equip, float swing) {
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translate(0.0F, equip * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(swing * swing * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    private static void itemUsed(float swing) {
        float f = -0.4F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI * 2.0F);
        float f2 = -0.2F * MathHelper.sin(swing * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
    }

    private static void drinking(AbstractClientPlayer p, float pt, ItemStack item) {
        float f = (float) p.getItemInUseCount() - pt + 1.0F;
        float f1 = f / (float) item.getMaxItemUseDuration();
        float f2 = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.1F);
        if (f1 >= 0.8F) f2 = 0.0F;
        GlStateManager.translate(0.0F, f2, 0.0F);
        float f3 = 1.0F - (float) Math.pow(f1, 27.0D);
        GlStateManager.translate(f3 * 0.6F, f3 * -0.5F, 0.0F);
        GlStateManager.rotate(f3 * 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f3 * 10.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f3 * 30.0F, 0.0F, 0.0F, 1.0F);
    }

    private static void blockTransform() {
        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void bowTransform(float pt, AbstractClientPlayer p, ItemStack item) {
        GlStateManager.rotate(-18.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-12.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-8.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(-0.9F, 0.2F, 0.0F);
        float f = (float) item.getMaxItemUseDuration() - ((float) p.getItemInUseCount() - pt + 1.0F);
        float f1 = f / 20.0F;
        f1 = (f1 * f1 + f1 * 2.0F) / 3.0F;
        if (f1 > 1.0F) f1 = 1.0F;
        if (f1 > 0.1F) {
            float f2 = MathHelper.sin((f - 0.1F) * 1.3F);
            GlStateManager.translate(0.0F, f2 * (f1 - 0.1F) * 0.01F, 0.0F);
        }
        GlStateManager.translate(0.0F, 0.0F, f1 * 0.1F);
        GlStateManager.scale(1.0F, 1.0F, 1.0F + f1 * 0.2F);
    }

    private static void arm(Minecraft mc, AbstractClientPlayer p, float equip, float swing) {
        float f = -0.3F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        float f1 = 0.4F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI * 2.0F);
        float f2 = -0.4F * MathHelper.sin(swing * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
        GlStateManager.translate(0.64000005F, -0.6F, -0.71999997F);
        GlStateManager.translate(0.0F, equip * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f3 = MathHelper.sin(swing * swing * (float) Math.PI);
        float f4 = MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        GlStateManager.rotate(f4 * 70.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f3 * -20.0F, 0.0F, 0.0F, 1.0F);
        mc.getTextureManager().bindTexture(p.getLocationSkin());
        GlStateManager.translate(-1.0F, 3.6F, 3.5F);
        GlStateManager.rotate(120.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(200.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(5.6F, 0.0F, 0.0F);
        Render<AbstractClientPlayer> render = mc.getRenderManager().getEntityRenderObject(p);
        GlStateManager.disableCull();
        ((RenderPlayer) render).renderRightArm(p);
        GlStateManager.enableCull();
    }
}
