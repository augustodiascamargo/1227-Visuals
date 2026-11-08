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
 * own config (not in Minecraft's Controls), so they never clash with a
 * launcher's or another mod's binding unless the user picks the same key,
 * and {@link #conflicts} reports that case in the menu.
 */
public final class InputManager {
    public static final InputManager INSTANCE = new InputManager();
    private boolean openNextTick;

    private InputManager() {}

    /** Opens the menu on the next client tick (used by the /1227 command). */
    public void requestOpen() {
        openNextTick = true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !openNextTick) return;
        openNextTick = false;
        Minecraft.getMinecraft().displayGuiScreen(new ClickGui());
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !Keyboard.getEventKeyState()) return;
        int k = Keyboard.getEventKey();
        if (k == 0) return;
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
                if (kb.getKeyCode() == key) out.add(I18n.format(kb.getKeyDescription()));
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
        if (key <= 0) return "NONE";
        String n = Keyboard.getKeyName(key);
        return n == null ? "?" : n;
    }
}
