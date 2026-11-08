package net.m1227.module;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * Restyled in-game chat (while the chat box is closed): new messages slide
 * in, old ones fade smoothly, with a rounded or no background. When you
 * open the chat to type or scroll, Minecraft's own chat is shown, so
 * clicking links and scrolling work exactly as usual.
 */
public final class ChatModule extends Module {
    private final Setting.Mode bg;
    private final Setting.Num opacity, slide;
    private final Setting.Bool shadow, accentBar;
    private final Map<ChatLine, Long> seen = new IdentityHashMap<ChatLine, Long>();
    private Field lines;
    private boolean broken;

    public ChatModule() {
        super("Chat", "Animated chat with a cleaner background.", Category.HUD, false);
        bg = add(new Setting.Mode("Background", "Per message", "Per message", "Block", "None"));
        opacity = add(new Setting.Num("Background opacity", 0.45, 0.0, 1.0, 0.05, "")).showIf(() -> !bg.is("None"));
        slide = add(new Setting.Num("Slide-in time", 0.18, 0.0, 0.6, 0.02, "s"));
        shadow = add(new Setting.Bool("Text shadow", true));
        accentBar = add(new Setting.Bool("Accent bar on new messages", true));
    }

    @SuppressWarnings("unchecked")
    private List<ChatLine> lines(GuiNewChat chat) {
        if (broken) return null;
        try {
            if (lines == null) lines = ReflectionHelper.findField(GuiNewChat.class, "drawnChatLines", "field_146253_i");
            return (List<ChatLine>) lines.get(chat);
        } catch (Throwable t) {
            broken = true;
            Mod1227.LOGGER.warn("1227 Chat: could not read chat lines, using vanilla chat", t);
            return null;
        }
    }

    @SubscribeEvent
    public void onChat(RenderGameOverlayEvent.Chat e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.ingameGUI == null || mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) return;
        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        if (chat.getChatOpen()) return; // vanilla while typing / scrolling
        List<ChatLine> list = lines(chat);
        if (list == null) return;
        e.setCanceled(true);
        draw(mc, chat, list, e.posX, e.posY);
    }

    private void draw(Minecraft mc, GuiNewChat chat, List<ChatLine> list, int px, int py) {
        int counter = mc.ingameGUI.getUpdateCounter();
        float scale = chat.getChatScale();
        int width = MathHelper.ceiling_float_int(chat.getChatWidth() / scale);
        int max = chat.getLineCount();
        float opacityChat = mc.gameSettings.chatOpacity * 0.9f + 0.1f;
        long now = System.currentTimeMillis();
        int ac = 0xFF000000 | ClientModule.accent();
        GlStateManager.pushMatrix();
        GlStateManager.translate(px + 2f, py + 20f, 0f); // same origin as vanilla drawChat
        GlStateManager.scale(scale, scale, 1f);
        int shown = 0;
        float blockTop = 0;
        float maxW = 0;
        // first pass: measure visible lines for "Block"
        for (int i = 0; i < list.size() && i < max; i++) {
            ChatLine l = list.get(i);
            if (counter - l.getUpdatedCounter() >= 200) break;
            shown++;
            maxW = Math.max(maxW, mc.fontRendererObj.getStringWidth(l.getChatComponent().getFormattedText()));
        }
        if (bg.is("Block") && shown > 0) {
            ChatLine newest = list.get(0);
            float a = alpha(counter - newest.getUpdatedCounter()) * opacityChat;
            blockTop = -shown * 9f;
            Gfx.roundedRect(-2, blockTop - 1, Math.min(width, maxW + 8), shown * 9f + 2, 3f, Gfx.alpha(0xFF0B0C11, opacity.f() * a));
        }
        for (int i = 0; i < shown; i++) {
            ChatLine l = list.get(i);
            int age = counter - l.getUpdatedCounter();
            float a = alpha(age) * opacityChat;
            if (a < 0.02f) continue;
            Long first = seen.get(l);
            if (first == null) {
                // lines older than 1 s when first seen (e.g. after joining) don't animate
                first = age > 20 ? 0L : now;
                seen.put(l, first);
            }
            float sl = slide.f() <= 0f || first == 0L ? 1f : Math.min(1f, (now - first) / (slide.f() * 1000f));
            float ease = 1f - (1f - sl) * (1f - sl) * (1f - sl);
            float off = (1f - ease) * -24f;
            a *= ease;
            String s = l.getChatComponent().getFormattedText();
            float y = -i * 9f - 8f;
            float tw = mc.fontRendererObj.getStringWidth(s);
            if (bg.is("Per message")) Gfx.roundedRect(-2 + off, y - 1, Math.min(width, tw + 6), 10, 2.5f, Gfx.alpha(0xFF0B0C11, opacity.f() * a));
            if (accentBar.on() && first != 0L && now - first < 2500) {
                float b = 1f - (now - first) / 2500f;
                Gfx.rect(-3 + off, y - 1, 1, 10, Gfx.alpha(ac, b * a));
            }
            int ta = Math.max(8, (int) (a * 255)) << 24;
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            mc.fontRendererObj.drawString(s, off + 1, y, ta | 0xFFFFFF, shadow.on());
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        if (seen.size() > 300) seen.keySet().retainAll(new java.util.HashSet<Object>(list));
    }

    /** Vanilla's fade: full for 9 s, then fades out over the last second. */
    private static float alpha(int age) {
        double d = 1.0 - age / 200.0;
        d = Math.max(0.0, Math.min(1.0, d * 10.0));
        return (float) (d * d);
    }
}
