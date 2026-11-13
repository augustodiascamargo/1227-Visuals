package net.m1227.client;

import java.io.File;
import net.m1227.Mod1227;

/** Opens folders and files with the operating system (off the game thread). */
public final class Os {
    private Os() {}

    public static void open(final File f) {
        if (f.isDirectory() || !f.exists()) f.mkdirs();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.awt.Desktop.getDesktop().open(f);
                } catch (Throwable t) {
                    try {
                        org.lwjgl.Sys.openURL(f.toURI().toString());
                    } catch (Throwable t2) {
                        Mod1227.LOGGER.warn("Could not open " + f, t2);
                    }
                }
            }
        }, "1227-open").start();
    }
}
