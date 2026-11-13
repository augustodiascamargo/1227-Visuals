package net.m1227.client.menu;

import java.util.List;
import net.m1227.client.Os;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.lyrics.LrcLib;
import net.m1227.lyrics.Lyrics;
import net.m1227.lyrics.LyricsService;
import net.m1227.module.FloatingLyricsModule;
import net.m1227.music.MediaBridge;
import net.m1227.music.NowPlaying;
import net.m1227.music.Track;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;

/**
 * Lyrics screen: what is playing and where its lyrics came from, the lyrics with the current
 * line highlighted, the LRCLIB results to choose from when the automatic match was not sure, a
 * manual search, the per-song sync offset, a manual clock for players without a timeline, and
 * the way into the lyrics editor.
 */
public final class LyricsScreen extends UiScreen {
    private String query = "";

    public LyricsScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        FloatingLyricsModule m = FloatingLyricsModule.INSTANCE;
        NowPlaying.Info np = NowPlaying.get();
        float pw = Math.min(620, w - 20), px = (w - pw) / 2f, top = 12;
        Ui.textBold("Lyrics", px, top, DS.T_TITLE, DS.TEXT);
        if (m != null && !m.isEnabled()) {
            ui.button("enable", "Turn on Floating Lyrics", px + pw - 150, top, 150, 20, Ui.PRIMARY, true, Glyph.CHECK, () -> m.setEnabled(true));
        }
        float y = top + 26;
        // ---- song
        Ui.card(px, y, pw, 46);
        if (np.source == NowPlaying.Source.NONE) {
            Ui.text("Nothing is playing. Play a song in the 1227 player, Spotify or YouTube.", px + 10, y + 10, DS.T_BODY, DS.MUTED);
            String bs = MediaBridge.supported() ? "Windows media link: " + MediaBridge.status() : "Spotify / YouTube timelines need Windows";
            Ui.text(bs, px + 10, y + 24, DS.T_SMALL, DS.FAINT);
        } else {
            Ui.textMedium(Gfx.trim(np.title, pw - 200), px + 10, y + 8, DS.T_BODY, DS.TEXT);
            Ui.text(Gfx.trim((np.artist.isEmpty() ? "Unknown artist" : np.artist) + "  ·  " + np.app, pw - 200), px + 10, y + 21, DS.T_SMALL, DS.MUTED);
            String clock = np.timeline ? "Timeline: " + (np.source == NowPlaying.Source.LOCAL ? "exact (1227 player)" : "from " + np.app + " (accurate to about 0.1 s)")
                    : "No timeline from " + np.app + ": use the manual clock below";
            Ui.text(clock, px + 10, y + 33, DS.T_SMALL, np.timeline ? DS.GOOD : DS.WARN);
            float p = np.position();
            Ui.textRight((p >= 0 ? Track.fmt(p) : "--:--") + " / " + (np.duration > 0 ? Track.fmt(np.duration) : "--:--"), px + pw - 10, y + 8, DS.T_BODY, DS.TEXT);
            LyricsService.State st = LyricsService.state();
            int sc = st == LyricsService.State.FOUND ? DS.GOOD : st == LyricsService.State.SEARCHING ? DS.MUTED : DS.WARN;
            Ui.textRight(Gfx.trim(LyricsService.message(), 220), px + pw - 10, y + 22, DS.T_SMALL, sc);
            long off = LyricsService.songOffset();
            Ui.textRight("Song offset " + (off >= 0 ? "+" : "") + off + " ms", px + pw - 10, y + 33, DS.T_SMALL, DS.FAINT);
        }
        y += 52;
        // ---- left: lyrics preview  |  right: results
        float lw = pw * 0.56f, rw = pw - lw - 8, lh = h - y - 64;
        Ui.card(px, y, lw, lh);
        Lyrics l = LyricsService.lyrics();
        float off = ui.beginScroll("lyr", px + 4, y + 4, lw - 8, lh - 8);
        float ly = y + 8;
        if (l == null) {
            Ui.text(np.source == NowPlaying.Source.NONE ? "" : "No lyrics loaded for this song.", px + 10, ly - off, DS.T_BODY, DS.FAINT);
        } else {
            long t = m != null ? m.timeMs() : -1;
            int cur = t >= 0 ? l.indexAt(t) : -1;
            for (int i = 0; i < l.lines.size(); i++) {
                Lyrics.Line line = l.lines.get(i);
                boolean c = i == cur;
                String time = l.synced ? Track.fmt(line.start / 1000f) : "";
                if (l.synced) Ui.text(time, px + 10, ly - off, DS.T_SMALL, DS.FAINT);
                String tx = line.blank() ? "• • •" : line.text;
                for (String row : Gfx.wrap(tx, lw - 60)) {
                    if (c) Ui.textMedium(row, px + 44, ly - off, DS.T_BODY, DS.TEXT);
                    else Ui.text(row, px + 44, ly - off, DS.T_BODY, line.blank() ? DS.FAINT : DS.MUTED);
                    ly += 12;
                }
                if (l.synced && np.source == NowPlaying.Source.LOCAL) {
                    final long at = line.start;
                    ui.click(px + 6, ly - 12 - off, lw - 12, 12, () -> net.m1227.module.MusicModule.INSTANCE.seek(Math.max(0, at - 300) / 1000f));
                }
            }
            if (!l.synced) Ui.text("These lyrics have no timing: they are shown as text, not in sync.", px + 10, ly + 4 - off, DS.T_SMALL, DS.WARN);
            ly += 14;
        }
        ui.endScroll(ly - (y + 8) + 8);
        float rx = px + lw + 8;
        Ui.card(rx, y, rw, lh);
        Ui.caption("SEARCH LRCLIB", rx + 8, y + 6);
        ui.textField("q", rx + 8, y + 16, rw - 60, 18, query, np.title.isEmpty() ? "title artist" : np.title + " " + np.artist, 80, v -> query = v);
        ui.button("go", "Search", rx + rw - 48, y + 16, 40, 18, Ui.SECONDARY, true, -1, () -> {
            String q = query.trim().isEmpty() ? (np.title + " " + np.artist).trim() : query.trim();
            if (!q.isEmpty()) LyricsService.manualSearch(q);
        });
        List<LrcLib.Record> list = LyricsService.candidates();
        float ry = y + 40;
        float roff = ui.beginScroll("res", rx + 4, ry, rw - 8, lh - 44);
        float iy = ry;
        if (list.isEmpty()) Ui.text("Results appear here when there is no sure match.", rx + 8, iy + 2 - roff, DS.T_SMALL, DS.FAINT);
        for (final LrcLib.Record r : list) {
            float yy = iy - roff;
            boolean hv = ui.hover(rx + 4, yy, rw - 8, 30);
            Gfx.roundedRect(rx + 4, yy, rw - 8, 30, DS.R_SM, hv ? DS.SURFACE_3 : DS.SURFACE_2);
            Ui.textMedium(Gfx.trim(r.trackName, rw - 70), rx + 9, yy + 4, DS.T_SMALL, DS.TEXT);
            Ui.text(Gfx.trim(r.artistName + (r.albumName.isEmpty() ? "" : "  ·  " + r.albumName), rw - 20), rx + 9, yy + 16, DS.T_SMALL, DS.MUTED);
            Ui.textRight((r.hasSynced() ? "synced" : r.instrumental ? "instr." : "text") + "  " + Track.fmt((float) r.duration), rx + rw - 9, yy + 4, DS.T_SMALL,
                    r.hasSynced() ? DS.GOOD : DS.FAINT);
            ui.click(rx + 4, yy, rw - 8, 30, () -> LyricsService.choose(r));
            iy += 33;
        }
        ui.endScroll(iy - ry);
        // ---- bottom bar
        float by = h - 30;
        float bx = px;
        ui.button("earlier", "-0.25 s", bx, by, 58, 20, Ui.SECONDARY, true, -1, () -> LyricsService.nudge(-250));
        ui.button("later", "+0.25 s", bx + 62, by, 58, 20, Ui.SECONDARY, true, -1, () -> LyricsService.nudge(250));
        bx += 128;
        if (m != null && !np.timeline && np.source != NowPlaying.Source.NONE) {
            ui.button("mstart", m.manualRunning() ? "Restart clock" : "Song starts now", bx, by, 104, 20, Ui.PRIMARY, true, Glyph.PLAY, () -> m.manualStart(0));
            ui.button("mpause", "Pause clock", bx + 108, by, 80, 20, Ui.SECONDARY, m.manualRunning(), Glyph.PAUSE, m::manualToggle);
            bx += 196;
        }
        ui.button("edit", "Editor", bx, by, 66, 20, Ui.SECONDARY, np.source != NowPlaying.Source.NONE, Glyph.PENCIL,
                () -> mc.displayGuiScreen(new LyricsEditorScreen(this)));
        ui.button("again", "Search again", bx + 70, by, 88, 20, Ui.SECONDARY, np.source != NowPlaying.Source.NONE, Glyph.REFRESH, LyricsService::retry);
        ui.iconButton("folder", bx + 162, by - 1, 22, Glyph.FOLDER, "Open your lyrics folder (put .lrc files here)", true, () -> Os.open(LyricsService.ownFolder()));
        ui.iconButton("back", px + pw - 22, by - 1, 22, Glyph.BACK, "Back", true, this::back);
        Ui.text("Lyrics from your .lrc files or LRCLIB (lrclib.net, community data). Nothing is stored in the mod.", px, h - 8, 0.7f, DS.FAINT);
    }
}
