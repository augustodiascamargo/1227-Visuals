package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.AuraShapes;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * A soft coloured glow around players. Large soft sprites sit inside the
 * body (head, chest, hips, legs, arms); because they are depth-tested after
 * the player is drawn, the body hides their centre and the light shows
 * around the silhouette, like a rim glow. Never visible through walls.
 */
public final class PlayerGlowModule extends Module {
    private final Setting.Mode who, colorMode;
    private final Setting.Col color;
    private final Setting.Num intensity, size, pulse;

    public PlayerGlowModule() {
        super("Player Glow", "Soft coloured rim glow around players.", Category.AURAS, false);
        who = add(new Setting.Mode("Players", "Target", "Target", "Self (3rd person)", "Target and self", "Everyone"));
        colorMode = add(new Setting.Mode("Color", "Team color", "Team color", "Custom", "Health", "Rainbow"));
        color = add(new Setting.Col("Custom color", 0x8B5CFF));
        intensity = add(new Setting.Num("Intensity", 0.45, 0.05, 1.0, 0.05, ""));
        size = add(new Setting.Num("Size", 1.0, 0.5, 2.0, 0.05, "x"));
        pulse = add(new Setting.Num("Pulse", 0.3, 0.0, 1.0, 0.05, ""));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        String w = who.get();
        boolean self = (w.equals("Self (3rd person)") || w.equals("Target and self") || w.equals("Everyone"))
                && mc.gameSettings.thirdPersonView != 0;
        if (self) glow(mc, mc.thePlayer, e.partialTicks);
        if (w.equals("Everyone")) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer p = list.get(i);
                if (p != mc.thePlayer && !p.isInvisible()) glow(mc, p, e.partialTicks);
            }
        } else if (!w.equals("Self (3rd person)")) {
            EntityLivingBase t = CombatTracker.target(5000L);
            if (t != null && t != mc.thePlayer && !t.isInvisible()) glow(mc, t, e.partialTicks);
        }
    }

    private int colorFor(Minecraft mc, EntityLivingBase e, double time) {
        String m = colorMode.get();
        if (m.equals("Custom")) return color.rgb();
        if (m.equals("Rainbow")) return ParticleSystem.rainbow((float) (time * 0.12));
        if (m.equals("Health")) {
            float f = e.getHealth() / Math.max(1f, e.getMaxHealth());
            return f > 0.5f ? ParticleSystem.mix(0xFFC857, 0x3DDC84, (f - 0.5f) * 2f) : ParticleSystem.mix(0xFF4D5E, 0xFFC857, f * 2f);
        }
        // team colour: first formatting colour in the display name
        String name = e.getDisplayName().getFormattedText();
        for (int i = 0; i + 1 < name.length(); i++) {
            if (name.charAt(i) == '§') {
                char c = Character.toLowerCase(name.charAt(i + 1));
                if ("0123456789abcdef".indexOf(c) >= 0) return mc.fontRendererObj.getColorCode(c) & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
    }

    private void glow(Minecraft mc, EntityLivingBase e, float pt) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        double x = ParticleSystem.lerp(e.lastTickPosX, e.posX, pt);
        double y = ParticleSystem.lerp(e.lastTickPosY, e.posY, pt);
        double z = ParticleSystem.lerp(e.lastTickPosZ, e.posZ, pt);
        if (!ps.inRange(x, y, z)) return;
        double t = ps.time;
        int c = colorFor(mc, e, t);
        float a = intensity.f() * (1f - pulse.f() * 0.5f * (1f + (float) Math.sin(t * 3)));
        float s = size.f();
        float h = e.height;
        boolean sneak = e.isSneaking();
        double by = Math.toRadians(e.prevRenderYawOffset + (e.renderYawOffset - e.prevRenderYawOffset) * pt);
        double rx = -Math.cos(by) * 0.38, rz = -Math.sin(by) * 0.38;
        float head = h - (sneak ? 0.45f : 0.25f);
        AuraShapes.sprite(ps, Sprite.GLOW, x, y + head, z, 0.55f * s, 0f, false, c, a);
        AuraShapes.sprite(ps, Sprite.GLOW, x, y + h * 0.62, z, 0.75f * s, 0f, false, c, a);
        AuraShapes.sprite(ps, Sprite.GLOW, x, y + h * 0.4, z, 0.7f * s, 0f, false, c, a * 0.9f);
        AuraShapes.sprite(ps, Sprite.GLOW, x, y + h * 0.16, z, 0.6f * s, 0f, false, c, a * 0.8f);
        AuraShapes.sprite(ps, Sprite.GLOW, x + rx, y + h * 0.55, z + rz, 0.45f * s, 0f, false, c, a * 0.8f);
        AuraShapes.sprite(ps, Sprite.GLOW, x - rx, y + h * 0.55, z - rz, 0.45f * s, 0f, false, c, a * 0.8f);
    }
}
