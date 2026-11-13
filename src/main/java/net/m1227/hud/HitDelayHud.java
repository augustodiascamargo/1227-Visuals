package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Timing of your own attacks: time since your last attack attempt, the gap between your last two
 * attempts and (1.21) the gap between your last two likely registered hits from the Hit
 * Registration Monitor (a rhythm reference for W-tap / block-hit practice). Read-only: it only watches your
 * attacks, it never changes or triggers them.
 */
public final class HitDelayHud extends HudModule {
    private long last = -1L, gap = -1L;
    private final Setting.Num window;
    private final Setting.Bool bar;

    public HitDelayHud() {
        super("Hit Delay", "Time since your last attack and between attacks and likely hits.", 0.44, 0.62);
        window = add(new Setting.Num("Bar length", 800, 300, 2000, 50, "ms"));
        bar = add(new Setting.Bool("Timing bar", true));
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onAttack(AttackEntityEvent e) {
                if (e.entityPlayer != Minecraft.getMinecraft().thePlayer) return;
                long now = System.currentTimeMillis();
                if (last > 0) gap = now - last;
                last = now;
            }
        });
    }

    @Override
    public float width() {
        return 110;
    }

    @Override
    public float height() {
        return 6 + 30 + (bar.on() ? 6 : 0);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        panel(x, y, width(), height());
        long now = System.currentTimeMillis();
        long since = last < 0 ? -1 : now - last;
        if (editing && since < 0) since = 320;
        long g = editing && gap < 0 ? 250 : gap;
        String a = since < 0 ? "-" : since > 9999 ? ">10 s" : since + " ms";
        String b = g < 0 ? "-" : g + " ms";
        float hg = net.m1227.combat.CombatLog.INSTANCE.hitGaps.last();
        String c = editing && Float.isNaN(hg) ? "480 ms" : Float.isNaN(hg) ? "-" : Math.round(hg) + " ms";
        Gfx.text("Last attack", x + 5, y + 4, LABEL);
        Gfx.text(a, x + width() - 5 - Gfx.textWidth(a), y + 4, value());
        Gfx.text("Between attacks", x + 5, y + 14, LABEL);
        Gfx.text(b, x + width() - 5 - Gfx.textWidth(b), y + 14, 0xFFFFFFFF);
        Gfx.text("Between hits", x + 5, y + 24, LABEL);
        Gfx.text(c, x + width() - 5 - Gfx.textWidth(c), y + 24, 0xFFFFFFFF);
        if (bar.on()) {
            float f = since < 0 ? 1f : Math.min(1f, since / window.f());
            Gfx.roundedRect(x + 5, y + 35, width() - 10, 3, 1.5f, 0x40FFFFFF);
            Gfx.roundedRect(x + 5, y + 35, (width() - 10) * f, 3, 1.5f, value());
        }
    }
}
