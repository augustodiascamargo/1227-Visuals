package net.m1227.module;

import java.util.ArrayList;
import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.lyrics.Lyrics;
import net.m1227.lyrics.LyricsService;
import net.m1227.music.MusicAnalysis;
import net.m1227.music.NowPlaying;
import net.m1227.ui.Gfx;
import net.m1227.ui.TtfFont;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * 1227 Floating Lyrics: the lyrics of what you are listening to, in time with the music, floating
 * in the world in front of you (or on screen).
 *
 * Timing comes from the real playback position: the 1227 player's sound-card clock, or the
 * Windows media timeline for Spotify / YouTube when the player publishes one. Without a timeline
 * nothing pretends to be in sync: the text says so, and the lyrics screen offers a manual clock.
 *
 * Drawn locally only (no entities, no packets), in RenderWorldLastEvent at the interpolated
 * camera position, with every GL state it touches restored afterwards.
 */
public final class FloatingLyricsModule extends Module {
    public static FloatingLyricsModule INSTANCE;

    public static final String[] PRESETS = {"Soft", "Minimal", "Neon", "Cinematic", "Glass", "Aurora", "Retro", "Monochrome", "Custom"};

    private final Setting.Mode mode, preset, animation, highlight, weight, align, backdrop, overlayPos;
    private final Setting.Bool showNext, showPrev, outline, shadow, beatGlow, upright, occlusion, avoidWalls, countdown, status, online;
    private final Setting.Col textColor, wordColor, nextColor, glowColor;
    private final Setting.Num size, spacing, maxWidth, opacity, glow, distance, height, side, smooth, syncOffset, overlayScale;
    private final Setting.Num anchorX, anchorY, anchorZ;
    private final Setting.Text applied;

    public FloatingLyricsModule() {
        super("Floating Lyrics", "Synced lyrics floating in the world in front of you, or on screen.", Category.MUSIC, false);
        INSTANCE = this;
        mode = add(new Setting.Mode("Display", "World Floating", "World Floating", "Player Follow", "World Anchor", "Screen Overlay"));
        preset = add(new Setting.Mode("Style preset", "Soft", PRESETS));
        animation = add(new Setting.Mode("Animation", "Rise", "Rise", "Fade", "Drop", "Slide", "Typewriter", "Word by Word", "Karaoke", "Smooth Flow"));
        highlight = add(new Setting.Mode("Word highlight", "Current word", "Current word", "Karaoke fill", "Off"));
        showNext = add(new Setting.Bool("Show next line", true));
        showPrev = add(new Setting.Bool("Show previous line", false));
        weight = add(new Setting.Mode("Font weight", "Medium", "Light", "Regular", "Medium", "Bold"));
        size = add(new Setting.Num("Text size", 1.0, 0.4, 2.5, 0.05, "x"));
        spacing = add(new Setting.Num("Line spacing", 1.45, 1.0, 2.5, 0.05, "x"));
        maxWidth = add(new Setting.Num("Max line width", 1.0, 0.4, 2.0, 0.05, "x"));
        align = add(new Setting.Mode("Alignment", "Center", "Center", "Left", "Right"));
        textColor = add(new Setting.Col("Text color", 0xF2F0FF));
        wordColor = add(new Setting.Col("Current word color", 0xFFFFFF));
        nextColor = add(new Setting.Col("Next line color", 0xB9B4D6));
        glowColor = add(new Setting.Col("Glow color", 0xB79CFF));
        opacity = add(new Setting.Num("Opacity", 0.95, 0.2, 1.0, 0.05, ""));
        glow = add(new Setting.Num("Glow", 0.35, 0.0, 1.0, 0.05, ""));
        beatGlow = add(new Setting.Bool("Glow pulses with the music", true));
        outline = add(new Setting.Bool("Outline", false));
        shadow = add(new Setting.Bool("Shadow", true));
        backdrop = add(new Setting.Mode("Backdrop", "None", "None", "Soft", "Glass panel"));
        distance = add(new Setting.Num("Distance", 3.2, 1.5, 8.0, 0.1, " m")).showIf(() -> !mode.is("Screen Overlay"));
        height = add(new Setting.Num("Height", 0.35, -1.0, 2.0, 0.05, " m")).showIf(() -> !mode.is("Screen Overlay"));
        side = add(new Setting.Num("Side offset", 0.0, -2.0, 2.0, 0.05, " m")).showIf(() -> !mode.is("Screen Overlay"));
        smooth = add(new Setting.Num("Follow smoothness", 0.6, 0.0, 1.0, 0.05, "")).showIf(() -> !mode.is("Screen Overlay") && !mode.is("World Anchor"));
        upright = add(new Setting.Bool("Keep text upright", true)).showIf(() -> !mode.is("Screen Overlay"));
        occlusion = add(new Setting.Bool("Hidden behind blocks", false)).showIf(() -> !mode.is("Screen Overlay"));
        avoidWalls = add(new Setting.Bool("Stay in front of walls", true)).showIf(() -> mode.is("World Floating") || mode.is("Player Follow"));
        overlayPos = add(new Setting.Mode("Screen position", "Bottom", "Bottom", "Center", "Top")).showIf(() -> mode.is("Screen Overlay"));
        overlayScale = add(new Setting.Num("Screen text size", 1.0, 0.5, 2.5, 0.05, "x")).showIf(() -> mode.is("Screen Overlay"));
        syncOffset = add(new Setting.Num("Sync offset", 0, -3000, 3000, 50, " ms"));
        countdown = add(new Setting.Bool("Instrumental countdown", true));
        status = add(new Setting.Bool("Show search status", true));
        online = add(new Setting.Bool("Search lyrics online (LRCLIB)", true));
        add(new Setting.Action("Open lyrics screen", () -> Minecraft.getMinecraft().displayGuiScreen(new net.m1227.client.menu.LyricsScreen(Minecraft.getMinecraft().currentScreen))));
        add(new Setting.Action("Lyrics earlier (-0.25 s)", () -> LyricsService.nudge(-250)));
        add(new Setting.Action("Lyrics later (+0.25 s)", () -> LyricsService.nudge(250)));
        add(new Setting.Action("Anchor lyrics here", this::anchorHere)).showIf(() -> mode.is("World Anchor"));
        add(new Setting.Action("Open lyrics folder", () -> net.m1227.client.Os.open(LyricsService.ownFolder())));
        add(new Setting.Action("Search again", LyricsService::retry));
        add(new Setting.Action("Clear downloaded lyrics", LyricsService::clearCache));
        anchorX = add(new Setting.Num("ax", 0, -3.0E7, 3.0E7, 0, "")).showIf(() -> false);
        anchorY = add(new Setting.Num("ay", 70, -64, 512, 0, "")).showIf(() -> false);
        anchorZ = add(new Setting.Num("az", 0, -3.0E7, 3.0E7, 0, "")).showIf(() -> false);
        applied = add(new Setting.Text("applied preset", "Soft", 32, "")).showIf(() -> false);
    }

    public boolean onlineSearch() {
        return online.on();
    }

    // ---------------------------------------------------------------- presets

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!preset.is("Custom") && !preset.get().equals(applied.get())) {
            applyPreset(preset.get());
            applied.set(preset.get());
        }
    }

    private void look(String w, int text, int word, int next, int glowC, double glowAmt, boolean ol, boolean sh, String bd, String anim, String hl) {
        weight.set(w);
        textColor.setRgb(text);
        wordColor.setRgb(word);
        nextColor.setRgb(next);
        glowColor.setRgb(glowC);
        glow.set(glowAmt);
        outline.set(ol);
        shadow.set(sh);
        backdrop.set(bd);
        animation.set(anim);
        highlight.set(hl);
    }

    /** Each preset has its own character; colours stay readable on bright and dark skies. */
    void applyPreset(String p) {
        if (p.equals("Minimal")) look("Light", 0xFFFFFF, 0xFFFFFF, 0x9A9AA6, 0xFFFFFF, 0.0, false, true, "None", "Fade", "Off");
        else if (p.equals("Neon")) look("Bold", 0xE9FBFF, 0x3DF5FF, 0x8FB8C8, 0x3DF5FF, 0.75, false, false, "None", "Karaoke", "Karaoke fill");
        else if (p.equals("Cinematic")) look("Medium", 0xFFFFFF, 0xFFE7B3, 0xC9C3B5, 0xFFC27A, 0.15, false, true, "Soft", "Fade", "Current word");
        else if (p.equals("Glass")) look("Medium", 0xFFFFFF, 0xC9B8FF, 0xC2C6D6, 0x8B5CFF, 0.2, false, false, "Glass panel", "Slide", "Current word");
        else if (p.equals("Aurora")) look("Medium", 0xE8FFF6, 0x3DFFA0, 0x9FD6C6, 0x2DD4FF, 0.55, false, true, "None", "Smooth Flow", "Karaoke fill");
        else if (p.equals("Retro")) look("Bold", 0xFFD36B, 0xFF4FD8, 0xC9A0FF, 0xFF4FD8, 0.45, true, false, "None", "Typewriter", "Current word");
        else if (p.equals("Monochrome")) look("Regular", 0xD9D9D9, 0xFFFFFF, 0x7A7A7A, 0xFFFFFF, 0.0, false, true, "None", "Fade", "Current word");
        else look("Medium", 0xF2F0FF, 0xFFFFFF, 0xB9B4D6, 0xB79CFF, 0.35, false, true, "None", "Rise", "Current word"); // Soft
        net.m1227.core.ConfigManager.markDirty();
    }

    private void anchorHere() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        Vec3 look = mc.thePlayer.getLook(1f);
        double hx = look.xCoord, hz = look.zCoord, l = Math.sqrt(hx * hx + hz * hz);
        if (l < 1e-4) {
            hx = 0;
            hz = 1;
            l = 1;
        }
        anchorX.set(mc.thePlayer.posX + hx / l * 3.0);
        anchorY.set(mc.thePlayer.posY + mc.thePlayer.getEyeHeight() + 0.3);
        anchorZ.set(mc.thePlayer.posZ + hz / l * 3.0);
        net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Floating Lyrics", "Anchored in front of you", net.m1227.core.Notifier.GOOD, 1500);
    }

    // ---------------------------------------------------------------- timing

    /** What to draw this frame. */
    private static final class Frame {
        Lyrics lyrics;
        long t; // song time in ms (offsets applied), -1 unknown
        boolean playing, timeline;
        String note; // small status text, or null
    }

    private long manualStartNanos = -1L, manualPausedAt = -1L;

    /** Manual clock for songs without a timeline (started from the lyrics screen). */
    public void manualStart(long atMs) {
        manualStartNanos = System.nanoTime() - atMs * 1_000_000L;
        manualPausedAt = -1L;
    }

    public void manualToggle() {
        if (manualStartNanos < 0) return;
        if (manualPausedAt < 0) manualPausedAt = (System.nanoTime() - manualStartNanos) / 1_000_000L;
        else {
            manualStartNanos = System.nanoTime() - manualPausedAt * 1_000_000L;
            manualPausedAt = -1L;
        }
    }

    public void manualStop() {
        manualStartNanos = -1L;
        manualPausedAt = -1L;
    }

    public boolean manualRunning() {
        return manualStartNanos >= 0;
    }

    private String lastSongKey = "";

    /** Song time the lyrics follow right now (offsets applied), in ms, or -1 without a clock. */
    public long timeMs() {
        Frame f = frame();
        return f.timeline ? f.t : -1;
    }

    /** Raw playback position in ms (no offsets) for the editor, or -1. */
    public long rawMs() {
        float p = NowPlaying.get().position();
        if (p >= 0) return (long) (p * 1000f);
        if (manualStartNanos >= 0) return manualPausedAt >= 0 ? manualPausedAt : (System.nanoTime() - manualStartNanos) / 1_000_000L;
        return -1;
    }

    private Frame frame() {
        Frame f = new Frame();
        NowPlaying.Info np = NowPlaying.get();
        if (!np.key().equals(lastSongKey)) {
            lastSongKey = np.key();
            manualStop();
        }
        f.lyrics = LyricsService.lyrics();
        f.playing = np.playing;
        float pos = np.position();
        long off = (long) syncOffset.get().doubleValue() + LyricsService.songOffset();
        if (pos >= 0) {
            f.t = (long) (pos * 1000f) - off;
            f.timeline = true;
        } else if (manualStartNanos >= 0) {
            long ms = manualPausedAt >= 0 ? manualPausedAt : (System.nanoTime() - manualStartNanos) / 1_000_000L;
            f.t = ms - off;
            f.timeline = true;
            f.playing = manualPausedAt < 0;
            f.note = "Manual sync";
        } else {
            f.t = -1;
        }
        if (np.source == NowPlaying.Source.NONE) {
            f.lyrics = null;
            return f;
        }
        LyricsService.State st = LyricsService.state();
        if (f.lyrics == null) {
            if (status.on()) {
                if (st == LyricsService.State.SEARCHING) f.note = "Looking for lyrics…";
                else if (st == LyricsService.State.INSTRUMENTAL) f.note = "Instrumental";
                else if (st == LyricsService.State.NOT_FOUND) f.note = "No lyrics for this song";
                else if (st == LyricsService.State.ERROR) f.note = "Lyrics unavailable: " + LyricsService.message();
            }
        } else if (f.lyrics.synced && !f.timeline) {
            f.note = np.app + " shares no timeline: lyrics can't follow (lyrics screen → manual sync)";
        } else if (!f.lyrics.synced) {
            f.note = "Lyrics without timing";
        }
        return f;
    }

    // ---------------------------------------------------------------- layout & drawing

    private static final int[] SIZES = {24, 32, 48, 64, 96};
    private int fontPx = 48;
    private final float[] uv = new float[4];

    /** One text row to draw, in font pixels relative to the block's centre line. */
    private static final class Row {
        String[] words;
        float[] widths;
        float width, x, y, alpha = 1f, scale = 1f;
        int color, sungColor;
        /** -1 = no highlight; else index of current word, with fill 0..1 inside it. */
        int current = -1;
        float fill;
        /** Words fully revealed (typewriter / word by word), and characters of the next one. */
        int revealWords = Integer.MAX_VALUE, revealChars = Integer.MAX_VALUE;
        long[] wordTimes;
        long now;
        boolean popWords;
        float glow;
    }

    private float pick(float projectedPx) {
        int best = SIZES[0];
        for (int s : SIZES) {
            if (Math.abs(s - projectedPx) < Math.abs(best - projectedPx)) best = s;
        }
        // hysteresis: keep the current size unless the new one is clearly better
        if (Math.abs(fontPx - projectedPx) > Math.abs(best - projectedPx) * 1.3f) fontPx = best;
        return fontPx;
    }

    private static int argb(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private List<String[]> wrap(TtfFont f, String text, float maxW) {
        List<String[]> rows = new ArrayList<String[]>();
        List<String> cur = new ArrayList<String>();
        float w = 0, sp = f.widthPx(" ");
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            float ww = f.widthPx(word);
            if (!cur.isEmpty() && w + sp + ww > maxW) {
                rows.add(cur.toArray(new String[0]));
                cur.clear();
                w = 0;
            }
            w += (cur.isEmpty() ? 0 : sp) + ww;
            cur.add(word);
        }
        if (!cur.isEmpty()) rows.add(cur.toArray(new String[0]));
        return rows;
    }

    /**
     * Builds the rows for this moment. Everything is a function of the song time, so pausing
     * freezes the animation and seeking jumps straight to the right line.
     */
    private List<Row> layout(TtfFont f, Frame fr, float maxW) {
        List<Row> rows = new ArrayList<Row>();
        Lyrics l = fr.lyrics;
        float lh = f.ascentPx() * 1.25f * spacing.f();
        int base = textColor.rgb(), word = wordColor.rgb();
        if (l == null) return rows;
        if (!l.synced || !fr.timeline) {
            // a page of the plain text; with a known position it turns by song progress (approximate)
            int per = 3;
            int page = 0;
            NowPlaying.Info np = NowPlaying.get();
            if (fr.t >= 0 && np.duration > 0) {
                int pages = Math.max(1, (l.lines.size() + per - 1) / per);
                page = Math.min(pages - 1, (int) (fr.t / (np.duration * 1000f) * pages));
            }
            float y = 0;
            for (int i = page * per; i < Math.min(l.lines.size(), page * per + per); i++) {
                for (String[] ws : wrap(f, l.lines.get(i).text, maxW)) {
                    Row r = row(f, ws, base, word);
                    r.y = y;
                    r.alpha = 0.85f;
                    rows.add(r);
                    y += lh;
                }
            }
            centre(rows);
            return rows;
        }
        long t = fr.t;
        int idx = l.indexAt(t);
        String anim = animation.get();
        boolean flow = anim.equals("Smooth Flow");
        // instrumental gap: countdown dots instead of a line
        int nextIdx = l.nextText(idx);
        boolean gap = idx < 0 || l.lines.get(idx).blank();
        if (gap) {
            if (countdown.on() && nextIdx >= 0) {
                long ns = l.lines.get(nextIdx).start;
                long gapStart = idx < 0 ? 0 : l.lines.get(idx).start;
                if (ns - t > 2500) {
                    Row r = new Row();
                    r.words = new String[]{"• • •"};
                    float rem = (ns - t) / (float) Math.max(1, ns - gapStart);
                    int dots = Math.max(1, Math.min(3, (int) Math.ceil(rem * 3)));
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < 3; i++) b.append(i < dots ? "•" : " ").append(i < 2 ? "  " : "");
                    r.words = new String[]{b.toString()};
                    r.widths = new float[]{f.widthPx(r.words[0])};
                    r.width = r.widths[0];
                    r.color = base;
                    r.alpha = 0.7f + 0.3f * MusicAnalysis.INSTANCE.beat;
                    rows.add(r);
                }
                if (showNext.on() && ns - t < 6000) {
                    for (String[] ws : wrap(f, l.lines.get(nextIdx).text, maxW)) {
                        Row r = row(f, ws, nextColor.rgb(), nextColor.rgb());
                        r.scale = 0.8f;
                        r.y = (rows.size()) * lh;
                        r.alpha = 0.6f * Math.min(1f, (6000 - (ns - t)) / 800f);
                        rows.add(r);
                    }
                }
            }
            centre(rows);
            return rows;
        }
        Lyrics.Line line = l.lines.get(idx);
        long start = line.start, end = l.endOf(idx);
        float trans = 350f;
        float k = Math.min(1f, (t - start) / trans);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        // current line
        List<String[]> cur = wrap(f, line.text, maxW);
        String[] all = line.text.trim().split("\\s+");
        long[] times = l.wordTimes(idx, all);
        int wi = 0;
        int curWord = -1;
        float curFill = 0f;
        for (int i = 0; i < all.length; i++) {
            if (t >= times[i]) curWord = i;
        }
        if (curWord >= 0) {
            long ws = times[curWord], we = curWord + 1 < all.length ? times[curWord + 1] : Math.min(end, ws + 900);
            curFill = Math.max(0f, Math.min(1f, (t - ws) / (float) Math.max(1, we - ws)));
        }
        float y = 0;
        List<Row> curRows = new ArrayList<Row>();
        for (String[] ws : cur) {
            Row r = row(f, ws, base, word);
            r.y = y;
            r.now = t;
            String hl = anim.equals("Karaoke") ? "Karaoke fill" : highlight.get();
            if (!hl.equals("Off") && curWord >= wi && curWord < wi + ws.length) {
                r.current = curWord - wi;
                r.fill = hl.equals("Karaoke fill") ? curFill : 1f;
            }
            if (!hl.equals("Off") && curWord >= wi + ws.length) r.current = ws.length; // whole row already sung
            if (hl.equals("Karaoke fill")) r.sungColor = word;
            if (anim.equals("Typewriter")) {
                int chars = 0;
                for (String s : all) chars += s.length() + 1;
                long dur = Math.min((long) ((end - start) * 0.7f), chars * 40L);
                int shown = (int) (chars * Math.min(1f, (t - start) / (float) Math.max(1, dur)));
                int before = 0;
                for (int i = 0; i < wi; i++) before += all[i].length() + 1;
                int left = shown - before, full = 0;
                while (full < ws.length && left >= ws[full].length() + 1) {
                    left -= ws[full].length() + 1;
                    full++;
                }
                r.revealWords = full;
                r.revealChars = Math.max(0, left);
            } else if (anim.equals("Word by Word")) {
                int full = 0;
                for (int i = 0; i < ws.length; i++) {
                    if (t >= times[wi + i]) full = i + 1;
                }
                r.revealWords = full;
                r.revealChars = 0;
                r.wordTimes = java.util.Arrays.copyOfRange(times, wi, wi + ws.length);
                r.popWords = true;
            }
            wi += ws.length;
            curRows.add(r);
            y += lh;
        }
        float curH = y;
        // entrance of the current line, exit of the previous one
        float dy = 0, dx = 0, a = 1f;
        if (anim.equals("Rise")) dy = (1f - e) * lh * 0.6f;
        else if (anim.equals("Drop")) dy = -(1f - e) * lh * 0.6f;
        else if (anim.equals("Slide")) dx = (1f - e) * lh * 1.4f;
        if (!anim.equals("Typewriter") && !anim.equals("Word by Word") && !anim.equals("Karaoke")) a = e;
        if (flow) {
            dy = (1f - e) * (lh * 1.0f);
            a = 0.5f + 0.5f * e;
        }
        for (Row r : curRows) {
            r.y += dy;
            r.x += dx;
            r.alpha *= a;
            rows.add(r);
        }
        // previous line: leaving (always during the transition, or kept when "show previous" is on)
        int prevIdx = idx - 1;
        while (prevIdx >= 0 && l.lines.get(prevIdx).blank()) prevIdx--;
        if (prevIdx >= 0 && (showPrev.on() || flow || k < 1f)) {
            List<String[]> pw = wrap(f, l.lines.get(prevIdx).text, maxW);
            boolean stays = showPrev.on() || flow;
            float py = stays ? -pw.size() * lh * 0.85f + dy : 0;
            for (String[] ws : pw) {
                Row r = row(f, ws, stays ? nextColor.rgb() : base, word);
                if (stays) {
                    // kept above the current line, smaller and dimmer
                    r.y = py;
                    r.scale = 0.82f;
                    r.alpha = 0.45f;
                } else {
                    // leaving from where it was, the opposite way the new line comes in
                    r.y = py + (anim.equals("Rise") ? -e * lh * 0.6f : anim.equals("Drop") ? e * lh * 0.6f : 0f);
                    if (anim.equals("Slide")) r.x = -e * lh * 1.4f;
                    r.alpha = 1f - e;
                }
                rows.add(r);
                py += stays ? lh * 0.85f : lh;
            }
        }
        // next line preview
        if ((showNext.on() || flow) && nextIdx >= 0) {
            float ny = curH + lh * 0.15f + (flow ? dy : 0);
            for (String[] ws : wrap(f, l.lines.get(nextIdx).text, maxW)) {
                Row r = row(f, ws, nextColor.rgb(), nextColor.rgb());
                r.y = ny;
                r.scale = 0.8f;
                r.alpha = 0.55f * (flow ? 1f : e);
                rows.add(r);
                ny += lh * 0.85f;
            }
        }
        return rows;
    }

    private Row row(TtfFont f, String[] ws, int color, int sung) {
        Row r = new Row();
        r.words = ws;
        r.widths = new float[ws.length];
        float sp = f.widthPx(" ");
        for (int i = 0; i < ws.length; i++) {
            r.widths[i] = f.widthPx(ws[i]);
            r.width += r.widths[i] + (i > 0 ? sp : 0);
        }
        r.color = color;
        r.sungColor = sung;
        return r;
    }

    private static void centre(List<Row> rows) {
        if (rows.isEmpty()) return;
        float top = rows.get(0).y, bottom = rows.get(rows.size() - 1).y;
        float mid = (top + bottom) / 2f;
        for (Row r : rows) r.y -= mid;
    }

    /** Draws the rows in the current 2D space (1 unit = 1 font pixel, y down). */
    private void draw(TtfFont f, List<Row> rows, float alpha, String note, TtfFont small) {
        float op = opacity.f() * alpha;
        float pulse = beatGlow.on() ? MusicAnalysis.INSTANCE.beat * 0.5f + MusicAnalysis.INSTANCE.energy * 0.3f : 0f;
        float g = glow.f() * (1f + pulse);
        float sp = f.widthPx(" ");
        float asc = f.ascentPx();
        String al = align.get();
        // backdrop behind the whole block
        if (!backdrop.is("None") && !rows.isEmpty()) {
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Row r : rows) {
                float x0 = rowX(r, al);
                minX = Math.min(minX, x0);
                maxX = Math.max(maxX, x0 + r.width * r.scale);
                minY = Math.min(minY, r.y);
                maxY = Math.max(maxY, r.y + asc * 1.2f * r.scale);
            }
            float pad = asc * 0.45f;
            GlStateManager.disableTexture2D();
            if (backdrop.is("Soft")) {
                Gfx.roundedRect(minX - pad * 2, minY - pad, maxX - minX + pad * 4, maxY - minY + pad * 2, pad, argb(0x05060A, 0.38f * op));
            } else {
                Gfx.roundedRect(minX - pad, minY - pad, maxX - minX + pad * 2, maxY - minY + pad * 2, pad * 0.8f, argb(0x161A26, 0.55f * op));
                Gfx.roundedOutline(minX - pad, minY - pad, maxX - minX + pad * 2, maxY - minY + pad * 2, pad * 0.8f, Math.max(1f, asc * 0.04f), argb(0xFFFFFF, 0.18f * op));
            }
            GlStateManager.enableTexture2D();
        }
        for (Row r : rows) {
            float a = op * r.alpha;
            if (a <= 0.01f) continue;
            float x = rowX(r, al);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, r.y, 0);
            if (r.scale != 1f) GlStateManager.scale(r.scale, r.scale, 1f);
            String full = visible(r);
            // soft bloom behind the row
            if (g > 0.01f) {
                ParticleSystem.INSTANCE.bindAtlas();
                ParticleSystem.uv(Sprite.GLOW, uv);
                GlStateManager.enableTexture2D();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                int gc = glowColor.rgb();
                GL11.glColor4f(((gc >> 16) & 255) / 255f, ((gc >> 8) & 255) / 255f, (gc & 255) / 255f, Math.min(1f, 0.35f * g * a));
                float w = r.width + asc * 2.2f, h = asc * 2.6f, cx = r.width / 2f, cy = asc * 0.55f;
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(uv[0], uv[1]);
                GL11.glVertex3f(cx - w / 2, cy - h / 2, 0);
                GL11.glTexCoord2f(uv[0], uv[3]);
                GL11.glVertex3f(cx - w / 2, cy + h / 2, 0);
                GL11.glTexCoord2f(uv[2], uv[3]);
                GL11.glVertex3f(cx + w / 2, cy + h / 2, 0);
                GL11.glTexCoord2f(uv[2], uv[1]);
                GL11.glVertex3f(cx + w / 2, cy - h / 2, 0);
                GL11.glEnd();
                // tight glow around the letters
                float o = Math.max(1f, asc * 0.05f);
                for (int i = 0; i < 8; i++) {
                    double an = Math.PI * 2 * i / 8;
                    f.draw(full, (float) Math.cos(an) * o, (float) Math.sin(an) * o, 1f, argb(gc, 0.12f * g * a));
                }
            }
            if (shadow.on()) f.draw(full, asc * 0.06f, asc * 0.08f, 1f, argb(0x000000, 0.45f * a));
            if (outline.on()) {
                float o = Math.max(1f, asc * 0.045f);
                for (int i = 0; i < 8; i++) {
                    double an = Math.PI * 2 * i / 8;
                    f.draw(full, (float) Math.cos(an) * o, (float) Math.sin(an) * o, 1f, argb(0x14081E, 0.85f * a));
                }
            }
            // the words themselves, coloured by the highlight
            float pen = 0;
            for (int i = 0; i < r.words.length; i++) {
                String w = r.words[i];
                if (i > r.revealWords || (i == r.revealWords && r.revealChars <= 0)) break;
                String shown = i == r.revealWords ? w.substring(0, Math.min(w.length(), r.revealChars)) : w;
                float wa = a;
                float lift = 0;
                if (r.popWords && r.wordTimes != null && i < r.wordTimes.length) {
                    float p = Math.min(1f, (r.now - r.wordTimes[i]) / 160f);
                    wa *= p;
                    lift = (1f - p) * asc * 0.3f;
                }
                int col = r.color;
                if (r.current >= 0) {
                    if (i < r.current) col = r.sungColor != r.color ? r.sungColor : r.color;
                    else if (i == r.current) {
                        if (r.fill >= 1f || r.sungColor == r.color) col = wordColor.rgb();
                        else {
                            // karaoke: the sung part of the current word in the highlight colour
                            int n = Math.round(shown.length() * r.fill);
                            String done = shown.substring(0, n), rest = shown.substring(n);
                            f.draw(done, pen, lift, 1f, argb(wordColor.rgb(), wa));
                            f.draw(rest, pen + f.widthPx(done), lift, 1f, argb(r.color, wa));
                            pen += r.widths[i] + sp;
                            continue;
                        }
                    }
                }
                f.draw(shown, pen, lift, 1f, argb(col, wa));
                pen += r.widths[i] + sp;
            }
            GlStateManager.popMatrix();
        }
        if (note != null && small != null) {
            float y = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + asc * 1.6f;
            float w = small.widthPx(note);
            float x = al.equals("Left") ? 0 : al.equals("Right") ? -w : -w / 2f;
            float k = asc / Math.max(1f, small.ascentPx()) * 0.55f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(al.equals("Center") ? 0 : 0, y, 0);
            GlStateManager.scale(k, k, 1f);
            small.draw(note, x, 0, 1f, argb(0xC8CCD8, 0.75f * op));
            GlStateManager.popMatrix();
        }
    }

    private static String visible(Row r) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.words.length; i++) {
            if (i > r.revealWords || (i == r.revealWords && r.revealChars <= 0)) break;
            if (i > 0) b.append(' ');
            String w = r.words[i];
            b.append(i == r.revealWords ? w.substring(0, Math.min(w.length(), r.revealChars)) : w);
        }
        return b.toString();
    }

    private float rowX(Row r, String al) {
        if (al.equals("Left")) return r.x;
        if (al.equals("Right")) return r.x - r.width * r.scale;
        return r.x - r.width * r.scale / 2f;
    }

    // ---------------------------------------------------------------- world

    private double px, py, pz;
    private boolean placed;
    private long lastNanos;
    private float fade;

    private float fadeTo(boolean show, float dt) {
        fade += ((show ? 1f : 0f) - fade) * Math.min(1f, dt * 6f);
        return fade;
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent e) {
        if (mode.is("Screen Overlay") || net.m1227.module.CinematicModule.hideLyrics()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (mc.theWorld == null || view == null) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Frame fr = frame();
        List<Row> rows;
        boolean has = fr.lyrics != null || fr.note != null;
        float vis = fadeTo(has, dt);
        if (vis < 0.01f) return;
        MusicAnalysis.INSTANCE.update();
        float pt = e.partialTicks;
        RenderManager rm = mc.getRenderManager();
        double ex = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double ey = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt + view.getEyeHeight();
        double ez = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        double tx, ty, tz;
        if (mode.is("World Anchor")) {
            tx = anchorX.get();
            ty = anchorY.get();
            tz = anchorZ.get();
            px = tx;
            py = ty;
            pz = tz;
            placed = true;
        } else {
            boolean follow = mode.is("Player Follow");
            Vec3 look = view.getLook(pt);
            double lx = look.xCoord, ly = follow ? look.yCoord : 0, lz = look.zCoord;
            double l = Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (l < 1e-4) {
                lx = 0;
                lz = 1;
                l = 1;
            }
            lx /= l;
            ly /= l;
            lz /= l;
            double rx = -lz, rz = lx; // right, horizontal
            double rl = Math.sqrt(rx * rx + rz * rz);
            if (rl > 1e-4) {
                rx /= rl;
                rz /= rl;
            }
            double d = distance.get();
            if (avoidWalls.on()) {
                Vec3 from = new Vec3(ex, ey, ez), to = new Vec3(ex + lx * d, ey + ly * d, ez + lz * d);
                MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(from, to, false, true, false);
                if (hit != null && hit.hitVec != null) d = Math.max(0.8, hit.hitVec.distanceTo(from) - 0.3);
            }
            tx = ex + lx * d + rx * side.get();
            ty = ey + ly * d + height.get();
            tz = ez + lz * d + rz * side.get();
            ty = Math.max(ty, view.posY + 0.3); // never in the ground under you
            float sm = smooth.f();
            float rate = follow ? 60f + (8f - 60f) * sm : 30f + (2.5f - 30f) * sm;
            double jump = (tx - px) * (tx - px) + (ty - py) * (ty - py) + (tz - pz) * (tz - pz);
            if (!placed || jump > 144) {
                px = tx;
                py = ty;
                pz = tz;
                placed = true;
            } else {
                double k = 1 - Math.exp(-dt * rate);
                px += (tx - px) * k;
                py += (ty - py) * k;
                pz += (tz - pz) * k;
            }
        }
        double bob = mode.is("World Floating") ? Math.sin(now / 1e9 * 1.3) * 0.03 : 0;
        double dx = px - ex, dy = py + bob - ey, dz = pz - ez;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > 64) return;
        // line height in blocks, then the font size that matches its size on screen
        float lineBlocks = 0.16f * size.f();
        float fov = mc.gameSettings.fovSetting;
        float projected = (float) (lineBlocks / Math.max(0.5, dist) / (2 * Math.tan(Math.toRadians(fov / 2))) * mc.displayHeight);
        String wgt = weight.get();
        TtfFont f = TtfFont.get(wgt, (int) pick(projected * 1.3f));
        TtfFont small = TtfFont.get(TtfFont.REGULAR, 32);
        if (f == null) return;
        float blocksPerPx = lineBlocks / f.ascentPx();
        float maxW = 2.6f * maxWidth.f() / blocksPerPx;
        rows = layout(f, fr, maxW);
        float paused = fr.playing ? 1f : 0.6f;

        // ---- GL state (restored below)
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG), light = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        float lx0 = OpenGlHelper.lastBrightnessX, ly0 = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableFog();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            if (occlusion.on()) GlStateManager.enableDepth();
            else GlStateManager.disableDepth();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.translate(px - rm.viewerPosX, py + bob - rm.viewerPosY, pz - rm.viewerPosZ);
            GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
            if (!upright.on() || mode.is("Player Follow")) GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
            GlStateManager.scale(-blocksPerPx, -blocksPerPx, blocksPerPx);
            draw(f, rows, vis * paused, fr.note, small);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            if (cull) GlStateManager.enableCull();
            if (light) GlStateManager.enableLighting();
            if (fog) GlStateManager.enableFog();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx0, ly0);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    // ---------------------------------------------------------------- screen overlay

    private long lastOverlay;

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !mode.is("Screen Overlay")) return;
        if (net.m1227.module.CinematicModule.hideLyrics()) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        float dt = lastOverlay == 0 ? 0.016f : Math.min(0.1f, (now - lastOverlay) / 1e9f);
        lastOverlay = now;
        Frame fr = frame();
        float vis = fadeTo(fr.lyrics != null || fr.note != null, dt);
        if (vis < 0.01f) return;
        MusicAnalysis.INSTANCE.update();
        ScaledResolution sr = e.resolution;
        int sf = sr.getScaleFactor();
        float lineGui = 11f * overlayScale.f();
        TtfFont f = TtfFont.get(weight.get(), Math.max(12, Math.min(96, Math.round(lineGui * sf * 1.25f))));
        TtfFont small = TtfFont.get(TtfFont.REGULAR, Math.max(12, Math.min(48, Math.round(7f * sf))));
        if (f == null) return;
        float guiPerPx = lineGui / f.ascentPx();
        float maxW = sr.getScaledWidth() * 0.7f * Math.min(1f, maxWidth.f()) / guiPerPx;
        List<Row> rows = layout(f, fr, maxW);
        float cx = sr.getScaledWidth() / 2f;
        float cy = overlayPos.is("Top") ? sr.getScaledHeight() * 0.16f : overlayPos.is("Center") ? sr.getScaledHeight() * 0.42f : sr.getScaledHeight() * 0.72f;
        if (align.is("Left")) cx = sr.getScaledWidth() * 0.15f;
        else if (align.is("Right")) cx = sr.getScaledWidth() * 0.85f;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.scale(guiPerPx, guiPerPx, 1f);
            GlStateManager.enableBlend();
            draw(f, rows, vis * (fr.playing ? 1f : 0.6f), fr.note, small);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }
}
