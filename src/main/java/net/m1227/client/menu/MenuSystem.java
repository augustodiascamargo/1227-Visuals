package net.m1227.client.menu;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.m1227.client.Reflect;
import net.m1227.client.settings.SettingsScreen;
import net.m1227.module.InterfaceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Menu System: swaps Minecraft's screens for 1227's when they open (Forge's GuiOpenEvent; no
 * class patching), and restyles the screens that are kept (connecting, loading terrain,
 * confirmations) by drawing them with the design system while Minecraft keeps handling their
 * buttons. Only the exact vanilla classes are replaced, so screens of other mods (even ones
 * extending vanilla screens) are left alone. Each replacement can be turned off in Interface.
 */
public final class MenuSystem {
    public static final MenuSystem INSTANCE = new MenuSystem();
    private static Class<?> bypass;
    private final Map<GuiScreen, List<GuiButton>> buttons = new WeakHashMap<GuiScreen, List<GuiButton>>();

    private MenuSystem() {}

    /** Opens the original Minecraft screen once, without replacing it (the "classic" buttons). */
    public static void openVanilla(GuiScreen screen) {
        bypass = screen.getClass();
        Minecraft.getMinecraft().displayGuiScreen(screen);
    }

    private static InterfaceModule m() {
        return InterfaceModule.INSTANCE;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOpen(GuiOpenEvent e) {
        GuiScreen g = e.gui;
        if (g == null || m() == null) return;
        Class<?> c = g.getClass();
        if (c == bypass) {
            bypass = null;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (c == GuiMainMenu.class && m().mainMenu.on()) {
                e.gui = new MainMenuScreen();
            } else if (c == GuiIngameMenu.class && m().pauseMenu.on()) {
                e.gui = new PauseScreen();
            } else if (c == GuiMultiplayer.class && m().servers.on()) {
                GuiScreen parent = Reflect.get(g, GuiMultiplayer.class, GuiScreen.class, 0);
                e.gui = new ServerScreen(parent != null ? parent : new MainMenuScreen());
            } else if (c == GuiSelectWorld.class && m().worlds.on()) {
                GuiScreen parent = Reflect.get(g, GuiSelectWorld.class, GuiScreen.class, 0);
                e.gui = new WorldScreen(parent != null ? parent : new MainMenuScreen());
            } else if (c == GuiOptions.class && m().options.on()) {
                GuiScreen parent = Reflect.get(g, GuiOptions.class, GuiScreen.class, 0);
                e.gui = new SettingsScreen(parent, null);
            } else if (c == GuiGameOver.class && m().death.on() && mc.theWorld != null
                    && !mc.theWorld.getWorldInfo().isHardcoreModeEnabled()) {
                e.gui = new DeathScreen();
            } else if (c == GuiDisconnected.class && m().disconnect.on()) {
                e.gui = DisconnectedScreen.from((GuiDisconnected) g);
            }
        } catch (Throwable t) {
            // never break navigation: keep Minecraft's own screen
            net.m1227.Mod1227.LOGGER.warn("1227 menu replacement failed for " + c.getName(), t);
            e.gui = g;
        }
    }

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (Restyle.handles(e.gui)) buttons.put(e.gui, e.buttonList);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre e) {
        if (m() == null || !m().restyle.on() || !Restyle.handles(e.gui)) return;
        List<GuiButton> list = buttons.get(e.gui);
        if (list == null) return;
        if (Restyle.draw(e.gui, list, e.mouseX, e.mouseY)) e.setCanceled(true);
    }
}
