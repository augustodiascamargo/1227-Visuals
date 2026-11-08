package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.AuraLibrary;
import net.m1227.vfx.FxSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** One of 24 auras around the player you are fighting. */
public final class TargetAuraModule extends Module {
    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Num radius, linger;
    private final Setting.Bool rainbow, preview;
    private final AuraLibrary.Params params = new AuraLibrary.Params();
    private final AuraLibrary.State target = new AuraLibrary.State(), self = new AuraLibrary.State();

    public TargetAuraModule() {
        super("Target Aura", "One of 24 auras around the player you are fighting.", Category.AURAS, true);
        style = add(new Setting.Mode("Style", "Orbit", AuraLibrary.STYLES));
        fx = new FxSettings(this, 0x8B5CFF, 0x2DD4FF, 6, 1.2, 0.13, 0.7);
        radius = add(new Setting.Num("Radius", 1.0, 0.5, 2.0, 0.05, "x"));
        rainbow = add(new Setting.Bool("Rainbow", false));
        linger = add(new Setting.Num("Linger", 4, 1, 8, 0.5, "s"));
        preview = add(new Setting.Bool("Preview on self (3rd person)", false));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        fill();
        EntityLivingBase t = CombatTracker.target((long) (linger.get() * 1000.0));
        if (t != null && t != mc.thePlayer) AuraLibrary.draw(style.get(), t, e.partialTicks, target, params);
        if (preview.on() && mc.gameSettings.thirdPersonView != 0) AuraLibrary.draw(style.get(), mc.thePlayer, e.partialTicks, self, params);
    }

    private void fill() {
        AuraLibrary.Params p = params;
        p.c0 = fx.color1.rgb();
        p.c1 = fx.color2.rgb();
        p.alpha = fx.alpha();
        p.size = fx.size.f();
        p.speed = fx.speed.f();
        p.amount = fx.amount.f();
        p.radius = radius.f();
        p.life = fx.duration.f();
        p.fade = fx.fade.f();
        p.rainbow = rainbow.on();
    }
}
