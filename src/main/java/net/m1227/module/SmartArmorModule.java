package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Smart Armor: in your inventory and in chests, pieces of armor that would protect you better
 * than what you are wearing in that slot get a green frame and an arrow (and a small "+" with
 * the difference while you hover them).
 *
 * The comparison uses Minecraft 1.8.9's own numbers: armor points of the piece, plus the
 * Protection enchantment (its damage reduction points, the "EPF"), and a piece that is about to
 * break counts as worse. Specialised enchantments (Fire, Blast, Projectile Protection) only
 * count a little, since they help in fewer situations. Only shows, never moves items.
 */
public final class SmartArmorModule extends Module {
    private final Setting.Bool showWorse, needMargin;
    private final Setting.Col color;

    public SmartArmorModule() {
        super("Smart Armor", "Highlights armor that is better than what you wear, in inventories and chests.", Category.GAMEPLAY, false);
        color = add(new Setting.Col("Better color", 0x3DDC84));
        showWorse = add(new Setting.Bool("Dim worse pieces", false));
        needMargin = add(new Setting.Bool("Ignore tiny differences", true));
    }

    /** Protection value of a piece (higher = better), in Minecraft's damage-reduction terms. */
    public static double score(ItemStack s) {
        if (s == null || !(s.getItem() instanceof ItemArmor)) return -1;
        ItemArmor a = (ItemArmor) s.getItem();
        // armor points: each point = 4% reduction (before toughness, which 1.8.9 does not have)
        double v = a.damageReduceAmount * 4.0;
        // Protection: EPF = floor((6 + level^2) * 0.75 / 3); each EPF ~ 4% (capped overall in game)
        int prot = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, s);
        if (prot > 0) v += Math.floor((6 + prot * prot) * 0.75 / 3.0) * 4.0;
        int special = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireProtection.effectId, s)
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.blastProtection.effectId, s)
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.projectileProtection.effectId, s);
        v += special * 0.75;
        v += EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, s) * 0.3;
        if (s.isItemStackDamageable()) {
            double left = 1.0 - s.getItemDamage() / (double) Math.max(1, s.getMaxDamage());
            if (left < 0.1) v *= 0.5; // about to break
        }
        return v;
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof GuiContainer)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        GuiContainer g = (GuiContainer) e.gui;
        // guiLeft / guiTop: the 3rd and 4th int fields of GuiContainer (after xSize, ySize)
        Integer gl = net.m1227.client.Reflect.get(g, GuiContainer.class, int.class, 2);
        Integer gt = net.m1227.client.Reflect.get(g, GuiContainer.class, int.class, 3);
        if (gl == null || gt == null) return;
        int left = gl, top = gt;
        ItemStack[] worn = mc.thePlayer.inventory.armorInventory; // 0 boots .. 3 helmet
        double margin = needMargin.on() ? 1.0 : 0.01;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 300); // above the item icons
        GlStateManager.disableDepth();
        for (Slot s : g.inventorySlots.inventorySlots) {
            ItemStack st = s.getStack();
            if (st == null || !(st.getItem() instanceof ItemArmor)) continue;
            int type = ((ItemArmor) st.getItem()).armorType; // 0 helmet .. 3 boots
            ItemStack cur = worn[3 - type];
            if (cur == st) continue;
            // the armor slots of your own inventory show what you wear: skip them
            if (s.inventory == mc.thePlayer.inventory && s.getSlotIndex() >= 36) continue;
            double d = score(st) - score(cur);
            float x = left + s.xDisplayPosition, y = top + s.yDisplayPosition;
            if (d > margin) {
                int c = 0xFF000000 | color.rgb();
                Gfx.roundedOutline(x - 1, y - 1, 18, 18, 2, 1f, c);
                Gfx.quad(x + 12, y + 5, x + 15, y + 1, x + 18, y + 5, x + 15, y + 5, c);
                Gfx.rect(x + 14, y + 4, 2, 4, c);
                if (e.mouseX >= x && e.mouseY >= y && e.mouseX < x + 16 && e.mouseY < y + 16) {
                    String t = "+" + Math.round(d * 10) / 10.0 + "% protection";
                    Gfx.roundedRect(x + 18, y - 12, Gfx.textWidth(t) + 6, 11, 2, 0xE0101218);
                    Gfx.text(t, x + 21, y - 10, c);
                }
            } else if (showWorse.on() && d < -margin) {
                Gfx.rect(x, y, 16, 16, 0x70000000);
            }
        }
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }
}
