package net.m1227.module;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.m1227.ui.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * A floating pill at the top of the screen with three layouts, picked by
 * priority: a notification (module toggles, presets, kills, combos, low
 * health, potion effects...), a combat card while you are fighting
 * (target, health bar, combo) and the info row (fps, ping, time...).
 * Switching layouts morphs the pill with a spring and cross-fades content.
 * When idle it can collapse to a small dot.
 */
public final class DynamicIslandModule extends Module {
    private static final float PAD = 9f, GAP = 13f, H = 20f;
    private static final int INFO = 0, COMBAT = 1, NOTE = 2, IDLE = 3;

    private final Setting.Mode anchor, shape;
    private final Setting.Num scale, offsetY, opacity, noteTime, springSpeed;
    private final Setting.Col accent;
    private final Setting.Bool glass;
    private final Setting.Num glassBlur;
    private final Setting.Bool showName, showFps, showPing, showCoords, showBps, showTime, showServer, autoHide, combatCard;
    private final Setting.Bool nToggles, nCombat, nState, nSystem;
    private final Setting.Mode customText;

    private float w = -1f, h = H, vw, vh;
    private final float[] layer = new float[4];
    private long lastNanos, lastBuild;
    private final String[] seg = new String[8];
    private final int[] segColor = new int[8];
    private int segCount;
    private double lastX, lastZ;
    private float bps;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm");
    private final Set<Integer> effects = new HashSet<Integer>();
    private boolean lowHealthWarned;
    private float shownHealth = -1f;

    public DynamicIslandModule() {
        super("Dynamic Island", "Morphing HUD pill: info, combat card and notifications.", Category.HUD, true);
        anchor = add(new Setting.Mode("Anchor", "Top Center", "Top Left", "Top Center", "Top Right"));
        shape = add(new Setting.Mode("Shape", "Pill", "Pill", "Rounded", "Sharp"));
        scale = add(new Setting.Num("Scale", 1.0, 0.75, 1.5, 0.05, "x"));
        offsetY = add(new Setting.Num("Offset Y", 5, 0, 120, 1, "px"));
        opacity = add(new Setting.Num("Background", 0.92, 0.4, 1.0, 0.02, ""));
        accent = add(new Setting.Col("Accent", 0x8B5CFF));
        glass = add(new Setting.Bool("Glass background", true));
        glassBlur = add(new Setting.Num("Glass blur", 6, 1, 14, 1, "px"));
        springSpeed = add(new Setting.Num("Morph speed", 1.0, 0.4, 2.0, 0.1, "x"));
        autoHide = add(new Setting.Bool("Collapse when idle", false));
        combatCard = add(new Setting.Bool("Combat card", true));
        noteTime = add(new Setting.Num("Notification time", 1.0, 0.5, 3.0, 0.1, "x"));
        nToggles = add(new Setting.Bool("Notify: module toggles", true));
        nCombat = add(new Setting.Bool("Notify: kills and combos", true));
        nState = add(new Setting.Bool("Notify: health, effects, sprint", true));
        nSystem = add(new Setting.Bool("Notify: presets and profiles", true));
        showName = add(new Setting.Bool("Username", true));
        showFps = add(new Setting.Bool("FPS", true));
        showPing = add(new Setting.Bool("Ping", true));
        showServer = add(new Setting.Bool("Server IP", false));
        showCoords = add(new Setting.Bool("XYZ", false));
        showBps = add(new Setting.Bool("Speed (BPS)", false));
        showTime = add(new Setting.Bool("Local time", true));
        customText = add(new Setting.Mode("Custom text", "Off", "Off", "1227", "GG", "Visuals ON"));
    }

    // ------------------------------------------------------------ events

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Notifier.allowToggles = nToggles.on();
        Notifier.allowCombat = nCombat.on();
        Notifier.allowState = nState.on();
        Notifier.allowSystem = nSystem.on();
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) {
            effects.clear();
            return;
        }
        // low health warning (once until healed)
        if (p.getHealth() <= 6f && p.getHealth() > 0f) {
            if (!lowHealthWarned) {
                Notifier.post(Notifier.Kind.STATE, "Low health", String.format(Locale.ROOT, "%.0f HP", p.getHealth()), Notifier.BAD, dur(2200));
                lowHealthWarned = true;
            }
        } else if (p.getHealth() >= 10f) {
            lowHealthWarned = false;
        }
        // newly gained potion effects
        Collection<PotionEffect> active = p.getActivePotionEffects();
        Set<Integer> now = new HashSet<Integer>();
        for (PotionEffect pe : active) {
            now.add(pe.getPotionID());
            if (!effects.contains(pe.getPotionID())) {
                Potion pot = Potion.potionTypes[pe.getPotionID()];
                String name = pot == null ? "Effect" : I18n.format(pot.getName());
                if (pe.getAmplifier() > 0) name += " " + (pe.getAmplifier() + 1);
                boolean bad = pot != null && pot.isBadEffect();
                Notifier.post(Notifier.Kind.STATE, name, bad ? "Debuff" : "Effect gained", bad ? Notifier.BAD : Notifier.INFO, dur(1600));
            }
        }
        effects.clear();
        effects.addAll(now);
    }

    @SubscribeEvent
    public void onKill(CombatTracker.KillEvent e) {
        Notifier.post(Notifier.Kind.COMBAT, e.target.getName(), "Eliminated", Notifier.ACCENT, dur(2000));
    }

    @SubscribeEvent
    public void onHit(CombatTracker.HitEvent e) {
        int c = CombatTracker.combo();
        if (c == 5 || c == 10 || c == 20) Notifier.post(Notifier.Kind.COMBAT, c + " hit combo", "Keep going", Notifier.WARN, dur(1400));
    }

    private int dur(int ms) {
        return Math.round(ms * noteTime.f());
    }

    // ------------------------------------------------------------ render

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (nanos - lastNanos) / 1.0E9f);
        lastNanos = nanos;
        long now = System.currentTimeMillis();
        if (now - lastBuild > 250L) {
            build(mc, (now - lastBuild) / 1000f);
            lastBuild = now;
        }

        EntityLivingBase target = combatCard.on() ? CombatTracker.target(4000L) : null;
        if (target == mc.thePlayer) target = null;
        int mode;
        if (Notifier.active(now)) mode = NOTE;
        else if (target != null) mode = COMBAT;
        else if (autoHide.on()) mode = IDLE;
        else mode = INFO;

        float fadeK = 1f - (float) Math.exp(-14f * dt);
        for (int i = 0; i < 4; i++) layer[i] += ((i == mode ? 1f : 0f) - layer[i]) * fadeK;

        // target size of the active layout
        float tw, th = H;
        if (mode == NOTE) {
            tw = Math.max(150f, PAD * 2f + 18f + Gfx.textWidth(Notifier.title) + 14f + Gfx.textWidth(Notifier.detail) + 16f);
            th = H + 8f;
        } else if (mode == COMBAT) {
            tw = Math.max(170f, PAD * 2f + 18f + Gfx.textWidth(target.getName()) + 70f);
            th = H + 12f;
        } else if (mode == IDLE) {
            tw = 34f;
            th = 12f;
        } else {
            tw = PAD * 2f + 14f;
            for (int i = 0; i < segCount; i++) tw += GAP + 9f + Gfx.textWidth(seg[i]);
        }
        if (w < 0f) {
            w = tw;
            h = th;
        }
        float k = 220f * springSpeed.f() * springSpeed.f(), c = 2f * (float) Math.sqrt(k);
        vw += (k * (tw - w) - c * vw) * dt;
        vh += (k * (th - h) - c * vh) * dt;
        w += vw * dt;
        h += vh * dt;

        float sc = scale.f();
        int sw = e.resolution.getScaledWidth();
        String a = anchor.get();
        float x = a.equals("Top Left") ? 6f : (a.equals("Top Right") ? sw / sc - w - 6f : (sw / sc - w) / 2f);
        float y = offsetY.f() / sc;
        int acc = 0xFF000000 | accent.rgb();
        float r = shape.is("Pill") ? h / 2f : (shape.is("Rounded") ? 6f : 1.5f);

        GlStateManager.pushMatrix();
        GlStateManager.scale(sc, sc, 1f);
        Gfx.roundedRect(x - 2f, y - 1f, w + 4f, h + 4f, r + 2f, 0x30000000);
        Gfx.roundedRect(x - 0.5f, y - 0.5f, w + 1f, h + 1f, r + 0.5f, Gfx.alpha(0x22FFFFFF, opacity.f()));
        if (glass.on()) Glass.panel(x, y, w, h, r, sc, 0xFF0B0C11, opacity.f() * 0.62f, glassBlur.f() * e.resolution.getScaleFactor());
        else Gfx.roundedRect(x, y, w, h, r, Gfx.alpha(0xFF0B0C11, opacity.f()));

        Gfx.scissorOn(x * sc, y * sc, w * sc, h * sc, e.resolution.getScaleFactor());
        if (layer[INFO] > 0.02f) drawInfo(x, y, acc, layer[INFO]);
        if (layer[COMBAT] > 0.02f && target != null) drawCombat(x, y, acc, layer[COMBAT], target, dt);
        if (layer[NOTE] > 0.02f) drawNote(x, y, acc, layer[NOTE], now);
        if (layer[IDLE] > 0.02f) {
            Gfx.circle(x + w / 2f, y + h / 2f, 2.5f, Gfx.alpha(acc, layer[IDLE]));
        }
        Gfx.scissorOff();
        GlStateManager.popMatrix();
    }

    private void drawInfo(float x, float y, int acc, float a) {
        float ty = y + (h - 8f) / 2f + 0.5f;
        float cx = x + PAD;
        Gfx.circle(cx + 5f, y + h / 2f, 4.5f, Gfx.alpha(acc, 0.35f * a));
        Gfx.circle(cx + 5f, y + h / 2f, 2.5f, Gfx.alpha(acc, a));
        cx += 14f;
        for (int i = 0; i < segCount; i++) {
            cx += GAP * 0.5f;
            Gfx.rect(cx - 0.5f, y + 6f, 1f, h - 12f, Gfx.alpha(0x2EFFFFFF, a));
            cx += GAP * 0.5f;
            Gfx.circle(cx + 2.5f, y + h / 2f, 2.2f, Gfx.alpha(0xFF000000 | segColor[i], a));
            cx += 9f;
            Gfx.textShadow(seg[i], cx, ty, Gfx.alpha(0xFFF4F5F9, a));
            cx += Gfx.textWidth(seg[i]);
        }
    }

    private void drawCombat(float x, float y, int acc, float a, EntityLivingBase t, float dt) {
        float hp = t.getHealth(), max = Math.max(1f, t.getMaxHealth());
        if (shownHealth < 0f) shownHealth = hp;
        shownHealth += (hp - shownHealth) * (1f - (float) Math.exp(-10f * dt));
        float cx = x + PAD, cy = y + h / 2f;
        Gfx.circle(cx + 6f, cy, 6f, Gfx.alpha(0xFFFF5A6A, 0.25f * a));
        Gfx.line(cx + 3.5f, cy - 2.5f, cx + 8.5f, cy + 2.5f, 1.4f, Gfx.alpha(0xFFFF5A6A, a));
        Gfx.line(cx + 8.5f, cy - 2.5f, cx + 3.5f, cy + 2.5f, 1.4f, Gfx.alpha(0xFFFF5A6A, a));
        cx += 18f;
        Gfx.textShadow(t.getName(), cx, y + 6f, Gfx.alpha(0xFFFFFFFF, a));
        float barW = w - (cx - x) - PAD - 34f;
        float frac = Math.max(0f, Math.min(1f, shownHealth / max));
        int hc = frac > 0.5f ? 0xFF3DDC84 : (frac > 0.25f ? 0xFFFFC857 : 0xFFFF5A6A);
        Gfx.roundedRect(cx, y + h - 9f, barW, 3f, 1.5f, Gfx.alpha(0x33FFFFFF, a));
        Gfx.roundedRect(cx, y + h - 9f, Math.max(3f, barW * frac), 3f, 1.5f, Gfx.alpha(hc, a));
        String hpText = String.format(Locale.ROOT, "%.1f", hp / 2f) + "❤";
        Gfx.text(hpText, x + w - PAD - Gfx.textWidth(hpText), y + 6f, Gfx.alpha(hc, a));
        int combo = CombatTracker.combo();
        if (combo > 1) {
            String cs = "x" + combo;
            Gfx.text(cs, x + w - PAD - Gfx.textWidth(cs), y + h - 12f, Gfx.alpha(acc, a));
        }
    }

    private void drawNote(float x, float y, int acc, float a, long now) {
        float t = Math.min(1f, (now - Notifier.time) / (float) Math.max(1, Notifier.duration));
        float cy = y + h / 2f;
        int col = 0xFF000000 | Notifier.color;
        float cx = x + PAD;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 90.0);
        Gfx.circle(cx + 6f, cy, 6f, Gfx.alpha(col, 0.25f * a * pulse + 0.1f * a));
        Gfx.circle(cx + 6f, cy, 3.2f, Gfx.alpha(col, a));
        cx += 18f;
        Gfx.textShadow(Notifier.title, cx, cy - 4f, Gfx.alpha(0xFFFFFFFF, a));
        cx += Gfx.textWidth(Notifier.title) + 10f;
        String d = Notifier.detail;
        float pw = Gfx.textWidth(d) + 12f;
        Gfx.roundedRect(cx, cy - 6.5f, pw, 13f, 6.5f, Gfx.alpha(col, 0.22f * a));
        Gfx.text(d, cx + 6f, cy - 3.5f, Gfx.alpha(col, a));
        Gfx.rect(x + Math.min(h / 2f, 8f), y + h - 2f, (w - Math.min(h, 16f)) * (1f - t), 1f, Gfx.alpha(acc, 0.8f * a));
    }

    private void build(Minecraft mc, float sinceLast) {
        EntityPlayerSP p = mc.thePlayer;
        segCount = 0;
        if (showName.on()) add(p.getName(), accent.rgb());
        if (showFps.on()) add(Minecraft.getDebugFPS() + " fps", 0x3DDC84);
        if (showPing.on()) {
            int ping = 0;
            if (mc.getNetHandler() != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(p.getUniqueID());
                if (info != null) ping = info.getResponseTime();
            }
            add(ping + " ms", ping < 80 ? 0x3DDC84 : (ping < 160 ? 0xFFC857 : 0xFF5A6A));
        }
        if (showServer.on()) {
            ServerData sd = mc.getCurrentServerData();
            add(sd != null ? sd.serverIP : "Singleplayer", 0xFF9F43);
        }
        if (showCoords.on()) add((int) Math.floor(p.posX) + " " + (int) Math.floor(p.posY) + " " + (int) Math.floor(p.posZ), 0x4DA3FF);
        if (showBps.on()) {
            double dx = p.posX - lastX, dz = p.posZ - lastZ;
            if (sinceLast > 0f && sinceLast < 2f) bps = (float) Math.sqrt(dx * dx + dz * dz) / sinceLast;
            add(String.format(Locale.ROOT, "%.1f b/s", bps), 0x2DD4FF);
        }
        lastX = p.posX;
        lastZ = p.posZ;
        if (showTime.on()) add(clock.format(new Date()), 0xC77DFF);
        if (!customText.is("Off")) add(customText.get(), 0xFFFFFF);
    }

    private void add(String s, int col) {
        if (segCount >= seg.length) return;
        seg[segCount] = s;
        segColor[segCount] = col;
        segCount++;
    }
}
