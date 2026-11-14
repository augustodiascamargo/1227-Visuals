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
 * visual. Other players see your emotes only if they use 1227 too and the server runs the 1227
 * Relay plugin (1227 Link); moving, jumping, sneaking, attacking or taking damage ends the emote.
 */
public final class EmotesModule extends Module {
    public static EmotesModule INSTANCE;
    public static final String[] EMOTES = {"Wave", "Groove", "Clap", "Flex", "Salute", "Sit", "Spin", "Facepalm", "T-Pose", "Cheer", "Bow", "Laugh",
            // dances (second page of the wheel)
            "Shuffle", "Robot", "Disco", "Headbang", "Glide", "Hype", "Breakdance", "Swing", "Sprinkler", "Pump It"};
    private static final boolean[] LOOPS = {false, true, true, false, false, true, true, false, true, true, false, true,
            true, true, true, true, true, true, true, true, true, true};
    public static final String[] PAGES = {"Emotes", "Dances"};
    private static final int[] PAGE_START = {0, 12, 22};

    public static int pageStart(int page) {
        return PAGE_START[page];
    }

    public static int pageSize(int page) {
        return PAGE_START[page + 1] - PAGE_START[page];
    }

    private final Setting.Key wheelKey;
    private final Setting.Mode camera;
    private final Setting.Num speed;
    private int current = -1;
    private long start;
    private int savedView = -1;
    private final Puppet.Pose pose = new Puppet.Pose();
    private float lastHealth = -1f;

    public EmotesModule() {
        super("Emotes", "12 emotes and 10 dances for your character, from a wheel.", Category.ANIMATIONS, true);
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
        net.m1227.social.SocialLink.INSTANCE.localEmote(i); // other 1227 players see it (1227 Link)
    }

    /** Whether emote i repeats until stopped. */
    public static boolean loops(int i) {
        return i >= 0 && i < LOOPS.length && LOOPS[i];
    }

    public void stop() {
        if (current < 0) return;
        current = -1;
        net.m1227.social.SocialLink.INSTANCE.localStop();
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
        if (k != 0 && mc.currentScreen == null && net.m1227.core.InputManager.isDown(k)) {
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
            // ---------------------------------------------------------------- dances
            case 12: { // Shuffle: heel-toe steps sliding side to side
                float b = sin(t, 9f), s = sin(t, 4.5f);
                p.rLeg[0] = 22f * b;
                p.lLeg[0] = -22f * b;
                p.rLeg[2] = 10f + 8f * s;
                p.lLeg[2] = -10f + 8f * s;
                p.body[2] = 5f * s;
                p.lift = 0.05f * Math.abs(b);
                p.rArm[0] = 30f + 25f * b;
                p.lArm[0] = 30f - 25f * b;
                p.rArm[1] = 25f;
                p.lArm[1] = 25f;
                p.head[2] = -6f * s;
                break;
            }
            case 13: { // Robot: stepped, held poses
                float st = (float) Math.floor(t * 3f);
                float a = (st % 4f) < 2f ? 1f : -1f, b = (st % 2f) < 1f ? 1f : -1f;
                p.rArm[0] = 90f;
                p.lArm[0] = 90f;
                p.rArm[1] = 25f * a;
                p.lArm[1] = 25f * a;
                p.rArm[2] = 10f + 10f * b;
                p.lArm[2] = 10f - 10f * b;
                p.head[1] = 35f * a;
                p.body[1] = 12f * a;
                p.rLeg[0] = 6f * b;
                p.lLeg[0] = -6f * b;
                break;
            }
            case 14: { // Disco: pointing up and down across the body
                float s = sin(t, 4f);
                p.rArm[0] = 90f + 75f * s;
                p.rArm[2] = 25f - 20f * s;
                p.lArm[0] = 20f;
                p.lArm[2] = 20f;
                p.lArm[1] = 40f;
                p.body[2] = -7f * s;
                p.body[1] = 10f * s;
                p.lift = 0.04f * Math.abs(sin(t, 8f));
                p.rLeg[0] = 10f * sin(t, 8f);
                p.head[0] = -8f * s;
                break;
            }
            case 15: { // Headbang
                float b = sin(t, 13f);
                p.head[0] = 22f + 22f * b;
                p.body[0] = -10f - 8f * b;
                p.rArm[0] = 60f + 15f * b;
                p.lArm[0] = 60f + 15f * b;
                p.rArm[1] = 30f;
                p.lArm[1] = 30f;
                p.lift = -0.06f;
                p.rLeg[0] = -12f;
                p.lLeg[0] = 12f;
                break;
            }
            case 16: { // Glide: smooth side-to-side wave through the body
                float s = sin(t, 3f), c = sin(t + 0.4f, 3f);
                p.body[2] = 9f * s;
                p.head[2] = -12f * c;
                p.rArm[2] = 70f + 20f * s;
                p.lArm[2] = 70f - 20f * s;
                p.rArm[0] = 15f * c;
                p.lArm[0] = -15f * c;
                p.rLeg[2] = 12f + 6f * s;
                p.lLeg[2] = -12f + 6f * s;
                p.lift = 0.03f * c;
                break;
            }
            case 17: { // Hype: jumping with fists pumping alternately
                float b = sin(t, 7f);
                p.lift = 0.30f * Math.abs(b);
                p.rArm[0] = 150f + 25f * b;
                p.lArm[0] = 150f - 25f * b;
                p.rArm[2] = 15f;
                p.lArm[2] = 15f;
                p.rLeg[0] = -15f * Math.abs(b);
                p.lLeg[0] = 15f * Math.abs(b);
                p.head[0] = -12f;
                break;
            }
            case 18: { // Breakdance: a crouched spin with kicking legs
                p.spin = t * 420f;
                p.lift = -0.45f * in;
                p.body[0] = -25f * in;
                p.rArm[0] = 40f;
                p.lArm[0] = 40f;
                p.rArm[2] = 35f;
                p.lArm[2] = 35f;
                p.rLeg[0] = 70f + 40f * sin(t, 7f);
                p.lLeg[0] = 20f - 30f * sin(t, 7f);
                p.rLeg[2] = 30f;
                p.lLeg[2] = -20f;
                break;
            }
            case 19: { // Swing: arms swinging together, hips bouncing
                float s = sin(t, 5f);
                p.rArm[0] = 50f * s;
                p.lArm[0] = 50f * s;
                p.rArm[2] = 12f;
                p.lArm[2] = 12f;
                p.body[1] = 18f * s;
                p.lift = 0.05f * Math.abs(sin(t, 10f)) - 0.04f;
                p.rLeg[0] = -10f * s;
                p.lLeg[0] = 10f * s;
                p.head[1] = -10f * s;
                break;
            }
            case 20: { // Sprinkler: hand behind the head, the other arm sweeping in steps
                float st = (float) Math.floor(t * 6f) % 8f;
                p.lArm[0] = 150f;
                p.lArm[2] = 50f;
                p.lArm[1] = -30f;
                p.rArm[0] = 90f;
                p.rArm[1] = -40f + st * 10f;
                p.body[1] = -15f + st * 4f;
                p.head[1] = -20f + st * 5f;
                p.rLeg[0] = 10f * sin(t, 12f);
                break;
            }
            case 21: { // Pump It: arms punching up together on the beat
                float b = Math.abs(sin(t, 6f));
                p.rArm[0] = 100f + 70f * b;
                p.lArm[0] = 100f + 70f * b;
                p.rArm[2] = 20f;
                p.lArm[2] = 20f;
                p.lift = 0.08f * b;
                p.head[0] = -10f * b;
                p.rLeg[0] = 8f * b;
                p.lLeg[0] = -8f * b;
                break;
            }
            default:
                break;
        }
    }
}
