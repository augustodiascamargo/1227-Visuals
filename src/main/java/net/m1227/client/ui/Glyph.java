package net.m1227.client.ui;

import net.m1227.ui.Gfx;

/**
 * Line icons of the 1227 design system, drawn from shapes (crisp at any GUI scale, one stroke
 * width). Size ~10 GUI units; (cx, cy) is the centre.
 */
public final class Glyph {
    private Glyph() {}

    public static final int PLAY = 0, SERVERS = 1, GEAR = 2, MODS = 3, CROWN = 4, PERSON = 5, POWER = 6, GLOBE = 7,
            BACK = 8, STAR = 9, REFRESH = 10, PLUS = 11, PENCIL = 12, TRASH = 13, FOLDER = 14, CLOCK = 15, LAN = 16,
            CAMERA = 17, CHART = 18, HUD = 19, KEY = 20, PAUSE = 21, BOLT = 22, CLOSE = 23, CHECK = 24, LINK = 25,
            SLIDERS = 26, SOUND = 27, EYE = 28, SWORD = 29, MOVE = 30, WORLD = 31, CODE = 32, PALETTE = 33, ARROW_RIGHT = 34;

    private static final float W = 1.2f;

    private static void ring(float cx, float cy, float r, int c) {
        Gfx.annulus(cx, cy, r - W * 0.5f, r + W * 0.5f, 0, Math.PI * 2, c);
    }

    private static void arc(float cx, float cy, float r, double a0, double a1, int c) {
        Gfx.annulus(cx, cy, r - W * 0.5f, r + W * 0.5f, a0, a1, c);
    }

    private static void l(float x0, float y0, float x1, float y1, int c) {
        Gfx.line(x0, y0, x1, y1, W, c);
    }

    public static void draw(int g, float cx, float cy, int c) {
        switch (g) {
            case PLAY:
                Gfx.quad(cx - 2.5f, cy - 4f, cx - 2.5f, cy + 4f, cx + 4f, cy, cx + 4f, cy, c);
                break;
            case SERVERS:
                Gfx.roundedRect(cx - 4.5f, cy - 4.5f, 9f, 3.6f, 1f, c);
                Gfx.roundedRect(cx - 4.5f, cy + 0.9f, 9f, 3.6f, 1f, c);
                Gfx.circle(cx + 2.6f, cy - 2.7f, 0.7f, 0xFF0B0C11);
                Gfx.circle(cx + 2.6f, cy + 2.7f, 0.7f, 0xFF0B0C11);
                break;
            case GEAR:
                net.m1227.ui.Icons.gear(cx, cy, c);
                break;
            case MODS: // puzzle piece
                Gfx.roundedRect(cx - 4f, cy - 2.5f, 7f, 7f, 1f, c);
                Gfx.circle(cx - 0.5f, cy - 3.2f, 1.6f, c);
                Gfx.circle(cx + 3.6f, cy + 1f, 1.6f, c);
                break;
            case CROWN:
                net.m1227.ui.Icons.draw(3, cx, cy, c);
                break;
            case PERSON:
                Gfx.circle(cx, cy - 2.2f, 2.2f, c);
                arc(cx, cy + 4.6f, 4f, Math.PI, Math.PI * 2, c);
                break;
            case POWER:
                arc(cx, cy + 0.5f, 3.8f, -Math.PI * 0.32, Math.PI * 1.32, c);
                l(cx, cy - 4.5f, cx, cy, c);
                break;
            case GLOBE:
                ring(cx, cy, 4.2f, c);
                l(cx - 4.2f, cy, cx + 4.2f, cy, c);
                Gfx.annulus(cx, cy, 1.4f, 1.4f + W, -Math.PI / 2, Math.PI / 2, c);
                Gfx.annulus(cx, cy, 1.4f, 1.4f + W, Math.PI / 2, Math.PI * 1.5, c);
                break;
            case BACK:
                l(cx + 3.5f, cy, cx - 3.5f, cy, c);
                l(cx - 3.5f, cy, cx - 0.5f, cy - 3f, c);
                l(cx - 3.5f, cy, cx - 0.5f, cy + 3f, c);
                break;
            case ARROW_RIGHT:
                l(cx - 3.5f, cy, cx + 3.5f, cy, c);
                l(cx + 3.5f, cy, cx + 0.5f, cy - 3f, c);
                l(cx + 3.5f, cy, cx + 0.5f, cy + 3f, c);
                break;
            case STAR:
                Gfx.star(cx, cy, 4.6f, true, c);
                break;
            case REFRESH:
                arc(cx, cy, 3.8f, -Math.PI * 0.1, Math.PI * 1.55, c);
                Gfx.quad(cx + 2.2f, cy - 4.8f, cx + 5.4f, cy - 1.8f, cx + 1.6f, cy - 0.9f, cx + 1.6f, cy - 0.9f, c);
                break;
            case PLUS:
                l(cx - 4f, cy, cx + 4f, cy, c);
                l(cx, cy - 4f, cx, cy + 4f, c);
                break;
            case PENCIL:
                l(cx - 3.5f, cy + 3.5f, cx + 2.8f, cy - 2.8f, c);
                l(cx - 3.5f, cy + 3.5f, cx - 3.8f, cy + 1.6f, c);
                l(cx - 3.5f, cy + 3.5f, cx - 1.6f, cy + 3.8f, c);
                break;
            case TRASH:
                l(cx - 4f, cy - 3f, cx + 4f, cy - 3f, c);
                l(cx - 1.4f, cy - 4.4f, cx + 1.4f, cy - 4.4f, c);
                Gfx.roundedOutline(cx - 3f, cy - 2.4f, 6f, 7f, 1f, W, c);
                break;
            case FOLDER:
                Gfx.roundedOutline(cx - 4.5f, cy - 2.5f, 9f, 6.5f, 1f, W, c);
                l(cx - 4.5f, cy - 2.5f, cx - 4.5f, cy - 3.8f, c);
                l(cx - 4.5f, cy - 3.8f, cx - 1f, cy - 3.8f, c);
                break;
            case CLOCK:
                ring(cx, cy, 4.2f, c);
                l(cx, cy, cx, cy - 2.6f, c);
                l(cx, cy, cx + 2f, cy + 1f, c);
                break;
            case LAN:
                Gfx.roundedOutline(cx - 4.5f, cy - 4f, 9f, 5.5f, 1f, W, c);
                l(cx, cy + 1.5f, cx, cy + 4f, c);
                l(cx - 3f, cy + 4f, cx + 3f, cy + 4f, c);
                break;
            case CAMERA:
                Gfx.roundedOutline(cx - 4.5f, cy - 2.6f, 9f, 6.6f, 1.2f, W, c);
                ring(cx, cy + 0.6f, 1.8f, c);
                Gfx.rect(cx - 1.8f, cy - 4f, 3.6f, 1.6f, c);
                break;
            case CHART:
                Gfx.rect(cx - 4f, cy + 1f, 2f, 3.5f, c);
                Gfx.rect(cx - 1f, cy - 2f, 2f, 6.5f, c);
                Gfx.rect(cx + 2f, cy - 4.5f, 2f, 9f, c);
                break;
            case HUD:
                net.m1227.ui.Icons.draw(9, cx, cy, c);
                break;
            case KEY:
                ring(cx - 2f, cy, 2.4f, c);
                l(cx + 0.4f, cy, cx + 4.6f, cy, c);
                l(cx + 3.4f, cy, cx + 3.4f, cy + 2f, c);
                break;
            case PAUSE:
                Gfx.rect(cx - 3f, cy - 4f, 2f, 8f, c);
                Gfx.rect(cx + 1f, cy - 4f, 2f, 8f, c);
                break;
            case BOLT:
                Gfx.quad(cx + 1f, cy - 5f, cx - 3.5f, cy + 0.8f, cx - 0.2f, cy + 0.8f, cx + 1f, cy - 5f, c);
                Gfx.quad(cx - 1f, cy + 5f, cx + 3.5f, cy - 0.8f, cx + 0.2f, cy - 0.8f, cx - 1f, cy + 5f, c);
                Gfx.rect(cx - 0.6f, cy - 0.9f, 1.4f, 1.8f, c);
                break;
            case CLOSE:
                l(cx - 3.2f, cy - 3.2f, cx + 3.2f, cy + 3.2f, c);
                l(cx + 3.2f, cy - 3.2f, cx - 3.2f, cy + 3.2f, c);
                break;
            case CHECK:
                l(cx - 3.6f, cy + 0.2f, cx - 1f, cy + 2.8f, c);
                l(cx - 1f, cy + 2.8f, cx + 3.8f, cy - 2.6f, c);
                break;
            case LINK:
                Gfx.roundedOutline(cx - 4.5f, cy - 1.8f, 5f, 3.6f, 1.8f, W, c);
                Gfx.roundedOutline(cx - 0.5f, cy - 1.8f, 5f, 3.6f, 1.8f, W, c);
                break;
            case SLIDERS:
                l(cx - 4.5f, cy - 2.5f, cx + 4.5f, cy - 2.5f, c);
                l(cx - 4.5f, cy + 2.5f, cx + 4.5f, cy + 2.5f, c);
                Gfx.circle(cx - 1.5f, cy - 2.5f, 1.6f, c);
                Gfx.circle(cx + 2f, cy + 2.5f, 1.6f, c);
                break;
            case SOUND:
                Gfx.quad(cx - 4.5f, cy - 1.6f, cx - 4.5f, cy + 1.6f, cx - 0.5f, cy + 4f, cx - 0.5f, cy - 4f, c);
                arc(cx - 0.5f, cy, 2.6f, -Math.PI / 3, Math.PI / 3, c);
                arc(cx - 0.5f, cy, 4.6f, -Math.PI / 3, Math.PI / 3, c);
                break;
            case EYE:
                arc(cx, cy + 3f, 5f, -Math.PI * 0.8, -Math.PI * 0.2, c);
                arc(cx, cy - 3f, 5f, Math.PI * 0.2, Math.PI * 0.8, c);
                Gfx.circle(cx, cy, 1.6f, c);
                break;
            case SWORD:
                l(cx - 3.8f, cy + 3.8f, cx + 3.8f, cy - 3.8f, c);
                l(cx - 3.8f, cy + 0.8f, cx - 0.8f, cy + 3.8f, c);
                break;
            case MOVE:
                Gfx.circle(cx + 2f, cy - 3.2f, 1.3f, c);
                l(cx + 1.2f, cy - 1.4f, cx - 0.6f, cy + 1.6f, c);
                l(cx - 0.6f, cy + 1.6f, cx + 1.8f, cy + 4.4f, c);
                l(cx - 0.6f, cy + 1.6f, cx - 3.4f, cy + 3.6f, c);
                l(cx + 0.8f, cy - 0.4f, cx - 3f, cy - 0.6f, c);
                break;
            case WORLD:
                Gfx.quad(cx - 4.5f, cy + 3.5f, cx + 4.5f, cy + 3.5f, cx + 1.5f, cy - 2f, cx - 1f, cy + 0.5f, c);
                Gfx.circle(cx + 2.8f, cy - 3.2f, 1.2f, c);
                break;
            case CODE:
                l(cx - 1.8f, cy - 3.2f, cx - 4.4f, cy, c);
                l(cx - 4.4f, cy, cx - 1.8f, cy + 3.2f, c);
                l(cx + 1.8f, cy - 3.2f, cx + 4.4f, cy, c);
                l(cx + 4.4f, cy, cx + 1.8f, cy + 3.2f, c);
                break;
            case PALETTE:
                ring(cx, cy, 4.3f, c);
                Gfx.circle(cx - 1.6f, cy - 1.6f, 0.9f, c);
                Gfx.circle(cx + 1.6f, cy - 1.6f, 0.9f, c);
                Gfx.circle(cx + 1.9f, cy + 1.4f, 0.9f, c);
                break;
            default:
                Gfx.circle(cx, cy, 2f, c);
        }
    }
}
