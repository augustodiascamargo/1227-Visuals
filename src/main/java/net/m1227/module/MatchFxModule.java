package net.m1227.module;

import java.util.Locale;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Victory and defeat moments. Defeat = your own death (health reaches 0).
 * Victory = a chat or title line containing a victory word ("victory",
 * "you won", "winner", "vitória", "venceu", "ganhou"...). Servers word this
 * differently, so the word list is a best effort; there is no universal
 * "match won" signal in Minecraft 1.8.9.
 */
public final class MatchFxModule extends Module {
    private static final String[] WIN = {"victory", "you won", "you win", "winner", "vitória", "vitoria", "venceu",
            "ganhou", "você venceu"};
    private final Setting.Bool victory, defeat, fireworks, screen;
    private final Setting.Col winColor, loseColor;
    private long winAt, loseAt;
    private boolean wasDead;
    private int fireworksLeft;
    private int fireworkTimer;

    public MatchFxModule() {
        super("Victory & Defeat", "Effects when you win a match or die.", Category.COMBAT, false);
        victory = add(new Setting.Bool("Victory effect", true));
        defeat = add(new Setting.Bool("Defeat effect", true));
        fireworks = add(new Setting.Bool("Fireworks around you", true)).showIf(() -> victory.on());
        screen = add(new Setting.Bool("Screen flash", true));
        winColor = add(new Setting.Col("Victory color", 0xFFC857));
        loseColor = add(new Setting.Col("Defeat color", 0xFF3B4E));
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (!victory.on() || e.message == null) return;
        String s = e.message.getUnformattedText().toLowerCase(Locale.ROOT);
        if (s.length() > 120) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        // ignore normal player chat ("<name> victory") to avoid false triggers
        if (s.startsWith("<") || s.contains(": ")) return;
        for (String w : WIN) {
            if (s.contains(w)) {
                win();
                return;
            }
        }
    }

    private void win() {
        long now = System.currentTimeMillis();
        if (now - winAt < 5000) return;
        winAt = now;
        if (fireworks.on()) {
            fireworksLeft = 7;
            fireworkTimer = 0;
        }
        Notifier.post(Notifier.Kind.STATE, "Victory", "GG", winColor.rgb(), 3000);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) {
            wasDead = false;
            return;
        }
        boolean dead = p.getHealth() <= 0f || p.isDead;
        if (dead && !wasDead && defeat.on()) {
            loseAt = System.currentTimeMillis();
            ash(p);
            Notifier.post(Notifier.Kind.STATE, "Defeat", "You died", loseColor.rgb(), 2600);
        }
        wasDead = dead;
        if (fireworksLeft > 0 && --fireworkTimer <= 0) {
            fireworkTimer = 5 + ParticleSystem.INSTANCE.rng.nextInt(5);
            fireworksLeft--;
            firework(p);
        }
    }

    private void ash(EntityPlayerSP p) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        int n = ps.scale(40f);
        for (int i = 0; i < n; i++) {
            Particle q = ps.glow(p.posX + ps.rand(-0.6f, 0.6f), p.posY + ps.rand(0f, 1.8f), p.posZ + ps.rand(-0.6f, 0.6f),
                    ps.rand(-0.3f, 0.3f), ps.rand(0.3f, 1.2f), ps.rand(-0.3f, 0.3f), ps.rand(1.5f, 2.6f), ps.rand(0.08f, 0.16f),
                    0.02f, loseColor.rgb(), 0x2A2A2A, 0.85f);
            if (q == null) continue;
            q.sprite = i % 2 == 0 ? Sprite.SMOKE : Sprite.GLOW;
            q.sway = 0.4f;
            q.swayFreq = 2f;
            q.drag = 0.8f;
        }
    }

    private void firework(EntityPlayerSP p) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        float a = ps.rand(0f, 6.28f), d = ps.rand(3f, 6f);
        double x = p.posX + Math.cos(a) * d, y = p.posY + ps.rand(4f, 7f), z = p.posZ + Math.sin(a) * d;
        int c = ps.rng.nextInt(3) == 0 ? 0xFFFFFF : ps.rng.nextBoolean() ? winColor.rgb() : ParticleSystem.rainbow(ps.rand(0f, 1f));
        int n = ps.scale(46f);
        for (int i = 0; i < n; i++) {
            float th = ps.rand(0f, 6.28f), zz = ps.rand(-1f, 1f), rr = (float) Math.sqrt(1 - zz * zz), sp = ps.rand(5f, 7f);
            Particle q = ps.glow(x, y, z, rr * (float) Math.cos(th) * sp, zz * sp, rr * (float) Math.sin(th) * sp,
                    ps.rand(1.0f, 1.6f), 0.18f, 0.03f, c, winColor.rgb(), 1f);
            if (q == null) continue;
            q.drag = 2.6f;
            q.gravity = 2.2f;
            q.stretch = 0.03f;
            if (i % 4 == 0) q.sprite = Sprite.STAR;
        }
        ps.glow(x, y, z, 0f, 0f, 0f, 0.3f, 2.5f, 4f, 0xFFFFFF, c, 0.8f);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !screen.on()) return;
        long now = System.currentTimeMillis();
        int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
        float lose = 1f - Math.min(1f, (now - loseAt) / 2200f);
        float win = 1f - Math.min(1f, (now - winAt) / 1800f);
        if (lose > 0f) edge(w, h, loseColor.rgb(), lose * 0.75f);
        if (win > 0f) edge(w, h, winColor.rgb(), win * 0.55f);
    }

    private static void edge(int w, int h, int rgb, float a) {
        int c = Gfx.alpha(0xFF000000 | rgb, a), z = rgb & 0xFFFFFF;
        float t = h * 0.28f;
        Gfx.gradient(0, 0, w, t, c, z);
        Gfx.gradient(0, h - t, w, t, z, c);
        Gfx.rect(0, 0, w, h, Gfx.alpha(0xFF000000 | rgb, a * 0.12f));
    }
}
