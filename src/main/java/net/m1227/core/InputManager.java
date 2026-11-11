package net.m1227.core;

import java.util.ArrayList;
import java.util.List;
import net.m1227.module.ClientModule;
import net.m1227.ui.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * All keyboard handling lives here, separate from the menu code: the menu
 * key, module toggle keys and other key settings. Keys are stored in 1227's
 * config and mirrored into Options > Controls (category "1227", see
 * {@link Keybinds}), so they can be changed in either place. Codes below 0
 * are mouse buttons, as in Minecraft ({@code -100 + button}).
 */
public final class InputManager {
    public static final InputManager INSTANCE = new InputManager();
    private boolean openNextTick;
    private net.minecraft.client.gui.GuiScreen pendingScreen;

    private InputManager() {}

    /** Opens the menu on the next client tick (used by the /1227 command). */
    public void requestOpen() {
        openNextTick = true;
    }

    /** Opens any screen on the next client tick (chat closes itself first). */
    public void requestScreen(net.minecraft.client.gui.GuiScreen s) {
        pendingScreen = s;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (pendingScreen != null) {
            Minecraft.getMinecraft().displayGuiScreen(pendingScreen);
            pendingScreen = null;
            openNextTick = false;
            return;
        }
        if (!openNextTick) return;
        openNextTick = false;
        Minecraft.getMinecraft().displayGuiScreen(new ClickGui());
    }

    /** Is this key (or mouse button, for codes below 0) held right now? */
    public static boolean isDown(int code) {
        if (code == 0) return false;
        if (code < 0) {
            int b = code + 100;
            return b >= 0 && b < org.lwjgl.input.Mouse.getButtonCount() && org.lwjgl.input.Mouse.isButtonDown(b);
        }
        return code < 256 && Keyboard.isKeyDown(code);
    }

    /** Key code of the current input event: the keyboard key, or -100 + button for a mouse press (0 = release / none). */
    public static int pressedCode(boolean mouse) {
        if (mouse) {
            int b = org.lwjgl.input.Mouse.getEventButton();
            return b >= 0 && org.lwjgl.input.Mouse.getEventButtonState() ? b - 100 : 0;
        }
        return Keyboard.getEventKeyState() ? Keyboard.getEventKey() : 0;
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent e) {
        int k = pressedCode(true);
        // left / right click stay with the game; only extra buttons can be bound
        if (k == -100 || k == -99) return;
        press(k);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        press(pressedCode(false));
    }

    private void press(int k) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || k == 0) return;
        if (k == ClientModule.menuKey()) {
            mc.displayGuiScreen(new ClickGui());
            return;
        }
        for (Module m : ModuleManager.all()) {
            if (m.key == k && m.toggleable()) m.toggle();
        }
    }

    /**
     * Everything else bound to this key: Minecraft and other mods' bindings
     * (from Controls), 1227 module keys and 1227 key settings. "self" is the
     * owner being edited, which is skipped.
     */
    public static List<String> conflicts(int key, Object self) {
        List<String> out = new ArrayList<String>();
        if (key <= 0) return out;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null) {
            for (KeyBinding kb : mc.gameSettings.keyBindings) {
                if (kb.getKeyCode() == key && !Keybinds.ours(kb)) out.add(I18n.format(kb.getKeyDescription()));
            }
        }
        for (Module m : ModuleManager.all()) {
            if (m != self && m.key == key) out.add(m.name);
            for (Setting<?> s : m.settings()) {
                if (s != self && s instanceof Setting.Key && ((Setting.Key) s).code() == key) out.add(s.name);
            }
        }
        return out;
    }

    public static String keyName(int key) {
        if (key == 0) return "NONE";
        if (key < 0) return "MOUSE " + (key + 101);
        String n = Keyboard.getKeyName(key);
        return n == null ? "?" : n;
    }
}
