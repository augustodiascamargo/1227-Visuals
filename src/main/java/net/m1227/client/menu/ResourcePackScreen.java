package net.m1227.client.menu;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.m1227.client.Os;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.ConfigManager;
import net.m1227.core.Notifier;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.ResourcePackRepository;

/**
 * Resource pack manager: every pack in the resourcepacks folder with its icon and description,
 * on/off and order (the top pack wins), a check of each file (readable zip or folder, a
 * pack.mcmeta, made for 1.8.9's pack format 1), import of a .zip, a backup of your pack list
 * before applying, and the folder. Nothing is downloaded.
 *
 * Applying reloads Minecraft's resources, like the vanilla screen does.
 */
public final class ResourcePackScreen extends UiScreen {
    private final List<ResourcePackRepository.Entry> available = new ArrayList<ResourcePackRepository.Entry>();
    private final List<ResourcePackRepository.Entry> selected = new ArrayList<ResourcePackRepository.Entry>();
    private final Map<String, String> checks = new HashMap<String, String>();
    private boolean changed;
    private String filter = "";

    public ResourcePackScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        super.initGui();
        reload();
    }

    private void reload() {
        ResourcePackRepository repo = mc.getResourcePackRepository();
        repo.updateRepositoryEntriesAll();
        available.clear();
        selected.clear();
        selected.addAll(repo.getRepositoryEntries());
        java.util.Collections.reverse(selected); // the screen shows the winning pack first
        for (ResourcePackRepository.Entry e : repo.getRepositoryEntriesAll()) {
            if (!selected.contains(e)) available.add(e);
        }
        checks.clear();
        formats.clear();
        changed = false;
    }

    private final Map<String, Integer> formats = new HashMap<String, Integer>();

    /** pack_format from the pack's own pack.mcmeta (1 = made for 1.8.x), -1 if unreadable. */
    private int format(ResourcePackRepository.Entry e) {
        String n = e.getResourcePackName();
        if (formats.containsKey(n)) return formats.get(n);
        int fmt = -1;
        File f = new File(mc.getResourcePackRepository().getDirResourcepacks(), n);
        try {
            byte[] data = null;
            if (f.isDirectory()) {
                File m = new File(f, "pack.mcmeta");
                if (m.isFile() && m.length() < 65536) data = Files.readAllBytes(m.toPath());
            } else {
                try (ZipFile z = new ZipFile(f)) {
                    ZipEntry m = z.getEntry("pack.mcmeta");
                    if (m != null && m.getSize() < 65536) {
                        try (java.io.InputStream in = z.getInputStream(m)) {
                            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                            byte[] b = new byte[4096];
                            int k;
                            while ((k = in.read(b)) > 0) bo.write(b, 0, k);
                            data = bo.toByteArray();
                        }
                    }
                }
            }
            if (data != null) {
                com.google.gson.JsonObject o = new com.google.gson.JsonParser().parse(new String(data, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                fmt = o.getAsJsonObject("pack").get("pack_format").getAsInt();
            }
        } catch (Exception ignored) {
            fmt = -1;
        }
        formats.put(n, fmt);
        return fmt;
    }

    /** Short verdict about a pack file: "ok", or what is wrong with it. */
    private String check(ResourcePackRepository.Entry e) {
        String n = e.getResourcePackName();
        if (checks.containsKey(n)) return checks.get(n);
        String v;
        File f = new File(mc.getResourcePackRepository().getDirResourcepacks(), n);
        try {
            if (f.isDirectory()) {
                v = new File(f, "pack.mcmeta").isFile() ? "ok" : "missing pack.mcmeta";
            } else {
                try (ZipFile z = new ZipFile(f)) {
                    ZipEntry m = z.getEntry("pack.mcmeta");
                    v = m != null ? "ok" : "missing pack.mcmeta (zipped one folder too deep?)";
                }
            }
            if (v.equals("ok")) {
                int fmt = format(e);
                if (fmt != 1) v = fmt < 0 ? "pack.mcmeta unreadable" : "made for another version (format " + fmt + ")";
            }
        } catch (Exception ex) {
            v = "damaged file (" + ex.getClass().getSimpleName() + ")";
        }
        checks.put(n, v);
        return v;
    }

    private void backup() {
        try {
            File d = new File(ConfigManager.dir(), "1227/resourcepack-backups");
            d.mkdirs();
            StringBuilder b = new StringBuilder();
            for (String s : mc.gameSettings.resourcePacks) b.append(s).append('\n');
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            Files.write(new File(d, "packs-" + stamp + ".txt").toPath(), b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            File[] all = d.listFiles();
            if (all != null && all.length > 10) {
                java.util.Arrays.sort(all, (x, y) -> x.getName().compareTo(y.getName()));
                for (int i = 0; i < all.length - 10; i++) all[i].delete();
            }
        } catch (Exception ignored) {
            // the backup is a convenience
        }
    }

    private void apply() {
        backup();
        ResourcePackRepository repo = mc.getResourcePackRepository();
        // the repository keeps the reverse of the screen order (bottom of the list loads first)
        List<ResourcePackRepository.Entry> order = new ArrayList<ResourcePackRepository.Entry>(selected);
        java.util.Collections.reverse(order);
        repo.setRepositories(order);
        mc.gameSettings.resourcePacks.clear();
        mc.gameSettings.incompatibleResourcePacks.clear();
        for (ResourcePackRepository.Entry e : order) {
            mc.gameSettings.resourcePacks.add(e.getResourcePackName());
            if (format(e) != 1) mc.gameSettings.incompatibleResourcePacks.add(e.getResourcePackName());
        }
        mc.gameSettings.saveOptions();
        mc.refreshResources();
        changed = false;
        Notifier.post(Notifier.Kind.SYSTEM, "Resource packs", "Applied " + selected.size() + " pack(s)", Notifier.GOOD, 2000);
    }

    private void importZip() {
        Thread t = new Thread(() -> {
            try {
                java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Choose a resource pack (.zip)", java.awt.FileDialog.LOAD);
                fd.setFile("*.zip");
                fd.setVisible(true);
                if (fd.getFile() == null) return;
                File src = new File(fd.getDirectory(), fd.getFile());
                try (ZipFile z = new ZipFile(src)) {
                    if (z.getEntry("pack.mcmeta") == null) {
                        Notifier.post(Notifier.Kind.SYSTEM, "Resource packs", "Not a resource pack (no pack.mcmeta)", Notifier.BAD, 2500);
                        return;
                    }
                }
                File dst = new File(mc.getResourcePackRepository().getDirResourcepacks(), src.getName());
                Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                mc.addScheduledTask(this::reload);
                Notifier.post(Notifier.Kind.SYSTEM, "Resource packs", "Imported " + src.getName(), Notifier.GOOD, 2000);
            } catch (Throwable ex) {
                Notifier.post(Notifier.Kind.SYSTEM, "Resource packs", "Import failed: " + ex.getMessage(), Notifier.BAD, 2500);
            }
        }, "1227-import-pack");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        float pw = Math.min(680, w - 20), px = (w - pw) / 2f, top = 12;
        Ui.textBold("Resource packs", px, top, DS.T_TITLE, DS.TEXT);
        ui.textField("filter", px + pw - 160, top, 160, 18, filter, "Search packs", 40, v -> filter = v.toLowerCase());
        float y = top + 28, colW = (pw - 8) / 2f, lh = h - y - 44;
        column("Available", available, px, y, colW, lh, false);
        column("Active (top wins)", selected, px + colW + 8, y, colW, lh, true);
        float by = h - 34;
        ui.button("apply", changed ? "Apply" : "Applied", px + pw - 92, by, 70, 22, Ui.PRIMARY, changed, Glyph.CHECK, this::apply);
        ui.button("import", "Import .zip", px, by, 90, 22, Ui.SECONDARY, true, Glyph.PLUS, this::importZip);
        ui.button("folder", "Open folder", px + 94, by, 90, 22, Ui.SECONDARY, true, Glyph.FOLDER, () -> Os.open(mc.getResourcePackRepository().getDirResourcepacks()));
        ui.button("refresh", "Refresh", px + 188, by, 70, 22, Ui.SECONDARY, true, Glyph.REFRESH, this::reload);
        ui.iconButton("back", px + pw - 22, by, 22, Glyph.BACK, changed ? "Back (changes not applied)" : "Back", true, this::back);
    }

    private void column(String title, final List<ResourcePackRepository.Entry> list, float x, float y, float w, float h, final boolean active) {
        Ui.card(x, y, w, h);
        Ui.caption(title.toUpperCase(), x + 8, y + 6);
        float off = ui.beginScroll(title, x + 4, y + 18, w - 8, h - 22);
        float ry = y + 18;
        for (int i = 0; i < list.size(); i++) {
            final ResourcePackRepository.Entry e = list.get(i);
            final int idx = i;
            if (!filter.isEmpty() && !e.getResourcePackName().toLowerCase().contains(filter)) continue;
            float yy = ry - off;
            if (yy > y - 40 && yy < y + h) {
                boolean hv = ui.hover(x + 4, yy, w - 8, 36);
                Gfx.roundedRect(x + 4, yy, w - 8, 36, DS.R_SM, hv ? DS.SURFACE_3 : DS.SURFACE_2);
                try {
                    e.bindTexturePackIcon(mc.getTextureManager());
                    net.minecraft.client.renderer.GlStateManager.enableTexture2D();
                    net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
                    net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture((int) x + 7, (int) yy + 3, 0, 0, 30, 30, 30, 30);
                } catch (Throwable ignored) {
                    // no icon
                }
                String c = check(e);
                Ui.textMedium(Gfx.trim(e.getResourcePackName().replace(".zip", ""), w - 70), x + 42, yy + 4, DS.T_SMALL, DS.TEXT);
                String desc = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(e.getTexturePackDescription());
                Ui.text(Gfx.trim(desc == null ? "" : desc.replace('\n', ' '), w - 70), x + 42, yy + 15, DS.T_SMALL, DS.MUTED);
                Ui.text(c.equals("ok") ? "✓ ok" : c, x + 42, yy + 25, 0.72f, c.equals("ok") ? DS.GOOD : DS.WARN);
                if (active) {
                    ui.iconButton("up" + idx, x + w - 26, yy + 2, 14, Glyph.ARROW_RIGHT, "Move up (wins over the ones below)", idx > 0, () -> {
                        list.remove(idx);
                        list.add(idx - 1, e);
                        changed = true;
                    });
                    ui.iconButton("off" + idx, x + w - 26, yy + 19, 14, Glyph.CLOSE, "Turn off", true, () -> {
                        list.remove(e);
                        available.add(0, e);
                        changed = true;
                    });
                } else {
                    ui.iconButton("on" + idx, x + w - 26, yy + 10, 16, Glyph.PLUS, "Turn on", true, () -> {
                        list.remove(e);
                        selected.add(0, e);
                        changed = true;
                    });
                }
            }
            ry += 39;
        }
        if (list.isEmpty()) Ui.text(active ? "No pack active: default textures." : "No other packs in the folder.", x + 10, y + 24, DS.T_SMALL, DS.FAINT);
        ui.endScroll(ry - y - 18);
    }
}
