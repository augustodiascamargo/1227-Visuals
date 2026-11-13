package net.m1227.client.menu;

import java.util.List;
import net.m1227.client.perf.ChangeSet;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.InputManager;
import net.m1227.module.CompetitiveModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * "This is exactly what will change": the list of a {@link ChangeSet} (current value, new value),
 * with a tick per line, Apply and Cancel, and Restore when an earlier apply can be undone. For
 * Competitive Mode it also lists every module the mode may turn off, to choose them.
 */
public final class ChangePreviewScreen extends UiScreen {
    private ChangeSet set;
    private final String applyLabel;
    private final Runnable after;
    private final boolean competitive;

    private ChangePreviewScreen(GuiScreen parent, ChangeSet set, String applyLabel, Runnable after, boolean competitive) {
        super(parent);
        this.set = set;
        this.applyLabel = applyLabel;
        this.after = after;
        this.competitive = competitive;
    }

    public static void open(ChangeSet set, String applyLabel, Runnable after) {
        GuiScreen cur = Minecraft.getMinecraft().currentScreen;
        InputManager.INSTANCE.requestScreen(new ChangePreviewScreen(cur, set, applyLabel, after, false));
    }

    public static void openCompetitive() {
        CompetitiveModule m = CompetitiveModule.INSTANCE;
        if (m == null) return;
        GuiScreen cur = Minecraft.getMinecraft().currentScreen;
        InputManager.INSTANCE.requestScreen(new ChangePreviewScreen(cur, m.build(), "Turn on Competitive Mode", null, true));
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        float pw = Math.min(600, w - 20), px = (w - pw) / 2f, top = 14;
        final CompetitiveModule cm = CompetitiveModule.INSTANCE;
        boolean compOn = competitive && cm != null && cm.isEnabled();
        Ui.textBold(set.title, px, top, DS.T_TITLE, DS.TEXT);
        String sub = compOn ? "Competitive Mode is on. Turning it off puts back everything below."
                : set.changes.isEmpty() ? "Nothing would change: everything already has these values." : "This is exactly what will change. Untick anything to keep it.";
        Ui.text(sub, px, top + 20, DS.T_SMALL, DS.MUTED);
        float y = top + 34, lh = h - y - 44;
        Ui.card(px, y, pw, lh);
        float off = ui.beginScroll("chg", px + 4, y + 4, pw - 8, lh - 8);
        float ry = y + 8;
        String group = null;
        for (final ChangeSet.Change c : set.changes) {
            if (!c.group.equals(group)) {
                group = c.group;
                Ui.caption(group.toUpperCase(java.util.Locale.ROOT), px + 12, ry - off);
                ry += 14;
            }
            float yy = ry - off;
            Gfx.roundedRect(px + 8, yy, pw - 16, 20, DS.R_SM, DS.SURFACE_2);
            if (!compOn) ui.toggle("t" + System.identityHashCode(c), px + 14, yy + 3.5f, c.enabled, () -> c.enabled = !c.enabled);
            Ui.text(c.what, px + 46, yy + 6, DS.T_BODY, c.enabled ? DS.TEXT : DS.FAINT);
            String fromTo = c.from + "  →  " + c.to;
            Ui.textRight(fromTo, px + pw - 16, yy + 6, DS.T_SMALL, c.enabled ? DS.MUTED : DS.FAINT);
            ry += 23;
        }
        if (competitive && cm != null) {
            ry += 6;
            Ui.caption("MODULES COMPETITIVE MODE TURNS OFF (CHOOSE)", px + 12, ry - off);
            ry += 14;
            List<String> av = CompetitiveModule.available();
            float colW = (pw - 24) / 2f;
            for (int i = 0; i < av.size(); i++) {
                final String n = av.get(i);
                float cx = px + 12 + (i % 2) * colW, yy = ry - off + (i / 2) * 20;
                boolean chosen = cm.isChosen(n);
                net.m1227.core.Module mod = net.m1227.core.ModuleManager.byName(n);
                ui.toggle("c" + n, cx, yy + 3, chosen, compOn ? null : () -> {
                    cm.setChosen(n, !cm.isChosen(n));
                    set = cm.build();
                });
                Ui.text(n + (mod != null && mod.isEnabled() ? "" : "  (off now)"), cx + 32, yy + 5, DS.T_SMALL, chosen ? DS.TEXT : DS.MUTED);
            }
            ry += ((av.size() + 1) / 2) * 20;
        }
        ry += 8;
        for (String n : set.notes) {
            Ui.text(n, px + 12, ry - off, DS.T_SMALL, DS.FAINT);
            ry += 11;
        }
        ui.endScroll(ry - y);
        float by = h - 34;
        if (compOn) {
            ui.button("off", "Turn off and restore", px, by, 150, 22, Ui.PRIMARY, true, Glyph.REFRESH, () -> {
                cm.setEnabled(false);
                back();
            });
        } else {
            ui.button("apply", applyLabel, px, by, Math.max(130, Ui.width(applyLabel, DS.T_BODY) + 34), 22, Ui.PRIMARY,
                    set.any() || competitive, Glyph.CHECK, () -> {
                        set.apply();
                        if (competitive && cm != null) cm.enableApplied();
                        if (after != null) after.run();
                        back();
                    });
            if (ChangeSet.canRestore(set.key)) {
                ui.button("restore", "Restore previous", px + Math.max(130, Ui.width(applyLabel, DS.T_BODY) + 34) + 8, by, 120, 22,
                        Ui.SECONDARY, true, Glyph.REFRESH, () -> {
                            ChangeSet.restore(set.key);
                            back();
                        });
            }
        }
        ui.button("cancel", "Cancel", px + pw - 90, by, 90, 22, Ui.GHOST, true, Glyph.BACK, this::back);
    }
}
