package net.m1227.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.m1227.module.ClientModule;
import net.m1227.module.MusicModule;
import net.m1227.music.External;
import net.m1227.music.Track;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The music window: Library (search), Queue, History and Spotify / YouTube tabs, with the
 * now-playing bar (cover, title, previous / play / next, seek bar, volume, shuffle, repeat).
 */
public final class MusicScreen extends GuiScreen {
    private static final String[] TABS = {"Library", "Queue", "History", "Spotify & YouTube"};
    private static int tab;
    private String search = "";
    private boolean searchFocus = true;
    private float scroll;
    private final List<Runnable> clicks = new ArrayList<Runnable>();
    private final List<float[]> areas = new ArrayList<float[]>();
    private boolean dragSeek, dragVol;
    private float seekX, seekW, volX, volW;

    private static final int W = 460, H = 300, BAR = 58, ROW = 22;

    private MusicModule m() {
        return MusicModule.INSTANCE;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        m().rescanSilent();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        net.m1227.core.ConfigManager.markDirty();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void click(float x, float y, float w, float h, Runnable r) {
        areas.add(new float[]{x, y, w, h});
        clicks.add(r);
    }

    private static boolean in(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void texture(ResourceLocation r, float x, float y, float s) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(r);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
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
    }

    /** Cover of a track (or a placeholder with a note glyph). */
    private void cover(ResourceLocation r, float x, float y, float s, int acc) {
        if (r != null) {
            texture(r, x, y, s);
        } else {
            Gfx.roundedRect(x, y, s, s, 3, Gfx.mix(0xFF1E2130, acc, 0.25f));
            Gfx.text("♪", x + s / 2f - Gfx.textWidth("♪") / 2f, y + s / 2f - 4, 0xFFFFFFFF);
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        clicks.clear();
        areas.clear();
        MusicModule mod = m();
        int acc = 0xFF000000 | ClientModule.accent();
        Gfx.rect(0, 0, width, height, 0x66000000);
        float x = (width - W) / 2f, y = (height - H) / 2f;
        Gfx.roundedRect(x, y, W, H, 12, 0xF00E1016);
        Gfx.roundedOutline(x, y, W, H, 12, 0.8f, 0x22FFFFFF);
        Gfx.weight(TtfFont.BOLD);
        Gfx.text("Music", x + 14, y + 12, 0xFFFFFFFF);
        Gfx.weight(TtfFont.REGULAR);
        // tabs
        float tx = x + 70;
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            float tw = Gfx.textWidth(TABS[i]) + 16;
            boolean on = tab == i;
            Gfx.roundedRect(tx, y + 8, tw, 16, 8, on ? Gfx.alpha(acc, 0.85f) : 0xFF1A1D27);
            Gfx.text(TABS[i], tx + 8, y + 12, on ? 0xFFFFFFFF : 0xFFA9ADBA);
            click(tx, y + 8, tw, 16, () -> {
                tab = k;
                scroll = 0f;
            });
            tx += tw + 4;
        }
        // close
        int xc = in(mx, my, x + W - 22, y + 8, 16, 16) ? 0xFFFFFFFF : 0xFF8A8F9C;
        Gfx.line(x + W - 18, y + 12, x + W - 10, y + 20, 1.2f, xc);
        Gfx.line(x + W - 10, y + 12, x + W - 18, y + 20, 1.2f, xc);
        click(x + W - 22, y + 8, 16, 16, () -> mc.displayGuiScreen(null));

        float listY = y + 32, listH = H - 32 - BAR - 6;
        if (tab == 0) {
            Gfx.roundedRect(x + 12, listY, W - 24, 16, 8, searchFocus ? Gfx.mix(0xFF1E2130, acc, 0.25f) : 0xFF1A1D27);
            Icons.search(x + 22, listY + 8, 0xFF8A8F9C);
            String s = search.isEmpty() && !searchFocus ? "Search title, artist or album" : search + (searchFocus && System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
            Gfx.text(s, x + 32, listY + 4, search.isEmpty() ? 0xFF6A6F7C : 0xFFFFFFFF);
            click(x + 12, listY, W - 24, 16, () -> searchFocus = true);
            listY += 22;
            listH -= 22;
            List<Track> shown = new ArrayList<Track>();
            for (Track t : mod.library()) if (t.matches(search)) shown.add(t);
            if (mod.library().isEmpty()) {
                empty(x, listY, "No songs yet. Put .ogg or .wav files in config/1227-music",
                        "(\"Artist - Title.ogg\"; a cover.png in the folder becomes the cover)");
                button(x + W / 2f - 60, listY + 40, 120, "Open music folder", mod::openMusicFolder, mx, my, acc);
            } else {
                list(shown, x, listY, listH, mx, my, acc, true);
            }
        } else if (tab == 1) {
            if (mod.queue().isEmpty()) empty(x, listY, "The queue is empty", "Play something from the Library to fill it");
            else list(mod.queue(), x, listY, listH, mx, my, acc, false);
        } else if (tab == 2) {
            if (mod.history().isEmpty()) empty(x, listY, "Nothing played yet", "");
            else list(mod.history(), x, listY, listH, mx, my, acc, false);
        } else {
            external(x, listY, mx, my, acc);
        }
        bar(x, y + H - BAR, mx, my, acc);
        super.drawScreen(mx, my, pt);
    }

    private void empty(float x, float y, String a, String b) {
        Gfx.text(a, x + W / 2f - Gfx.textWidth(a) / 2f, y + 10, 0xFFC9CCD6);
        if (!b.isEmpty()) Gfx.text(b, x + W / 2f - Gfx.textWidth(b) / 2f, y + 22, 0xFF6A6F7C);
    }

    private void button(float x, float y, float w, String label, Runnable r, int mx, int my, int acc) {
        boolean hv = in(mx, my, x, y, w, 16);
        Gfx.roundedRect(x, y, w, 16, 8, hv ? acc : 0xFF22263A);
        Gfx.text(label, x + w / 2f - Gfx.textWidth(label) / 2f, y + 4, 0xFFFFFFFF);
        click(x, y, w, 16, r);
    }

    private void list(final List<Track> tracks, float x, float y, float h, int mx, int my, int acc, final boolean fromLibrary) {
        float max = Math.max(0, tracks.size() * ROW - h);
        scroll = Math.max(0, Math.min(max, scroll));
        Gfx.scissorOn(x + 8, y, W - 16, h);
        Track cur = m().player.current();
        int first = (int) (scroll / ROW);
        for (int i = first; i < tracks.size() && i * ROW - scroll < h; i++) {
            final Track t = tracks.get(i);
            final int idx = i;
            float ry = y + i * ROW - scroll;
            boolean hv = in(mx, my, x + 10, ry, W - 20, ROW - 2) && my >= y && my < y + h;
            boolean playing = t == cur;
            if (hv || playing) Gfx.roundedRect(x + 10, ry, W - 20, ROW - 2, 5, playing ? Gfx.alpha(acc, 0.25f) : 0x14FFFFFF);
            cover(m().cover(t.cover), x + 14, ry + 2, 16, acc);
            Gfx.text(Gfx.trim(t.title, 200), x + 36, ry + 2, playing ? 0xFFFFFFFF : 0xFFE6E8EF);
            Gfx.textScaled(Gfx.trim(t.artist + (t.album.isEmpty() ? "" : "  ·  " + t.album), 260), x + 36, ry + 12, 0.75f, 0xFF8A8F9C);
            String tm = t.time();
            Gfx.text(tm, x + W - 18 - Gfx.textWidth(tm), ry + 6, 0xFF8A8F9C);
            if (my >= y && my < y + h) {
                click(x + 10, ry, W - 20, ROW - 2, () -> {
                    if (fromLibrary) {
                        List<Track> copy = new ArrayList<Track>(tracks);
                        m().playFrom(copy, idx);
                    } else {
                        m().playFrom(new ArrayList<Track>(tracks), idx);
                    }
                });
            }
        }
        Gfx.scissorOff();
    }

    private void external(float x, float y, int mx, int my, int acc) {
        MusicModule mod = m();
        if (!External.available()) {
            empty(x, y, "Spotify / YouTube control works on Windows only", "");
            return;
        }
        String[] now = mod.external();
        Gfx.text("Now playing outside the game", x + 14, y + 2, 0xFF8A8F9C);
        Gfx.text(now == null ? "Nothing detected (open Spotify, or a YouTube tab, and press play)" : now[0] + ":  " + Gfx.trim(now[1], 330),
                x + 14, y + 14, now == null ? 0xFF6A6F7C : 0xFFFFFFFF);
        float ry = y + 34;
        ry = linkCard("Spotify", mod.spotifyLink.get(), x, ry, mx, my, acc, "Open in Spotify");
        ry = linkCard("YouTube", mod.youtubeLink.get(), x, ry + 6, mx, my, acc, "Open in browser");
        Gfx.textScaled("Set the playlist links in Music Player settings. They play in Spotify / your browser with your own account;",
                x + 14, ry + 8, 0.72f, 0xFF6A6F7C);
        Gfx.textScaled("the buttons below control them with the media keys. 1227 never downloads or streams music.", x + 14, ry + 17, 0.72f, 0xFF6A6F7C);
    }

    private float linkCard(String name, final String link, float x, float y, int mx, int my, int acc, String action) {
        MusicModule mod = m();
        Gfx.roundedRect(x + 12, y, W - 24, 46, 8, 0xFF161922);
        boolean set = link != null && !link.trim().isEmpty();
        String[] info = set ? mod.embed(link) : null;
        cover(set ? mod.embedCover(link) : null, x + 18, y + 6, 34, acc);
        Gfx.text(name, x + 60, y + 9, 0xFFFFFFFF);
        String sub = !set ? "No playlist link set" : info != null && info[0] != null ? info[0] : link;
        Gfx.textScaled(Gfx.trim(sub, 330), x + 60, y + 22, 0.8f, 0xFF8A8F9C);
        if (set) {
            button(x + W - 120, y + 15, 96, action, () -> {
                if (!External.open(link)) net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Music", "That link is not a Spotify / YouTube link", net.m1227.core.Notifier.BAD, 2000);
            }, mx, my, acc);
        }
        return y + 46;
    }

    private void bar(float x, float y, int mx, int my, int acc) {
        MusicModule mod = m();
        Gfx.rect(x + 12, y - 4, W - 24, 1, 0x14FFFFFF);
        Track t = mod.player.current();
        boolean ext = t == null && mod.external() != null;
        cover(t == null ? null : mod.cover(t.cover), x + 14, y + 6, 40, acc);
        String title = t != null ? t.title : ext ? mod.external()[1] : "Not playing";
        String sub = t != null ? t.artist : ext ? mod.external()[0] : "Pick a song from the Library";
        Gfx.text(Gfx.trim(title, 150), x + 62, y + 10, 0xFFFFFFFF);
        Gfx.textScaled(Gfx.trim(sub, 180), x + 62, y + 22, 0.8f, 0xFF8A8F9C);
        // controls
        float cx = x + W / 2f + 10;
        boolean playing = t != null && !mod.player.paused();
        iconButton(cx - 30, y + 6, 0, () -> mod.skip(false), mx, my);
        iconButton(cx - 9, y + 4, playing ? 2 : 1, mod::playPause, mx, my);
        iconButton(cx + 14, y + 6, 3, () -> mod.skip(true), mx, my);
        // seek bar (local songs)
        seekX = x + W / 2f - 70;
        seekW = 160;
        float len = t == null ? 0 : Math.max(mod.player.length(), 0.001f);
        float f = t == null ? 0 : Math.min(1f, mod.player.position() / len);
        Gfx.text(t == null ? "" : Track.fmt(mod.player.position()), seekX - 30, y + 37, 0xFF8A8F9C);
        Gfx.roundedRect(seekX, y + 40, seekW, 3, 1.5f, 0x30FFFFFF);
        Gfx.roundedRect(seekX, y + 40, seekW * f, 3, 1.5f, acc);
        if (t != null) Gfx.circle(seekX + seekW * f, y + 41.5f, 3, 0xFFFFFFFF);
        Gfx.text(t == null ? "" : Track.fmt(len), seekX + seekW + 6, y + 37, 0xFF8A8F9C);
        // volume
        volX = x + W - 92;
        volW = 64;
        Gfx.text("Vol", volX - 20, y + 15, 0xFF8A8F9C);
        Gfx.roundedRect(volX, y + 18, volW, 3, 1.5f, 0x30FFFFFF);
        Gfx.roundedRect(volX, y + 18, volW * mod.volume.f(), 3, 1.5f, acc);
        Gfx.circle(volX + volW * mod.volume.f(), y + 19.5f, 3, 0xFFFFFFFF);
        // shuffle / repeat
        String sh = mod.shuffle.on() ? "Shuffle on" : "Shuffle off";
        boolean shOn = mod.shuffle.on();
        Gfx.textScaled(sh, volX - 20, y + 32, 0.75f, shOn ? acc : 0xFF6A6F7C);
        click(volX - 20, y + 30, 50, 10, () -> mod.shuffle.set(!mod.shuffle.on()));
        String rp = "Repeat " + mod.repeat.get().toLowerCase();
        Gfx.textScaled(rp, volX + 32, y + 32, 0.75f, mod.repeat.is("Off") ? 0xFF6A6F7C : acc);
        click(volX + 32, y + 30, 50, 10, () -> mod.repeat.cycle(1));
    }

    /** 0 = previous, 1 = play, 2 = pause, 3 = next. Drawn as shapes so no font glyphs are needed. */
    private void iconButton(float x, float y, int kind, Runnable r, int mx, int my) {
        boolean hv = in(mx, my, x, y, 18, 18);
        Gfx.circle(x + 9, y + 9, 9, hv ? 0x30FFFFFF : 0x14FFFFFF);
        float cx = x + 9, cy = y + 9;
        int c = 0xFFFFFFFF;
        if (kind == 1) {
            Gfx.quad(cx - 2.5f, cy - 4f, cx - 2.5f, cy + 4f, cx + 4f, cy, cx + 4f, cy, c);
        } else if (kind == 2) {
            Gfx.rect(cx - 3f, cy - 3.5f, 2f, 7f, c);
            Gfx.rect(cx + 1f, cy - 3.5f, 2f, 7f, c);
        } else {
            float d = kind == 3 ? 1f : -1f;
            Gfx.quad(cx - 3f * d, cy - 3.5f, cx - 3f * d, cy + 3.5f, cx + 2f * d, cy, cx + 2f * d, cy, c);
            Gfx.rect(kind == 3 ? cx + 2f : cx - 3.5f, cy - 3.5f, 1.5f, 7f, c);
        }
        click(x, y, 18, 18, r);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button != 0) return;
        float y = (height - H) / 2f + H - BAR;
        if (in(mx, my, seekX - 2, y + 36, seekW + 4, 10) && m().player.current() != null) {
            dragSeek = true;
            return;
        }
        if (in(mx, my, volX - 2, y + 14, volW + 4, 10)) {
            dragVol = true;
            setVol(mx);
            return;
        }
        searchFocus = false;
        for (int i = areas.size() - 1; i >= 0; i--) {
            float[] a = areas.get(i);
            if (in(mx, my, a[0], a[1], a[2], a[3])) {
                clicks.get(i).run();
                return;
            }
        }
    }

    private void setVol(int mx) {
        m().volume.set((double) Math.max(0f, Math.min(1f, (mx - volX) / volW)));
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long t) {
        if (dragVol) setVol(mx);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragSeek) {
            float f = Math.max(0f, Math.min(1f, (mx - seekX) / seekW));
            m().seek(f * m().player.length());
        }
        dragSeek = dragVol = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) scroll -= Math.signum(d) * ROW * 2;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (tab == 0 && searchFocus) {
            if (key == Keyboard.KEY_BACK) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            } else if (ChatAllowedCharacters.isAllowedCharacter(c) && search.length() < 40) {
                search += c;
            }
            scroll = 0f;
            return;
        }
        if (key == Keyboard.KEY_SPACE) m().playPause();
    }
}
