package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Toggle Sprint: keeps Minecraft's own sprint key "held" for you. Vanilla
 * still decides whether you can actually sprint (moving forward, enough
 * hunger, not using an item, not blind...), so the normal rules apply.
 * The toggle key pauses/resumes it; menus and the sprint key's real state
 * are respected when it lets go.
 */
public final class ToggleSprintModule extends Module {
    private final Setting.Key toggleKey;
    private final Setting.Bool startActive, notify;
    private static ToggleSprintModule instance;
    private boolean active = true;
    private boolean holding;

    public ToggleSprintModule() {
        super("Toggle Sprint", "Sprint without holding the key (vanilla sprint rules apply).", Category.GAMEPLAY, false);
        toggleKey = add(new Setting.Key("Toggle key", 0));
        startActive = add(new Setting.Bool("Active when enabled", true));
        notify = add(new Setting.Bool("Show in Dynamic Island", true));
        instance = this;
    }

    /** True while Toggle Sprint is holding the sprint key for you. */
    public static boolean toggled() {
        return instance != null && instance.isEnabled() && instance.active;
    }

    @Override
    protected void onEnable() {
        active = startActive.on();
    }

    @Override
    protected void onDisable() {
        release();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (!Keyboard.getEventKeyState() || Minecraft.getMinecraft().currentScreen != null) return;
        int k = Keyboard.getEventKey();
        if (k != 0 && k == toggleKey.code()) {
            active = !active;
            if (!active) release();
            if (notify.on()) Notifier.post(Notifier.Kind.STATE, "Sprint", active ? "Toggled" : "Off",
                    active ? Notifier.GOOD : Notifier.BAD, 1300);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (!active || mc.currentScreen != null) {
            release();
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        holding = true;
    }

    /** Hands the sprint key back to whatever the physical key is doing. */
    private void release() {
        if (!holding) return;
        holding = false;
        int code = Minecraft.getMinecraft().gameSettings.keyBindSprint.getKeyCode();
        boolean down = false;
        try {
            if (code > 0) down = Keyboard.isKeyDown(code);
            else if (code < 0) down = Mouse.isButtonDown(code + 100);
        } catch (Exception ignored) {
            // unusual key code: treat as released
        }
        KeyBinding.setKeyBindState(code, down);
    }

    public boolean sprinting() {
        return isEnabled() && active;
    }
}
