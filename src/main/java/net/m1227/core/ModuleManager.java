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
        register(new WorldModule());
        register(new ItemPhysicsModule());
        register(new FogModule());
        register(new BlockOverlayModule());
        register(new WetWorldModule());
        register(new TargetAuraModule());
        register(new PlayerAuraModule());
        register(new PlayerGlowModule());
        register(new AuraHatModule());
        register(new HaloModule());
        register(new WingsModule());
        register(new EnergyShellModule());
        register(new ArrivalEffectsModule());
        register(new TrailsModule());
        register(new AfterimageModule());
        register(new JumpEffectModule());
        register(new PlayerFxModule());
        register(new HitEffectModule());
        register(new HitColorModule());
        register(new KillEffectModule());
        register(new ShockwaveModule());
        register(new ProjectileTrailsModule());
        register(new PearlHelperModule());
        register(new SpacingCircleModule());
        register(new AimHelperModule());
        register(new HitParticlesModule());
        register(new DamageNumbersModule());
        register(new MatchFxModule());
        register(new WeaponEffectsModule());
        register(new ItemGlowModule());
        register(new WeaponModelsModule());
        register(new MotionBlurModule());
        register(new ViewBobbingModule());
        register(new FovModule());
        register(new ZoomModule());
        register(new CameraFxModule());
        register(new CinematicModule());
        register(new LowFireModule());
        register(new AnimationsModule());
        register(new ToggleSprintModule());
        register(new AlertsModule());
        register(new SmartArmorModule());
        register(new WaypointsModule());
        register(new EmotesModule());
        register(new HitSoundsModule());
        register(new MusicModule());
        register(new FloatingLyricsModule());
        register(new MusicAtmosphereModule());
        register(new DiscordPresenceModule());
        register(new DynamicIslandModule());
        register(new HitMarkerModule());
        register(new CrosshairModule());
        register(new NametagsModule());
        register(new ChatModule());
        register(new net.m1227.hud.WatermarkHud());
        register(new net.m1227.hud.HeldItemHud());
        register(new net.m1227.hud.InfoHud());
        register(new net.m1227.hud.KeystrokesHud());
        register(new net.m1227.hud.TargetHud());
        register(new net.m1227.hud.ComboHud());
        register(new net.m1227.hud.ArmorHud());
        register(new net.m1227.hud.PotionHud());
        register(new net.m1227.hud.CoordsHud());
        register(new net.m1227.hud.MovementHud());
        register(new net.m1227.hud.ClockHud());
        register(new net.m1227.hud.SystemHud());
        register(new net.m1227.hud.ServerHud());
        register(new net.m1227.hud.CompassHud());
        register(new net.m1227.hud.SessionHud());
        register(new net.m1227.hud.ScoreboardHud());
        register(new net.m1227.hud.HitDelayHud());
        register(new net.m1227.hud.WTapHud());
        register(new net.m1227.hud.ReachHud());
        register(new net.m1227.hud.AimHelperHud());
        register(new net.m1227.hud.MusicHud());
        register(new net.m1227.hud.VisualizerHud());
        register(new net.m1227.hud.InventoryHud());
        register(new net.m1227.hud.KeybindsHud());
        register(new TabListModule());
        register(new BossBarModule());
        register(new MenuStyleModule());
        register(new InterfaceModule());
        register(new ClientModule());
        register(new PresetsModule());
        register(new ServerProfilesModule());
        register(PerformanceModule.INSTANCE);
        for (Module m : ALL) {
            if (m.toggleable() && m.defaultEnabled) m.setEnabled(true);
            // always-on groups (Music Player, Interface, Client, Performance...) never go through
            // setEnabled, so they are put on the event bus here; before 1.20 their key and tick
            // handlers (music keys, window title) never ran
            if (!m.toggleable()) net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(m);
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
