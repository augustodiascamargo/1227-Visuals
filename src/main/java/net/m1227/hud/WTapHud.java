package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * W-Tap Indicator: feedback on your sprint resets. In 1.8 the server stops your sprint after a
 * sprint hit; the next hit only gets the extra knockback if you reset sprint in between (release
 * W or stop sprinting for at least one tick, then sprint again). This HUD estimates that from your
 * own sprint state at the end of each tick (the state the game sends) and grades every hit:
 * Perfect (1-2 tick reset), Good (3-5), Slow (6+), No reset, No sprint.
 *
 * Read-only practice tool: it only watches your keys and sprint state; it never presses, releases
 * or changes anything.
 */
public final class WTapHud extends HudModule {
    private static final int HIST = 40;
    private final byte[] hist = new byte[HIST]; // bit0 sprinting, bit1 forward held, bit2 hit
    private int head;
    private boolean lastSent, serverSprint, pendingHit;
    private int sinceHit = 999, resetLen, releaseDelay = -1;
    private String grade = "";
    private int gradeColor = 0xFFFFFFFF;
    private long gradeAt;
    private int hits, sprintHits, perfect;
    private final Setting.Bool timeline, stats;

    public WTapHud() {
        super("W-Tap Indicator", "Grades your sprint resets (W-taps) between hits.", 0.44, 0.70);
        timeline = add(new Setting.Bool("Timeline", true));
        stats = add(new Setting.Bool("Session stats", true));
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onAttack(AttackEntityEvent e) {
                if (!isEnabled() || e.entityPlayer != Minecraft.getMinecraft().thePlayer) return;
                hit(e.entityPlayer.isSprinting());
            }

            @SubscribeEvent
            public void onTick(TickEvent.ClientTickEvent e) {
                if (e.phase == TickEvent.Phase.END && isEnabled()) tick();
            }
        });
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        boolean sprint = mc.thePlayer.isSprinting();
        boolean fwd = mc.gameSettings.keyBindForward.isKeyDown();
        // the game sends a sprint change only when the state at the end of the tick differs from the last one sent
        if (sprint != lastSent) {
            lastSent = sprint;
            serverSprint = sprint;
        }
        if (sinceHit < 999) sinceHit++;
        if (!sprint) {
            if (resetLen == 0) releaseDelay = sinceHit;
            resetLen++;
        }
        hist[head] = (byte) ((sprint ? 1 : 0) | (fwd ? 2 : 0) | (pendingHit ? 4 : 0));
        head = (head + 1) % HIST;
        pendingHit = false;
    }

    private void hit(boolean clientSprinting) {
        boolean effective = serverSprint;
        boolean chained = sinceHit <= 30; // previous hit within 1.5 s
        hits++;
        if (effective) {
            sprintHits++;
            if (!chained) {
                set("Sprint hit", 0xFF3DDC97);
            } else if (resetLen <= 2) {
                perfect++;
                set("Perfect", 0xFF3DDC97);
            } else if (resetLen <= 5) {
                set("Good", 0xFFB6E35A);
            } else {
                set("Slow", 0xFFFFC24A);
            }
            serverSprint = false; // the server stops your sprint after a sprint hit
        } else if (clientSprinting) {
            set("No reset", 0xFFFF6B6B);
        } else {
            set("No sprint", 0xFF9AA0AE);
        }
        lastResetLen = resetLen;
        lastDelay = releaseDelay;
        sinceHit = 0;
        resetLen = 0;
        releaseDelay = -1;
        pendingHit = true;
    }

    private int lastResetLen, lastDelay = -1;

    private void set(String g, int c) {
        grade = g;
        gradeColor = c;
        gradeAt = System.currentTimeMillis();
    }

    @Override
    public float width() {
        return 124;
    }

    @Override
    public float height() {
        return 30 + (timeline.on() ? 9 : 0) + (stats.on() ? 10 : 0);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        panel(x, y, width(), height());
        String g = grade;
        int gc = gradeColor;
        if (editing && g.isEmpty()) {
            g = "Perfect";
            gc = 0xFF3DDC97;
        }
        long age = System.currentTimeMillis() - gradeAt;
        float pop = editing ? 1f : Math.max(0f, 1f - age / 2500f);
        Gfx.text("W-Tap", x + 5, y + 4, LABEL);
        if (!g.isEmpty() && pop > 0f) {
            int alpha = (int) (80 + 175 * pop);
            Gfx.textScaled(g, x + width() - 5 - Gfx.textWidth(g, 1.25f), y + 3, 1.25f, (alpha << 24) | (gc & 0xFFFFFF));
        }
        String detail = lastResetLen > 0 || editing
                ? "reset " + (editing ? 2 : lastResetLen) + "t" + (lastDelay >= 0 || editing ? "  after " + (editing ? 1 : lastDelay) + "t" : "")
                : "-";
        Gfx.text(detail, x + 5, y + 17, 0xFFFFFFFF);
        float cy = y + 29;
        if (timeline.on()) {
            float bw = (width() - 10) / HIST;
            for (int i = 0; i < HIST; i++) {
                int b = editing ? (i % 9 == 0 ? 4 : 0) | (i % 9 == 1 || i % 9 == 2 ? 0 : 1) : hist[(head + i) % HIST];
                int col = (b & 1) != 0 ? (accent() & 0x00FFFFFF) | 0xC0000000 : 0x40FFFFFF;
                Gfx.rect(x + 5 + i * bw, cy, Math.max(1f, bw - 0.6f), 4, col);
                if ((b & 4) != 0) Gfx.rect(x + 5 + i * bw, cy - 1.5f, Math.max(1f, bw - 0.6f), 7, 0xFFFFFFFF);
            }
            cy += 9;
        }
        if (stats.on()) {
            int h = editing ? 24 : hits, s = editing ? 19 : sprintHits, p = editing ? 11 : perfect;
            String st = h == 0 ? "no hits yet" : Math.round(100f * s / h) + "% sprint hits  ·  " + p + " perfect";
            Gfx.text(st, x + 5, cy, LABEL);
        }
    }
}
