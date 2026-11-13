package net.m1227.client.perf;

import net.m1227.module.InputMonitorModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Input Monitor: an estimate of how long your clicks and the forward key take to go through the
 * game, measured with System.nanoTime().
 *
 * How Minecraft 1.8.9 handles input: the window reads new mouse and keyboard events at the end of
 * every frame (inside Display.update, right after the picture is handed to the driver). Clicks and
 * key presses are then acted on only inside a game tick, and ticks run 20 times per second, so a
 * click can wait up to 50 ms for the next tick at any FPS.
 *
 * What is measured, per press:
 * <ul>
 * <li><b>Wait for tick</b>: from the end of the frame during which the window read the press, to
 * the moment the game tick processed it;</li>
 * <li><b>Input to frame (estimate)</b>: from that same frame end to the end of the first frame
 * drawn after the press was processed (the frame that shows the swing).</li>
 * </ul>
 * Not included, so these are not input-to-photon numbers: the time the press spent in the mouse,
 * USB and the OS before the window read it (up to one frame), and the time the GPU and monitor
 * take to show the frame. The "frame end" used is the end of our render event, just before the
 * buffer swap, so the numbers can be slightly too high, never too low.
 *
 * Nothing here changes input: it only looks at what the game already read.
 */
public final class InputMonitor {
    public static final InputMonitor INSTANCE = new InputMonitor();

    public final Stats.Series tickWait = new Stats.Series(128), toFrame = new Stats.Series(128);
    public final Stats.Series keyWait = new Stats.Series(64);
    private long prevFrameEnd;
    // per tracked input: 0 left, 1 right, 2 forward key
    private final boolean[] down = new boolean[3];
    private final long[] polledAt = new long[3], processedAt = new long[3];
    private final boolean[] waitingProcess = new boolean[3], waitingFrame = new boolean[3];
    private final long[] processedEarly = new long[3];
    public volatile int presses;
    private final boolean[] st = new boolean[3];

    private InputMonitor() {}

    private static boolean on() {
        InputMonitorModule m = InputMonitorModule.INSTANCE;
        return m != null && m.isEnabled();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFrame(TickEvent.RenderTickEvent e) {
        if (!on()) return;
        long now = System.nanoTime();
        if (e.phase == TickEvent.Phase.START) {
            st[0] = Mouse.isButtonDown(0);
            st[1] = Mouse.isButtonDown(1);
            st[2] = forwardDown();
            for (int i = 0; i < 3; i++) {
                if (st[i] && !down[i] && prevFrameEnd != 0L) {
                    // the press was read by the window at the end of the previous frame
                    polledAt[i] = prevFrameEnd;
                    presses++;
                    if (processedEarly[i] >= prevFrameEnd) {
                        processed(i, processedEarly[i]); // a tick earlier in this frame already handled it
                    } else {
                        waitingProcess[i] = true;
                    }
                }
                down[i] = st[i];
            }
        } else {
            for (int i = 0; i < 2; i++) {
                if (waitingFrame[i]) {
                    waitingFrame[i] = false;
                    toFrame.add(now, (now - polledAt[i]) / 1e6f);
                }
            }
            waitingFrame[2] = false;
            prevFrameEnd = now;
        }
    }

    private static boolean forwardDown() {
        int k = Minecraft.getMinecraft().gameSettings.keyBindForward.getKeyCode();
        return k > 0 && k < 256 && Keyboard.isKeyDown(k);
    }

    private void processed(int i, long at) {
        waitingProcess[i] = false;
        processedAt[i] = at;
        float ms = (at - polledAt[i]) / 1e6f;
        if (ms < 0 || ms > 1000) return;
        if (i < 2) {
            tickWait.add(at, ms);
            waitingFrame[i] = true;
        } else {
            keyWait.add(at, ms);
        }
    }

    /** Fired by Forge inside the game tick, for each mouse event the game processes. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!on() || !e.buttonstate || (e.button != 0 && e.button != 1)) return;
        long now = System.nanoTime();
        if (waitingProcess[e.button]) processed(e.button, now);
        else processedEarly[e.button] = now;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKey(InputEvent.KeyInputEvent e) {
        if (!on()) return;
        int k = Minecraft.getMinecraft().gameSettings.keyBindForward.getKeyCode();
        if (Keyboard.getEventKey() != k || !Keyboard.getEventKeyState()) return;
        long now = System.nanoTime();
        if (waitingProcess[2]) processed(2, now);
        else processedEarly[2] = now;
    }

    public void clear() {
        tickWait.clear();
        toFrame.clear();
        keyWait.clear();
        presses = 0;
    }
}
