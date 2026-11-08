package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Dynamic FOV control. Vanilla widens the view with sprinting, Speed
 * potions and flying, and narrows it while drawing a bow. This module
 * removes the speed part (the screen stays steady when you sprint or get
 * Speed) and lets you choose whether flying and the bow still change it.
 * Runs before Zoom, so zooming still works on top.
 */
public final class FovModule extends Module {
    private final Setting.Bool keepBow, keepFly;
    private final Setting.Num strength;

    public FovModule() {
        super("Static FOV", "FOV no longer changes with sprint, Speed or slowness.", Category.CAMERA, false);
        strength = add(new Setting.Num("Speed FOV kept", 0, 0, 100, 5, "%"));
        keepBow = add(new Setting.Bool("Keep bow zoom", true));
        keepFly = add(new Setting.Bool("Keep flying FOV", false));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFov(FOVUpdateEvent e) {
        EntityPlayer p = e.entity;
        float bow = 1f;
        if (p.isUsingItem() && p.getItemInUse() != null && p.getItemInUse().getItem() == Items.bow) {
            float f = p.getItemInUseDuration() / 20f;
            f = f > 1f ? 1f : f * f;
            bow = 1f - f * 0.15f;
        }
        float fly = p.capabilities.isFlying ? 1.1f : 1f;
        // what vanilla computed, without the bow and flying factors = the speed factor
        float speed = e.newfov / (bow * fly);
        if (Float.isNaN(speed) || Float.isInfinite(speed)) speed = 1f;
        float keep = strength.f() / 100f;
        float out = 1f + (speed - 1f) * keep;
        if (keepFly.on()) out *= fly;
        if (keepBow.on()) out *= bow;
        e.newfov = out;
    }
}
