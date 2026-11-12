package net.m1227.hud;

import net.m1227.core.CombatTracker;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Session statistics counted on this client: kills (the target you last hit died), deaths,
 * K/D, confirmed hits and best combo. They are counted from what your client sees, so a kill
 * credited by the server to someone else can still count here if you hit last.
 */
public final class SessionHud extends HudModule {
    private int kills, deaths, hits, bestCombo;
    private boolean wasDead;
    private final Setting.Bool showKd, showHits, showCombo;

    public SessionHud() {
        super("Session Stats", "Kills, deaths, K/D, hits and best combo this session.", 0.01, 0.42);
        showKd = add(new Setting.Bool("K/D", true));
        showHits = add(new Setting.Bool("Hits", true));
        showCombo = add(new Setting.Bool("Best combo", true));
        add(new Setting.Action("Reset counters", new Runnable() {
            @Override
            public void run() {
                kills = deaths = hits = bestCombo = 0;
            }
        }));
        // counters keep running even while the element is hidden
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onHit(CombatTracker.HitEvent e) {
                hits++;
                bestCombo = Math.max(bestCombo, CombatTracker.combo());
            }

            @SubscribeEvent
            public void onKill(CombatTracker.KillEvent e) {
                kills++;
            }

            @SubscribeEvent
            public void onTick(TickEvent.ClientTickEvent e) {
                if (e.phase != TickEvent.Phase.END) return;
                Minecraft mc = Minecraft.getMinecraft();
                boolean dead = mc.thePlayer != null && (mc.thePlayer.isDead || mc.thePlayer.getHealth() <= 0f);
                if (dead && !wasDead) deaths++;
                wasDead = dead;
            }
        });
    }

    private int rows() {
        return 2 + (showKd.on() ? 1 : 0) + (showHits.on() ? 1 : 0) + (showCombo.on() ? 1 : 0);
    }

    @Override
    public float width() {
        return 84;
    }

    @Override
    public float height() {
        return 6 + 10 * rows();
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        panel(x, y, width(), height());
        float ry = y + 4;
        row(x, ry, "Kills", String.valueOf(kills));
        ry += 10;
        row(x, ry, "Deaths", String.valueOf(deaths));
        if (showKd.on()) {
            ry += 10;
            row(x, ry, "K/D", String.format(java.util.Locale.ROOT, "%.2f", deaths == 0 ? kills : kills / (float) deaths));
        }
        if (showHits.on()) {
            ry += 10;
            row(x, ry, "Hits", String.valueOf(hits));
        }
        if (showCombo.on()) {
            ry += 10;
            row(x, ry, "Best combo", String.valueOf(bestCombo));
        }
    }

    private void row(float x, float y, String l, String v) {
        Gfx.text(l, x + 5, y, LABEL);
        Gfx.text(v, x + width() - 5 - Gfx.textWidth(v), y, value());
    }
}
