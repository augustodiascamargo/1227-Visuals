package net.m1227.module;

import java.util.Collection;
import net.m1227.client.Reflect;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Centered Inventory: keeps the inventory (and the creative inventory) in the middle of the
 * screen when you have potion effects.
 *
 * The cause of the "inventory moves to the side" problem is Minecraft itself: with any active
 * effect, InventoryEffectRenderer sets guiLeft = 160 + (width - xSize - 200) / 2, i.e. 60 px to
 * the right of the centre, to make room for the effect list on the left. This puts guiLeft back
 * to the centre right after the screen is laid out (buttons the screen created move with it) and
 * draws the effect list itself where there is room: left of the inventory, otherwise right of
 * it, otherwise as a column of icons. No matrix is moved to hide it: the screen's own layout
 * value is corrected, so clicks, slots and tooltips all line up.
 */
public final class CenteredInventoryModule extends Module {
    private static final ResourceLocation INV = new ResourceLocation("textures/gui/container/inventory.png");
    private final Setting.Mode effects;

    public CenteredInventoryModule() {
        super("Centered Inventory", "Potion effects no longer push the inventory to the side.", Category.GAMEPLAY, true);
        effects = add(new Setting.Mode("Effect list", "Beside the inventory", "Beside the inventory", "Icons only", "Hidden"));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (!(e.gui instanceof InventoryEffectRenderer)) return;
        GuiContainer g = (GuiContainer) e.gui;
        Integer left = Reflect.get(g, GuiContainer.class, int.class, 2), xSize = Reflect.get(g, GuiContainer.class, int.class, 0);
        if (left == null || xSize == null) return;
        int centred = (g.width - xSize) / 2;
        int delta = centred - left;
        if (delta == 0) return;
        if (!Reflect.set(g, GuiContainer.class, int.class, 2, centred)) return;
        for (GuiButton b : e.buttonList) b.xPosition += delta;
        // the creative inventory's search box is laid out from guiLeft too
        if (g instanceof net.minecraft.client.gui.inventory.GuiContainerCreative) {
            net.minecraft.client.gui.GuiTextField f = Reflect.get(g, net.minecraft.client.gui.inventory.GuiContainerCreative.class,
                    net.minecraft.client.gui.GuiTextField.class, 0);
            if (f != null) f.xPosition += delta;
        }
        // we draw the list ourselves (vanilla would draw it at guiLeft - 124, off screen when centred)
        Reflect.set(g, InventoryEffectRenderer.class, boolean.class, 0, Boolean.FALSE);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof InventoryEffectRenderer) || effects.is("Hidden")) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        Collection<PotionEffect> list = mc.thePlayer.getActivePotionEffects();
        if (list.isEmpty()) return;
        GuiContainer g = (GuiContainer) e.gui;
        Integer left = Reflect.get(g, GuiContainer.class, int.class, 2), top = Reflect.get(g, GuiContainer.class, int.class, 3),
                xSize = Reflect.get(g, GuiContainer.class, int.class, 0);
        if (left == null || top == null || xSize == null) return;
        boolean icons = effects.is("Icons only");
        int w = icons ? 22 : 120;
        int x;
        if (left - w - 4 >= 2) x = left - w - 4;
        else if (left + xSize + 4 + w <= g.width - 2) x = left + xSize + 4;
        else {
            icons = true;
            w = 22;
            x = left - w - 2 >= 0 ? left - w - 2 : left + xSize + 2;
        }
        int step = icons ? 22 : list.size() > 5 ? Math.max(18, 132 / (list.size() - 1)) : 24;
        int y = top;
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        for (PotionEffect pe : list) {
            Potion p = Potion.potionTypes[pe.getPotionID()];
            Gfx.roundedRect(x, y, w, 20, 4, 0xC0101218);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableBlend();
            if (p != null && p.hasStatusIcon()) {
                mc.getTextureManager().bindTexture(INV);
                int idx = p.getStatusIconIndex();
                g.drawTexturedModalRect(x + 1, y + 1, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18);
            }
            if (!icons && p != null) {
                String name = I18n.format(p.getName());
                if (pe.getAmplifier() > 0) name += " " + (pe.getAmplifier() + 1);
                Gfx.text(Gfx.trim(name, w - 26), x + 22, y + 2.5f, 0xFFFFFFFF);
                Gfx.textScaled(Potion.getDurationString(pe), x + 22, y + 12, 0.8f, 0xFFB8BCC8);
            }
            y += step;
        }
        GlStateManager.enableDepth();
    }
}
