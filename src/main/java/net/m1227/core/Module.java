package net.m1227.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.MinecraftForge;

/**
 * A feature. While enabled it is registered on the Forge event bus, so a
 * disabled module costs nothing per frame.
 */
public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    public final boolean defaultEnabled;
    /** Toggle key for this module (0 = none). Handled by InputManager. */
    public int key;
    /** Starred in the menu (shown in the Favorites list). Not touched by reset. */
    public boolean favorite;

    private boolean enabled;
    private boolean subscribed;
    private final List<Setting<?>> settings = new ArrayList<Setting<?>>();

    protected Module(String name, String description, Category category, boolean defaultEnabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
    }

    public <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public Setting<?> setting(String settingName) {
        for (Setting<?> s : settings) {
            if (s.name.equals(settingName)) return s;
        }
        return null;
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    /** False for always-on groups of options (e.g. Performance). */
    public boolean toggleable() {
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean on) {
        if (!toggleable() || on == enabled) return;
        enabled = on;
        if (on) {
            if (!subscribed) {
                MinecraftForge.EVENT_BUS.register(this);
                subscribed = true;
            }
            onEnable();
        } else {
            if (subscribed) {
                MinecraftForge.EVENT_BUS.unregister(this);
                subscribed = false;
            }
            onDisable();
        }
        Notifier.moduleToggled(name, on);
        ConfigManager.markDirty();
    }

    /** Restores every setting, the key and the on/off state to their defaults. */
    public void reset() {
        for (Setting<?> s : settings) s.reset();
        key = 0;
        if (toggleable()) setEnabled(defaultEnabled);
        ConfigManager.markDirty();
    }

    protected void onEnable() {}

    protected void onDisable() {}
}
