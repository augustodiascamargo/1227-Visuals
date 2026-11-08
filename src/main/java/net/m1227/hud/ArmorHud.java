package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Worn armour and held item with durability. */
public final class ArmorHud extends HudModule {
    private final Setting.Bool held, percent;
    private final Setting.Mode layout;

    public ArmorHud() {
        super("Armor", "Armor and held item durability.", 0.40, 0.80);
        layout = add(new Setting.Mode("Layout", "Row", "Row", "Column"));
        held = add(new Setting.Bool("Held item", true));
        percent = add(new Setting.Bool("Durability as %", false));
    }

    private int count() {
        return 4 + (held.on() ? 1 : 0);
    }

    @Override
    public float width() {
        return layout.is("Row") ? count() * 22 + 4 : 56;
    }

    @Override
    public float height() {
        return layout.is("Row") ? 30 : count() * 18 + 4;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack[] s = new ItemStack[5];
        if (mc.thePlayer != null) {
            for (int i = 0; i < 4; i++) s[i] = mc.thePlayer.inventory.armorInventory[3 - i];
            if (held.on()) s[4] = mc.thePlayer.getHeldItem();
        }
        if (editing) {
            if (s[0] == null) s[0] = new ItemStack(Items.diamond_helmet);
            if (s[1] == null) s[1] = new ItemStack(Items.diamond_chestplate);
            if (s[2] == null) s[2] = new ItemStack(Items.diamond_leggings);
            if (s[3] == null) s[3] = new ItemStack(Items.diamond_boots);
            if (held.on() && s[4] == null) s[4] = new ItemStack(Items.diamond_sword);
        }
        panel(x, y, width(), height());
        boolean row = layout.is("Row");
        for (int i = 0; i < count(); i++) {
            float ix = row ? x + 4 + i * 22 : x + 3, iy = row ? y + 3 : y + 3 + i * 18;
            ItemStack st = s[i];
            if (st == null) continue;
            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();
            mc.getRenderItem().renderItemAndEffectIntoGUI(st, (int) ix, (int) iy);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.popMatrix();
            String txt = null;
            int col = 0xFFFFFFFF;
            if (st.isItemStackDamageable()) {
                int max = st.getMaxDamage(), left = max - st.getItemDamage();
                float f = left / (float) max;
                txt = percent.on() ? Math.round(f * 100) + "%" : String.valueOf(left);
                col = f > 0.5f ? 0xFF3DDC84 : f > 0.2f ? 0xFFFFC857 : 0xFFFF5A6A;
            } else if (st.stackSize > 1) {
                txt = String.valueOf(st.stackSize);
            }
            if (txt != null) {
                if (row) Gfx.textScaled(txt, ix + 8 - Gfx.textWidth(txt) * 0.35f, iy + 18, 0.7f, col);
                else Gfx.text(txt, ix + 20, iy + 4, col);
            }
        }
        GlStateManager.enableBlend();
    }
}
