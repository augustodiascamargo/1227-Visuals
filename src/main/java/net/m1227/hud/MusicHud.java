package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.module.MusicModule;
import net.m1227.music.Track;
import net.m1227.ui.Gfx;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Mini player: cover, title, artist and progress of the local song, or what Spotify / YouTube is playing. */
public final class MusicHud extends HudModule {
    private final Setting.Bool coverArt, progress;

    public MusicHud() {
        super("Music", "Mini player: what's playing, with cover and progress.", 0.01, 0.78);
        coverArt = add(new Setting.Bool("Cover", true));
        progress = add(new Setting.Bool("Progress bar", true));
    }

    @Override
    public float width() {
        return 150;
    }

    @Override
    public float height() {
        return coverArt.on() ? 34 : 28;
    }

    @Override
    protected boolean hasContent() {
        MusicModule m = MusicModule.INSTANCE;
        return m != null && (m.player.current() != null || m.external() != null);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        MusicModule m = MusicModule.INSTANCE;
        if (m == null) return;
        Track t = m.player.current();
        String[] ext = t == null ? m.external() : null;
        if (t == null && ext == null && !editing) return;
        panel(x, y, width(), height());
        float tx = x + 6;
        if (coverArt.on()) {
            ResourceLocation r = t == null ? null : m.cover(t.cover);
            if (r != null) {
                net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(r);
                GlStateManager.enableTexture2D();
                GlStateManager.color(1f, 1f, 1f, 1f);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0, 0);
                GL11.glVertex2f(x + 5, y + 5);
                GL11.glTexCoord2f(0, 1);
                GL11.glVertex2f(x + 5, y + 29);
                GL11.glTexCoord2f(1, 1);
                GL11.glVertex2f(x + 29, y + 29);
                GL11.glTexCoord2f(1, 0);
                GL11.glVertex2f(x + 29, y + 5);
                GL11.glEnd();
            } else {
                Gfx.roundedRect(x + 5, y + 5, 24, 24, 3, Gfx.alpha(value(), 0.35f));
            }
            tx = x + 34;
        }
        String title = t != null ? t.title : ext != null ? ext[1] : "Song title";
        String sub = t != null ? t.artist + (m.player.paused() ? "  ·  paused" : "") : ext != null ? ext[0] : "Artist";
        float w = width() - (tx - x) - 5;
        Gfx.text(Gfx.trim(title, w), tx, y + 6, 0xFFFFFFFF);
        Gfx.text(Gfx.trim(sub, w), tx, y + 16, LABEL);
        if (progress.on() && t != null) {
            float f = m.player.length() <= 0 ? 0f : Math.min(1f, m.player.position() / m.player.length());
            Gfx.roundedRect(tx, y + height() - 6, w, 2, 1, 0x40FFFFFF);
            Gfx.roundedRect(tx, y + height() - 6, w * f, 2, 1, value());
        }
    }
}
