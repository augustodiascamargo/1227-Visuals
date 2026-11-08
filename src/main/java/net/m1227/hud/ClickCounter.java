package net.m1227.hud;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Counts real mouse presses (left / right) during the last second. Read-only, never cancels input. */
public final class ClickCounter {
    public static final ClickCounter INSTANCE = new ClickCounter();
    private static final int N = 64;
    private final long[][] times = new long[2][N];
    private final int[] head = new int[2];

    private ClickCounter() {}

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (!e.buttonstate || (e.button != 0 && e.button != 1)) return;
        int b = e.button;
        times[b][head[b]] = System.currentTimeMillis();
        head[b] = (head[b] + 1) % N;
    }

    public static int cps(int button) {
        long now = System.currentTimeMillis();
        int c = 0;
        for (long t : INSTANCE.times[button]) {
            if (now - t <= 1000L) c++;
        }
        return c;
    }
}
