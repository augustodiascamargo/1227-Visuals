package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Hit Particles: more of Minecraft's own hit particles when you hit something, and optionally
 * always the critical and sharpness sparks. Only extra particles on your screen; damage and
 * criticals themselves are unchanged.
 */
public final class HitParticlesModule extends Module {
    private final Setting.Num multiplier;
    private final Setting.Bool alwaysCrit, alwaysSharp;

    public HitParticlesModule() {
        super("Hit Particles", "More hit sparks; always show critical and sharpness particles.", Category.COMBAT, false);
        multiplier = add(new Setting.Num("Multiplier", 3, 1, 10, 1, "x"));
        alwaysCrit = add(new Setting.Bool("Always show critical sparks", true));
        alwaysSharp = add(new Setting.Bool("Always show sharpness sparks", false));
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.entityPlayer != mc.thePlayer || !(e.target instanceof EntityLivingBase)) return;
        // vanilla already emits one burst when the hit is a real critical / enchanted; these are extra
        int n = multiplier.i() - 1 + (alwaysCrit.on() ? 1 : 0);
        for (int i = 0; i < n; i++) mc.effectRenderer.emitParticleAtEntity(e.target, EnumParticleTypes.CRIT);
        if (alwaysSharp.on()) for (int i = 0; i < multiplier.i(); i++) mc.effectRenderer.emitParticleAtEntity(e.target, EnumParticleTypes.CRIT_MAGIC);
    }
}
