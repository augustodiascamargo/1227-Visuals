package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/**
 * Item Physics (visual): dropped items lie flat on the ground and blocks rest on it instead of
 * floating and spinning; while falling they tumble. Drawn by {@link net.m1227.render.PhysicsItemRender}.
 * Nothing about the item entity changes (same position, pickup and hitbox).
 */
public final class ItemPhysicsModule extends Module {
    public static ItemPhysicsModule INSTANCE;
    private final Setting.Bool tumble;
    private final Setting.Num spin;

    public ItemPhysicsModule() {
        super("Item Physics", "Dropped items lie flat on the ground and tumble while falling.", Category.AMBIENCE, false);
        tumble = add(new Setting.Bool("Tumble while falling", true));
        spin = add(new Setting.Num("Tumble speed", 1.0, 0.2, 3.0, 0.1, "x"));
        INSTANCE = this;
    }

    public boolean tumble() {
        return tumble.on();
    }

    public float spinSpeed() {
        return spin.f();
    }
}
