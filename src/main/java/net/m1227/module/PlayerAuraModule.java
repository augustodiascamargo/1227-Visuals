package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.AuraLibrary;
import net.m1227.vfx.FxSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** The same 24 aura styles, on yourself (and optionally other players). */
public final class PlayerAuraModule extends Module {
    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Num radius;
    private final Setting.Bool rainbow, others, firstPerson;
    private final AuraLibrary.Params params = new AuraLibrary.Params();
    private final AuraLibrary.State mine = new AuraLibrary.State(), theirs = new AuraLibrary.State();

    public PlayerAuraModule() {
        super("Player Aura", "Any of the 24 aura styles on yourself.", Category.AURAS, false);
        style = add(new Setting.Mode("Style", "Crystals", AuraLibrary.STYLES));
        fx = new FxSettings(this, 0xFFFFFF, 0xBFEFFF, 8, 1.0, 0.16, 1.2);
        radius = add(new Setting.Num("Radius", 1.0, 0.5, 2.0, 0.05, "x"));
        rainbow = add(new Setting.Bool("Rainbow", false));
        others = add(new Setting.Bool("Show on other players", false));
        firstPerson = add(new Setting.Bool("Show in first person", false));
    }

    /** Menu gallery: draws one style around a stand-in entity (into the preview stage's systems). */
    public void drawPreview(String st, net.minecraft.entity.Entity e, AuraLibrary.State s) {
        fill();
        AuraLibrary.draw(st, e, 1f, s, params);
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

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
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
        if (mc.gameSettings.thirdPersonView != 0 || firstPerson.on()) AuraLibrary.draw(style.get(), mc.thePlayer, e.partialTicks, mine, p);
        if (others.on()) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer o = list.get(i);
                if (o != mc.thePlayer && !o.isInvisible() && net.m1227.client.perf.EffectBudget.allow(net.m1227.client.perf.EffectBudget.Kind.AURA, o)) AuraLibrary.draw(style.get(), o, e.partialTicks, theirs, p);
            }
        }
    }
}
