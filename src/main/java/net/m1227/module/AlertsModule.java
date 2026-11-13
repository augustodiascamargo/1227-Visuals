package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Alerts: a notification (and an optional sound) when something needs your attention. Low
 * health, low food, armor or held item about to break, few healing potions left, inventory full,
 * your target lost, and a kill confirmation. Each alert fires once when the condition starts and
 * waits a few seconds before it can fire again, so nothing spams. Low health can also tint the
 * screen edges. Reads only your own state.
 *
 * Ender pearl cooldown is not offered: Minecraft 1.8.9 has no pearl cooldown, so there is
 * nothing real to show.
 */
public final class AlertsModule extends Module {
    private final Setting.Bool health, food, armor, durability, potions, invFull, target, kill, sound, vignette;
    private final Setting.Num healthAt, foodAt, armorAt, potionsAt;
    private final boolean[] was = new boolean[8];
    private final long[] lastFire = new long[8];
    private EntityLivingBase lastTarget;
    private long lastTargetSeen;

    public AlertsModule() {
        super("Alerts", "Low health, food, armor, potions, full inventory, target lost and kills.", Category.GAMEPLAY, false);
        health = add(new Setting.Bool("Low health", true));
        healthAt = add(new Setting.Num("Health below", 6, 1, 19, 1, " hp")).showIf(() -> health.on());
        vignette = add(new Setting.Bool("Red screen edges at low health", true)).showIf(() -> health.on());
        food = add(new Setting.Bool("Low food", true));
        foodAt = add(new Setting.Num("Food below", 6, 1, 19, 1, "")).showIf(() -> food.on());
        armor = add(new Setting.Bool("Armor about to break", true));
        durability = add(new Setting.Bool("Held item about to break", true));
        armorAt = add(new Setting.Num("Durability below", 10, 1, 50, 1, "%")).showIf(() -> armor.on() || durability.on());
        potions = add(new Setting.Bool("Few healing potions", false));
        potionsAt = add(new Setting.Num("Potions below", 2, 1, 16, 1, "")).showIf(() -> potions.on());
        invFull = add(new Setting.Bool("Inventory full", false));
        target = add(new Setting.Bool("Target lost", false));
        kill = add(new Setting.Bool("Kill confirmation", true));
        sound = add(new Setting.Bool("Play a sound", true));
    }

    private void fire(int i, boolean now, String title, String detail, int color) {
        long t = System.currentTimeMillis();
        if (now && !was[i] && t - lastFire[i] > 5000L) {
            lastFire[i] = t;
            Notifier.post(Notifier.Kind.STATE, title, detail, color, 2600);
            if (sound.on()) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.thePlayer != null) mc.thePlayer.playSound("note.pling", 0.6f, i == 0 ? 0.6f : 1.2f);
            }
        }
        was[i] = now;
    }

    private static float left(ItemStack s) {
        if (s == null || !s.isItemStackDamageable()) return 1f;
        return 1f - s.getItemDamage() / (float) Math.max(1, s.getMaxDamage());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null || p.isDead) {
            java.util.Arrays.fill(was, false);
            return;
        }
        if (health.on()) fire(0, p.getHealth() < healthAt.f(), "Low health", Math.round(p.getHealth()) + " hp left", Notifier.BAD);
        if (food.on()) fire(1, p.getFoodStats().getFoodLevel() < foodAt.f(), "Low food", "Eat something soon", Notifier.WARN);
        if (armor.on()) {
            boolean low = false;
            String which = "";
            String[] names = {"Boots", "Leggings", "Chestplate", "Helmet"};
            for (int i = 0; i < 4; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (s != null && left(s) * 100 < armorAt.f()) {
                    low = true;
                    which = names[i];
                }
            }
            fire(2, low, "Armor breaking", which + " is almost broken", Notifier.WARN);
        }
        if (durability.on()) {
            ItemStack h = p.getHeldItem();
            fire(3, h != null && h.isItemStackDamageable() && left(h) * 100 < armorAt.f(), "Item breaking",
                    (h == null ? "" : h.getDisplayName()) + " is almost broken", Notifier.WARN);
        }
        if (potions.on()) {
            int n = 0;
            boolean any = false;
            for (ItemStack s : p.inventory.mainInventory) {
                if (s != null && s.getItem() instanceof ItemPotion) {
                    any = true;
                    java.util.List<net.minecraft.potion.PotionEffect> fx = ((ItemPotion) s.getItem()).getEffects(s);
                    if (fx != null) {
                        for (net.minecraft.potion.PotionEffect pe : fx) {
                            if (pe.getPotionID() == net.minecraft.potion.Potion.heal.id) {
                                n += s.stackSize;
                                break;
                            }
                        }
                    }
                }
            }
            fire(4, any && n < potionsAt.f(), "Few potions", n + " healing potion(s) left", Notifier.WARN);
        }
        if (invFull.on()) {
            boolean full = p.inventory.getFirstEmptyStack() < 0;
            fire(5, full, "Inventory full", "New items will stay on the ground", Notifier.INFO);
        }
        if (target.on()) {
            EntityLivingBase t = CombatTracker.target(1500L);
            if (t != null) {
                lastTarget = t;
                lastTargetSeen = System.currentTimeMillis();
                was[6] = false;
            } else if (lastTarget != null && System.currentTimeMillis() - lastTargetSeen > 1500L) {
                boolean lost = !lastTarget.isDead && lastTarget.getHealth() > 0;
                if (lost) fire(6, true, "Target lost", lastTarget.getName(), Notifier.INFO);
                lastTarget = null;
            }
        }
    }

    @SubscribeEvent
    public void onKill(CombatTracker.KillEvent e) {
        if (!kill.on()) return;
        lastTarget = null;
        was[7] = false;
        fire(7, true, "Kill", e.target.getName(), Notifier.GOOD);
        was[7] = false;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !health.on() || !vignette.on()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.getHealth() >= healthAt.f() || mc.thePlayer.isDead) return;
        float k = 1f - mc.thePlayer.getHealth() / healthAt.f();
        float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
        int c = Gfx.alpha(0xFFFF2A3A, 0.35f * k * pulse);
        float b = Math.max(18, h * 0.12f);
        Gfx.gradient(0, 0, w, b, c, 0x00FF2A3A);
        Gfx.gradient(0, h - b, w, b, 0x00FF2A3A, c);
        Gfx.gradientH(0, 0, b, h, c, 0x00FF2A3A);
        Gfx.gradientH(w - b, 0, b, h, 0x00FF2A3A, c);
    }
}
