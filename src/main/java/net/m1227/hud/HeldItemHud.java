package net.m1227.hud;

import java.util.Map;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Card that slides in when you switch items: icon, name (rarity colour),
 * enchantments and durability. Can replace Minecraft's floating item name.
 */
public final class HeldItemHud extends HudModule {
    private final Setting.Num stay;
    private final Setting.Bool enchants, hideVanilla, always;
    private ItemStack last;
    private long changed;
    private Boolean vanillaBefore;
    private float w = 120;

    public HeldItemHud() {
        super("Held Item", "Item name, enchants and durability when switching.", 0.40, 0.72);
        always = add(new Setting.Bool("Always visible", false));
        stay = add(new Setting.Num("Show for", 2.0, 0.5, 6.0, 0.25, "s")).showIf(() -> !always.on());
        enchants = add(new Setting.Bool("Enchantments", true));
        hideVanilla = add(new Setting.Bool("Hide vanilla item name", true));
    }

    @Override
    protected void onEnable() {
        vanillaBefore = Minecraft.getMinecraft().gameSettings.heldItemTooltips;
    }

    @Override
    protected void onDisable() {
        if (vanillaBefore != null) Minecraft.getMinecraft().gameSettings.heldItemTooltips = vanillaBefore;
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return 24;
    }

    private float visibility() {
        Minecraft mc = Minecraft.getMinecraft();
        if (vanillaBefore != null) mc.gameSettings.heldItemTooltips = hideVanilla.on() ? false : vanillaBefore;
        ItemStack cur = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
        if (!ItemStack.areItemStacksEqual(cur, last) && (cur == null || last == null || cur.getItem() != last.getItem()
                || !cur.getDisplayName().equals(last.getDisplayName()))) {
            changed = System.currentTimeMillis();
        }
        last = cur == null ? null : cur.copy();
        if (cur == null) return 0f;
        if (always.on()) return 1f;
        float age = (System.currentTimeMillis() - changed) / 1000f, s = stay.f();
        if (age < 0.15f) return age / 0.15f;
        if (age > s) return Math.max(0f, 1f - (age - s) / 0.3f);
        return 1f;
    }

    @Override
    protected boolean hasContent() {
        return visibility() > 0.01f;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack st = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
        float a = editing ? 1f : visibility();
        if (st == null && editing) st = new ItemStack(Items.diamond_sword);
        if (st == null || a <= 0.01f) return;
        String name = st.getRarity().rarityColor + st.getDisplayName();
        StringBuilder en = new StringBuilder();
        if (enchants.on()) {
            Map<Integer, Integer> m = EnchantmentHelper.getEnchantments(st);
            for (Map.Entry<Integer, Integer> e : m.entrySet()) {
                Enchantment ench = Enchantment.getEnchantmentById(e.getKey());
                if (ench == null) continue;
                if (en.length() > 0) en.append(", ");
                en.append(ench.getTranslatedName(e.getValue()));
            }
        }
        String sub = en.toString();
        if (st.isItemStackDamageable()) {
            String d = (st.getMaxDamage() - st.getItemDamage()) + "/" + st.getMaxDamage();
            sub = sub.isEmpty() ? d : sub + "  ·  " + d;
        }
        if (Gfx.textWidth(sub) > 220) sub = mc.fontRendererObj.trimStringToWidth(sub, 214) + "..";
        w = Math.max(Gfx.textWidth(name), Gfx.textWidth(sub)) + 32;
        float slide = (1f - a) * 8f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0f, slide, 0f);
        if (a > 0.98f) panel(x, y, w, 24);
        else Gfx.roundedRect(x, y, w, 24, 4f, Gfx.alpha(0xFF0B0C11, bgOpacity.f() * a));
        Gfx.rect(x, y + 5, 2, 14, Gfx.alpha(accent(), a));
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(st, (int) x + 5, (int) y + 4);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        int al = Math.max(8, (int) (a * 255)) << 24;
        Gfx.text(name, x + 25, y + (sub.isEmpty() ? 8 : 3.5f), al | 0xFFFFFF);
        if (!sub.isEmpty()) Gfx.text(sub, x + 25, y + 13.5f, al | (LABEL & 0xFFFFFF));
        GlStateManager.popMatrix();
    }
}
