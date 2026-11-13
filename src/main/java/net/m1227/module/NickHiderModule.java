package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Nick Hider: shows another name instead of yours on your own screen, for videos and streams.
 * Replaces your name in chat lines, in the 1227 HUD (Dynamic Island, scoreboard, module menu,
 * main menu). Purely local: other players and the server still see your real name; nothing is
 * sent. Chat lines that contained your name are rebuilt as plain text, so links in those lines
 * stop being clickable.
 */
public final class NickHiderModule extends Module {
    public static NickHiderModule INSTANCE;
    private final Setting.Text alias;
    private final Setting.Bool chat;

    public NickHiderModule() {
        super("Nick Hider", "Show another name instead of yours on your screen (only for you).", Category.GAMEPLAY, false);
        INSTANCE = this;
        alias = add(new Setting.Text("Shown name", "You", 16, "name to show"));
        chat = add(new Setting.Bool("Also in chat", true));
    }

    private static String own() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.getSession() == null ? null : mc.getSession().getUsername();
    }

    /** The name to show for this name (your alias when it is yours and the module is on). */
    public static String name(String real) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || real == null) return real;
        String me = own();
        return me != null && real.equals(me) ? INSTANCE.alias.get() : real;
    }

    /** Text with every occurrence of your name replaced (when on). */
    public static String text(String s) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || s == null) return s;
        String me = own();
        return me == null || me.isEmpty() ? s : s.replace(me, INSTANCE.alias.get());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChat(ClientChatReceivedEvent e) {
        if (!chat.on() || e.message == null) return;
        String me = own();
        if (me == null || me.isEmpty()) return;
        String f = e.message.getFormattedText();
        if (!f.contains(me)) return;
        e.message = new ChatComponentText(f.replace(me, alias.get()));
    }
}
