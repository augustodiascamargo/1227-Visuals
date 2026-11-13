package net.m1227.client.menu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.Notifier;
import net.m1227.lyrics.LrcParser;
import net.m1227.lyrics.Lyrics;
import net.m1227.lyrics.LyricsService;
import net.m1227.module.FloatingLyricsModule;
import net.m1227.module.MusicModule;
import net.m1227.music.NowPlaying;
import net.m1227.music.Track;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * Lyrics editor: fix the timing of a song's lyrics (or write them from scratch) while it plays.
 * <ul>
 * <li>Space (or "Stamp") sets the selected line to the current playback time and moves to the
 * next line: tap along with the song to time every line.</li>
 * <li>Up/Down select, [ and ] move the selected line by 0.1 s, the text field edits its text,
 * Insert / Delete add and remove lines, "Shift all" moves every line.</li>
 * <li>"Test from here" jumps the 1227 player to just before the selected line.</li>
 * <li>Save writes a standard .lrc next to the song (or in config/1227-lyrics for Spotify /
 * YouTube), which then always wins over downloaded lyrics.</li>
 * </ul>
 */
public final class LyricsEditorScreen extends UiScreen {
    private final List<Lyrics.Line> lines = new ArrayList<Lyrics.Line>();
    private int sel;
    private boolean dirty;
    private String title = "", artist = "", album = "";
    private final File target;

    public LyricsEditorScreen(GuiScreen parent) {
        super(parent);
        NowPlaying.Info np = NowPlaying.get();
        target = LyricsService.saveTarget(np);
        title = np.title;
        artist = np.artist;
        album = np.album;
        Lyrics l = LyricsService.lyrics();
        if (l != null) {
            for (Lyrics.Line x : l.lines) lines.add(new Lyrics.Line(l.synced ? x.start : -1, x.text, null));
        }
        if (lines.isEmpty()) lines.add(new Lyrics.Line(-1, "", null));
    }

    private long now() {
        FloatingLyricsModule m = FloatingLyricsModule.INSTANCE;
        return m == null ? -1 : m.rawMs();
    }

    private void stamp() {
        long t = now();
        if (t < 0 || sel < 0 || sel >= lines.size()) {
            Notifier.post(Notifier.Kind.SYSTEM, "Lyrics editor", "No playback time: play the song (or start the manual clock)", Notifier.WARN, 2500);
            return;
        }
        lines.get(sel).start = t;
        dirty = true;
        if (sel < lines.size() - 1) sel++;
    }

    private void shift(int i, long ms) {
        Lyrics.Line l = lines.get(i);
        if (l.start < 0) return;
        l.start = Math.max(0, l.start + ms);
        dirty = true;
    }

    private Lyrics build() {
        List<Lyrics.Line> out = new ArrayList<Lyrics.Line>();
        boolean timed = false;
        for (Lyrics.Line l : lines) timed |= l.start >= 0;
        for (Lyrics.Line l : lines) {
            if (timed && l.start < 0) continue; // untimed lines can't be placed: left out of a synced file
            out.add(new Lyrics.Line(l.start, l.text, null));
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        Lyrics l = new Lyrics(out, timed);
        l.title = title;
        l.artist = artist;
        l.album = album;
        l.source = target.getName();
        return l;
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        float pw = Math.min(640, w - 20), px = (w - pw) / 2f, top = 12;
        Ui.textBold("Lyrics editor", px, top, DS.T_TITLE, DS.TEXT);
        long t = now();
        Ui.textRight((t >= 0 ? Track.fmt(t / 1000f) + String.format(".%d", (t / 100) % 10) : "no clock") + "   " + (dirty ? "unsaved" : "saved"),
                px + pw, top + 4, DS.T_BODY, dirty ? DS.WARN : DS.MUTED);
        Ui.text(Gfx.trim(title + (artist.isEmpty() ? "" : "  ·  " + artist) + "   →  " + target.getName(), pw), px, top + 20, DS.T_SMALL, DS.FAINT);
        float y = top + 34, lh = h - y - 92;
        Ui.card(px, y, pw, lh);
        float off = ui.beginScroll("lines", px + 4, y + 4, pw - 8, lh - 8);
        float ry = y + 6;
        for (int i = 0; i < lines.size(); i++) {
            final int k = i;
            Lyrics.Line l = lines.get(i);
            float yy = ry - off;
            boolean s = i == sel;
            if (s) Gfx.roundedRect(px + 6, yy - 2, pw - 12, 14, DS.R_SM, Gfx.alpha(DS.accent(), 0.22f));
            Ui.text(l.start >= 0 ? LrcParser.stamp(l.start) : "[--:--.--]", px + 10, yy, DS.T_SMALL, s ? DS.TEXT : DS.FAINT);
            Ui.text(Gfx.trim(l.text.isEmpty() ? "(instrumental gap)" : l.text, pw - 90), px + 70, yy, DS.T_BODY, l.text.isEmpty() ? DS.FAINT : s ? DS.TEXT : DS.MUTED);
            ui.click(px + 6, yy - 2, pw - 12, 14, () -> sel = k);
            ry += 14;
        }
        ui.endScroll(ry - y);
        // selected line tools
        float ty = y + lh + 6;
        if (sel >= 0 && sel < lines.size()) {
            final Lyrics.Line l = lines.get(sel);
            ui.textField("text" + sel, px, ty, pw - 250, 20, l.text, "Line text (empty = instrumental gap)", 200, v -> {
                l.text = v;
                dirty = true;
            });
            ui.button("m", "-0.1 s", px + pw - 244, ty, 50, 20, Ui.SECONDARY, l.start >= 0, -1, () -> shift(sel, -100));
            ui.button("p", "+0.1 s", px + pw - 190, ty, 50, 20, Ui.SECONDARY, l.start >= 0, -1, () -> shift(sel, 100));
            ui.button("ins", "Insert", px + pw - 136, ty, 48, 20, Ui.SECONDARY, true, Glyph.PLUS, () -> {
                lines.add(sel + 1, new Lyrics.Line(-1, "", null));
                sel++;
                dirty = true;
            });
            ui.button("del", "Delete", px + pw - 84, ty, 52, 20, Ui.DANGER, lines.size() > 1, Glyph.TRASH, () -> {
                lines.remove(sel);
                sel = Math.min(sel, lines.size() - 1);
                dirty = true;
            });
            ui.iconButton("clr", px + pw - 26, ty - 1, 22, Glyph.CLOSE, "Remove this line's time", l.start >= 0, () -> {
                l.start = -1;
                dirty = true;
            });
        }
        float by = h - 56;
        ui.button("stamp", "Stamp (Space)", px, by, 104, 22, Ui.PRIMARY, t >= 0, Glyph.CLOCK, this::stamp);
        boolean local = NowPlaying.get().source == NowPlaying.Source.LOCAL;
        ui.button("test", "Test from here", px + 108, by, 100, 22, Ui.SECONDARY, local && sel >= 0 && sel < lines.size() && lines.get(sel).start >= 0,
                Glyph.PLAY, () -> MusicModule.INSTANCE.seek(Math.max(0, lines.get(sel).start - 2000) / 1000f));
        ui.button("pause", "Play / pause", px + 212, by, 90, 22, Ui.SECONDARY, true, Glyph.PAUSE, () -> {
            if (local) MusicModule.INSTANCE.togglePause();
            else if (FloatingLyricsModule.INSTANCE != null) FloatingLyricsModule.INSTANCE.manualToggle();
        });
        ui.button("all-", "Shift all -0.1 s", px + 306, by, 100, 22, Ui.SECONDARY, true, -1, () -> {
            for (int i = 0; i < lines.size(); i++) shift(i, -100);
        });
        ui.button("all+", "Shift all +0.1 s", px + 410, by, 100, 22, Ui.SECONDARY, true, -1, () -> {
            for (int i = 0; i < lines.size(); i++) shift(i, 100);
        });
        float b2 = h - 30;
        ui.button("use", "Use without saving", px, b2, 124, 22, Ui.SECONDARY, true, Glyph.EYE, () -> LyricsService.useEdited(build()));
        ui.button("paste", "Paste lyrics", px + 128, b2, 92, 22, Ui.SECONDARY, true, Glyph.PLUS, () -> {
            String clip = GuiScreen.getClipboardString();
            if (clip == null || clip.trim().isEmpty()) return;
            Lyrics p = LrcParser.parse(clip);
            lines.clear();
            for (Lyrics.Line x : p.lines) lines.add(new Lyrics.Line(p.synced ? x.start : -1, x.text, null));
            if (lines.isEmpty()) lines.add(new Lyrics.Line(-1, "", null));
            sel = 0;
            dirty = true;
        });
        ui.button("save", "Save .lrc", px + pw - 128, b2, 100, 22, Ui.PRIMARY, true, Glyph.CHECK, () -> LyricsService.save(build(), target, () -> {
            dirty = false;
            Notifier.post(Notifier.Kind.SYSTEM, "Lyrics", "Saved " + target.getName(), Notifier.GOOD, 2000);
        }));
        ui.iconButton("back", px + pw - 22, b2, 22, Glyph.BACK, "Back", true, this::back);
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (!ui.typing()) {
            if (key == Keyboard.KEY_SPACE) {
                stamp();
                return;
            }
            if (key == Keyboard.KEY_UP) {
                sel = Math.max(0, sel - 1);
                return;
            }
            if (key == Keyboard.KEY_DOWN) {
                sel = Math.min(lines.size() - 1, sel + 1);
                return;
            }
            if (key == Keyboard.KEY_LBRACKET) {
                shift(sel, -100);
                return;
            }
            if (key == Keyboard.KEY_RBRACKET) {
                shift(sel, 100);
                return;
            }
        }
        super.keyTyped(c, key);
    }
}
