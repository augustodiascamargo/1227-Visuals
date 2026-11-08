package net.m1227.module;

import java.awt.Desktop;
import java.io.File;
import net.m1227.audio.HitSoundEngine;
import net.m1227.audio.SoundBank;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Custom hit sounds with their own volume, independent of Minecraft's
 * volume sliders. "Confirmed" plays only when the target really took
 * damage (its hurt timer rose after your swing), so missed or blocked
 * swings stay silent. "On swing" is instant but can play on hits the
 * server rejects.
 */
public final class HitSoundsModule extends Module {
    private final Setting.Mode sound, trigger;
    private final Setting.Num volume, pitch, pitchVar, cooldown, voices;
    private final Setting.Bool muteVanilla;
    private long last;

    public HitSoundsModule() {
        super("Hit Sounds", "Your own hit sound, volume and pitch.", Category.HITSOUNDS, false);
        sound = add(new Setting.Mode("Sound", "Crisp Click", SoundBank.options()));
        volume = add(new Setting.Num("Volume", 80, 0, 200, 5, "%"));
        pitch = add(new Setting.Num("Pitch", 1.0, 0.5, 2.0, 0.05, "x"));
        pitchVar = add(new Setting.Num("Pitch variation", 0.04, 0.0, 0.2, 0.01, ""));
        trigger = add(new Setting.Mode("Trigger", "Confirmed hit", "Confirmed hit", "On swing"));
        cooldown = add(new Setting.Num("Min interval", 45, 0, 250, 5, "ms"));
        voices = add(new Setting.Num("Max overlap", 3, 1, 6, 1, ""));
        muteVanilla = add(new Setting.Bool("Mute vanilla hurt sound", false));
        add(new Setting.Action("Preview sound", new Runnable() {
            @Override
            public void run() {
                playNow(true);
            }
        }));
        add(new Setting.Action("Reload custom sounds", new Runnable() {
            @Override
            public void run() {
                HitSoundEngine.INSTANCE.reload();
                sound.setOptions(SoundBank.options());
            }
        }));
        add(new Setting.Action("Open sounds folder", new Runnable() {
            @Override
            public void run() {
                final File dir = SoundBank.folder();
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Desktop.getDesktop().open(dir);
                        } catch (Throwable ignored) {
                            // no desktop integration: the path is in the log
                        }
                    }
                }, "Mod1227-OpenFolder");
                t.setDaemon(true);
                t.start();
                net.m1227.Mod1227.LOGGER.info("Hit sounds folder: " + dir.getAbsolutePath());
            }
        }));
    }

    @SubscribeEvent
    public void onConfirmed(CombatTracker.HitEvent e) {
        if (trigger.is("Confirmed hit")) playNow(false);
    }

    @SubscribeEvent
    public void onSwing(AttackEntityEvent e) {
        if (!trigger.is("On swing")) return;
        if (e.entityPlayer != Minecraft.getMinecraft().thePlayer) return;
        playNow(false);
    }

    @SubscribeEvent
    public void onSound(PlaySoundEvent e) {
        if (muteVanilla.on() && "game.player.hurt".equals(e.name)) e.result = null;
    }

    private void playNow(boolean preview) {
        long now = System.currentTimeMillis();
        if (!preview && now - last < cooldown.get()) return;
        last = now;
        float var = pitchVar.f();
        float p = pitch.f() * (1f + (float) (Math.random() * 2.0 - 1.0) * var);
        HitSoundEngine.INSTANCE.play(sound.get(), volume.f() / 100f, p, voices.i());
    }
}
