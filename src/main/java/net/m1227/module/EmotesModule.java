package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.vfx.Puppet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Emotes for your character: open the wheel (default the period key), pick one, and your
 * player model performs it (the camera can turn around to show it). Purely
 * visual and only on your screen: other players don't see emotes, and moving,
 * jumping, sneaking, attacking or taking damage ends the emote.
 */
public final class EmotesModule extends Module {
    public static EmotesModule INSTANCE;
    public static final String[] EMOTES = {"Wave", "Groove", "Clap", "Flex", "Salute", "Sit", "Spin", "Facepalm", "T-Pose", "Cheer", "Bow", "Laugh"};
    private static final boolean[] LOOPS = {false, true, true, false, false, true, true, false, true, true, false, true};

    private final Setting.Key wheelKey;
    private final Setting.Mode camera;
    private final Setting.Num speed;
    private int current = -1;
    private long start;
    private int savedView = -1;
    private final Puppet.Pose pose = new Puppet.Pose();
    private float lastHealth = -1f;

    public EmotesModule() {
        super("Emotes", "12 emotes for your character, from a wheel.", Category.ANIMATIONS, true);
        INSTANCE = this;
        // "." by default: B is already the emote key in several clients (Lunar and others). The setting was
        // renamed so configs that saved B pick up the new default.
        wheelKey = add(new Setting.Key("Emote key", Keyboard.KEY_PERIOD));
        camera = add(new Setting.Mode("Camera while emoting", "Front view", "Front view", "Back view", "Don't change"));
        speed = add(new Setting.Num("Speed", 1.0, 0.5, 2.0, 0.1, "x"));
    }

    public int wheelKey() {
        return wheelKey.code();
    }

    public boolean playing() {
        return current >= 0;
    }

    public void play(int i) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || i < 0 || i >= EMOTES.length) return;
        if (current < 0) savedView = mc.gameSettings.thirdPersonView;
        current = i;
        start = System.currentTimeMillis();
        if (camera.is("Front view")) mc.gameSettings.thirdPersonView = 2;
        else if (camera.is("Back view")) mc.gameSettings.thirdPersonView = 1;
        Notifier.post(Notifier.Kind.STATE, "Emote", EMOTES[i], Notifier.ACCENT, 1200);
    }

    public void stop() {
        if (current < 0) return;
        current = -1;
        Minecraft mc = Minecraft.getMinecraft();
        if (savedView >= 0 && !camera.is("Don't change")) mc.gameSettings.thirdPersonView = savedView;
        savedView = -1;
    }

    @Override
    protected void onDisable() {
        stop();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) {
            current = -1;
            return;
        }
        int k = wheelKey.code();
        if (k > 0 && mc.currentScreen == null && Keyboard.isKeyDown(k)) {
            mc.displayGuiScreen(new net.m1227.ui.EmoteWheel(this, k));
        }
        if (current < 0) {
            lastHealth = p.getHealth();
            return;
        }
        boolean moved = p.movementInput != null && (Math.abs(p.movementInput.moveForward) > 0.01f || Math.abs(p.movementInput.moveStrafe) > 0.01f
                || p.movementInput.jump || p.movementInput.sneak);
        boolean hurt = lastHealth > 0f && p.getHealth() < lastHealth - 0.01f;
        lastHealth = p.getHealth();
        float t = (System.currentTimeMillis() - start) / 1000f * speed.f();
        if (moved || hurt || mc.gameSettings.keyBindAttack.isKeyDown() || (!LOOPS[current] && t > 3.2f)) stop();
    }

    @SubscribeEvent
    public void onRender(RenderPlayerEvent.Pre e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (current < 0 || e.entityPlayer != mc.thePlayer) return;
        e.setCanceled(true);
        float t = (System.currentTimeMillis() - start) / 1000f * speed.f();
        pose(current, t, pose.reset());
        float yaw = mc.thePlayer.prevRenderYawOffset + (mc.thePlayer.renderYawOffset - mc.thePlayer.prevRenderYawOffset) * e.partialRenderTick;
        Puppet.render(mc.thePlayer, e.x, e.y, e.z, yaw, pose, e.partialRenderTick);
    }

    private static float sin(float t, float f) {
        return (float) Math.sin(t * f);
    }

    /** Fills p with emote i at time t (seconds). */
    public static void pose(int i, float t, Puppet.Pose p) {
        float in = Math.min(1f, t / 0.25f); // blend in from the idle pose
        switch (i) {
            case 0: // Wave
                p.rArm[0] = 165f * in;
                p.rArm[2] = (20f + 18f * sin(t, 10f)) * in;
                p.head[1] = -8f * in;
                break;
            case 1: // Groove
                p.body[1] = 14f * sin(t, 5f);
                p.lift = 0.06f * Math.abs(sin(t, 5f));
                p.rArm[0] = 45f + 40f * sin(t, 5f);
                p.lArm[0] = 45f - 40f * sin(t, 5f);
                p.rArm[2] = 15f;
                p.lArm[2] = 15f;
                p.rLeg[0] = 18f * sin(t, 5f);
                p.lLeg[0] = -18f * sin(t, 5f);
                p.head[2] = 8f * sin(t, 5f);
                break;
            case 2: { // Clap
                float c = Math.abs(sin(t, 8f));
                p.rArm[0] = 80f * in;
                p.lArm[0] = 80f * in;
                p.rArm[1] = (15f + 30f * c) * in;
                p.lArm[1] = (15f + 30f * c) * in;
                p.head[0] = 6f * sin(t, 8f);
                break;
            }
            case 3: // Flex
                p.rArm[2] = 120f * in;
                p.lArm[2] = 120f * in;
                p.rArm[0] = 20f * in + 8f * sin(t, 6f);
                p.lArm[0] = 20f * in + 8f * sin(t, 6f);
                p.body[0] = -4f * in;
                p.head[0] = 10f * in;
                break;
            case 4: // Salute
                p.rArm[0] = 145f * in;
                p.rArm[1] = 45f * in;
                p.rArm[2] = 10f * in;
                p.head[0] = 4f * in;
                break;
            case 5: // Sit
                p.lift = -0.56f * in;
                p.rLeg[0] = 90f * in;
                p.lLeg[0] = 90f * in;
                p.rLeg[2] = 8f * in;
                p.lLeg[2] = -8f * in;
                p.rArm[0] = 25f * in;
                p.lArm[0] = 25f * in;
                p.head[1] = 10f * sin(t, 0.8f);
                break;
            case 6: // Spin
                p.spin = t * 540f;
                p.rArm[2] = 85f * in;
                p.lArm[2] = 85f * in;
                p.head[0] = 10f;
                break;
            case 7: // Facepalm
                p.rArm[0] = 140f * in;
                p.rArm[1] = 35f * in;
                p.head[0] = -18f * in;
                p.head[1] = 4f * sin(t, 3f);
                break;
            case 8: // T-Pose
                p.rArm[2] = 90f * in;
                p.lArm[2] = 90f * in;
                break;
            case 9: // Cheer
                p.rArm[0] = 170f * in;
                p.lArm[0] = 170f * in;
                p.rArm[2] = 18f + 10f * sin(t, 9f);
                p.lArm[2] = 18f - 10f * sin(t, 9f);
                p.lift = 0.22f * Math.abs(sin(t, 6f));
                p.head[0] = 15f * in;
                break;
            case 10: { // Bow
                float b = t < 0.6f ? t / 0.6f : t < 2f ? 1f : Math.max(0f, 1f - (t - 2f) / 0.6f);
                b = b * b * (3f - 2f * b);
                p.body[0] = -45f * b;
                p.rArm[0] = 20f * b;
                p.lArm[0] = 20f * b;
                p.rArm[1] = 30f * b;
                p.lArm[1] = 30f * b;
                break;
            }
            case 11: // Laugh
                p.body[0] = 6f + 6f * sin(t, 14f);
                p.head[0] = 20f + 8f * sin(t, 14f);
                p.rArm[0] = 35f;
                p.lArm[0] = 35f;
                p.rArm[1] = 35f;
                p.lArm[1] = 35f;
                p.lift = 0.02f * Math.abs(sin(t, 14f));
                break;
            default:
                break;
        }
    }
}
