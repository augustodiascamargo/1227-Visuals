package net.m1227.installer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The 1227 installer: what opens when you double-click 1227.jar. It finds your Minecraft folder,
 * checks that Forge 1.8.9 is installed, copies this jar into mods (older 1227 jars are moved to
 * mods/1227-old, never deleted) and tells you to start Minecraft with the Forge 1.8.9 profile.
 * Nothing is downloaded and no launcher is changed. Plain Java: no Minecraft classes here.
 */
public final class InstallerMain {
    private static final String FORGE_ID = "1.8.9-forge1.8.9-11.15.1.2318-1.8.9";
    private static final String FORGE_PAGE = "https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html";
    private static final Color BG = new Color(0x0E1016), TEXT = new Color(0xECEEF4), MUTED = new Color(0x9AA0AE),
            GOOD = new Color(0x3DDC97), WARN = new Color(0xFFC24A), ACCENT = new Color(0x8B5CFF);

    private File mc;
    private final JLabel folder = label("", MUTED, 12f), forge = label("", MUTED, 12f), result = label(" ", MUTED, 12f);
    private JButton install;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--install")) {
            // command line: java -jar 1227.jar --install [minecraft folder]
            InstallerMain m = new InstallerMain();
            m.mc = args.length > 1 ? new File(args[1]) : defaultMinecraft();
            System.out.println(m.installTo(m.mc));
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look
        }
        SwingUtilities.invokeLater(() -> new InstallerMain().show());
    }

    static File defaultMinecraft() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return new File(appdata != null ? appdata : home, ".minecraft");
        }
        if (os.contains("mac")) return new File(home, "Library/Application Support/minecraft");
        return new File(home, ".minecraft");
    }

    static String version() {
        try (InputStream in = InstallerMain.class.getResourceAsStream("/mcmod.info")) {
            if (in == null) return "dev";
            String s = new Scanner(in, "UTF-8").useDelimiter("\\A").next();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
            return m.find() ? m.group(1) : "dev";
        } catch (Exception e) {
            return "dev";
        }
    }

    static boolean forgeInstalled(File mc) {
        return new File(mc, "versions/" + FORGE_ID + "/" + FORGE_ID + ".json").isFile();
    }

    /** The jar this class runs from, or null when not started from a jar. */
    static File self() {
        try {
            File f = new File(InstallerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Copies the jar into mods and moves older 1227 jars aside. Returns a message for the user. */
    String installTo(File mc) {
        File jar = self();
        if (jar == null) return "Run this from the 1227 jar file.";
        if (!mc.isDirectory()) return "Minecraft folder not found: " + mc;
        File mods = new File(mc, "mods");
        if (!mods.isDirectory() && !mods.mkdirs()) return "Could not create " + mods;
        String name = "1227-" + version() + ".jar";
        try {
            File[] old = mods.listFiles((d, n) -> n.toLowerCase().matches("1227(-.*)?\\.jar"));
            int moved = 0;
            if (old != null) {
                File keep = new File(mods, "1227-old/" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
                for (File o : old) {
                    if (o.getCanonicalPath().equals(jar.getCanonicalPath()) || o.getName().equals(name)) continue; // same version: replaced below
                    keep.mkdirs();
                    Files.move(o.toPath(), new File(keep, o.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    moved++;
                }
            }
            File dest = new File(mods, name);
            if (!dest.getCanonicalPath().equals(jar.getCanonicalPath())) {
                Files.copy(jar.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return "Installed " + name + (moved > 0 ? " (" + moved + " older 1227 jar(s) moved to mods/1227-old)" : "")
                    + ". Start Minecraft with the Forge 1.8.9 profile.";
        } catch (Exception e) {
            return "Could not install: " + e.getMessage();
        }
    }

    private static JLabel label(String s, Color c, float size) {
        JLabel l = new JLabel(s);
        l.setForeground(c);
        l.setFont(l.getFont().deriveFont(size));
        l.setAlignmentX(0f);
        return l;
    }

    private static JButton button(String s, boolean primary) {
        JButton b = new JButton(s);
        b.setFocusPainted(false);
        if (primary) {
            b.setBackground(ACCENT);
            b.setForeground(Color.WHITE);
        }
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        return b;
    }

    private void refresh() {
        folder.setText("Minecraft folder: " + mc.getAbsolutePath() + (mc.isDirectory() ? "" : "  (not found)"));
        boolean f = mc.isDirectory() && forgeInstalled(mc);
        forge.setText(f ? "Forge 1.8.9 (11.15.1.2318): installed" : "Forge 1.8.9 (11.15.1.2318): not found. Install it first (button below), then come back.");
        forge.setForeground(f ? GOOD : WARN);
        install.setEnabled(mc.isDirectory());
    }

    void show() {
        mc = defaultMinecraft();
        JFrame f = new JFrame("1227 " + version() + " installer");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel title = label("1227", TEXT, 26f);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        col.add(title);
        col.add(label("Client mod for Minecraft 1.8.9 + Forge 11.15.1.2318  ·  version " + version(), MUTED, 12f));
        col.add(Box.createVerticalStrut(14));
        col.add(folder);
        col.add(Box.createVerticalStrut(4));
        col.add(forge);
        col.add(Box.createVerticalStrut(14));
        col.add(label("This copies the mod into your mods folder. Older 1227 jars are moved to mods/1227-old.", MUTED, 12f));
        col.add(Box.createVerticalStrut(10));
        col.add(result);
        root.add(col, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton choose = button("Choose folder...", false);
        choose.addActionListener(e -> {
            JFileChooser ch = new JFileChooser(mc);
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (ch.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                mc = ch.getSelectedFile();
                refresh();
            }
        });
        JButton forgeBtn = button("Get Forge 1.8.9", false);
        forgeBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(FORGE_PAGE));
            } catch (Exception ex) {
                result.setText("Open " + FORGE_PAGE + " in your browser.");
            }
        });
        install = button("Install", true);
        install.addActionListener(e -> {
            String msg = installTo(mc);
            result.setText("<html><div style='width:420px'>" + msg + "</div></html>");
            result.setForeground(msg.startsWith("Installed") ? GOOD : WARN);
        });
        JButton openMods = button("Open mods folder", false);
        openMods.addActionListener(e -> {
            try {
                File m = new File(mc, "mods");
                m.mkdirs();
                Desktop.getDesktop().open(m);
            } catch (Exception ignored) {
                // nothing to do
            }
        });
        buttons.add(choose);
        buttons.add(forgeBtn);
        buttons.add(openMods);
        buttons.add(install);
        root.add(buttons, BorderLayout.SOUTH);
        f.setContentPane(root);
        f.setMinimumSize(new Dimension(620, 300));
        refresh();
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
