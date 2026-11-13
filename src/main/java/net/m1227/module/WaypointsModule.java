package net.m1227.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.m1227.ui.TtfFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Waypoints: named, coloured points you place in the world, shown with a label, icon and
 * distance, and an optional light beam. Saved per server (address) or per singleplayer world, and
 * per dimension, in config/1227/waypoints.json. A key adds one where you stand; the list screen
 * edits, deletes, imports and exports them (clipboard JSON).
 *
 * Waypoints mark places, never players: there is no radar and nothing about other players.
 */
public final class WaypointsModule extends Module {
    public static WaypointsModule INSTANCE;
    public static final String[] ICONS = {"Pin", "Star", "Heart", "Diamond", "Home", "Flag"};

    public static final class Waypoint {
        public String name = "Waypoint", icon = "Pin", world = "", dim = "0";
        public int color = 0x8B5CFF;
        public double x, y, z;
        public boolean visible = true;
    }

    private final Setting.Key addKey, listKey;
    private final Setting.Bool beam, throughWalls, showDistance;
    private final Setting.Num size, maxDistance;
    private final List<Waypoint> all = new ArrayList<Waypoint>();
    private boolean loaded;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public WaypointsModule() {
        super("Waypoints", "Named points in the world with beams, distance and a list screen.", Category.GAMEPLAY, false);
        INSTANCE = this;
        addKey = add(new Setting.Key("Add waypoint key", Keyboard.KEY_B));
        listKey = add(new Setting.Key("Waypoint list key", 0));
        add(new Setting.Action("Open waypoint list", () -> Minecraft.getMinecraft().displayGuiScreen(
                new net.m1227.client.menu.WaypointsScreen(Minecraft.getMinecraft().currentScreen))));
        beam = add(new Setting.Bool("Light beam", true));
        throughWalls = add(new Setting.Bool("Visible through blocks", true));
        showDistance = add(new Setting.Bool("Show distance", true));
        size = add(new Setting.Num("Label size", 1.0, 0.5, 2.0, 0.05, "x"));
        maxDistance = add(new Setting.Num("Hide farther than", 2000, 50, 10000, 50, " m"));
    }

    // ------------------------------------------------------------- context and storage

    /** "server:address" or "world:folder" for the place you are playing in. */
    public static String worldKey() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) return "world:" + mc.getIntegratedServer().getFolderName();
        if (mc.getCurrentServerData() != null) return "server:" + mc.getCurrentServerData().serverIP.toLowerCase();
        return "unknown";
    }

    public static String dimKey() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? "0" : String.valueOf(mc.thePlayer.dimension);
    }

    private static File file() {
        File d = new File(ConfigManager.dir(), "1227");
        d.mkdirs();
        return new File(d, "waypoints.json");
    }

    public List<Waypoint> all() {
        load();
        return all;
    }

    /** Waypoints of this world/server and dimension. */
    public List<Waypoint> here() {
        load();
        String w = worldKey(), d = dimKey();
        List<Waypoint> out = new ArrayList<Waypoint>();
        for (Waypoint p : all) {
            if (p.world.equals(w) && p.dim.equals(d)) out.add(p);
        }
        return out;
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        all.clear();
        File f = file();
        if (!f.isFile()) return;
        try {
            JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (JsonElement e : root.getAsJsonArray()) {
                Waypoint p = GSON.fromJson(e, Waypoint.class);
                if (p != null) all.add(p);
            }
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not read waypoints.json", ex);
        }
    }

    public void save() {
        try {
            JsonArray a = new JsonArray();
            for (Waypoint p : all) a.add(GSON.toJsonTree(p));
            ConfigManager.writeAtomic(file(), GSON.toJson(a).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not save waypoints.json", ex);
        }
    }

    public Waypoint addHere(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return null;
        load();
        Waypoint p = new Waypoint();
        p.name = name;
        p.x = Math.floor(mc.thePlayer.posX) + 0.5;
        p.y = Math.floor(mc.thePlayer.posY);
        p.z = Math.floor(mc.thePlayer.posZ) + 0.5;
        p.world = worldKey();
        p.dim = dimKey();
        int[] palette = {0x8B5CFF, 0x3DDC84, 0xFFC857, 0x2DD4FF, 0xFF5A6A, 0xFF6FD8};
        p.color = palette[here().size() % palette.length];
        all.add(p);
        save();
        return p;
    }

    public void remove(Waypoint p) {
        all.remove(p);
        save();
    }

    /** Waypoints of this world as JSON text (for the clipboard). */
    public String exportHere() {
        JsonArray a = new JsonArray();
        for (Waypoint p : here()) a.add(GSON.toJsonTree(p));
        return GSON.toJson(a);
    }

    /** Adds waypoints from JSON text to this world; returns how many. */
    public int importText(String text) {
        int n = 0;
        try {
            JsonElement root = new JsonParser().parse(text);
            JsonArray a = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
            if (root.isJsonObject()) a.add(root);
            for (JsonElement e : a) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (!o.has("x") || !o.has("z")) continue;
                Waypoint p = GSON.fromJson(o, Waypoint.class);
                p.world = worldKey();
                if (!o.has("dim")) p.dim = dimKey();
                if (p.name == null || p.name.isEmpty()) p.name = "Imported";
                if (p.icon == null) p.icon = "Pin";
                all.add(p);
                n++;
            }
        } catch (Exception ignored) {
            return 0;
        }
        if (n > 0) save();
        return n;
    }

    // ------------------------------------------------------------- keys

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Keyboard.getEventKeyState() || mc.currentScreen != null || mc.thePlayer == null) return;
        int k = Keyboard.getEventKey();
        if (k == 0) return;
        if (k == addKey.code()) {
            Waypoint p = addHere("Waypoint " + (here().size() + 1));
            if (p != null) Notifier.post(Notifier.Kind.SYSTEM, "Waypoint added", (int) p.x + ", " + (int) p.y + ", " + (int) p.z, Notifier.GOOD, 2000);
        } else if (k == listKey.code()) {
            mc.displayGuiScreen(new net.m1227.client.menu.WaypointsScreen(null));
        }
    }

    // ------------------------------------------------------------- drawing

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity v = mc.getRenderViewEntity();
        if (mc.theWorld == null || v == null) return;
        List<Waypoint> list = here();
        if (list.isEmpty()) return;
        RenderManager rm = mc.getRenderManager();
        float pt = e.partialTicks;
        double ex = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt;
        double ey = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt + v.getEyeHeight();
        double ez = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        GlStateManager.disableFog();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        TtfFont f = TtfFont.get(TtfFont.MEDIUM, 48);
        try {
            for (Waypoint p : list) {
                if (!p.visible) continue;
                double dx = p.x - ex, dy = p.y - ey, dz = p.z - ez;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > maxDistance.get()) continue;
                if (throughWalls.on()) GlStateManager.disableDepth();
                else GlStateManager.enableDepth();
                if (beam.on()) beam(p, rm, dist);
                label(p, rm, ex, ey, ez, dist, f);
            }
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (fog) GlStateManager.enableFog();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    private void beam(Waypoint p, RenderManager rm, double dist) {
        float r = ((p.color >> 16) & 255) / 255f, g = ((p.color >> 8) & 255) / 255f, b = (p.color & 255) / 255f;
        float a = (float) Math.min(0.5, 0.15 + dist / 200.0);
        double x = p.x - rm.viewerPosX, y0 = p.y - rm.viewerPosY, z = p.z - rm.viewerPosZ, y1 = y0 + 256;
        double w = 0.12 + Math.min(0.6, dist / 300.0);
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 2; i++) {
            double ax = i == 0 ? w : 0, az = i == 0 ? 0 : w;
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex3d(x - ax, y0, z - az);
            GL11.glVertex3d(x + ax, y0, z + az);
            GL11.glColor4f(r, g, b, 0f);
            GL11.glVertex3d(x + ax, y1, z + az);
            GL11.glVertex3d(x - ax, y1, z - az);
        }
        GL11.glEnd();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
    }

    private void label(Waypoint p, RenderManager rm, double ex, double ey, double ez, double dist, TtfFont f) {
        // labels further than 12 m are pulled in and scaled, so they keep the same size on screen
        double show = Math.min(dist, 12.0);
        double k = dist < 1e-3 ? 0 : show / dist;
        double lx = ex + (p.x - ex) * k, ly = ey + (p.y + 1.6 - ey) * k, lz = ez + (p.z - ez) * k;
        float s = (float) (0.022 * size.f() * Math.max(0.6, show / 6.0));
        GlStateManager.pushMatrix();
        GlStateManager.translate(lx - rm.viewerPosX, ly - rm.viewerPosY, lz - rm.viewerPosZ);
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-s, -s, s);
        String name = p.name;
        String d = showDistance.on() ? (dist >= 1000 ? String.format("%.1f km", dist / 1000) : (int) dist + " m") : "";
        float nw = f == null ? Gfx.textWidth(name) : f.widthPx(name) / 4f;
        float dw = d.isEmpty() ? 0 : f == null ? Gfx.textWidth(d) : f.widthPx(d) / 5f;
        float w = Math.max(nw, dw) + 18, h = d.isEmpty() ? 14 : 22;
        int c = 0xFF000000 | p.color;
        GlStateManager.disableTexture2D();
        Gfx.roundedRect(-w / 2f, -h / 2f, w, h, 4, 0xB0101218);
        Gfx.roundedRect(-w / 2f, -h / 2f, 2.2f, h, 1.1f, c);
        icon(p.icon, -w / 2f + 8, -h / 2f + 7, c);
        GlStateManager.enableTexture2D();
        if (f != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-w / 2f + 14, -h / 2f + 2.5f, 0);
            GlStateManager.scale(0.25f, 0.25f, 1f);
            f.draw(name, 0, 0, 1f, 0xFFFFFFFF);
            GlStateManager.popMatrix();
            if (!d.isEmpty()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(-w / 2f + 14, -h / 2f + 12f, 0);
                GlStateManager.scale(0.2f, 0.2f, 1f);
                f.draw(d, 0, 0, 1f, 0xFFA9ADBA);
                GlStateManager.popMatrix();
            }
        } else {
            Gfx.text(name, -w / 2f + 14, -h / 2f + 3, 0xFFFFFFFF);
            if (!d.isEmpty()) Gfx.text(d, -w / 2f + 14, -h / 2f + 12, 0xFFA9ADBA);
        }
        GlStateManager.popMatrix();
    }

    /** Small shape for each icon name. */
    public static void icon(String icon, float cx, float cy, int c) {
        if (icon.equals("Star")) Gfx.star(cx, cy, 3.6f, true, c);
        else if (icon.equals("Heart")) {
            Gfx.circle(cx - 1.4f, cy - 0.8f, 1.7f, c);
            Gfx.circle(cx + 1.4f, cy - 0.8f, 1.7f, c);
            Gfx.quad(cx - 3.1f, cy - 0.4f, cx + 3.1f, cy - 0.4f, cx, cy + 3f, cx, cy + 3f, c);
        } else if (icon.equals("Diamond")) Gfx.quad(cx, cy - 3.5f, cx + 3f, cy, cx, cy + 3.5f, cx - 3f, cy, c);
        else if (icon.equals("Home")) {
            Gfx.quad(cx - 3.5f, cy, cx, cy - 3.5f, cx + 3.5f, cy, cx + 3.5f, cy, c);
            Gfx.rect(cx - 2.4f, cy, 4.8f, 3f, c);
        } else if (icon.equals("Flag")) {
            Gfx.rect(cx - 2.5f, cy - 3.5f, 0.9f, 7f, c);
            Gfx.quad(cx - 1.6f, cy - 3.5f, cx + 3f, cy - 2f, cx + 3f, cy - 2f, cx - 1.6f, cy - 0.5f, c);
        } else {
            Gfx.circle(cx, cy, 2.6f, c);
            Gfx.circle(cx, cy, 1.1f, 0xFF101218);
        }
    }
}
