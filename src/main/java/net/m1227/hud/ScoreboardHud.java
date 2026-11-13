package net.m1227.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.GuiIngameForge;

/**
 * Replaces the sidebar scoreboard with a movable one: hide the red numbers, change the
 * background, scale and position, shadow, and title. While this element is on, Forge's own
 * sidebar is switched off (GuiIngameForge.renderObjective) and switched back on when it's off.
 * It shows exactly what the server sends; nothing is added or changed.
 */
public final class ScoreboardHud extends HudModule {
    private final Setting.Bool numbers, title, shadow;
    private final Setting.Num lineGap;
    private final List<String[]> lines = new ArrayList<String[]>(); // {text, score}
    private String header = "";
    private float cachedW = 90f;

    public ScoreboardHud() {
        super("Scoreboard", "Movable sidebar: hide the red numbers, background, scale.", 0.86, 0.38);
        numbers = add(new Setting.Bool("Red score numbers", false));
        title = add(new Setting.Bool("Title", true));
        shadow = add(new Setting.Bool("Text shadow", true));
        lineGap = add(new Setting.Num("Line spacing", 9, 8, 13, 1, "px"));
    }

    @Override
    protected void onEnable() {
        GuiIngameForge.renderObjective = false;
    }

    @Override
    protected void onDisable() {
        GuiIngameForge.renderObjective = true;
    }

    /** The sidebar objective the same way vanilla picks it (team-coloured slot first). */
    private static ScoreObjective objective(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScorePlayerTeam team = sb.getPlayersTeam(mc.thePlayer.getName());
        if (team != null) {
            int slot = team.getChatFormat().getColorIndex();
            if (slot >= 0) {
                ScoreObjective o = sb.getObjectiveInDisplaySlot(3 + slot);
                if (o != null) return o;
            }
        }
        return sb.getObjectiveInDisplaySlot(1);
    }

    private void collect(boolean editing) {
        lines.clear();
        Minecraft mc = Minecraft.getMinecraft();
        ScoreObjective o = objective(mc);
        if (o == null) {
            if (editing) {
                header = "§e§lMY SERVER";
                String[][] sample = {{"§7Mode: §aDuels", "5"}, {"", "4"}, {"Kills: §a3", "3"}, {"Coins: §6120", "2"}, {"§eplay.example.net", "1"}};
                for (String[] s : sample) lines.add(s);
            }
            return;
        }
        Scoreboard sb = o.getScoreboard();
        header = o.getDisplayName();
        Collection<Score> all = sb.getSortedScores(o);
        List<Score> list = new ArrayList<Score>();
        for (Score s : all) {
            if (s.getPlayerName() != null && !s.getPlayerName().startsWith("#")) list.add(s);
        }
        if (list.size() > 15) list = list.subList(list.size() - 15, list.size());
        for (int i = list.size() - 1; i >= 0; i--) { // highest score at the top, like vanilla
            Score s = list.get(i);
            String name = ScorePlayerTeam.formatPlayerName(sb.getPlayersTeam(s.getPlayerName()), s.getPlayerName());
            lines.add(new String[]{net.m1227.module.NickHiderModule.text(name), String.valueOf(s.getScorePoints())});
        }
    }

    @Override
    protected boolean hasContent() {
        return objective(Minecraft.getMinecraft()) != null;
    }

    @Override
    public float width() {
        return cachedW;
    }

    @Override
    public float height() {
        int n = lines.isEmpty() ? 5 : lines.size();
        return 6 + (title.on() ? lineGap.f() + 2 : 0) + n * lineGap.f();
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        collect(editing);
        FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        float w = title.on() ? fr.getStringWidth(header) : 0;
        for (String[] l : lines) {
            w = Math.max(w, fr.getStringWidth(l[0]) + (numbers.on() ? fr.getStringWidth(l[1]) + 8 : 0));
        }
        cachedW = w + 10;
        panel(x, y, width(), height());
        float ry = y + 4;
        if (title.on()) {
            fr.drawString(header, x + (width() - fr.getStringWidth(header)) / 2f, ry, 0xFFFFFFFF, shadow.on());
            ry += lineGap.f() + 2;
        }
        for (String[] l : lines) {
            fr.drawString(l[0], x + 5, ry, 0xFFFFFFFF, shadow.on());
            if (numbers.on()) fr.drawString("§c" + l[1], x + width() - 5 - fr.getStringWidth(l[1]), ry, 0xFFFFFFFF, shadow.on());
            ry += lineGap.f();
        }
        net.minecraft.client.renderer.GlStateManager.enableBlend();
    }
}
