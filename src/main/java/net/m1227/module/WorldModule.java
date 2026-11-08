package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Client-side world look: fullbright, fixed time of day and clear/rain view.
 * Values are swapped in only while a frame is drawn and put back right after,
 * so nothing is saved to options.txt and the server's world is untouched.
 */
public final class WorldModule extends Module {
    private final Setting.Bool fullbright;
    private final Setting.Num brightness;
    private final Setting.Mode time, weather;
    private final Setting.Num customTime;
    private float savedGamma;
    private long savedTime;
    private float sRain, sThunder;
    private boolean swapped, weatherSwapped;

    public WorldModule() {
        super("World", "Fullbright, time of day and weather (only on your screen).", Category.AMBIENCE, false);
        fullbright = add(new Setting.Bool("Fullbright", true));
        brightness = add(new Setting.Num("Brightness", 10, 1, 15, 0.5, "")).showIf(() -> fullbright.on());
        time = add(new Setting.Mode("Time", "Server", "Server", "Sunrise", "Day", "Sunset", "Night", "Midnight", "Custom"));
        customTime = add(new Setting.Num("Custom time", 6000, 0, 23999, 250, "")).showIf(() -> time.is("Custom"));
        weather = add(new Setting.Mode("Weather", "Server", "Server", "Clear", "Rain", "Thunder"));
    }

    private long timeValue() {
        String t = time.get();
        if (t.equals("Sunrise")) return 23200;
        if (t.equals("Day")) return 6000;
        if (t.equals("Sunset")) return 12600;
        if (t.equals("Night")) return 15000;
        if (t.equals("Midnight")) return 18000;
        return (long) customTime.f();
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        World w = mc.theWorld;
        if (e.phase == TickEvent.Phase.START) {
            if (w == null) return;
            swapped = true;
            savedGamma = mc.gameSettings.gammaSetting;
            if (fullbright.on()) mc.gameSettings.gammaSetting = brightness.f();
            WorldInfo info = w.getWorldInfo();
            savedTime = info.getWorldTime();
            if (!time.is("Server")) {
                // keep the day count so the moon phase stays the server's
                info.setWorldTime(savedTime - savedTime % 24000L + timeValue());
            }
            weatherSwapped = !weather.is("Server");
            if (weatherSwapped) {
                sRain = w.getRainStrength(1f);
                // getThunderStrength() already includes the rain factor
                sThunder = sRain > 0.001f ? Math.min(1f, w.getThunderStrength(1f) / sRain) : 0f;
                w.setRainStrength(weather.is("Clear") ? 0f : 1f);
                w.setThunderStrength(weather.is("Thunder") ? 1f : 0f);
            }
        } else if (swapped) {
            swapped = false;
            mc.gameSettings.gammaSetting = savedGamma;
            if (w == null) return;
            w.getWorldInfo().setWorldTime(savedTime);
            if (weatherSwapped) {
                w.setRainStrength(sRain);
                w.setThunderStrength(sThunder);
                weatherSwapped = false;
            }
        }
    }
}
