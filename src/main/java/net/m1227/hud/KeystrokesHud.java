package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** WASD, mouse buttons with CPS and space bar; keys fade in the accent colour when pressed. */
public final class KeystrokesHud extends HudModule {
    private static final float K = 22f, G = 2f;
    private final Setting.Bool mouse, space, cps;
    private final float[] glow = new float[7];
    private long last;

    public KeystrokesHud() {
        super("Keystrokes", "WASD, mouse buttons, CPS and jump.", 0.01, 0.30);
        mouse = add(new Setting.Bool("Mouse buttons", true));
        cps = add(new Setting.Bool("Show CPS on buttons", true)).showIf(() -> mouse.on());
        space = add(new Setting.Bool("Space bar", true));
    }

    @Override
    public float width() {
        return K * 3 + G * 2;
    }

    @Override
    public float height() {
        return K * 2 + G + (mouse.on() ? G + K : 0) + (space.on() ? G + 10 : 0);
    }

    private static boolean down(KeyBinding k) {
        int c = k.getKeyCode();
        if (c == 0) return false;
        try {
            return c < 0 ? Mouse.isButtonDown(c + 100) : Keyboard.isKeyDown(c);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        KeyBinding[] b = {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindRight, mc.gameSettings.keyBindAttack, mc.gameSettings.keyBindUseItem,
                mc.gameSettings.keyBindJump};
        for (int i = 0; i < 7; i++) {
            boolean d = mc.currentScreen == null && down(b[i]);
            glow[i] += ((d ? 1f : 0f) - glow[i]) * Math.min(1f, dt * (d ? 30f : 10f));
        }
        key(x + K + G, y, K, K, name(b[0]), glow[0]);
        float y2 = y + K + G;
        key(x, y2, K, K, name(b[1]), glow[1]);
        key(x + K + G, y2, K, K, name(b[2]), glow[2]);
        key(x + (K + G) * 2, y2, K, K, name(b[3]), glow[3]);
        float y3 = y2 + K + G;
        float half = (width() - G) / 2f;
        if (mouse.on()) {
            key(x, y3, half, K, cps.on() ? ClickCounter.cps(0) + " CPS" : "LMB", glow[4]);
            key(x + half + G, y3, half, K, cps.on() ? ClickCounter.cps(1) + " CPS" : "RMB", glow[5]);
            y3 += K + G;
        }
        if (space.on()) {
            float g = glow[6];
            panel(x, y3, width(), 10);
            if (g > 0.01f) Gfx.roundedRect(x, y3, width(), 10, 4f, Gfx.alpha(accent(), g * 0.75f));
            Gfx.rect(x + width() / 2f - 12, y3 + 4.5f, 24, 1f, Gfx.mix(0xFFFFFFFF, 0xFF0B0C11, g * 0.6f));
        }
    }

    private static String name(KeyBinding k) {
        int c = k.getKeyCode();
        if (c < 0) return "M" + (c + 101);
        String n = Keyboard.getKeyName(c);
        return n == null ? "?" : n.length() > 4 ? n.substring(0, 4) : n;
    }

    private void key(float x, float y, float w, float h, String label, float g) {
        panel(x, y, w, h);
        if (g > 0.01f) Gfx.roundedRect(x, y, w, h, 4f, Gfx.alpha(accent(), g * 0.75f));
        int c = Gfx.mix(0xFFFFFFFF, 0xFF0B0C11, g * 0.55f);
        Gfx.text(label, x + (w - Gfx.textWidth(label)) / 2f, y + (h - 8f) / 2f + 0.5f, c);
    }
}
