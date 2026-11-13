package net.m1227.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.m1227.client.perf.ChangeSet;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;

/**
 * Competitive Mode: one switch for a match. It turns off the 1227 visual modules you chose,
 * turns on PvP Visual Optimization, lowers the cosmetic effect quality and Minecraft's particle
 * limit, and puts every one of those back exactly as it was when you turn it off (the old values
 * are saved to disk, so this also works after a restart).
 *
 * Never changed: players and their render distance, name tags, the crosshair, hit sounds, hit
 * markers, hit colour, damage numbers, the Target HUD and your HUD layout. "Preview" shows the
 * full list before anything changes; you can untick any module there.
 */
public final class CompetitiveModule extends Module {
    public static CompetitiveModule INSTANCE;
    static final String KEY = "competitive";

    /** Modules that can be turned off by the mode (heavy or purely cosmetic). */
    public static final String[] CANDIDATES = {"Motion Blur", "Dynamic Skies", "Ambience", "Ambient Particles", "Wet World",
            "Player Aura", "Aura Hat", "Halo", "Wings", "Energy Shell", "Trails", "Afterimage", "Jump Effects", "Player FX",
            "Weapon Effects", "Music Atmosphere", "Camera FX", "Floating Lyrics", "Item Physics", "Weapon Models", "Item Glow",
            "Player Glow", "Target Aura", "Projectile Trails", "Cinematic Mode"};
    private static final String DEFAULT_OFF = "Motion Blur|Dynamic Skies|Ambience|Ambient Particles|Wet World|Player Aura|Aura Hat|Halo|"
            + "Wings|Energy Shell|Trails|Afterimage|Jump Effects|Player FX|Weapon Effects|Music Atmosphere|Camera FX|Floating Lyrics";

    private final Setting.Text offList;
    private final Setting.Bool lowerQuality, lowerParticles, pvpVisual;
    private boolean skipApply;

    public CompetitiveModule() {
        super("Competitive Mode", "Turns off the visuals you choose for a match and restores them after.", Category.PVP, false);
        INSTANCE = this;
        offList = add(new Setting.Text("Modules to turn off", DEFAULT_OFF, 4000, "")).showIf(() -> false);
        pvpVisual = add(new Setting.Bool("Turn on PvP Visual Optimization", true));
        lowerQuality = add(new Setting.Bool("Effect quality to Medium", true));
        lowerParticles = add(new Setting.Bool("Minecraft particle limit to 1500", true));
        add(new Setting.Action("Preview changes / choose modules", () -> net.m1227.client.menu.ChangePreviewScreen.openCompetitive()));
    }

    public List<String> chosen() {
        return new ArrayList<String>(Arrays.asList(offList.get().isEmpty() ? new String[0] : offList.get().split("\\|")));
    }

    public boolean isChosen(String name) {
        return chosen().contains(name);
    }

    public void setChosen(String name, boolean on) {
        List<String> l = chosen();
        l.remove(name);
        if (on) l.add(name);
        StringBuilder b = new StringBuilder();
        for (String s : l) {
            if (b.length() > 0) b.append('|');
            b.append(s);
        }
        offList.set(b.toString());
        net.m1227.core.ConfigManager.markDirty();
    }

    /** Exactly what turning the mode on would change now. */
    public ChangeSet build() {
        ChangeSet c = new ChangeSet(KEY, "Competitive Mode");
        for (String n : chosen()) c.module(n, false);
        if (pvpVisual.on()) c.module("PvP Visual Optimization", true);
        PerformanceModule p = PerformanceModule.INSTANCE;
        if (lowerQuality.on() && (p.quality.is("High") || p.quality.is("Ultra"))) c.setting("Performance", "Quality", "Medium");
        if (lowerParticles.on() && p.particleCap.get() > 1500) c.setting("Performance", "Minecraft particle limit", 1500.0);
        c.notes.add("Never changed: players and their render distance, name tags, crosshair, hit sounds,");
        c.notes.add("hit markers, hit colour, damage numbers, Target HUD and your HUD layout.");
        return c;
    }

    /** Turn on with a change set already applied by the preview screen. */
    public void enableApplied() {
        skipApply = true;
        setEnabled(true);
        skipApply = false;
    }

    @Override
    protected void onEnable() {
        // at start-up the mode comes back on with its changes already in your config: the undo file
        // from the first time is kept, never overwritten
        if (skipApply || ChangeSet.canRestore(KEY)) return;
        ChangeSet c = build();
        c.apply();
        if (net.m1227.core.Notifier.ready) {
            Notifier.post(Notifier.Kind.SYSTEM, "Competitive Mode", c.changes.size() + " changes, turn it off to restore", Notifier.GOOD, 2600);
        }
    }

    @Override
    protected void onDisable() {
        if (ChangeSet.restore(KEY)) Notifier.post(Notifier.Kind.SYSTEM, "Competitive Mode", "Your previous settings are back", Notifier.INFO, 2200);
    }

    /** Modules that exist in this build (the candidates list may name removed ones). */
    public static List<String> available() {
        List<String> out = new ArrayList<String>();
        for (String n : CANDIDATES) if (ModuleManager.byName(n) != null) out.add(n);
        return out;
    }
}
