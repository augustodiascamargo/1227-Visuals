package net.m1227.module;

import java.io.File;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/**
 * Settings of the 1227 client interface: which Minecraft screens are replaced by 1227's own
 * (main menu, pause, servers, options, death, disconnect), the restyle of the remaining vanilla
 * screens, and the menu background. Every replacement can be turned off here, and every 1227
 * screen keeps a way to open the original one.
 */
public final class InterfaceModule extends Module {
    public static InterfaceModule INSTANCE;
    public final Setting.Bool mainMenu, pauseMenu, servers, worlds, options, death, disconnect, restyle, parallax, changelog, pauseBlur;
    public final Setting.Mode background, layout;
    public final Setting.Text image;
    public final Setting.Num dim;

    public InterfaceModule() {
        super("Interface", "1227 main menu, pause menu, server list, settings and screen style.", Category.SETTINGS, true);
        INSTANCE = this;
        mainMenu = add(new Setting.Bool("1227 main menu", true));
        pauseMenu = add(new Setting.Bool("1227 pause menu", true));
        servers = add(new Setting.Bool("1227 server list", true));
        worlds = add(new Setting.Bool("1227 world list", true));
        options = add(new Setting.Bool("Options opens 1227 Settings", true));
        death = add(new Setting.Bool("1227 death screen", true));
        disconnect = add(new Setting.Bool("1227 disconnect screen", true));
        restyle = add(new Setting.Bool("Restyle connecting, loading and confirmation screens", true));
        layout = add(new Setting.Mode("Main menu layout", "Left", "Left", "Center"));
        background = add(new Setting.Mode("Menu background", "Aurora", "Aurora", "Constellation", "Image", "Solid"));
        image = add(new Setting.Text("Background image file", "", 64, "empty = first image in config/1227/backgrounds"))
                .showIf(() -> background.is("Image"));
        add(new Setting.Action("Open backgrounds folder", () -> net.m1227.client.menu.MenuBackground.openFolder()));
        dim = add(new Setting.Num("Background dim", 0.35, 0.0, 0.9, 0.05, ""));
        parallax = add(new Setting.Bool("Background follows the mouse", true));
        changelog = add(new Setting.Bool("Show what's new on the main menu", true));
        pauseBlur = add(new Setting.Bool("Blur the game behind the pause menu", true));
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    public static boolean on(Setting.Bool b) {
        return INSTANCE != null && b.on();
    }

    public static File backgroundsDir() {
        File d = new File(new File(Mod1227.configDir, "1227"), "backgrounds");
        d.mkdirs();
        return d;
    }
}
