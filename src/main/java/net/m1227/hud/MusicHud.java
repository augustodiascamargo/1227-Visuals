package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.module.MusicModule;
import net.m1227.music.MusicAnalysis;
import net.m1227.music.NowPlaying;
import net.m1227.music.Track;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Now Playing widget: cover (square, round or a spinning vinyl), title, artist, album, where it
 * plays (1227, Spotify, Chrome...), progress with times, play state and volume. While the chat
 * is open the buttons work with the mouse (previous, play/pause, next; scroll = volume for the
 * 1227 player). Appears and disappears smoothly when a song starts or stops.
 */
public final class MusicHud extends HudModule {
    private final Setting.Mode coverStyle, place;
    private final Setting.Bool showAlbum, showTimes, showSource, showControls, showVolume, progress, beat;
    private float vis, spin;
    private long last;
    // last drawn content, kept while fading out
    private String lTitle = "", lArtist = "", lAlbum = "", lSource = "";
    // button rects in screen GUI units, for clicks while the chat is open
    private final float[][] buttons = new float[3][4];
    private boolean buttonsValid;

    public MusicHud() {
        super("Now Playing", "What's playing: cover, song, progress and controls (click them with the chat open).", 0.01, 0.74);
        place = add(new Setting.Mode("Position", "Custom (HUD editor)", "Custom (HUD editor)", "Top left", "Top right", "Bottom left", "Bottom right", "Center"));
        coverStyle = add(new Setting.Mode("Cover", "Square", "Square", "Circle", "Vinyl", "Off"));
        showAlbum = add(new Setting.Bool("Album", true));
        showSource = add(new Setting.Bool("Where it plays", true));
        progress = add(new Setting.Bool("Progress bar", true));
        showTimes = add(new Setting.Bool("Time and duration", true));
        showControls = add(new Setting.Bool("Controls", true));
        showVolume = add(new Setting.Bool("Volume", false));
        beat = add(new Setting.Bool("Pulse with the music", true));
    }

    private boolean cover() {
        return !coverStyle.is("Off");
    }

    @Override
    public float width() {
        return 176;
    }

    @Override
    public float height() {
        float h = 40;
        if (showAlbum.on()) h += 1;
        if (progress.on() || showTimes.on()) h += 10;
        if (showControls.on()) h += 14;
        return h;
    }

    @Override
    public float screenX(ScaledResolution sr) {
        if (place.is("Custom (HUD editor)")) return super.screenX(sr);
        float w = width() * scale.f();
        if (place.is("Top left") || place.is("Bottom left")) return 4;
        if (place.is("Center")) return (sr.getScaledWidth() - w) / 2f;
        return sr.getScaledWidth() - w - 4;
    }

    @Override
    public float screenY(ScaledResolution sr) {
        if (place.is("Custom (HUD editor)")) return super.screenY(sr);
        float h = height() * scale.f();
        if (place.is("Top left") || place.is("Top right")) return 4;
        if (place.is("Center")) return 4 + 20;
        return sr.getScaledHeight() - h - 4;
    }

    @Override
    protected boolean hasContent() {
        NowPlaying.wantBridge();
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        boolean has = NowPlaying.get().source != NowPlaying.Source.NONE;
        vis += ((has ? 1f : 0f) - vis) * Math.min(1f, dt * 5f);
        if (has) spin += dt * (NowPlaying.get().playing ? 40f : 0f);
        return vis > 0.02f;
    }

    private static int a(int argb, float k) {
        return Gfx.alpha(argb, k);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        MusicModule m = MusicModule.INSTANCE;
        if (m == null) return;
        NowPlaying.Info np = NowPlaying.get();
        boolean real = np.source != NowPlaying.Source.NONE;
        float k = editing ? 1f : vis;
        if (real) {
            lTitle = np.title;
            lArtist = np.artist.isEmpty() ? "" : np.artist;
            lAlbum = np.album == null ? "" : np.album;
            lSource = np.app;
        } else if (editing) {
            lTitle = "Song title";
            lArtist = "Artist";
            lAlbum = "Album";
            lSource = "1227";
        }
        // slide in from the side a little while appearing
        float off = (1f - k) * 12f;
        x -= off;
        float w = width(), h = height();
        if (k > 0.35f || editing) panel(x, y, w, h);
        float pulse = beat.on() ? MusicAnalysis.INSTANCE.beat * 0.6f : 0f;
        if (beat.on()) MusicAnalysis.INSTANCE.update();
        float tx = x + 6;
        if (cover()) {
            float cs = 30, cx = x + 5, cy = y + 5;
            ResourceLocation r = null;
            Track t = m.player.current();
            if (np.source == NowPlaying.Source.LOCAL && t != null) r = m.cover(t.cover);
            else if (np.source == NowPlaying.Source.MEDIA) r = NowPlaying.mediaCover();
            drawCover(r, cx, cy, cs, k, pulse);
            tx = x + 41;
        }
        float tw = x + w - tx - 6;
        Gfx.text(Gfx.trim(lTitle, tw), tx, y + 6, a(0xFFFFFFFF, k));
        String sub = lArtist;
        if (showSource.on() && !lSource.isEmpty()) sub = sub.isEmpty() ? lSource : sub + "  ·  " + lSource;
        Gfx.text(Gfx.trim(sub, tw), tx, y + 16, a(LABEL, k));
        float ly = y + 26;
        if (showAlbum.on() && !lAlbum.isEmpty()) Gfx.textScaled(Gfx.trim(lAlbum, tw / 0.8f), tx, ly, 0.8f, a(0xFF7D8292, k));
        ly = y + 40 + (showAlbum.on() ? 1 : 0);
        float pos = real ? np.position() : 63f, dur = real ? np.duration : 214f;
        float bx = x + 6, bw = w - 12;
        if (progress.on() || showTimes.on()) {
            if (progress.on()) {
                float f = dur > 0 && pos >= 0 ? Math.min(1f, pos / dur) : 0f;
                Gfx.roundedRect(bx, ly, bw, 2, 1, a(0x40FFFFFF, k));
                if (pos >= 0 && dur > 0) Gfx.roundedRect(bx, ly, bw * f, 2, 1, a(value(), k));
            }
            if (showTimes.on()) {
                String l = pos >= 0 ? Track.fmt(pos) : (np.timeline || editing ? "0:00" : "live");
                String r = dur > 0 ? Track.fmt(dur) : "--:--";
                Gfx.textScaled(l, bx, ly + 4, 0.72f, a(LABEL, k));
                Gfx.textScaled(r, bx + bw - Gfx.textWidth(r, 0.72f), ly + 4, 0.72f, a(LABEL, k));
                if (real && !np.playing) Gfx.textScaled("paused", bx + bw / 2f - Gfx.textWidth("paused", 0.72f) / 2f, ly + 4, 0.72f, a(0xFFFFC857, k));
            }
            ly += 10;
        }
        buttonsValid = false;
        if (showControls.on()) {
            float cy = ly + 6, cx = x + w / 2f;
            boolean playing = real ? np.playing : true;
            controls(cx, cy, playing, k);
            if (showVolume.on() && (np.source == NowPlaying.Source.LOCAL || editing)) {
                float v = m.volume.f();
                Gfx.roundedRect(x + w - 44, cy - 1, 36, 2, 1, a(0x40FFFFFF, k));
                Gfx.roundedRect(x + w - 44, cy - 1, 36 * v, 2, 1, a(value(), k));
                Gfx.textScaled("vol", x + w - 58, cy - 3, 0.7f, a(LABEL, k));
            }
        }
        GlStateManager.enableBlend();
    }

    private void controls(float cx, float cy, boolean playing, float k) {
        int c = a(0xFFECEEF4, k), dim = a(0xFFA9ADBA, k);
        // previous
        Gfx.quad(cx - 22, cy, cx - 16, cy - 4, cx - 16, cy + 4, cx - 22, cy, dim);
        Gfx.quad(cx - 28, cy, cx - 22, cy - 4, cx - 22, cy + 4, cx - 28, cy, dim);
        // play / pause
        Gfx.circle(cx, cy, 6.5f, a(value(), k * 0.9f));
        if (playing) {
            Gfx.rect(cx - 2.6f, cy - 3, 1.8f, 6, 0xFF0B0C11);
            Gfx.rect(cx + 0.8f, cy - 3, 1.8f, 6, 0xFF0B0C11);
        } else {
            Gfx.quad(cx - 2, cy - 3.5f, cx - 2, cy + 3.5f, cx + 3.5f, cy, cx + 3.5f, cy, 0xFF0B0C11);
        }
        // next
        Gfx.quad(cx + 16, cy - 4, cx + 22, cy, cx + 22, cy, cx + 16, cy + 4, dim);
        Gfx.quad(cx + 22, cy - 4, cx + 28, cy, cx + 28, cy, cx + 22, cy + 4, dim);
        // screen-space hit boxes for clicks in the chat: draw() works in space scaled by the HUD scale
        float s = scale.f();
        set(0, (cx - 30) * s, (cy - 7) * s, 16 * s, 14 * s);
        set(1, (cx - 8) * s, (cy - 8) * s, 16 * s, 16 * s);
        set(2, (cx + 14) * s, (cy - 7) * s, 16 * s, 14 * s);
        buttonsValid = true;
    }

    private void set(int i, float x, float y, float w, float h) {
        buttons[i][0] = x;
        buttons[i][1] = y;
        buttons[i][2] = w;
        buttons[i][3] = h;
    }

    private void drawCover(ResourceLocation r, float x, float y, float s, float k, float pulse) {
        String st = coverStyle.get();
        float cx = x + s / 2f, cy = y + s / 2f;
        if (st.equals("Vinyl")) {
            float rr = s / 2f + pulse;
            Gfx.circle(cx, cy, rr, a(0xFF0A0A0D, k));
            for (int i = 1; i <= 4; i++) Gfx.annulus(cx, cy, rr * (0.55f + i * 0.1f), rr * (0.55f + i * 0.1f) + 0.4f, 0, Math.PI * 2, a(0x22FFFFFF, k));
            if (r != null) texturedCircle(r, cx, cy, rr * 0.48f, spin, k);
            else Gfx.circle(cx, cy, rr * 0.48f, a(value(), k * 0.8f));
            Gfx.circle(cx, cy, 1.4f, a(0xFF0A0A0D, k));
            // a highlight that stays still while the disc turns
            Gfx.annulus(cx, cy, rr * 0.7f, rr * 0.95f, -2.4, -1.9, a(0x18FFFFFF, k));
            return;
        }
        if (r == null) {
            if (st.equals("Circle")) Gfx.circle(cx, cy, s / 2f, a(value(), 0.35f * k));
            else Gfx.roundedRect(x, y, s, s, 4, a(value(), 0.35f * k));
            Gfx.textScaled("♪".equals("") ? "" : "", cx, cy, 1f, 0);
            return;
        }
        if (st.equals("Circle")) {
            texturedCircle(r, cx, cy, s / 2f, 0f, k);
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(r);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GL11.glColor4f(1f, 1f, 1f, k);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y + s);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x + s, y + s);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x + s, y);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void texturedCircle(ResourceLocation r, float cx, float cy, float rad, float rotDeg, float k) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(r);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GL11.glColor4f(1f, 1f, 1f, k);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glTexCoord2f(0.5f, 0.5f);
        GL11.glVertex2f(cx, cy);
        double rot = Math.toRadians(rotDeg);
        for (int i = 0; i <= 40; i++) {
            double an = Math.PI * 2 * i / 40;
            GL11.glTexCoord2f(0.5f + 0.5f * (float) Math.cos(an + rot), 0.5f + 0.5f * (float) Math.sin(an + rot));
            GL11.glVertex2f(cx + (float) Math.cos(an) * rad, cy + (float) Math.sin(an) * rad);
        }
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------- clicks with the chat open

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (!(e.gui instanceof GuiChat) || !buttonsValid || !showControls.on()) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        float mx = Mouse.getEventX() * sr.getScaledWidth() / (float) mc.displayWidth;
        float my = sr.getScaledHeight() - Mouse.getEventY() * sr.getScaledHeight() / (float) mc.displayHeight - 1;
        MusicModule m = MusicModule.INSTANCE;
        if (m == null) return;
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && NowPlaying.get().source == NowPlaying.Source.LOCAL) {
            float x = screenX(sr), y = screenY(sr);
            if (mx >= x && my >= y && mx <= x + width() * scale.f() && my <= y + height() * scale.f()) {
                m.volume.setClamped(m.volume.get() + (wheel > 0 ? 0.05 : -0.05));
                e.setCanceled(true);
            }
            return;
        }
        if (!Mouse.getEventButtonState() || Mouse.getEventButton() != 0) return;
        for (int i = 0; i < 3; i++) {
            float[] b = buttons[i];
            if (mx >= b[0] && my >= b[1] && mx <= b[0] + b[2] && my <= b[1] + b[3]) {
                if (i == 0) m.skip(false);
                else if (i == 1) m.playPause();
                else m.skip(true);
                e.setCanceled(true);
                return;
            }
        }
    }
}
