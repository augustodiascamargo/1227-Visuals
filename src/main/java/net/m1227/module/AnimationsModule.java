package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
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
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/**
 * Classic 1.7 first-person animations on 1.8.9.
 *
 * 1.8 freezes the swing while you block, eat/drink or draw a bow; 1.7 kept
 * playing it. When any 1.7 option is on, this module draws the first-person
 * hand itself (same steps as vanilla's ItemRenderer, with the 1.7 swing
 * transforms) and cancels the vanilla hand pass. Maps fall back to vanilla.
 *
 * Everything here is visual only: no packets, no change to reach, attack
 * timing or blocking. "Old swing" restarts the arm animation locally while
 * you hold attack during block/eat, it does not attack.
 */
public final class AnimationsModule extends Module {
    private final Setting.Bool blockhit, eating, bow, rod, swing;
    private final Setting.Num offX, offY, offZ, scale;

    public static AnimationsModule INSTANCE;

    public AnimationsModule() {
        super("1.7 Animations", "Classic 1.7 blockhit, eating, bow, rod and swing.", Category.ANIMATIONS, false);
        INSTANCE = this;
        add(new Setting.Action("Use 1.7 animations (all on)", new Runnable() {
            @Override
            public void run() {
                setAll(true);
                Notifier.post(Notifier.Kind.SYSTEM, "Animations", "1.7 set", Notifier.ACCENT, 1500);
            }
        }));
        add(new Setting.Action("Use 1.8 animations (vanilla)", new Runnable() {
            @Override
            public void run() {
                setAll(false);
                Notifier.post(Notifier.Kind.SYSTEM, "Animations", "1.8 vanilla", Notifier.INFO, 1500);
            }
        }));
        blockhit = add(new Setting.Bool("Old sword blocking (blockhit)", true));
        eating = add(new Setting.Bool("Old eating / drinking", true));
        bow = add(new Setting.Bool("Old bow", true));
        rod = add(new Setting.Bool("Old rod (approximation)", false));
        swing = add(new Setting.Bool("Old swing while using items", true));
        offX = add(new Setting.Num("Item offset X", 0, -0.5, 0.5, 0.01, ""));
        offY = add(new Setting.Num("Item offset Y", 0, -0.5, 0.5, 0.01, ""));
        offZ = add(new Setting.Num("Item offset Z", 0, -0.5, 0.5, 0.01, ""));
        scale = add(new Setting.Num("Item scale", 1.0, 0.5, 1.5, 0.05, "x"));
    }

    private void setAll(boolean on) {
        blockhit.set(on);
        eating.set(on);
        bow.set(on);
        swing.set(on);
        if (!on) {
            rod.set(false);
            offX.set(0.0);
            offY.set(0.0);
            offZ.set(0.0);
            scale.set(1.0);
        }
    }

    public boolean needsTakeover() {
        return isEnabled() && (blockhit.on() || eating.on() || bow.on() || rod.on() || offX.get() != 0 || offY.get() != 0
                || offZ.get() != 0 || scale.get() != 1.0);
    }

    // ------------------------------------------------------- old swing

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !swing.on()) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.currentScreen != null) return;
        if (p.isUsingItem() && mc.gameSettings.keyBindAttack.isKeyDown() && mc.objectMouseOver != null) {
            // restart the local arm animation (no packet is sent)
            if (!p.isSwingInProgress || p.swingProgressInt >= 3 || p.swingProgressInt < 0) {
                p.swingProgressInt = -1;
                p.isSwingInProgress = true;
            }
        }
    }

    // read by the shared first-person renderer (all false / neutral when the module is off)
    public boolean blockhit() { return isEnabled() && blockhit.on(); }
    public boolean eating() { return isEnabled() && eating.on(); }
    public boolean bow() { return isEnabled() && bow.on(); }
    public boolean rod() { return isEnabled() && rod.on(); }
    public float offX() { return isEnabled() ? offX.f() : 0f; }
    public float offY() { return isEnabled() ? offY.f() : 0f; }
    public float offZ() { return isEnabled() ? offZ.f() : 0f; }
    public float itemScale() { return isEnabled() ? scale.f() : 1f; }
}
