package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

/** Safe install: right artifact only, copy and check first, roll back on failure. */
public class InstallerTest {

    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = Class.forName("net.m1227.installer.InstallerMain").getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static File jar(File f, boolean client) throws Exception {
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            z.putNextEntry(new ZipEntry(client ? "mcmod.info" : "plugin.yml"));
            z.write("{}".getBytes("UTF-8"));
            z.closeEntry();
            if (client) {
                z.putNextEntry(new ZipEntry("net/m1227/Mod1227.class"));
                z.write(new byte[]{1, 2, 3});
                z.closeEntry();
            }
        }
        return f;
    }

    @Test
    public void onlyClientJarNamesMatch() throws Exception {
        Class<?>[] t = {String.class};
        assertTrue((Boolean) call("isClientJarName", t, "1227-1.26.0.jar"));
        assertTrue((Boolean) call("isClientJarName", t, "1227.jar"));
        assertFalse((Boolean) call("isClientJarName", t, "1227-relay-1.0.0.jar"));
        assertFalse((Boolean) call("isClientJarName", t, "12270.jar"));
        assertFalse((Boolean) call("isClientJarName", t, "other-1227-1.0.jar"));
    }

    @Test
    public void installsAndArchivesOldClientOnly() throws Exception {
        File dir = Files.createTempDirectory("i1227").toFile(), mods = new File(dir, "mods");
        mods.mkdirs();
        File src = jar(new File(dir, "1227-9.9.9.jar"), true);
        jar(new File(mods, "1227-1.0.0.jar"), true);
        jar(new File(mods, "1227-relay-1.0.0.jar"), false);
        String msg = (String) call("install", new Class<?>[]{File.class, File.class, String.class}, src, mods, "9.9.9");
        assertTrue(msg, msg.startsWith("Installed"));
        assertTrue(new File(mods, "1227-9.9.9.jar").isFile());
        assertFalse(new File(mods, "1227-1.0.0.jar").exists());
        assertTrue("the relay plugin is not touched", new File(mods, "1227-relay-1.0.0.jar").isFile());
        assertEquals(1, new File(mods, "1227-old").listFiles()[0].listFiles().length);
        assertFalse(new File(mods, "1227-9.9.9.jar.installing").exists());
    }

    @Test
    public void wrongArtifactIsRefusedAndNothingChanges() throws Exception {
        File dir = Files.createTempDirectory("i1227").toFile(), mods = new File(dir, "mods");
        mods.mkdirs();
        File relay = jar(new File(dir, "1227-relay-1.0.0.jar"), false);
        jar(new File(mods, "1227-1.0.0.jar"), true);
        String msg = (String) call("install", new Class<?>[]{File.class, File.class, String.class}, relay, mods, "9.9.9");
        assertTrue(msg, msg.startsWith("This file is not"));
        assertTrue(new File(mods, "1227-1.0.0.jar").isFile());
        assertFalse(new File(mods, "1227-old").exists());
    }

    @Test
    public void failureKeepsThePreviousVersion() throws Exception {
        File dir = Files.createTempDirectory("i1227").toFile(), mods = new File(dir, "mods");
        mods.mkdirs();
        File src = jar(new File(dir, "1227-9.9.9.jar"), true);
        jar(new File(mods, "1227-1.0.0.jar"), true);
        // the destination name is taken by a non-empty folder: the final rename cannot happen
        File block = new File(mods, "1227-9.9.9.jar");
        block.mkdirs();
        new File(block, "x").createNewFile();
        String msg = (String) call("install", new Class<?>[]{File.class, File.class, String.class}, src, mods, "9.9.9");
        assertTrue(msg, msg.startsWith("Could not install"));
        assertTrue("old version put back", new File(mods, "1227-1.0.0.jar").isFile());
        assertFalse(new File(mods, "1227-9.9.9.jar.installing").exists());
    }
}
