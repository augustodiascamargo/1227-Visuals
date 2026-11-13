package net.m1227.client.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.m1227.Mod1227;
import net.m1227.core.Notifier;
import net.m1227.module.ClientModule;

/**
 * Themes Forge's loading screen (the progress screen before the main menu) with 1227's colours,
 * through Forge's own config/splash.properties. Takes effect on the next start. The original
 * file is backed up once and can be restored. Forge reads the colours as hex numbers.
 */
public final class SplashTheme {
    private SplashTheme() {}

    private static File file() {
        return new File(Mod1227.configDir, "splash.properties");
    }

    private static File backup() {
        return new File(Mod1227.configDir, "splash.properties.1227-backup");
    }

    private static File absentMarker() {
        return new File(Mod1227.configDir, "splash.properties.1227-absent");
    }

    private static boolean appliedCache;
    private static long appliedAt;

    public static boolean applied() {
        long now = System.currentTimeMillis();
        if (now - appliedAt > 1000) {
            appliedCache = backup().isFile() || absentMarker().isFile();
            appliedAt = now;
        }
        return appliedCache;
    }

    private static String hex(int rgb) {
        return String.format("0x%06X", rgb & 0xFFFFFF);
    }

    public static void apply() {
        try {
            File f = file();
            if (!applied()) {
                if (f.isFile()) Files.copy(f.toPath(), backup().toPath(), StandardCopyOption.REPLACE_EXISTING);
                else Files.write(absentMarker().toPath(), new byte[0]);
            }
            Properties p = new Properties();
            if (f.isFile()) {
                try (FileInputStream in = new FileInputStream(f)) {
                    p.load(in);
                }
            }
            int acc = ClientModule.accent();
            p.setProperty("background", hex(0x0B0C11));
            p.setProperty("font", hex(0xECEEF4));
            p.setProperty("barBorder", hex(acc));
            p.setProperty("bar", hex(acc));
            p.setProperty("barBackground", hex(0x181B24));
            p.setProperty("memoryGood", hex(0x3DDC97));
            p.setProperty("memoryWarn", hex(0xFFC24A));
            p.setProperty("memoryLow", hex(0xFF6B6B));
            try (FileOutputStream out = new FileOutputStream(f)) {
                p.store(out, "Forge splash screen; colours set by 1227 (Settings > Advanced restores the original)");
            }
            appliedAt = 0;
            Notifier.post(Notifier.Kind.SYSTEM, "Loading screen", "Themed from the next start", Notifier.GOOD, 2000);
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not theme splash.properties", e);
            Notifier.post(Notifier.Kind.SYSTEM, "Loading screen", "Could not change it", Notifier.BAD, 2000);
        }
    }

    public static void restore() {
        try {
            if (backup().isFile()) {
                Files.move(backup().toPath(), file().toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (absentMarker().isFile()) {
                Files.deleteIfExists(file().toPath());
                Files.deleteIfExists(absentMarker().toPath());
            }
            appliedAt = 0;
            Notifier.post(Notifier.Kind.SYSTEM, "Loading screen", "Forge default from the next start", Notifier.GOOD, 2000);
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not restore splash.properties", e);
        }
    }
}
