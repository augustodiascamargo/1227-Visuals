package net.m1227.client.ui;

import net.m1227.core.Notifier;

/**
 * Shows 1227 notifications (the same ones the Dynamic Island shows in game) as a toast in the
 * top-right corner of client screens, so feedback is visible in menus too.
 */
public final class ClientToasts {
    private ClientToasts() {}

    public static void draw(int w, int h) {
        long now = System.currentTimeMillis();
        if (!Notifier.active(now)) return;
        long age = now - Notifier.time;
        float in = Math.min(1f, age / 180f), out = Math.min(1f, (Notifier.duration - age) / 250f);
        float a = Math.max(0f, Math.min(in, out));
        Ui.toast(Notifier.title, Notifier.detail, Notifier.color, w - 8, 8 - (1f - in) * 8f, a);
    }
}
