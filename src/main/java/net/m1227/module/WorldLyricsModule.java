package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.lyrics.Lyrics;
import net.m1227.lyrics.LyricsService;
import net.m1227.music.MusicAnalysis;
import net.m1227.ui.TtfFont;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * World Lyrics: the words of the song fly through the world around you while it plays, each one
 * appearing the moment it is sung (Enhanced LRC word times, or an estimate spread over the line).
 *
 * Motion styles: Drift (floats slowly through the air), Rise (climbs towards the sky), Fly past
 * (comes from ahead and passes beside you), Cross view (crosses your field of view), Pop (appears
 * and fades in place), Orbit (circles around you), Rain (falls from above), Beat burst (thrown
 * outwards, pushed again on every beat). At the end of its life a word can scatter into its letters.
 *
 * Rendering: real 3D billboards in the world (position, camera and perspective), depth-tested by
 * default so blocks in front hide them; words are only spawned in open air with a clear line from
 * your eyes, and a word that drifts into a solid block fades out at once. Performance: a fixed pool
 * (no allocation per frame), at most the chosen number of words, distance and behind-the-camera
 * culling, one GL state setup per frame. Uses the same lyrics and clock (with its sync offsets) as
 * Floating Lyrics; both can be on at once.
 */
public final class WorldLyricsModule extends Module {
    public static WorldLyricsModule INSTANCE;
    private static final int POOL = 160;
    public static final String[] PRESETS = {"Dreamy", "Concert", "Storm", "Galaxy", "Minimal", "Hype", "Custom"};

    private final Setting.Mode preset, motion, unit, direction, colorMode, weight;
    private final Setting.Num speed, minDist, maxDist, size, opacity, maxWords, life, intensity, beatPulse, renderDist;
    private final Setting.Col color1, color2;
    private final Setting.Bool scatter, depth, avoidBlocks, beatSync, upright;
    private final Setting.Text appliedPreset;

    private static final class W {
        boolean alive, letter;
        String text;
        double x, y, z, vx, vy, vz, ox, oz; // ox/oz: orbit centre offsets
        float age, life, size, phase, angle, angVel;
        int color;
        boolean blocked;
    }

    private final W[] pool = new W[POOL];
    private int lastLine = -2, lastWord = -1;
    private long lastT = -1;
    private long lastNanos;
    private int checkTick;
    private float prevBeat;
    // the words of the line being sung, split once per line (not every frame)
    private Lyrics cacheLyrics;
    private int cacheLine = -1;
    private String[] cacheWords;
    private long[] cacheTimes;

    public WorldLyricsModule() {
        super("World Lyrics", "Song words flying through the world around you, in sync with the music.", Category.MUSIC, false);
        INSTANCE = this;
        for (int i = 0; i < POOL; i++) pool[i] = new W();
        preset = add(new Setting.Mode("Preset", "Dreamy", PRESETS));
        appliedPreset = add(new Setting.Text("applied", "", 20, "")).showIf(() -> false);
        motion = add(new Setting.Mode("Motion", "Drift", "Drift", "Rise", "Fly past", "Cross view", "Pop", "Orbit", "Rain", "Beat burst"));
        unit = add(new Setting.Mode("Show", "Words", "Words", "Whole lines"));
        direction = add(new Setting.Mode("Where", "Around you", "Around you", "In front", "Behind you", "Left and right", "Above"));
        speed = add(new Setting.Num("Speed", 1.0, 0.1, 4.0, 0.1, "x"));
        minDist = add(new Setting.Num("Closest distance", 3, 1.5, 20, 0.5, " m"));
        maxDist = add(new Setting.Num("Farthest distance", 9, 3, 40, 0.5, " m"));
        size = add(new Setting.Num("Size", 1.0, 0.3, 4.0, 0.05, "x"));
        opacity = add(new Setting.Num("Opacity", 0.9, 0.1, 1.0, 0.05, ""));
        weight = add(new Setting.Mode("Font", "Medium", "Light", "Regular", "Medium", "Bold"));
        colorMode = add(new Setting.Mode("Color", "Gradient", "Single", "Gradient", "Rainbow", "Beat"));
        color1 = add(new Setting.Col("Color 1", 0xF2F0FF));
        color2 = add(new Setting.Col("Color 2", 0xB79CFF)).showIf(() -> !colorMode.is("Single") && !colorMode.is("Rainbow"));
        maxWords = add(new Setting.Num("Max words at once", 40, 4, POOL, 1, ""));
        life = add(new Setting.Num("Word lifetime", 4.0, 1.0, 12.0, 0.5, " s"));
        intensity = add(new Setting.Num("Intensity", 1.0, 0.2, 3.0, 0.1, "x"));
        beatSync = add(new Setting.Bool("Follow the beat", true));
        beatPulse = add(new Setting.Num("Beat pulse", 0.35, 0, 1.0, 0.05, "")).showIf(() -> beatSync.on());
        scatter = add(new Setting.Bool("Scatter into letters at the end", true));
        upright = add(new Setting.Bool("Keep words upright", true));
        depth = add(new Setting.Bool("Hidden behind blocks", true));
        avoidBlocks = add(new Setting.Bool("Stay out of blocks", true));
        renderDist = add(new Setting.Num("Max render distance", 48, 8, 128, 4, " m"));
        add(new Setting.Action("Clear words", this::clear));
    }

    public static boolean active() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    private void clear() {
        for (W w : pool) w.alive = false;
    }

    @Override
    protected void onDisable() {
        clear();
    }

    // ---------------------------------------------------------------- presets

    private void applyPreset(String p) {
        if (p.equals("Custom")) return;
        Setting<?>[] all = {motion, unit, direction, speed, minDist, maxDist, size, opacity, weight, colorMode, color1, color2, maxWords, life,
                intensity, beatSync, beatPulse, scatter, upright};
        for (Setting<?> s : all) s.reset();
        if (p.equals("Concert")) {
            motion.set("Fly past");
            direction.set("In front");
            size.setClamped(1.4);
            weight.set("Bold");
            colorMode.set("Beat");
            color1.setRgb(0xFFFFFF);
            color2.setRgb(0xFF5CA8);
            beatPulse.setClamped(0.6);
        } else if (p.equals("Storm")) {
            motion.set("Rain");
            direction.set("Above");
            speed.setClamped(1.6);
            maxWords.setClamped(80);
            life.setClamped(3);
            color1.setRgb(0x9FD8FF);
            color2.setRgb(0xE6F4FF);
        } else if (p.equals("Galaxy")) {
            motion.set("Orbit");
            colorMode.set("Rainbow");
            size.setClamped(1.2);
            life.setClamped(7);
            maxDist.setClamped(7);
            minDist.setClamped(4);
        } else if (p.equals("Minimal")) {
            motion.set("Pop");
            direction.set("In front");
            unit.set("Whole lines");
            colorMode.set("Single");
            scatter.set(false);
            maxWords.setClamped(6);
            weight.set("Light");
        } else if (p.equals("Hype")) {
            motion.set("Beat burst");
            size.setClamped(1.3);
            speed.setClamped(1.8);
            intensity.setClamped(1.6);
            weight.set("Bold");
            colorMode.set("Beat");
            color1.setRgb(0xFFC857);
            color2.setRgb(0xFF4D5E);
            beatPulse.setClamped(0.8);
        } else if (p.equals("Dreamy")) {
            motion.set("Drift");
            speed.setClamped(0.6);
            life.setClamped(6);
        }
    }

    // ---------------------------------------------------------------- spawning

    private W free() {
        int limit = maxWords.i(), alive = 0;
        W oldest = null, freeOne = null;
        for (W w : pool) {
            if (w.alive) {
                alive++;
                if (!w.letter && (oldest == null || w.age / w.life > oldest.age / oldest.life)) oldest = w;
            } else if (freeOne == null) freeOne = w;
        }
        if (alive >= limit || freeOne == null) {
            if (oldest == null) return null;
            oldest.alive = false; // reuse the oldest word
            return oldest;
        }
        return freeOne;
    }

    private int colorFor(float t) {
        String m = colorMode.get();
        if (m.equals("Single")) return color1.rgb();
        if (m.equals("Rainbow")) return ParticleSystem.rainbow(t);
        if (m.equals("Beat")) return ParticleSystem.mix(color1.rgb(), color2.rgb(), Math.min(1f, MusicAnalysis.INSTANCE.beat));
        return ParticleSystem.mix(color1.rgb(), color2.rgb(), (float) Math.random());
    }

    private void spawn(Minecraft mc, Entity view, String text, double time) {
        if (text == null || text.trim().isEmpty()) return;
        W w = free();
        if (w == null) return;
        double yaw = Math.toRadians(view.rotationYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw); // facing, horizontal
        double rx = -fz, rz = fx;
        double ex = view.posX, ey = view.posY + view.getEyeHeight(), ez = view.posZ;
        double d = minDist.get() + Math.random() * Math.max(0, maxDist.get() - minDist.get());
        double ang;
        String where = direction.get();
        if (where.equals("In front")) ang = (Math.random() - 0.5) * Math.PI * 0.8;
        else if (where.equals("Behind you")) ang = Math.PI + (Math.random() - 0.5) * Math.PI * 0.8;
        else if (where.equals("Left and right")) ang = (Math.random() < 0.5 ? -1 : 1) * (Math.PI / 2 + (Math.random() - 0.5) * 0.6);
        else ang = Math.random() * Math.PI * 2;
        double dirX = fx * Math.cos(ang) + rx * Math.sin(ang), dirZ = fz * Math.cos(ang) + rz * Math.sin(ang);
        double x = ex + dirX * d, z = ez + dirZ * d;
        double y = ey + (where.equals("Above") ? 3 + Math.random() * 3 : (Math.random() - 0.3) * 2.2);
        String m = motion.get();
        float sp = speed.f();
        double vx = 0, vy = 0, vz = 0;
        if (m.equals("Drift")) {
            vx = (Math.random() - 0.5) * 0.6;
            vy = 0.05 + Math.random() * 0.1;
            vz = (Math.random() - 0.5) * 0.6;
        } else if (m.equals("Rise")) {
            vy = 0.6 + Math.random() * 0.5;
        } else if (m.equals("Fly past")) {
            // start ahead, slightly to one side, fly backwards past the player
            double side = (Math.random() < 0.5 ? -1 : 1) * (1.4 + Math.random() * 1.6);
            x = ex + fx * (d + 6) + rx * side;
            z = ez + fz * (d + 6) + rz * side;
            y = ey + (Math.random() - 0.4) * 1.6;
            vx = -fx * 4.5;
            vz = -fz * 4.5;
        } else if (m.equals("Cross view")) {
            double s = Math.random() < 0.5 ? -1 : 1;
            x = ex + fx * d - rx * s * 7;
            z = ez + fz * d - rz * s * 7;
            vx = rx * s * 3.2;
            vz = rz * s * 3.2;
            vy = (Math.random() - 0.5) * 0.2;
        } else if (m.equals("Rain")) {
            y = ey + 5 + Math.random() * 4;
            vy = -2.2 - Math.random();
        } else if (m.equals("Beat burst")) {
            x = ex + dirX * 1.5;
            z = ez + dirZ * 1.5;
            vx = dirX * 3.5;
            vz = dirZ * 3.5;
            vy = 0.4 + Math.random() * 0.8;
        }
        // open air and a clear line from the eyes, else pull the word in front of the obstacle
        if (avoidBlocks.on()) {
            Vec3 from = new Vec3(ex, ey, ez);
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(from, new Vec3(x, y, z), false, true, false);
            if (hit != null && hit.hitVec != null) {
                double hd = hit.hitVec.distanceTo(from);
                if (hd < 1.5) return; // walled in: skip this word
                double k = (hd - 0.6) / Math.max(0.1, Math.sqrt((x - ex) * (x - ex) + (y - ey) * (y - ey) + (z - ez) * (z - ez)));
                x = ex + (x - ex) * k;
                y = ey + (y - ey) * k;
                z = ez + (z - ez) * k;
            }
        }
        w.alive = true;
        w.letter = false;
        w.text = text.trim();
        w.x = x;
        w.y = y;
        w.z = z;
        w.vx = vx * sp;
        w.vy = vy * sp;
        w.vz = vz * sp;
        w.ox = ex;
        w.oz = ez;
        w.age = 0f;
        w.life = life.f() * (m.equals("Fly past") || m.equals("Cross view") ? 0.8f : 1f);
        w.size = size.f() * (unit.is("Whole lines") ? 0.75f : 1f) * (0.85f + (float) Math.random() * 0.3f);
        w.phase = (float) (Math.random() * Math.PI * 2);
        w.angle = (float) Math.atan2(z - ez, x - ex);
        w.angVel = (float) ((0.35 + Math.random() * 0.2) * sp * (Math.random() < 0.5 ? -1 : 1));
        w.color = colorFor((float) time * 0.1f + (float) Math.random() * 0.2f);
        w.blocked = false;
    }

    /** A word breaking into its letters, each flying off on its own. */
    private void scatterLetters(W w) {
        int n = Math.min(14, w.text.length());
        for (int i = 0; i < n; i++) {
            char c = w.text.charAt(i);
            if (c == ' ') continue;
            W l = null;
            for (W o : pool) {
                if (!o.alive) {
                    l = o;
                    break;
                }
            }
            if (l == null) return;
            l.alive = true;
            l.letter = true;
            l.text = String.valueOf(c);
            double off = (i - n / 2.0) * 0.18 * w.size;
            l.x = w.x + off;
            l.y = w.y;
            l.z = w.z;
            l.vx = (Math.random() - 0.5) * 2.4;
            l.vy = Math.random() * 1.6;
            l.vz = (Math.random() - 0.5) * 2.4;
            l.age = 0f;
            l.life = 0.9f + (float) Math.random() * 0.6f;
            l.size = w.size;
            l.color = w.color;
            l.phase = (float) (Math.random() * 6);
            l.blocked = false;
        }
    }

    /** Spawns the words sung since the last frame. */
    private void follow(Minecraft mc, Entity view) {
        FloatingLyricsModule fl = FloatingLyricsModule.INSTANCE;
        Lyrics ly = LyricsService.lyrics();
        long t = fl == null ? -1 : fl.timeMs();
        if (ly == null || !ly.synced || t < 0) {
            lastLine = -2;
            lastT = -1;
            return;
        }
        if (lastT >= 0 && (t < lastT - 300 || t > lastT + 5000)) lastLine = -2; // seek: start over from here
        lastT = t;
        int li = ly.indexAt(t);
        if (li < 0) return;
        if (lastLine == -2) {
            lastLine = li;
            lastWord = 1 << 30; // do not replay the line we jumped into
            return;
        }
        float rate = intensity.f();
        for (int line = Math.max(0, lastLine); line <= li; line++) {
            int word = line == lastLine ? lastWord : -1;
            Lyrics.Line l = ly.lines.get(line);
            if (!l.blank()) {
                if (unit.is("Whole lines")) {
                    if (word < 0) {
                        spawn(mc, view, l.text, t / 1000.0);
                        word = 0;
                    }
                } else {
                    if (cacheLyrics != ly || cacheLine != line) {
                        cacheLyrics = ly;
                        cacheLine = line;
                        cacheWords = l.text.trim().split("\\s+");
                        cacheTimes = ly.wordTimes(line, cacheWords);
                    }
                    String[] words = cacheWords;
                    long[] times = cacheTimes;
                    for (int k = Math.max(0, word + 1); k < words.length; k++) {
                        if (times[k] > t) break;
                        int copies = rate >= 1f ? Math.round(rate) : (Math.random() < rate ? 1 : 0);
                        for (int c = 0; c < copies; c++) spawn(mc, view, words[k], t / 1000.0 + c);
                        word = k;
                    }
                }
            }
            lastLine = line;
            lastWord = word;
        }
    }

    // ---------------------------------------------------------------- update + draw

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (mc.theWorld == null || view == null || CinematicModule.hideLyrics()) return;
        if (!preset.get().equals(appliedPreset.get())) {
            applyPreset(preset.get());
            appliedPreset.set(preset.get());
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        MusicAnalysis.INSTANCE.update();
        follow(mc, view);
        float beat = beatSync.on() ? MusicAnalysis.INSTANCE.beat : 0f;
        boolean onBeat = beat > 0.6f && prevBeat <= 0.6f;
        prevBeat = beat;
        boolean check = (++checkTick & 3) == 0;
        String m = motion.get();
        int alive = 0;
        for (W w : pool) {
            if (!w.alive) continue;
            w.age += dt;
            if (w.age >= w.life) {
                w.alive = false;
                if (!w.letter && scatter.on() && !w.blocked) scatterLetters(w);
                continue;
            }
            alive++;
            if (w.letter) {
                w.vy -= 1.2 * dt;
            } else if (m.equals("Orbit")) {
                w.angle += w.angVel * dt;
                double r = Math.sqrt((w.x - w.ox) * (w.x - w.ox) + (w.z - w.oz) * (w.z - w.oz));
                w.x = w.ox + Math.cos(w.angle) * r;
                w.z = w.oz + Math.sin(w.angle) * r;
                w.y += Math.sin(w.age * 1.5 + w.phase) * 0.15 * dt;
            } else if (m.equals("Drift")) {
                w.vx += Math.sin(w.age * 0.9 + w.phase) * 0.12 * dt;
                w.vz += Math.cos(w.age * 0.7 + w.phase) * 0.12 * dt;
            } else if (m.equals("Beat burst")) {
                w.vx *= 1 - Math.min(1, dt * 1.6);
                w.vz *= 1 - Math.min(1, dt * 1.6);
                w.vy *= 1 - Math.min(1, dt * 1.6);
                if (onBeat) {
                    double l = Math.sqrt(w.vx * w.vx + w.vz * w.vz) + 1e-3;
                    w.vx += w.vx / l * 1.8 * speed.f();
                    w.vz += w.vz / l * 1.8 * speed.f();
                    w.vy += 0.6;
                }
            }
            if (!m.equals("Orbit") || w.letter) {
                w.x += w.vx * dt;
                w.y += w.vy * dt;
                w.z += w.vz * dt;
            }
            // drifted into a solid block: fade out quickly instead of showing through it
            if (avoidBlocks.on() && check && !w.blocked) {
                Material mat = mc.theWorld.getBlockState(new BlockPos(w.x, w.y, w.z)).getBlock().getMaterial();
                if (mat.isSolid()) {
                    w.blocked = true;
                    w.life = Math.min(w.life, w.age + 0.25f);
                }
            }
        }
        if (alive > 0) draw(mc, view, e.partialTicks, beat);
    }

    private void draw(Minecraft mc, Entity view, float pt, float beat) {
        RenderManager rm = mc.getRenderManager();
        TtfFont f = TtfFont.get(weight.get(), 64);
        if (f == null) return;
        double ex = rm.viewerPosX, ey = rm.viewerPosY, ez = rm.viewerPosZ;
        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt + view.getEyeHeight();
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        Vec3 look = view.getLook(pt);
        double maxD2 = renderDist.get() * renderDist.get();
        float op = opacity.f();
        float blocksPerPx = 0.22f / f.ascentPx();
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG), light = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        float lx0 = OpenGlHelper.lastBrightnessX, ly0 = OpenGlHelper.lastBrightnessY;
        try {
            GlStateManager.disableFog();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            if (depth.on()) GlStateManager.enableDepth();
            else GlStateManager.disableDepth();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            for (W w : pool) {
                if (!w.alive) continue;
                double dx = w.x - camX, dy = w.y - camY, dz = w.z - camZ;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > maxD2 || d2 < 0.36) continue;
                double dl = Math.sqrt(d2);
                if ((dx * look.xCoord + dy * look.yCoord + dz * look.zCoord) / dl < -0.1) continue; // behind the camera
                float a = w.age < 0.35f ? w.age / 0.35f : 1f;
                float out = (w.life - w.age) / Math.min(0.8f, w.life * 0.4f);
                a = Math.min(a, Math.min(1f, out));
                float far = (float) Math.min(1.0, (renderDist.get() - dl) / 6.0);
                a *= op * Math.max(0f, far);
                if (a < 0.02f) continue;
                float pop = motion.is("Pop") && !w.letter ? 0.6f + 0.4f * Math.min(1f, w.age * 4f) : 1f;
                float s = w.size * pop * (1f + beat * beatPulse.f() * 0.5f) * blocksPerPx;
                int argb = ((int) (a * 255) << 24) | (w.color & 0xFFFFFF);
                float tw = f.widthPx(w.text);
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(w.x - ex, w.y - ey, w.z - ez);
                    GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
                    if (!upright.on()) GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
                    if (w.letter) GlStateManager.rotate(w.age * 240f + w.phase * 50f, 0f, 0f, 1f);
                    GlStateManager.scale(-s, -s, s);
                    f.draw(w.text, -tw / 2f, -f.ascentPx() / 2f, 1f, argb);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            if (cull) GlStateManager.enableCull();
            if (light) GlStateManager.enableLighting();
            if (fog) GlStateManager.enableFog();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx0, ly0);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }
}
