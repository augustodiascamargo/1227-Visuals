package net.m1227.client.menu;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.client.settings.SettingsScreen;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Img;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.module.InterfaceModule;
import net.m1227.ui.ClickGui;
import net.m1227.ui.Gfx;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.util.ResourceLocation;

/**
 * The 1227 main menu: logo, Singleplayer, Multiplayer, Options, Modules, Cosmetics, Profiles and
 * Mods, plus Language, Screenshots, the classic Minecraft menu and Quit. Right side: your
 * account name and skin, the installed version and what is new.
 */
public final class MainMenuScreen extends UiScreen {
    private static List<String> whatsNew;
    private static boolean introShown, forceIntro;
    private Intro intro;
    private boolean introChecked;
    private final MenuCharacter character = new MenuCharacter();

    public MainMenuScreen() {
        super(null);
    }

    @Override
    public void initGui() {
        super.initGui();
        if (introChecked) return; // resizes and returning from sub-screens never replay it
        introChecked = true;
        InterfaceModule m = InterfaceModule.INSTANCE;
        String style = m == null ? "Press to enter" : m.intro.get();
        boolean every = m != null && m.introWhen.get().startsWith("Every");
        if (forceIntro && style.equals("Off")) style = "Press to enter";
        if (!style.equals("Off") && (forceIntro || every || !introShown)) intro = new Intro(style);
        introShown = true;
        forceIntro = false;
    }

    /** Opens the main menu and plays the opening animation once (Settings › Interface). */
    public static void preview() {
        forceIntro = true;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            forceIntro = false;
            net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Opening animation", "Plays on the main menu", net.m1227.core.Notifier.INFO, 1800);
            return;
        }
        mc.displayGuiScreen(new MainMenuScreen());
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        if (intro != null && intro.menuAlpha() < 0.5f) {
            intro.skip();
            return;
        }
        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (intro != null) {
            intro.skip();
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void back() {
        // Esc does nothing on the title screen (as in vanilla)
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static List<String> whatsNew() {
        if (whatsNew != null) return whatsNew;
        List<String> out = new ArrayList<String>();
        try (InputStream in = MainMenuScreen.class.getResourceAsStream("/assets/m1227/whatsnew.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String l;
                while ((l = r.readLine()) != null) if (!l.trim().isEmpty()) out.add(l.trim());
            }
        } catch (Exception ignored) {
            // no list
        }
        whatsNew = out;
        return out;
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, false);
        if (intro != null && intro.menuAlpha() <= 0f) open = 0f; // the menu builds up as the intro hands over
        float a = appear();
        boolean center = InterfaceModule.INSTANCE != null && InterfaceModule.INSTANCE.layout.is("Center");
        float colW = 168;
        float x = center ? (w - colW) / 2f : Math.max(18, w * 0.07f);
        int acc = DS.accent();

        String[] labels = {"Singleplayer", "Multiplayer", "Options", "Modules", "Cosmetics", "Profiles", "Mods"};
        int[] glyphs = {Glyph.PLAY, Glyph.SERVERS, Glyph.SLIDERS, Glyph.GEAR, Glyph.CROWN, Glyph.PERSON, Glyph.MODS};
        String[] tips = {null, null, "Minecraft and 1227 settings in one place", "Every 1227 module (also Right Ctrl in game)",
                null, "Save and load your 1227 setups", "Forge mod list"};
        float bh = 22, gap = 4;
        float listH = labels.length * (bh + gap);
        float top = Math.max(64, (h - listH) / 2f + 12);
        // logo block
        float ly = top - 58;
        if (intro == null) Gfx.logo(x - 2, ly, 34, acc, 0.6f);
        Ui.textBold("1227", x + 38, ly + 6, 2.1f, Gfx.alpha(DS.TEXT, a));
        Ui.caption("Client  ·  Minecraft 1.8.9", x + 39, ly + 27);

        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            float d = Math.max(0f, Math.min(1f, a * 1.6f - i * 0.08f));
            float by = top + i * (bh + gap) + (1f - d) * 8f;
            menuButton(labels[i], glyphs[i], x, by, colW, bh, d, tips[i], () -> action(k));
        }
        // small actions under the list
        float iy = top + listH + 6;
        ui.iconButton("lang", x, iy, 20, Glyph.GLOBE, "Language", true, () -> mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager())));
        ui.iconButton("shots", x + 24, iy, 20, Glyph.CAMERA, "Screenshots", true, () -> mc.displayGuiScreen(new ScreenshotScreen(this)));
        ui.iconButton("classic", x + 48, iy, 20, Glyph.EYE, "Classic Minecraft menu", true, () -> MenuSystem.openVanilla(new GuiMainMenu()));
        ui.iconButton("quit", x + 72, iy, 20, Glyph.POWER, "Quit game", true, () -> mc.shutdown());

        InterfaceModule im = InterfaceModule.INSTANCE;
        boolean showChar = im != null && im.character.on() && w > 360;
        if (!center && w > 420) sidePanel(w, h, a, showChar);
        if (showChar) {
            // the character walks in once the opening animation hands over to the menu
            if (!character.started() && (intro == null || intro.menuAlpha() > 0f)) character.start(im.walkIn.on());
            float size = h * 0.27f;
            character.draw(center ? w * 0.82f : w * 0.78f, h * 0.97f, size, ui.mx, ui.my, w);
        }

        // footer
        Ui.text("1227 v" + Mod1227.VERSION + "  ·  Minecraft 1.8.9  ·  Forge 11.15.1.2318", 8, h - 12, DS.T_SMALL, Gfx.alpha(DS.FAINT, a));
        Ui.textRight("Not affiliated with Mojang or Microsoft", w - 8, h - 12, DS.T_SMALL, Gfx.alpha(DS.FAINT, a));
        if (intro != null) {
            intro.draw(w, h, x - 2, ly, 34);
            if (intro.done()) intro = null;
        }
    }

    private void menuButton(String label, int glyph, float x, float y, float w, float h, float alpha, String tip, Runnable r) {
        boolean hv = ui.hover(x, y, w, h);
        float k = ui.anim("mm" + label, hv ? 1f : 0f);
        int acc = DS.accent();
        Gfx.roundedRect(x, y, w, h, DS.R_MD, Gfx.alpha(Gfx.mix(0x99151820, 0xE01E2130, k), alpha));
        Gfx.roundedRect(x, y + 5, 2 + k * 1.5f, h - 10, 1.5f, Gfx.alpha(acc, (0.25f + 0.75f * k) * alpha));
        Glyph.draw(glyph, x + 15 + k * 2, y + h / 2f, Gfx.alpha(Gfx.mix(DS.MUTED, acc, k), alpha));
        Ui.textMedium(label, x + 28 + k * 2, y + h / 2f - 3.6f, DS.T_BODY, Gfx.alpha(Gfx.mix(DS.TEXT, 0xFFFFFFFF, k), alpha));
        if (hv && tip != null) ui.tip(tip);
        ui.click(x, y, w, h, r);
    }

    private void action(int i) {
        switch (i) {
            case 0:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 1:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 2:
                mc.displayGuiScreen(new SettingsScreen(this, null));
                break;
            case 3:
                ClickGui.open(null, this);
                break;
            case 4:
                mc.displayGuiScreen(new net.m1227.client.settings.CosmeticsScreen(this));
                break;
            case 5:
                mc.displayGuiScreen(new SettingsScreen(this, "Profiles"));
                break;
            default:
                mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(this));
        }
    }

    /** Account card and what's new. */
    private void sidePanel(int w, int h, float a, boolean withCharacter) {
        float pw = Math.min(210, w * 0.3f), px = w - pw - 18, py = withCharacter ? 12 : Math.max(18, h * 0.16f);
        String name = mc.getSession() == null ? "Player" : net.m1227.module.NickHiderModule.name(mc.getSession().getUsername());
        Ui.glassCard(px, py, pw, 44);
        face(name, px + 10, py + 10, 24);
        Ui.textMedium(Gfx.trim(name, pw - 60), px + 42, py + 12, DS.T_BODY, DS.TEXT);
        Ui.text("Signed in with the Minecraft launcher", px + 42, py + 24, 0.72f, DS.FAINT);

        List<String> news = whatsNew();
        if (InterfaceModule.INSTANCE != null && !InterfaceModule.INSTANCE.changelog.on() || news.isEmpty() || withCharacter) return;
        float ny = py + 52;
        float nh = 30 + news.size() * 12;
        Ui.glassCard(px, ny, pw, nh);
        Ui.caption("What's new in " + Mod1227.VERSION, px + 10, ny + 10);
        for (int i = 0; i < news.size(); i++) {
            float y = ny + 24 + i * 12;
            Gfx.circle(px + 13, y + 3, 1.3f, DS.accent());
            Ui.text(Gfx.trim(news.get(i), pw - 30), px + 19, y, DS.T_SMALL, DS.MUTED);
        }
    }

    /** The player's skin face (falls back to the default skin while it downloads). */
    private void face(String name, float x, float y, float s) {
        try {
            ResourceLocation skin = AbstractClientPlayer.getLocationSkin(name);
            AbstractClientPlayer.getDownloadImageSkin(skin, name);
            Img.draw(skin, x, y, s, s, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 1f);
            Img.draw(skin, x - s * 0.06f, y - s * 0.06f, s * 1.12f, s * 1.12f, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 1f);
        } catch (Throwable t) {
            Gfx.roundedRect(x, y, s, s, 4, DS.SURFACE_3);
            Ui.textCenter(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), x + s / 2f, y + s / 2f - 4, DS.T_BODY, DS.TEXT);
        }
    }
}
