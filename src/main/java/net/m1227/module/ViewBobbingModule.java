package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * View bobbing control: vanilla, off (No View Bobbing) or a custom strength.
 * Vanilla's bobbing amplitude is proportional to the player's cameraYaw /
 * cameraPitch, so "Custom" scales those values just for the render and puts
 * them back afterwards. Changes apply instantly; disabling the module
 * restores your original Minecraft setting.
 */
public final class ViewBobbingModule extends Module {
    private final Setting.Mode mode;
    private final Setting.Num strength;
    private Boolean original;
    private float sYaw, sPrevYaw, sPitch, sPrevPitch;
    private boolean scaled;

    public ViewBobbingModule() {
        super("View Bobbing", "Turn camera bobbing off or set its strength.", Category.CAMERA, false);
        mode = add(new Setting.Mode("Mode", "Off (No View Bobbing)", "Vanilla", "Off (No View Bobbing)", "Custom"));
        strength = add(new Setting.Num("Custom strength", 40, 0, 150, 5, "%"));
    }

    @Override
    protected void onEnable() {
        original = Minecraft.getMinecraft().gameSettings.viewBobbing;
    }

    @Override
    protected void onDisable() {
        if (original != null) Minecraft.getMinecraft().gameSettings.viewBobbing = original;
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (e.phase == TickEvent.Phase.START) {
            mc.gameSettings.viewBobbing = !mode.is("Off (No View Bobbing)");
            if (p != null && mode.is("Custom")) {
                float k = strength.f() / 100f;
                sYaw = p.cameraYaw;
                sPrevYaw = p.prevCameraYaw;
                sPitch = p.cameraPitch;
                sPrevPitch = p.prevCameraPitch;
                p.cameraYaw *= k;
                p.prevCameraYaw *= k;
                p.cameraPitch *= k;
                p.prevCameraPitch *= k;
                scaled = true;
            }
        } else if (scaled && p != null) {
            p.cameraYaw = sYaw;
            p.prevCameraYaw = sPrevYaw;
            p.cameraPitch = sPitch;
            p.prevCameraPitch = sPrevPitch;
            scaled = false;
        }
    }
}
