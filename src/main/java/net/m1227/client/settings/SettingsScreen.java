package net.m1227.client.settings;

import java.io.File;
import java.util.List;
import net.m1227.client.Os;
import net.m1227.client.menu.MenuBackground;
import net.m1227.client.menu.MenuSystem;
import net.m1227.client.perf.Benchmark;
import net.m1227.client.perf.PerfPresets;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.module.ClientModule;
import net.m1227.module.InterfaceModule;
import net.m1227.module.MenuStyleModule;
import net.m1227.module.PerformanceModule;
import net.m1227.ui.ClickGui;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiSnooper;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.client.audio.SoundCategory;
import org.lwjgl.input.Keyboard;

/**
 * The one settings screen of the 1227 client. Minecraft's own options (video, sound, chat,
 * controls, skin, language, resource packs) and every 1227 module live here, in 13 categories:
 * General, Interface, HUD, Visuals, Cosmetics, PvP, Animations, Audio, Performance, Controls,
 * World, Profiles and Advanced. Minecraft options are changed through Minecraft's own setters,
 * so they behave exactly as in the vanilla screens; the vanilla screens stay one click away.
 */
public final class SettingsScreen extends UiScreen {
    static final String[] PAGES = {"General", "Interface", "HUD", "Visuals", "Cosmetics", "PvP", "Animations", "Audio",
            "Performance", "Controls", "World", "Profiles", "Advanced"};
    private static final int[] GLYPHS = {Glyph.SLIDERS, Glyph.PALETTE, Glyph.HUD, Glyph.EYE, Glyph.CROWN, Glyph.SWORD,
            Glyph.MOVE, Glyph.SOUND, Glyph.BOLT, Glyph.KEY, Glyph.WORLD, Glyph.PERSON, Glyph.CODE};
    private static final String[] DESC = {
            "Language, field of view, skin layers and resource packs",
            "Menus, chat, GUI scale, accent colour and menu style",
            "On-screen elements and the HUD layout editor",
            "Minecraft graphics and 1227 auras, trails and weapon effects",
            "Halo, china hat, wings, energy shell and more",
            "Hit, kill and combat effects, sounds and practice tools",
            "1.7 animations, camera, zoom and motion blur",
            "Minecraft volume, music player and Discord",
            "Presets, limits, background FPS and benchmark",
            "Mouse, key bindings and the 1227 menu key",
            "Skies, ambience, weather, time and item physics",
            "Save and load your whole 1227 setup",
            "Config files, backups, loading screen and resets"};

    private static String lastPage = "General";
    private String page;
    private Module moduleView;
    private String search = "";
    private String profileName = "";
    private Object confirm;
    private long confirmAt;
    private Float pendingMip;

    public SettingsScreen(GuiScreen parent, String startPage) {
        super(parent);
        page = startPage != null ? startPage : lastPage;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        applyMip();
        mc.gameSettings.saveOptions();
        net.m1227.core.Keybinds.INSTANCE.sync();
        ConfigManager.save();
        Rows.capturing = null;
        lastPage = page;
    }

    @Override
    protected void back() {
        if (Rows.capturing != null) {
            Rows.capturing = null;
            return;
        }
        if (moduleView != null) {
            moduleView = null;
            return;
        }
        mc.displayGuiScreen(parent);
    }

    private boolean confirming(Object key) {
        if (confirm != null && System.currentTimeMillis() - confirmAt > 3000) confirm = null;
        return key.equals(confirm);
    }

    /** First click arms, second click (within 3 s) runs. */
    private Runnable twice(final Object key, final Runnable r) {
        return () -> {
            if (confirming(key)) {
                confirm = null;
                r.run();
            } else {
                confirm = key;
                confirmAt = System.currentTimeMillis();
            }
        };
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        float ww = Math.min(660, w - 16), wh = Math.min(420, h - 16);
        float wx = (w - ww) / 2f, wy = (h - wh) / 2f + (1f - appear()) * 6f;
        Gfx.roundedRect(wx, wy, ww, wh, DS.R_LG, 0xF00E1016);
        Gfx.roundedOutline(wx, wy, ww, wh, DS.R_LG, 0.6f, DS.LINE_STRONG);
        float sw = Math.min(124, ww * 0.26f);
        sidebar(wx, wy, sw, wh);
        content(wx + sw, wy, ww - sw, wh);
    }

    private void sidebar(float x, float y, float w, float h) {
        Gfx.rect(x + w, y + 8, 0.6f, h - 16, DS.LINE);
        Gfx.logo(x + 10, y + 10, 16, DS.accent(), 0.4f);
        Ui.textBold("Settings", x + 30, y + 14, DS.T_BODY, DS.TEXT);
        float listTop = y + 34, listH = h - 34 - 34;
        float off = ui.beginScroll("sidebar", x + 4, listTop, w - 8, listH);
        float iy = listTop - off;
        for (int i = 0; i < PAGES.length; i++) {
            final String p = PAGES[i];
            boolean on = p.equals(page) && moduleView == null || moduleView != null && p.equals(page);
            boolean hv = ui.hover(x + 4, iy, w - 8, 18);
            float k = ui.anim("side" + p, on ? 1f : hv ? 0.5f : 0f);
            Gfx.roundedRect(x + 6, iy, w - 12, 18, DS.R_SM, Gfx.alpha(on ? Gfx.alpha(DS.accent(), 0.2f) : DS.SURFACE_3, k));
            if (on) Gfx.roundedRect(x + 6, iy + 4, 2, 10, 1, DS.accent());
            Glyph.draw(GLYPHS[i], x + 18, iy + 9, on ? DS.accent() : Gfx.mix(DS.FAINT, DS.MUTED, k));
            Ui.text(p, x + 28, iy + 5.4f, DS.T_BODY, on ? DS.TEXT : Gfx.mix(DS.MUTED, DS.TEXT, k));
            ui.click(x + 4, iy, w - 8, 18, () -> {
                page = p;
                moduleView = null;
                search = "";
                ui.resetScroll("content");
            });
            iy += 20;
        }
        ui.endScroll(PAGES.length * 20);
        ui.button("done", "Done", x + 8, y + h - 28, w - 16, 20, Ui.PRIMARY, true, -1, () -> mc.displayGuiScreen(parent));
    }

    private void content(float x, float y, float w, float h) {
        float pad = 14;
        String title = moduleView != null ? moduleView.name : page;
        String desc = moduleView != null ? moduleView.description : DESC[indexOf(page)];
        float tx = x + pad;
        if (moduleView != null) {
            ui.iconButton("backmod", tx, y + 12, 18, Glyph.BACK, "Back to " + page, true, () -> moduleView = null);
            tx += 24;
        }
        Ui.textBold(title, tx, y + 13, DS.T_LABEL, DS.TEXT);
        Ui.text(Gfx.trim(desc, w - pad * 2 - 150), tx, y + 26, DS.T_SMALL, DS.FAINT);
        if (moduleView == null && !page.equals("Profiles")) {
            ui.textField("search", x + w - pad - 130, y + 13, 130, 16, search, "Search", 30, v -> search = v);
        }
        float top = y + 42, viewH = h - 42 - 10;
        float off = ui.beginScroll("content", x + pad - 2, top, w - pad * 2 + 4, viewH);
        Rows r = new Rows(ui, x + pad, top - off, w - pad * 2 - 4, top, top + viewH, moduleView == null ? search : "");
        float start = r.y;
        if (moduleView != null) modulePage(r, moduleView);
        else page(r);
        ui.endScroll(r.y - start + 6);
    }

    private static int indexOf(String p) {
        for (int i = 0; i < PAGES.length; i++) if (PAGES[i].equals(p)) return i;
        return 0;
    }

    // ------------------------------------------------------------------ helpers

    private void modules(Rows r, Category c) {
        for (final Module m : ModuleManager.inCategory(c)) r.module(m, () -> open(m));
    }

    private void open(Module m) {
        moduleView = m;
        ui.resetScroll("content");
    }

    private void moduleSettings(Rows r, Module m, boolean actions) {
        for (Setting<?> s : m.settings()) if (actions || !(s instanceof Setting.Action)) r.setting(s);
    }

    private void modulePage(Rows r, final Module m) {
        if (m.toggleable()) r.toggle("Enabled", null, m.isEnabled(), m::toggle);
        moduleSettings(r, m, true);
        r.space(6);
        Object key = "reset" + m.name;
        r.button("Reset " + m.name, "Every option of this module back to its default", confirming(key) ? "Click again" : "Reset",
                Ui.DANGER, twice(key, m::reset));
    }

    /** A Minecraft option drawn with 1227 controls, changed through Minecraft's own setters. */
    private void vanilla(Rows r, final GameSettings.Options o, String desc) {
        final GameSettings gs = mc.gameSettings;
        String label = I18n.format(o.getEnumString());
        String full = gs.getKeyBinding(o);
        String value = full.startsWith(label + ": ") ? full.substring(label.length() + 2) : full;
        if (o.getEnumFloat()) {
            float cur = o == GameSettings.Options.MIPMAP_LEVELS && pendingMip != null ? pendingMip : gs.getOptionFloatValue(o);
            if (o == GameSettings.Options.MIPMAP_LEVELS && pendingMip != null) value = String.valueOf(Math.round(pendingMip));
            r.slider(label, desc, value, o.normalizeValue(cur), v -> {
                float val = o.denormalizeValue(v);
                // mipmaps rebuild every texture: applied once, when the slider is released
                if (o == GameSettings.Options.MIPMAP_LEVELS) pendingMip = val;
                else gs.setOptionFloatValue(o, val);
            });
        } else if (o.getEnumBoolean()) {
            r.toggle(label, desc, gs.getOptionOrdinalValue(o), () -> changed(o));
        } else {
            r.cycle(label, desc, value, () -> changed(o), () -> changed(o));
        }
    }

    private void changed(GameSettings.Options o) {
        int before = mc.gameSettings.guiScale;
        mc.gameSettings.setOptionValue(o, 1);
        if (o == GameSettings.Options.GUI_SCALE && mc.gameSettings.guiScale != before) {
            ScaledResolution sr = new ScaledResolution(mc);
            setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
        }
    }

    private void applyMip() {
        if (pendingMip == null) return;
        float v = pendingMip;
        pendingMip = null;
        if ((int) v != mc.gameSettings.mipmapLevels) mc.gameSettings.setOptionFloatValue(GameSettings.Options.MIPMAP_LEVELS, v);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(mx, my, state);
        applyMip();
    }

    // ------------------------------------------------------------------ pages

    private void page(Rows r) {
        final GameSettings gs = mc.gameSettings;
        if (!search.trim().isEmpty()) {
            // search covers every module, wherever it lives
            List<Module> found = ModuleManager.search(search);
            if (!found.isEmpty()) {
                r.section("Modules");
                for (final Module m : found) r.module(m, () -> open(m));
            }
        }
        switch (page) {
            case "General":
                r.section("Game");
                r.button("Language", I18n.format("options.language") + ": " + mc.getLanguageManager().getCurrentLanguage().toString(), "Change",
                        Ui.SECONDARY, () -> mc.displayGuiScreen(new GuiLanguage(this, gs, mc.getLanguageManager())));
                vanilla(r, GameSettings.Options.FOV, null);
                r.button("Resource packs", "Packs with icons, checks, order, import and backup", "Open", Ui.PRIMARY,
                        () -> mc.displayGuiScreen(new net.m1227.client.menu.ResourcePackScreen(this)));
                r.button("Classic resource pack screen", "Minecraft's own screen", "Open", Ui.SECONDARY, () -> mc.displayGuiScreen(new GuiScreenResourcePacks(this)));
                r.button("Mods", "Mods installed with Forge", "Open", Ui.SECONDARY, () -> mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(this)));
                r.section("Skin layers");
                for (final EnumPlayerModelParts part : EnumPlayerModelParts.values()) {
                    r.toggle(pretty(part.name()), null, gs.getModelParts().contains(part), () -> gs.switchModelPartEnabled(part));
                }
                r.section("Other");
                r.button("Snooper", "Anonymous usage data sent to Mojang", "Open", Ui.SECONDARY, () -> mc.displayGuiScreen(new GuiSnooper(this, gs)));
                r.button("Classic Minecraft options", "The original options screen (difficulty and everything else)", "Open", Ui.GHOST,
                        () -> MenuSystem.openVanilla(new GuiOptions(this, gs)));
                break;
            case "Interface":
                r.section("1227 screens");
                moduleSettings(r, InterfaceModule.INSTANCE, true);
                r.section("Accent and menu");
                moduleSettings(r, ClientModule.INSTANCE, false);
                moduleSettings(r, MenuStyleModule.INSTANCE, false);
                r.section("Minecraft interface");
                vanilla(r, GameSettings.Options.GUI_SCALE, null);
                vanilla(r, GameSettings.Options.FORCE_UNICODE_FONT, null);
                vanilla(r, GameSettings.Options.REDUCED_DEBUG_INFO, null);
                r.section("Chat");
                vanilla(r, GameSettings.Options.CHAT_VISIBILITY, null);
                vanilla(r, GameSettings.Options.CHAT_COLOR, null);
                vanilla(r, GameSettings.Options.CHAT_LINKS, null);
                vanilla(r, GameSettings.Options.CHAT_LINKS_PROMPT, null);
                vanilla(r, GameSettings.Options.CHAT_OPACITY, null);
                vanilla(r, GameSettings.Options.CHAT_SCALE, null);
                vanilla(r, GameSettings.Options.CHAT_WIDTH, null);
                vanilla(r, GameSettings.Options.CHAT_HEIGHT_FOCUSED, null);
                vanilla(r, GameSettings.Options.CHAT_HEIGHT_UNFOCUSED, null);
                for (Module m : ModuleManager.all()) if (m.name.equals("Chat")) r.module(m, () -> open(m));
                break;
            case "HUD":
                r.button("HUD layout", "Move, resize and lock every element", "Edit layout", Ui.PRIMARY,
                        () -> mc.displayGuiScreen(new net.m1227.hud.HudEditorScreen()));
                r.section("Elements");
                modules(r, Category.HUD);
                break;
            case "Visuals":
                r.section("Minecraft graphics");
                vanilla(r, GameSettings.Options.GRAPHICS, null);
                vanilla(r, GameSettings.Options.AMBIENT_OCCLUSION, null);
                vanilla(r, GameSettings.Options.GAMMA, null);
                vanilla(r, GameSettings.Options.RENDER_CLOUDS, null);
                vanilla(r, GameSettings.Options.PARTICLES, null);
                vanilla(r, GameSettings.Options.VIEW_BOBBING, null);
                vanilla(r, GameSettings.Options.ENTITY_SHADOWS, null);
                vanilla(r, GameSettings.Options.BLOCK_ALTERNATIVES, null);
                vanilla(r, GameSettings.Options.ANAGLYPH, null);
                r.section("Auras");
                modules(r, Category.AURAS);
                r.section("Trails and jumps");
                modules(r, Category.MOVEMENT);
                r.section("Weapons");
                r.button("Weapon editor", "Model, finish and colours of your sword with a 3D preview", "Open", Ui.PRIMARY,
                        () -> mc.displayGuiScreen(new WeaponEditorScreen(this)));
                modules(r, Category.WEAPONS);
                break;
            case "Cosmetics":
                r.button("Cosmetics preview", "See them on your character in 3D", "Open", Ui.PRIMARY, () -> mc.displayGuiScreen(new CosmeticsScreen(this)));
                modules(r, Category.COSMETICS);
                break;
            case "PvP":
                r.section("Combat");
                modules(r, Category.COMBAT);
                r.section("Gameplay");
                r.button("Waypoints", "Your waypoints in this world or server", "Open", Ui.SECONDARY,
                        () -> mc.displayGuiScreen(new net.m1227.client.menu.WaypointsScreen(this)));
                modules(r, Category.GAMEPLAY);
                r.section("Hit sounds");
                modules(r, Category.HITSOUNDS);
                break;
            case "Animations":
                r.section("Animations");
                modules(r, Category.ANIMATIONS);
                r.section("Camera");
                modules(r, Category.CAMERA);
                break;
            case "Audio":
                r.section("Minecraft volume");
                for (final SoundCategory c : SoundCategory.values()) {
                    float v = gs.getSoundLevel(c);
                    r.slider(I18n.format("soundCategory." + c.getCategoryName()), null, Math.round(v * 100) + "%", v, nv -> gs.setSoundLevel(c, nv));
                }
                r.section("Music and integrations");
                r.button("Music player", "Local music, Spotify and YouTube playlists", "Open", Ui.PRIMARY, () -> mc.displayGuiScreen(new net.m1227.ui.MusicScreen()));
                r.button("Lyrics", "Floating Lyrics: what is found, results to choose, sync and the editor", "Open", Ui.PRIMARY,
                        () -> mc.displayGuiScreen(new net.m1227.client.menu.LyricsScreen(this)));
                r.info("Windows media link", "Reads Spotify / YouTube title and timeline (read only, no network)", net.m1227.music.MediaBridge.status());
                modules(r, Category.MUSIC);
                break;
            case "Performance":
                performance(r);
                break;
            case "Controls":
                r.section("Mouse");
                vanilla(r, GameSettings.Options.SENSITIVITY, null);
                vanilla(r, GameSettings.Options.INVERT_MOUSE, null);
                vanilla(r, GameSettings.Options.TOUCHSCREEN, null);
                r.section("Keys");
                r.button("Key bindings", "Every Minecraft and mod key", "Open", Ui.PRIMARY, () -> mc.displayGuiScreen(new GuiControls(this, gs)));
                r.setting(ClientModule.INSTANCE.menuKey);
                r.button("Module keys", "Keys that toggle 1227 modules are set in the module menu", "Open modules", Ui.SECONDARY,
                        () -> ClickGui.open(null, this));
                break;
            case "World":
                r.button("Sky gallery", "All 58 skies as live previews, with collections and favorites", "Open", Ui.PRIMARY,
                        () -> mc.displayGuiScreen(new SkyGalleryScreen(this)));
                r.section("Skies");
                modules(r, Category.SKY);
                r.section("Ambience and world");
                modules(r, Category.AMBIENCE);
                break;
            case "Profiles":
                profiles(r);
                break;
            default:
                advanced(r);
        }
    }

    private static String pretty(String enumName) {
        String s = enumName.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void performance(Rows r) {
        PerformanceModule p = PerformanceModule.INSTANCE;
        r.section("Presets  ·  active: " + p.activePreset.get());
        r.note("Each preset sets Minecraft's video options and 1227's limits. Change anything, then \"Save\" to keep it in that preset.");
        for (final String n : PerfPresets.NAMES) {
            String d = n.equals("Low End") ? "Lowest settings, fewest effects" : n.equals("High FPS") ? "Fast graphics, short distances"
                    : n.equals("Visual Quality") ? "Fancy graphics, every effect" : "Good looks at a steady frame rate";
            Object key = "save" + n;
            r.buttons(n, d, new String[]{confirming(key) ? "Click again" : "Save", "Apply"}, new int[]{Ui.GHOST, Ui.PRIMARY},
                    new Runnable[]{twice(key, () -> PerfPresets.saveCurrentAs(n)), () -> PerfPresets.apply(n)});
        }
        Object rk = "resetPresets";
        r.button("Reset presets", "Back to the built-in values", confirming(rk) ? "Click again" : "Reset", Ui.DANGER, twice(rk, PerfPresets::resetAll));
        r.section("1227 limits");
        moduleSettings(r, p, false);
        r.section("Minecraft performance");
        vanilla(r, GameSettings.Options.RENDER_DISTANCE, null);
        vanilla(r, GameSettings.Options.FRAMERATE_LIMIT, null);
        vanilla(r, GameSettings.Options.ENABLE_VSYNC, null);
        vanilla(r, GameSettings.Options.USE_VBO, null);
        vanilla(r, GameSettings.Options.MIPMAP_LEVELS, "Applied when you release the slider");
        vanilla(r, GameSettings.Options.USE_FULLSCREEN, null);
        r.section("Memory");
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        r.info("Memory in use", "Java heap (RAM given to Minecraft in the launcher)", used + " / " + max + " MB");
        r.section("Benchmark");
        r.note("Measures real frame times in the world for 30 or 60 s. Run it in the same spot before and after a change.");
        r.buttons("Run benchmark", mc.theWorld == null ? "Join a world first" : null, new String[]{"30 s", "60 s"},
                new int[]{Ui.SECONDARY, Ui.SECONDARY},
                new Runnable[]{mc.theWorld == null ? null : (Runnable) () -> Benchmark.start(30), mc.theWorld == null ? null : (Runnable) () -> Benchmark.start(60)});
        PerformanceModule pm = PerformanceModule.INSTANCE;
        r.setting(pm.compareWith);
        r.buttons("Compare", "Current settings (A) vs that preset (B), 20 s each; your settings come back after",
                new String[]{"Run A / B"}, new int[]{Ui.SECONDARY},
                new Runnable[]{mc.theWorld == null ? null : (Runnable) Benchmark::compareStart});
        List<Benchmark.Result> res = Benchmark.results();
        for (Benchmark.Result b : res) {
            String detail = b.renderDistance + " chunks" + (b.graphics != null ? " " + b.graphics : "")
                    + (b.avgMs > 0 ? "  ·  " + b.avgMs + " ms avg, " + b.worstMs + " ms worst" : "")
                    + (b.memoryMb > 0 ? "  ·  " + b.memoryMb + " MB, " + b.entities + " entities, " + b.particles + " particles" : "");
            r.info(b.date + "  ·  " + b.label, detail, b.avgFps + " avg  ·  " + b.low1Fps + " 1%" + (b.low01Fps > 0 ? "  ·  " + b.low01Fps + " 0.1%" : ""));
        }
        if (!res.isEmpty()) {
            Object ck = "clearBench";
            r.button("Clear results", null, confirming(ck) ? "Click again" : "Clear", Ui.DANGER, twice(ck, Benchmark::clear));
        }
    }

    private void profiles(Rows r) {
        r.section("New profile");
        r.text("Name", "Letters, numbers, spaces, - and _", profileName, "My PvP setup", 32, v -> profileName = v);
        r.button("Save current setup", "Saves every 1227 module and setting as this profile", "Save", Ui.PRIMARY, () -> {
            if (Profiles.save(profileName)) profileName = "";
        });
        List<File> list = Profiles.list();
        r.section("Your profiles (" + list.size() + ")");
        if (list.isEmpty()) r.note("No profiles yet.");
        for (final File f : list) {
            final String n = Profiles.name(f);
            Object ok = "over" + n, dk = "del" + n;
            r.buttons(n, new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(f.lastModified())),
                    new String[]{confirming(dk) ? "Click again" : "Delete", confirming(ok) ? "Click again" : "Overwrite", "Load"},
                    new int[]{Ui.DANGER, Ui.GHOST, Ui.PRIMARY},
                    new Runnable[]{twice(dk, () -> Profiles.delete(f)), twice(ok, () -> Profiles.save(n)), () -> Profiles.load(f)});
        }
        r.section("Share");
        r.buttons("Clipboard", "Copy your setup as text, or load one a friend sent", new String[]{"Copy", "Load"},
                new int[]{Ui.SECONDARY, Ui.SECONDARY},
                new Runnable[]{ConfigManager::exportToClipboard, () -> {
                    ConfigManager.importFromClipboard();
                    net.m1227.core.Keybinds.INSTANCE.sync();
                }});
        r.button("Open profiles folder", null, "Open", Ui.SECONDARY, () -> Os.open(Profiles.dir()));
        r.section("Automatic backups");
        r.note("1227 keeps the config from the start of each of your last 5 sessions.");
        for (final File b : Profiles.backups()) {
            Object k = "restore" + b.getName();
            r.button(b.getName().replace("1227-", "").replace(".json", "").replace('_', ' '), null, confirming(k) ? "Click again" : "Restore",
                    Ui.SECONDARY, twice(k, () -> Profiles.load(b)));
        }
    }

    private void advanced(Rows r) {
        r.section("Files");
        r.button("Config folder", "config/1227.json, profiles, backups and presets", "Open", Ui.SECONDARY, () -> Os.open(ConfigManager.dir()));
        r.button("Game folder", "Screenshots, resource packs, saves and logs", "Open", Ui.SECONDARY, () -> Os.open(mc.mcDataDir));
        r.section("Loading screen");
        r.buttons("Themed Forge loading screen", SplashTheme.applied() ? "On: 1227 colours from the next start" : "Off: Forge's default colours",
                new String[]{"Restore", "Apply"}, new int[]{Ui.GHOST, Ui.PRIMARY},
                new Runnable[]{SplashTheme.applied() ? (Runnable) SplashTheme::restore : null, SplashTheme::apply});
        r.section("Menus");
        r.button("Module menu", "The full 1227 module menu (Right Ctrl in game)", "Open", Ui.SECONDARY, () -> ClickGui.open(null, this));
        r.button("Classic Minecraft options", null, "Open", Ui.GHOST, () -> MenuSystem.openVanilla(new GuiOptions(this, mc.gameSettings)));
        for (Module m : ModuleManager.all()) if (m.name.equals("Presets")) r.module(m, () -> open(m));
        r.section("Reset");
        Object k = "resetAll";
        r.button("Reset every module", "All 1227 modules and settings back to their defaults (a backup stays in Profiles)",
                confirming(k) ? "Click again" : "Reset all", Ui.DANGER, twice(k, () -> {
                    for (Module m : ModuleManager.all()) m.reset();
                    Notifier.post(Notifier.Kind.SYSTEM, "Settings", "Everything reset", Notifier.WARN, 1800);
                }));
        r.section("About");
        r.info("1227", "Client mod for Minecraft 1.8.9 and Forge 11.15.1.2318", "v" + net.m1227.Mod1227.VERSION);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (Rows.capturing != null) {
            int code = key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE ? 0 : key;
            Rows.capturing.set(code);
            Rows.capturing = null;
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws java.io.IOException {
        if (Rows.capturing != null && button >= 2) {
            Rows.capturing.set(-100 + button);
            Rows.capturing = null;
            return;
        }
        super.mouseClicked(mx, my, button);
    }
}
