package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

/**
 * Coloured glow around the item in your hand (first person). Drawn as a few
 * tinted, slightly larger additive copies of the item model, so the glow
 * follows the item's real shape and every animation.
 */
public final class ItemGlowModule extends Module {
    public static ItemGlowModule INSTANCE;

    private final Setting.Mode items, colorMode;
    private final Setting.Col color1, color2;
    private final Setting.Num intensity, spread, layers, pulseSpeed, pulseDepth;

    public ItemGlowModule() {
        super("Item Glow", "Coloured glow that follows the shape of the held item.", Category.WEAPONS, false);
        INSTANCE = this;
        items = add(new Setting.Mode("Items", "Swords", "Swords", "Weapons and tools", "Any item"));
        style = add(new Setting.Mode("Style", "Glow", "Glow", "Outline", "Glow + Outline"));
        outline = add(new Setting.Num("Outline thickness", 1.0, 0.4, 3.0, 0.1, "x")).showIf(() -> !style.is("Glow"));
        colorMode = add(new Setting.Mode("Color mode", "Gradient", "Single", "Gradient", "Rainbow", "Weapon effect"));
        color1 = add(new Setting.Col("Color", 0x8B5CFF));
        color2 = add(new Setting.Col("Color 2", 0x2DD4FF));
        intensity = add(new Setting.Num("Intensity", 0.45, 0.05, 1.0, 0.05, ""));
        spread = add(new Setting.Num("Spread", 0.12, 0.02, 0.4, 0.01, ""));
        layers = add(new Setting.Num("Layers", 3, 1, 6, 1, ""));
        pulseSpeed = add(new Setting.Num("Pulse speed", 1.5, 0.0, 6.0, 0.1, ""));
        pulseDepth = add(new Setting.Num("Pulse depth", 0.35, 0.0, 1.0, 0.05, ""));
    }

    private final Setting.Mode style;
    private final Setting.Num outline;

    public boolean glowOn() {
        return !style.is("Outline");
    }

    public boolean outlineOn() {
        return !style.is("Glow");
    }

    public float outlineWidth() {
        return 0.03f * outline.f();
    }

    public boolean applies(ItemStack s) {
        if (s == null) return false;
        if (items.is("Any item")) return true;
        if (s.getItem() instanceof ItemSword) return true;
        return items.is("Weapons and tools") && (s.getItem() instanceof ItemTool || s.getItem() instanceof ItemAxe || s.getItem() instanceof ItemBow);
    }

    public int layers() {
        return layers.i();
    }

    public float spread() {
        return spread.f();
    }

    public float intensity() {
        return intensity.f();
    }

    public float pulse(float t) {
        float d = pulseDepth.f();
        return 1f - d * (0.5f + 0.5f * (float) Math.sin(t * pulseSpeed.f() * 2f));
    }

    public int color(float t) {
        if (colorMode.is("Single")) return color1.rgb();
        if (colorMode.is("Rainbow")) return ParticleSystem.rainbow(t * 0.12f);
        if (colorMode.is("Weapon effect") && WeaponEffectsModule.INSTANCE != null) {
            WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;
            return ParticleSystem.mix(w.col0(), w.col1(), 0.5f + 0.5f * (float) Math.sin(t));
        }
        return ParticleSystem.mix(color1.rgb(), color2.rgb(), 0.5f + 0.5f * (float) Math.sin(t * 0.8f));
    }
}
