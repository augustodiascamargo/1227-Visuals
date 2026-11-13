package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * No Hurt Cam: removes (or weakens) the camera and hand tilt when you take damage.
 *
 * Purely visual. Minecraft tilts the view from {@code hurtTime} while drawing the frame, so for
 * the length of each frame your own {@code hurtTime} reads 0, and it is put back right after.
 * Game logic (ticks, damage, knockback, sounds) runs outside the frame and always sees the real
 * value. While your own model, the world effects and the HUD are drawn it is put back too, so the
 * red flash on your character in third person and the hit effects still work.
 *
 * "Shake strength" above 0 % brings back a softer version of the same tilt (first person).
 */
public final class NoHurtCamModule extends Module {
    private final Setting.Num strength;
    private final Setting.Bool keepRed;
    private EntityPlayerSP held;
    private int saved;
    private boolean hidden;

    public NoHurtCamModule() {
        super("No Hurt Cam", "No camera shake or tilt when you take damage.", Category.CAMERA, false);
        strength = add(new Setting.Num("Shake strength", 0, 0, 100, 5, "%"));
        keepRed = add(new Setting.Bool("Keep red flash on your character", true));
    }

    private void hide() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null || hidden) return;
        held = p;
        saved = p.hurtTime;
        p.hurtTime = 0;
        hidden = true;
    }

    private void restore() {
        if (!hidden) return;
        // only put it back if nothing else changed it in between (a tick never runs inside a frame)
        if (held != null && held.hurtTime == 0) held.hurtTime = saved;
        hidden = false;
        held = null;
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) hide();
        else restore();
    }

    // the world effects drawn after the entities (hit colours, rings...) see the real value
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onWorldLastFirst(RenderWorldLastEvent e) {
        restore();
    }

    // ...and it is hidden again before the hand is drawn
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWorldLastEnd(RenderWorldLastEvent e) {
        hide();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSelfPre(RenderLivingEvent.Pre<?> e) {
        if (keepRed.on() && e.entity == held) restore();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSelfPost(RenderLivingEvent.Post<?> e) {
        if (keepRed.on() && e.entity == Minecraft.getMinecraft().thePlayer) hide();
    }

    /** A weaker copy of Minecraft's tilt, when Shake strength is above 0. */
    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup e) {
        float k = strength.f() / 100f;
        Minecraft mc = Minecraft.getMinecraft();
        if (k <= 0f || !hidden || held == null || e.entity != held || mc.gameSettings.thirdPersonView != 0) return;
        float f = saved - (float) e.renderPartialTicks;
        if (f < 0f) return;
        f /= Math.max(1f, held.maxHurtTime);
        f = MathHelper.sin(f * f * f * f * (float) Math.PI);
        float yaw = held.attackedAtYaw;
        GlStateManager.rotate(-yaw, 0f, 1f, 0f);
        GlStateManager.rotate(-f * 14f * k, 0f, 0f, 1f);
        GlStateManager.rotate(yaw, 0f, 1f, 0f);
    }
}
