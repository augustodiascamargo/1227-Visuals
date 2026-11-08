package net.m1227.hud;

import java.util.Locale;
import net.m1227.module.ToggleSprintModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Sprinting / sneaking / flying / riding state and speed. */
public final class MovementHud extends HudModule {
    private float w = 90;

    public MovementHud() {
        super("Movement Status", "Sprint, sneak, fly state and speed.", 0.01, 0.96);
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return 14;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        String state = "Standing";
        if (p != null) {
            if (p.capabilities.isFlying) state = "Flying";
            else if (p.ridingEntity != null) state = "Riding";
            else if (p.isSneaking()) state = "Sneaking";
            else if (p.isSprinting()) state = ToggleSprintModule.toggled() ? "Sprinting (Toggled)" : "Sprinting";
            else if (InfoHud.speed(p) > 0.5) state = "Walking";
        }
        String sp = p == null ? "0.0" : String.format(Locale.ROOT, "%.1f", InfoHud.speed(p));
        w = Gfx.textWidth(state) + Gfx.textWidth(sp) + 26;
        panel(x, y, w, 14);
        Gfx.text(state, x + 5, y + 3.5f, value());
        Gfx.text(sp + "b/s", x + w - 5 - Gfx.textWidth(sp + "b/s"), y + 3.5f, LABEL);
    }
}
