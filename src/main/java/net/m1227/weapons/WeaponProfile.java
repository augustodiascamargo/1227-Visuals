package net.m1227.weapons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;

/**
 * Per-model settings, saved next to the model as "&lt;file&gt;.1227.json": the name shown in the
 * menus, how it sits in the hand (size, offsets, angles, which end is the tip, hold point),
 * which of its own animations play for idle / swing / inspect / draw, the hold &amp; swing style
 * and whether its glowing parts are full-bright. Pure (Gson only).
 */
public final class WeaponProfile {
    public String name = "";
    public float size = 1f;
    /** Offsets in item units: along the blade, across it, and through it (towards the camera). */
    public float along, across, depth;
    /** Degrees: twist around the blade, tilt in the blade's plane, roll around the edge. */
    public float twist, tilt, roll;
    public boolean flip, turnEdge;
    /** Hold point, 0 = handle end .. 1 = tip; negative = found automatically. */
    public float grip = -1f;
    /** Clip names; "" = pick by name, "-" = none (the built-in procedural motion is used). */
    public String idle = "", swing = "", inspect = "", draw = "";
    /** Auto, Classic, Two-handed, Heavy, Thrust, Flourish, Twin. */
    public String style = "Auto";
    public boolean glow = true;
    public boolean thirdPerson = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static File fileFor(File model) {
        return new File(model.getParentFile(), model.getName() + ".1227.json");
    }

    public static WeaponProfile load(File model) {
        File f = fileFor(model);
        if (f.isFile()) {
            try {
                WeaponProfile p = GSON.fromJson(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"), WeaponProfile.class);
                if (p != null) return p.sane();
            } catch (Exception ignored) {
                // a broken file falls back to the defaults (and is overwritten on the next save)
            }
        }
        return new WeaponProfile();
    }

    public void save(File model) throws Exception {
        java.nio.file.Files.write(fileFor(model).toPath(), GSON.toJson(this).getBytes("UTF-8"));
    }

    public WeaponProfile copy() {
        return GSON.fromJson(GSON.toJson(this), WeaponProfile.class).sane();
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    WeaponProfile sane() {
        if (name == null) name = "";
        if (idle == null) idle = "";
        if (swing == null) swing = "";
        if (inspect == null) inspect = "";
        if (draw == null) draw = "";
        if (style == null) style = "Auto";
        if (!(size > 0.05f && size < 20f)) size = 1f;
        return this;
    }
}
