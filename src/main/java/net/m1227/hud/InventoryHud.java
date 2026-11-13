package net.m1227.hud;

import java.util.ArrayList;
import java.util.List;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/**
 * Inventory HUD (item counter): how many golden apples, ender pearls, arrows, blocks, potions...
 * you carry, with their icons. Pick the items, extra ones by id, the layout, and a warning colour
 * when a count runs low. Counts your whole inventory (hotbar included); reads only.
 */
public final class InventoryHud extends HudModule {
    private final Setting.Bool gapples, pearls, arrows, blocks, healing, potions, food, snowballs, hideZero, icons;
    private final Setting.Text extra;
    private final Setting.Mode layout;
    private final Setting.Num low;

    private static final class Entry {
        final ItemStack icon;
        final int count;

        Entry(ItemStack icon, int count) {
            this.icon = icon;
            this.count = count;
        }
    }

    private List<Entry> entries = new ArrayList<Entry>();
    private long at;

    public InventoryHud() {
        super("Inventory Counter", "Counts golden apples, pearls, arrows, blocks, potions and more.", 0.86, 0.45);
        layout = add(new Setting.Mode("Layout", "Column", "Column", "Row"));
        icons = add(new Setting.Bool("Item icons", true));
        gapples = add(new Setting.Bool("Golden apples", true));
        pearls = add(new Setting.Bool("Ender pearls", true));
        arrows = add(new Setting.Bool("Arrows", true));
        blocks = add(new Setting.Bool("Blocks", true));
        healing = add(new Setting.Bool("Healing potions", true));
        potions = add(new Setting.Bool("All potions", false));
        food = add(new Setting.Bool("Food", false));
        snowballs = add(new Setting.Bool("Snowballs and eggs", false));
        extra = add(new Setting.Text("Extra item ids", "", 120, "e.g. 46, 259, minecraft:web"));
        hideZero = add(new Setting.Bool("Hide items you don't have", true));
        low = add(new Setting.Num("Warn when below", 2, 0, 32, 1, ""));
    }

    private static boolean healing(ItemStack s) {
        if (!(s.getItem() instanceof ItemPotion)) return false;
        List<PotionEffect> fx = ((ItemPotion) s.getItem()).getEffects(s);
        if (fx == null) return false;
        for (PotionEffect e : fx) {
            if (e.getPotionID() == Potion.heal.id || e.getPotionID() == Potion.regeneration.id) return true;
        }
        return false;
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - at < 250L) return; // a few times a second is plenty
        at = now;
        List<Entry> out = new ArrayList<Entry>();
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack[] inv = mc.thePlayer == null ? new ItemStack[0] : mc.thePlayer.inventory.mainInventory;
        if (gapples.on()) add(out, new ItemStack(Items.golden_apple), count(inv, s -> s.getItem() == Items.golden_apple));
        if (pearls.on()) add(out, new ItemStack(Items.ender_pearl), count(inv, s -> s.getItem() == Items.ender_pearl));
        if (arrows.on()) add(out, new ItemStack(Items.arrow), count(inv, s -> s.getItem() == Items.arrow));
        if (blocks.on()) add(out, new ItemStack(net.minecraft.init.Blocks.wool), count(inv, s -> s.getItem() instanceof ItemBlock));
        if (healing.on()) add(out, new ItemStack(Items.potionitem, 1, 16421), count(inv, InventoryHud::healing));
        if (potions.on()) add(out, new ItemStack(Items.potionitem, 1, 8193), count(inv, s -> s.getItem() instanceof ItemPotion));
        if (food.on()) add(out, new ItemStack(Items.cooked_beef), count(inv, s -> s.getItem() instanceof net.minecraft.item.ItemFood && s.getItem() != Items.golden_apple));
        if (snowballs.on()) add(out, new ItemStack(Items.snowball), count(inv, s -> s.getItem() == Items.snowball || s.getItem() == Items.egg));
        for (String id : extra.get().split(",")) {
            id = id.trim();
            if (id.isEmpty()) continue;
            Item it = null;
            try {
                it = Item.getItemById(Integer.parseInt(id));
            } catch (NumberFormatException ex) {
                it = Item.getByNameOrId(id);
            }
            if (it == null) continue;
            final Item fit = it;
            add(out, new ItemStack(fit), count(inv, s -> s.getItem() == fit));
        }
        entries = out;
    }

    private void add(List<Entry> out, ItemStack icon, int n) {
        if (n == 0 && hideZero.on()) return;
        out.add(new Entry(icon, n));
    }

    private interface Test {
        boolean ok(ItemStack s);
    }

    private static int count(ItemStack[] inv, Test t) {
        int n = 0;
        for (ItemStack s : inv) {
            if (s != null && t.ok(s)) n += s.stackSize;
        }
        return n;
    }

    @Override
    public float width() {
        refresh();
        int n = Math.max(1, entries.size());
        return layout.is("Row") ? n * 34 + 4 : 48;
    }

    @Override
    public float height() {
        int n = Math.max(1, entries.size());
        return layout.is("Row") ? 22 : n * 18 + 4;
    }

    @Override
    protected boolean hasContent() {
        refresh();
        return !entries.isEmpty();
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        refresh();
        List<Entry> list = entries;
        if (editing && list.isEmpty()) {
            list = new ArrayList<Entry>();
            list.add(new Entry(new ItemStack(Items.golden_apple), 6));
            list.add(new Entry(new ItemStack(Items.ender_pearl), 1));
            list.add(new Entry(new ItemStack(Items.arrow), 32));
        }
        panel(x, y, width(), height());
        Minecraft mc = Minecraft.getMinecraft();
        boolean row = layout.is("Row");
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            float ix = row ? x + 3 + i * 34 : x + 3, iy = row ? y + 3 : y + 3 + i * 18;
            if (icons.on()) {
                GlStateManager.pushMatrix();
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableDepth();
                mc.getRenderItem().renderItemAndEffectIntoGUI(e.icon, (int) ix, (int) iy);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();
                GlStateManager.popMatrix();
            }
            int col = e.count <= low.i() ? 0xFFFF5A6A : e.count <= low.i() * 2 ? 0xFFFFC857 : 0xFFFFFFFF;
            Gfx.text(String.valueOf(e.count), ix + (icons.on() ? 19 : 2), iy + 4, col);
        }
        GlStateManager.enableBlend();
    }
}
