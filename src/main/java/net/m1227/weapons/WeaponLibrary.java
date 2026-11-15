package net.m1227.weapons;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.m1227.Mod1227;

/**
 * Weapon models from files: the models that come with 1227 (in the jar, unpacked to
 * config/1227/weapons-included so their settings can be saved next to them) and your own, every
 * .bbmodel, .gltf / .glb, .fbx and .obj in config/1227/weapons (sub-folders too). They are read
 * in the background, fitted to the hand and listed next to the code-built models. Nothing is
 * downloaded. Each model keeps its settings in "&lt;file&gt;.1227.json" beside it.
 */
public final class WeaponLibrary {
    private WeaponLibrary() {}

    public static final class Entry {
        public final File file;
        /** True for the models that come with 1227. */
        public boolean included;
        public volatile WModel model;
        public volatile String error;
        public volatile WeaponProfile profile = new WeaponProfile();
        public volatile WeaponFit fit;
        public volatile float[] matrix;
        /** Bone matrices of the rest pose (models drawn without animation). */
        public volatile float[][] restBones;
        /** First-person animator (your own weapon). */
        public final WeaponAnimator anim = new WeaponAnimator();
        /** Display name, unique in the model list. */
        public String name;
        final WeaponGpu gpu = new WeaponGpu();
        private boolean fitFlip;
        private float fitGrip = Float.NaN;
        private long saveAt;

        Entry(File f) {
            file = f;
        }

        public boolean ready() {
            return model != null && matrix != null && restBones != null && fit != null;
        }

        /** Recomputes the fit (when tip or hold point changed), the matrix and the clip slots. */
        public synchronized void apply() {
            WModel m = model;
            if (m == null) return;
            WeaponProfile p = profile;
            WeaponFit f = fit;
            if (f == null || fitFlip != p.flip || Float.compare(fitGrip, p.grip) != 0) {
                f = WeaponFit.compute(m, p.flip, p.grip);
                fitFlip = p.flip;
                fitGrip = p.grip;
            }
            float[] mat = f.matrix(p);
            float[][] rb = m.boneMatrices(null);
            anim.bind(m, p);
            fit = f;
            restBones = rb;
            matrix = mat; // last: ready() turns true only when everything is in place
        }

        /** Call after changing the profile: applies now, writes the file a moment later. */
        public void changed() {
            apply();
            saveAt = System.currentTimeMillis() + 600L;
            pendingSave = true;
        }

        public void saveNow() {
            saveAt = 0L;
            try {
                profile.save(file);
            } catch (Exception e) {
                Mod1227.LOGGER.warn("1227: could not save " + WeaponProfile.fileFor(file).getName() + ": " + e);
            }
        }

        public boolean gpuReady() {
            return gpu.ready();
        }

        /** Draws the model (W: model space to the current space). Uploads it on first use. */
        public void draw(float[][] bones, float[] W, long ticks, float alpha) {
            WModel m = model;
            if (m == null) return;
            if (!gpu.ready()) {
                if (gpu.failed()) return;
                try {
                    gpu.upload(m);
                } catch (RuntimeException e) {
                    error = "could not upload: " + e.getMessage();
                    Mod1227.LOGGER.warn("1227: weapon model upload failed for " + file.getName(), e);
                    return;
                }
            }
            gpu.draw(m, bones, W, profile.glow, ticks, alpha);
        }
    }

    private static volatile List<Entry> entries = Collections.emptyList();
    private static volatile boolean scanning, scanned;
    private static volatile boolean pendingSave;
    private static Runnable onLoaded;

    public static File dir() {
        return new File(new File(Mod1227.configDir, "1227"), "weapons");
    }

    /** Where the models that come with 1227 are unpacked (managed by the mod). */
    public static File includedDir() {
        return new File(new File(Mod1227.configDir, "1227"), "weapons-included");
    }

    /**
     * Unpacks the jar's models (listed in assets/m1227/weapons/index.txt) when they are missing
     * or the mod version changed. Model and texture files are replaced; a model's .1227.json is
     * written only when there is none, so your adjustments are kept across updates.
     */
    static void unpackIncluded() {
        File d = includedDir();
        File stamp = new File(d, ".version");
        try {
            boolean fresh = !stamp.isFile() || !new String(java.nio.file.Files.readAllBytes(stamp.toPath()), "UTF-8").trim().equals(Mod1227.VERSION);
            java.io.InputStream idx = WeaponLibrary.class.getResourceAsStream("/assets/m1227/weapons/index.txt");
            if (idx == null) return;
            java.util.List<String> names = new ArrayList<>();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(idx, "UTF-8"))) {
                for (String l; (l = r.readLine()) != null; ) if (!l.trim().isEmpty() && !l.contains("..")) names.add(l.trim());
            }
            for (String n : names) {
                File out = new File(d, n);
                boolean profile = n.endsWith(".1227.json");
                if (out.isFile() && (profile || !fresh)) continue;
                try (java.io.InputStream in = WeaponLibrary.class.getResourceAsStream("/assets/m1227/weapons/" + n)) {
                    if (in == null) continue;
                    out.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            d.mkdirs();
            java.nio.file.Files.write(stamp.toPath(), Mod1227.VERSION.getBytes("UTF-8"));
        } catch (Exception e) {
            Mod1227.LOGGER.warn("1227: could not unpack the included weapon models: " + e);
        }
    }

    public static List<Entry> entries() {
        if (!scanned && !scanning) scan(null);
        return entries;
    }

    public static boolean scanning() {
        return scanning;
    }

    public static Entry byName(String name) {
        for (Entry e : entries) if (e.name.equals(name)) return e;
        return null;
    }

    /** Writes profiles whose save delay has passed (call from a tick). */
    public static void tick() {
        if (!pendingSave) return;
        long now = System.currentTimeMillis();
        boolean left = false;
        for (Entry e : entries) {
            if (e.saveAt > 0L && now >= e.saveAt) e.saveNow();
            else if (e.saveAt > 0L) left = true;
        }
        pendingSave = left;
    }

    /** (Re)reads the folder in the background; 'done' runs on the main thread afterwards. */
    public static synchronized void scan(final Runnable done) {
        if (done != null) onLoaded = done;
        if (scanning) return;
        scanning = true;
        final List<Entry> old = entries;
        Thread t = new Thread(() -> {
            List<Entry> list = new ArrayList<>();
            try {
                unpackIncluded();
                File d = dir();
                if (!d.isDirectory() && d.mkdirs()) readme(d);
                List<File> inc = new ArrayList<>(), files = new ArrayList<>();
                collect(includedDir(), inc, 0);
                collect(d, files, 0);
                Collections.sort(inc, (a, b) -> a.getPath().compareToIgnoreCase(b.getPath()));
                Collections.sort(files, (a, b) -> a.getPath().compareToIgnoreCase(b.getPath()));
                java.util.Set<File> included = new java.util.HashSet<>(inc);
                java.util.Set<String> incNames = new java.util.HashSet<>();
                for (File f : inc) incNames.add(f.getName().toLowerCase(java.util.Locale.ROOT));
                // a copy of an included model in your folder (e.g. from the example pack) is not listed twice
                for (File f : files) if (!incNames.contains(f.getName().toLowerCase(java.util.Locale.ROOT))) inc.add(f);
                files = inc;
                java.util.Set<String> names = new java.util.HashSet<>();
                for (File f : files) {
                    Entry e = new Entry(f);
                    e.included = included.contains(f);
                    e.profile = WeaponProfile.load(f);
                    String n = e.profile.name.trim().isEmpty() ? ModelFiles.displayName(f) : e.profile.name.trim();
                    if (net.m1227.module.WeaponModelsModule.isBuiltIn(n)) n = n + " (file)";
                    String u = n;
                    for (int k = 2; names.contains(u.toLowerCase(java.util.Locale.ROOT)); k++) u = n + " " + k;
                    names.add(u.toLowerCase(java.util.Locale.ROOT));
                    e.name = u;
                    list.add(e);
                }
                entries = list;
                for (Entry e : list) {
                    try {
                        long t0 = System.nanoTime();
                        WModel m = ModelFiles.load(e.file);
                        e.model = m;
                        e.apply();
                        Mod1227.LOGGER.info(String.format("1227: weapon model %s (%s, %d triangles, %d clips) in %.0f ms%s", e.name, m.format,
                                m.triangles(), m.clips.size(), (System.nanoTime() - t0) / 1e6, m.warnings.isEmpty() ? "" : " - " + m.warnings));
                    } catch (Throwable ex) {
                        e.model = null;
                        e.error = String.valueOf(ex.getMessage() != null ? ex.getMessage() : ex);
                        Mod1227.LOGGER.warn("1227: could not read weapon model " + e.file.getName() + ": " + ex);
                    }
                }
            } catch (Throwable ex) {
                Mod1227.LOGGER.warn("1227: weapon folder scan failed", ex);
            } finally {
                scanned = true;
                scanning = false;
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                    for (Entry e : old) e.gpu.free();
                    Runnable r = onLoaded;
                    onLoaded = null;
                    if (r != null) r.run();
                    if (net.m1227.module.WeaponModelsModule.INSTANCE != null) net.m1227.module.WeaponModelsModule.INSTANCE.refreshModelList();
                });
            }
        }, "1227 weapon models");
        t.setDaemon(true);
        t.start();
    }

    private static void collect(File d, List<File> out, int depth) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory() && depth < 3) collect(f, out, depth + 1);
            else if (f.isFile() && ModelFiles.isModel(f)) out.add(f);
        }
    }

    private static void readme(File d) {
        try {
            String s = "Put weapon models here: .bbmodel (Blockbench), .gltf / .glb, .fbx (binary) or .obj.\r\n"
                    + "Sub-folders are fine (keep a model's textures next to it). Open Weapon Models > Weapon editor\r\n"
                    + "in game to choose one, adjust how it sits in your hand and pick its animations.\r\n"
                    + "Each model keeps its settings in <file>.1227.json next to it.\r\n"
                    + "Only use models you are allowed to use (check each model's license).\r\n";
            java.nio.file.Files.write(new File(d, "README.txt").toPath(), s.getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }
}
