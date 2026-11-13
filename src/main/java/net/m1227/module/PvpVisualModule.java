package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/**
 * PvP Visual Optimization: limits for 1227's own cosmetic effects while you fight. Players, name
 * tags, the crosshair and combat feedback (hit sounds, hit markers, hit colour, damage numbers)
 * are never hidden or limited here.
 *
 * <ul>
 * <li>Dynamic Quality: fewer cosmetic particles while frames are slower than the target, back up
 * when there is room (see DynamicQuality);</li>
 * <li>Particle priority: a share of the particle budget is kept free for hit, critical and kill
 * effects, so cosmetics can never take all of it;</li>
 * <li>Cosmetic effect distance;</li>
 * <li>how many OTHER players get auras / cosmetics and weapon effects (nearest first), how many
 * projectile trails per tick, and how many hit bursts per second.</li>
 * </ul>
 */
public final class PvpVisualModule extends Module {
    public static PvpVisualModule INSTANCE;
    private final Setting.Bool dynamic;
    private final Setting.Mode target, priority, reaction;
    private final Setting.Num minQ, reserve, cosmeticDist, maxAuras, maxWeapon, maxTrails, maxHits;

    public PvpVisualModule() {
        super("PvP Visual Optimization", "Limits 1227's cosmetic effects in fights; never hides players or hit feedback.", Category.PVP, false);
        INSTANCE = this;
        dynamic = add(new Setting.Bool("Dynamic quality", true));
        target = add(new Setting.Mode("Dynamic quality target", "Monitor refresh rate", "Monitor refresh rate", "60 FPS", "120 FPS", "144 FPS", "240 FPS"))
                .showIf(() -> dynamic.on());
        minQ = add(new Setting.Num("Lowest cosmetic quality", 0.35, 0.1, 1.0, 0.05, "x")).showIf(() -> dynamic.on());
        reaction = add(new Setting.Mode("Reaction", "Normal", "Slow", "Normal", "Fast")).showIf(() -> dynamic.on());
        priority = add(new Setting.Mode("Particle priority", "Combat first", "Combat first", "Equal"));
        reserve = add(new Setting.Num("Budget kept for combat effects", 20, 5, 50, 5, "%")).showIf(() -> priority.is("Combat first"));
        cosmeticDist = add(new Setting.Num("Cosmetic effect distance", 32, 8, 128, 4, "m"));
        maxAuras = add(new Setting.Num("Max other players with auras / cosmetics", 4, 0, 32, 1, ""));
        maxWeapon = add(new Setting.Num("Max other players with weapon effects", 3, 0, 32, 1, ""));
        maxTrails = add(new Setting.Num("Max projectile trails", 12, 0, 64, 1, ""));
        maxHits = add(new Setting.Num("Max hit bursts per second", 8, 1, 30, 1, "/s"));
        add(new Setting.Action("Open Performance Dashboard", () -> net.m1227.client.menu.PerfDashboardScreen.open(1)));
    }

    public boolean dynamicOn() {
        return dynamic.on();
    }

    public float targetFps() {
        String t = target.get();
        if (t.startsWith("Monitor")) return net.m1227.client.perf.DynamicQuality.refreshRate();
        return Float.parseFloat(t.substring(0, t.indexOf(' ')));
    }

    public float minQuality() {
        return minQ.f();
    }

    public float reaction() {
        return reaction.is("Slow") ? 0.5f : reaction.is("Fast") ? 2f : 1f;
    }

    public boolean combatFirst() {
        return priority.is("Combat first");
    }

    public float combatReserve() {
        return reserve.f() / 100f;
    }

    public double cosmeticDistance() {
        return cosmeticDist.get();
    }

    public int maxAuras() {
        return maxAuras.i();
    }

    public int maxWeaponFx() {
        return maxWeapon.i();
    }

    public int maxTrails() {
        return maxTrails.i();
    }

    public float maxHitEffectsPerSecond() {
        return maxHits.f();
    }
}
