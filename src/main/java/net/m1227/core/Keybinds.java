package net.m1227.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Mirrors every 1227 key into Minecraft's Options > Controls, under its own "1227" category:
 * the menu key, each key setting (zoom, emotes, sprint toggle, inspect...) and every module's
 * on/off key. Either side can be edited: a change made in Controls is copied into 1227's
 * config, and a change made in the 1227 menu is copied into Controls (and options.txt).
 *
 * The bindings only carry the key; 1227 still reads the keys itself, so nothing is
 * pressed twice and Minecraft's own "conflict" red text works as usual.
 */
public final class Keybinds {
    public static final String CATEGORY = "1227";
    public static final Keybinds INSTANCE = new Keybinds();

    /** One pair: a KeyBinding and the 1227 value it mirrors. */
    private abstract static class Link {
        final KeyBinding kb;
        int last;

        Link(KeyBinding kb, int value) {
            this.kb = kb;
            this.last = value;
        }

        abstract int get();

        abstract void set(int v);
    }

    private final List<Link> links = new ArrayList<Link>();
    private boolean registered;
    private int tick;

    private Keybinds() {}

    /** Call once after modules and the config are loaded. */
    public void register() {
        if (registered) return;
        registered = true;
        for (final Module m : ModuleManager.all()) {
            for (Setting<?> s : m.settings()) {
                if (!(s instanceof Setting.Key)) continue;
                final Setting.Key k = (Setting.Key) s;
                String desc = clean(k.name.toLowerCase().contains(m.name.toLowerCase()) ? k.name : m.name + " - " + k.name);
                KeyBinding kb = new KeyBinding(desc, k.def, CATEGORY);
                kb.setKeyCode(k.code());
                ClientRegistry.registerKeyBinding(kb);
                links.add(new Link(kb, k.code()) {
                    int get() {
                        return k.code();
                    }

                    void set(int v) {
                        k.set(v);
                    }
                });
            }
        }
        for (final Module m : ModuleManager.all()) {
            if (!m.toggleable()) continue;
            KeyBinding kb = new KeyBinding(clean("Toggle " + m.name), 0, CATEGORY);
            kb.setKeyCode(m.key);
            ClientRegistry.registerKeyBinding(kb);
            links.add(new Link(kb, m.key) {
                int get() {
                    return m.key;
                }

                void set(int v) {
                    m.key = v;
                    ConfigManager.markDirty();
                }
            });
        }
        KeyBinding.resetKeyBindingArrayAndHash();
    }

    /** options.txt stores bindings as "key_<name>:<code>", so a ':' in the name would break loading. */
    private static String clean(String s) {
        return s.replace(':', ' ').replace("  ", " ").trim();
    }

    /** True for one of 1227's own bindings (skipped by the conflict list, which already lists the 1227 side). */
    public static boolean ours(KeyBinding kb) {
        return CATEGORY.equals(kb.getKeyCategory());
    }

    /** Two-way sync; cheap, so it runs a few times a second and whenever a screen closes. */
    public void sync() {
        if (links.isEmpty()) return;
        boolean toOptions = false;
        for (Link l : links) {
            int kbCode = l.kb.getKeyCode(), val = l.get();
            if (kbCode != l.last) {
                // changed in Options > Controls (or options.txt was loaded): Controls wins
                l.set(kbCode);
                l.last = kbCode;
            } else if (val != l.last) {
                // changed in the 1227 menu or by a preset/reset: push it to Controls
                l.kb.setKeyCode(val);
                l.last = val;
                toOptions = true;
            }
        }
        if (toOptions) {
            KeyBinding.resetKeyBindingArrayAndHash();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.gameSettings != null) mc.gameSettings.saveOptions();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++tick % 5 == 0) sync();
    }
}
