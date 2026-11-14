package net.m1227.module;

import java.lang.reflect.Field;
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
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Centered Inventory: keeps the inventory (and the creative inventory) in the middle of the
 * screen when you have potion effects.
 *
 * The cause of the "inventory moves to the side" problem is Minecraft itself: with any active
 * effect, InventoryEffectRenderer sets guiLeft = 160 + (width - xSize - 200) / 2, i.e. 60 px to
 * the right of the centre, to make room for the effect list on the left. This puts guiLeft back
 * to the centre after layout and reapplies it because updateScreen() recalculates it every tick.
 * Buttons created by the screen are shifted once during init, and the effect list is drawn where there is room: left of the inventory, otherwise right of
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
        Integer oldLeft = readInt(g, GuiContainer.class, "guiLeft", "field_147003_i", 2);
        Integer xSize = readInt(g, GuiContainer.class, "xSize", "field_146999_f", 0);
        if (oldLeft == null || xSize == null) return;

        int centred = (g.width - xSize) / 2;
        int delta = centred - oldLeft;
        keepCentered(g);

        // Buttons are positioned during initGui, so move them once alongside guiLeft.
        if (delta != 0) {
            for (GuiButton b : e.buttonList) b.xPosition += delta;
            // The creative inventory search box is positioned from guiLeft too.
            if (g instanceof net.minecraft.client.gui.inventory.GuiContainerCreative) {
                net.minecraft.client.gui.GuiTextField f = Reflect.get(g,
                        net.minecraft.client.gui.inventory.GuiContainerCreative.class,
                        net.minecraft.client.gui.GuiTextField.class, 0);
                if (f != null) f.xPosition += delta;
            }
        }
    }

    /**
     * InventoryEffectRenderer.updateScreen() recalculates guiLeft every tick while potion
     * effects are active. An InitGuiEvent-only adjustment therefore gets overwritten almost
     * immediately. Re-apply the layout at the end of the client tick, before drawing, and before
     * mouse handling so both rendering and slot hit-testing use the same coordinates.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof InventoryEffectRenderer) {
            keepCentered((GuiContainer) mc.currentScreen);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawPre(GuiScreenEvent.DrawScreenEvent.Pre e) {
        if (e.gui instanceof InventoryEffectRenderer) keepCentered((GuiContainer) e.gui);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre e) {
        if (e.gui instanceof InventoryEffectRenderer) keepCentered((GuiContainer) e.gui);
    }

    private void keepCentered(GuiContainer g) {
        Integer xSize = readInt(g, GuiContainer.class, "xSize", "field_146999_f", 0);
        if (xSize == null) return;
        int centred = (g.width - xSize) / 2;
        writeInt(g, GuiContainer.class, "guiLeft", "field_147003_i", 2, centred);
        // Prevent vanilla from drawing its own effect column at the old guiLeft - 124 offset.
        writeBoolean(g, InventoryEffectRenderer.class,
                "hasActivePotionEffects", "field_147045_u", 0, false);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if (!(e.gui instanceof InventoryEffectRenderer) || effects.is("Hidden")) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        Collection<PotionEffect> list = mc.thePlayer.getActivePotionEffects();
        if (list.isEmpty()) return;
        GuiContainer g = (GuiContainer) e.gui;
        Integer left = readInt(g, GuiContainer.class, "guiLeft", "field_147003_i", 2);
        Integer top = readInt(g, GuiContainer.class, "guiTop", "field_147009_r", 3);
        Integer xSize = readInt(g, GuiContainer.class, "xSize", "field_146999_f", 0);
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

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
                // Try the other runtime mapping name.
            }
        }
        return null;
    }

    private static Integer readInt(Object target, Class<?> owner, String mcpName,
                                   String srgName, int fallbackIndex) {
        try {
            Field field = findField(owner, mcpName, srgName);
            if (field != null) return Integer.valueOf(field.getInt(target));
        } catch (Throwable ignored) {
            // Fall back to the type-ordered accessor for unusual mappings.
        }
        return Reflect.get(target, owner, int.class, fallbackIndex);
    }

    private static void writeInt(Object target, Class<?> owner, String mcpName,
                                 String srgName, int fallbackIndex, int value) {
        try {
            Field field = findField(owner, mcpName, srgName);
            if (field != null) {
                field.setInt(target, value);
                return;
            }
        } catch (Throwable ignored) {
            // Fall back to the type-ordered accessor for unusual mappings.
        }
        Reflect.set(target, owner, int.class, fallbackIndex, Integer.valueOf(value));
    }

    private static void writeBoolean(Object target, Class<?> owner, String mcpName,
                                     String srgName, int fallbackIndex, boolean value) {
        try {
            Field field = findField(owner, mcpName, srgName);
            if (field != null) {
                field.setBoolean(target, value);
                return;
            }
        } catch (Throwable ignored) {
            // Fall back to the type-ordered accessor for unusual mappings.
        }
        Reflect.set(target, owner, boolean.class, fallbackIndex, Boolean.valueOf(value));
    }

}
