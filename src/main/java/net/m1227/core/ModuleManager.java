package net.m1227.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.m1227.module.*;

public final class ModuleManager {
    private static final List<Module> ALL = new ArrayList<Module>();

    private ModuleManager() {}

    public static void init() {
        // order = order inside each menu category
        register(new SkyModule());
        register(new AmbienceModule());
        register(new AmbientModule());
        register(new TargetAuraModule());
        register(new PlayerAuraModule());
        register(new AuraHatModule());
        register(new HaloModule());
        register(new TrailsModule());
        register(new JumpEffectModule());
        register(new HitEffectModule());
        register(new KillEffectModule());
        register(new ShockwaveModule());
        register(new WeaponEffectsModule());
        register(new MotionBlurModule());
        register(new ViewBobbingModule());
        register(new AnimationsModule());
        register(new ToggleSprintModule());
        register(new HitSoundsModule());
        register(new DynamicIslandModule());
        register(new HitMarkerModule());
        register(new ClientModule());
        register(new PresetsModule());
        register(PerformanceModule.INSTANCE);
        for (Module m : ALL) {
            if (m.toggleable() && m.defaultEnabled) m.setEnabled(true);
        }
    }

    private static void register(Module m) {
        ALL.add(m);
    }

    public static Module byName(String name) {
        for (Module m : ALL) {
            if (m.name.equals(name)) return m;
        }
        return null;
    }

    public static List<Module> all() {
        return ALL;
    }

    public static List<Module> inCategory(Category c) {
        List<Module> out = new ArrayList<Module>();
        for (Module m : ALL) {
            if (m.category == c) out.add(m);
        }
        return out;
    }

    public static List<Module> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Module> out = new ArrayList<Module>();
        for (Module m : ALL) {
            if (m.name.toLowerCase(Locale.ROOT).contains(q)
                    || m.category.title.toLowerCase(Locale.ROOT).contains(q)
                    || m.description.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(m);
            }
        }
        return out;
    }
}
