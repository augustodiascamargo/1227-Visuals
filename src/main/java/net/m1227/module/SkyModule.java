package net.m1227.module;

import java.util.Random;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.SkyPreset;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * 30 dynamic skies (see {@link SkyPreset}). Installed through Forge's sky
 * renderer hook, so it replaces the vanilla sky only while enabled.
 * Switching presets cross-fades: the gradient blends and the old layers
 * fade out while the new ones fade in.
 */
public final class SkyModule extends Module {
    public static SkyModule INSTANCE;

    private static final int MAXL = 32, MAXSEG = 96, STARS = 420, NEB = 12, CLOUDS = 26, CODE = 56;
    private static final int E0 = 0, A1 = 1, F1 = 2, P1 = 3, S1 = 4, A2 = 5, F2 = 6, P2 = 7, S2 = 8,
            PHI0 = 9, SPAN = 10, B = 11, F3 = 12, P3 = 13, S3 = 14, NP = 15;
    private static final float[][] PARAM = new float[MAXL][NP];
    private static final float[] STAR = new float[STARS * 5]; // x, y, z, phase, size
    private static final float[] NEBP = new float[NEB * 4];   // azimuth, elevation, size, mix
    private static final float[] CLOUD = new float[CLOUDS * 4];
    private static final float[] CODEP = new float[CODE * 3];  // azimuth, speed, offset
    private static final int GAL = 900;
    private static final float[] GALP = new float[GAL * 4];    // azimuth, offset, colour mix, alpha
    private static final double TAU = Math.PI * 2.0;

    static {
        Random r = new Random(7L);
        for (int k = 0; k < MAXL; k++) {
            float[] q = PARAM[k];
            q[E0] = 0.25f + r.nextFloat() * 0.8f;
            q[A1] = 0.35f + r.nextFloat() * 0.45f;
            q[F1] = 1f + r.nextFloat() * 1.5f;
            q[P1] = (float) (r.nextFloat() * TAU);
            q[S1] = (r.nextBoolean() ? 1f : -1f) * (0.08f + r.nextFloat() * 0.1f);
            q[A2] = 0.08f + r.nextFloat() * 0.18f;
            q[F2] = 3f + r.nextFloat() * 2f;
            q[P2] = (float) (r.nextFloat() * TAU);
            q[S2] = (r.nextBoolean() ? 1f : -1f) * (0.1f + r.nextFloat() * 0.12f);
            q[PHI0] = (float) (k * TAU / MAXL + r.nextFloat() * 0.6f);
            q[SPAN] = 0.9f + r.nextFloat() * 1.6f;
            q[B] = 0.2f + r.nextFloat() * 0.45f;
            q[F3] = 1.5f + r.nextFloat() * 1.5f;
            q[P3] = (float) (r.nextFloat() * TAU);
            q[S3] = (r.nextBoolean() ? 1f : -1f) * (0.06f + r.nextFloat() * 0.1f);
        }
        for (int i = 0; i < STARS; i++) {
            double a = r.nextDouble() * TAU, y = r.nextDouble() * 1.1 - 0.1;
            double rr = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            STAR[i * 5] = (float) (Math.cos(a) * rr);
            STAR[i * 5 + 1] = (float) y;
            STAR[i * 5 + 2] = (float) (Math.sin(a) * rr);
            STAR[i * 5 + 3] = (float) (r.nextDouble() * TAU);
            STAR[i * 5 + 4] = r.nextFloat();
        }
        for (int i = 0; i < NEB; i++) {
            NEBP[i * 4] = (float) (r.nextDouble() * TAU);
            NEBP[i * 4 + 1] = 0.15f + r.nextFloat() * 1.0f;
            NEBP[i * 4 + 2] = 18f + r.nextFloat() * 26f;
            NEBP[i * 4 + 3] = r.nextFloat();
        }
        for (int i = 0; i < CLOUDS; i++) {
            CLOUD[i * 4] = (float) (r.nextDouble() * TAU);
            CLOUD[i * 4 + 1] = 0.04f + r.nextFloat() * 0.38f;
            CLOUD[i * 4 + 2] = 12f + r.nextFloat() * 16f;
            CLOUD[i * 4 + 3] = r.nextFloat();
        }
        Random g = new Random(11L);
        for (int i = 0; i < GAL; i++) {
            GALP[i * 4] = (float) (g.nextDouble() * TAU);
            GALP[i * 4 + 1] = (float) (g.nextGaussian() * 0.09);
            GALP[i * 4 + 2] = g.nextFloat();
            GALP[i * 4 + 3] = 0.3f + 0.6f * g.nextFloat();
        }
        for (int i = 0; i < CODE; i++) {
            CODEP[i * 3] = (float) (i * TAU / CODE + r.nextFloat() * 0.05);
            CODEP[i * 3 + 1] = 0.25f + r.nextFloat() * 0.45f;
            CODEP[i * 3 + 2] = r.nextFloat() * 3f;
        }
    }

    private final Setting.Mode preset;
    private final Setting.Num speed, brightness, starDensity, tintAmount, transition;
    private final Setting.Col tint;
    private final Setting.Bool showBody, fogMatch;
    private final IRenderHandler renderer = new Renderer();

    private SkyPreset shown, previous;
    private float mix = 1f;
    private final float[] uv = new float[4];
    private final float[] qa = new float[3], qb = new float[3];
    private final Random rr = new Random();
    private final Random flashRng = new Random();
    private double nextFlash, flashTime = -10;
    private int flashSeed;

    // scratch for flow lines
    private final float[] px = new float[MAXSEG + 1], py = new float[MAXSEG + 1], pz = new float[MAXSEG + 1];
    private final float[] sx = new float[MAXSEG + 1], sy = new float[MAXSEG + 1], sz = new float[MAXSEG + 1];
    private final float[] env = new float[MAXSEG + 1];

    public SkyModule() {
        super("Dynamic Skies", "58 animated skies with smooth transitions.", Category.SKY, false);
        INSTANCE = this;
        preset = add(new Setting.Mode("Sky", "Night Flow", SkyPreset.names()));
        speed = add(new Setting.Num("Animation speed", 1.0, 0.1, 3.0, 0.1, "x"));
        brightness = add(new Setting.Num("Brightness", 1.0, 0.2, 1.5, 0.05, "x"));
        starDensity = add(new Setting.Num("Star density", 1.0, 0.0, 2.0, 0.1, "x"));
        tint = add(new Setting.Col("Tint color", 0x8B5CFF));
        tintAmount = add(new Setting.Num("Tint strength", 0.0, 0.0, 1.0, 0.05, ""));
        transition = add(new Setting.Num("Transition time", 1.5, 0.2, 5.0, 0.1, "s"));
        showBody = add(new Setting.Bool("Sun / moon / planet", true));
        fogMatch = add(new Setting.Bool("Fog matches sky", true));
        picture = add(new Setting.Mode("Picture sky", "Off", pictureNames()));
        pictureSpin = add(new Setting.Num("Picture rotation", 0.3, 0.0, 3.0, 0.05, "\u00b0/s")).showIf(() -> !picture.is("Off"));
        pictureEffects = add(new Setting.Bool("Sky effects over the picture", false)).showIf(() -> !picture.is("Off"));
        add(new Setting.Action("Open pictures folder", () -> {
            final java.io.File d = pictureDir();
            d.mkdirs();
            Thread th = new Thread(() -> {
                try {
                    java.awt.Desktop.getDesktop().open(d);
                } catch (Throwable ignored) {
                    // no desktop integration available
                }
            }, "1227-open-folder");
            th.setDaemon(true);
            th.start();
        }));
        add(new Setting.Action("Reload pictures", () -> {
            picture.setOptions(pictureNames());
            pictureLoaded = null;
            net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Picture sky",
                    (picture.options.length - 1) + " picture(s) found", net.m1227.core.Notifier.GOOD, 1800);
        }));
        // "Off" = only the sky's own layer (the cloud skies have one); any other choice replaces it
        shaderStyle = add(new Setting.Mode("Shader layer (GLSL)", "Off", net.m1227.vfx.SkyShader.STYLES));
        shaderStrength = add(new Setting.Num("Shader strength", 0.8, 0.1, 1.5, 0.05, "x")).showIf(() -> !shaderStyle.is("Off"));
        shaderPalette = add(new Setting.Mode("Shader colors", "Match sky", "Match sky", "Custom")).showIf(() -> !shaderStyle.is("Off"));
        shaderA = add(new Setting.Col("Shader color 1", 0x8B5CFF)).showIf(() -> !shaderStyle.is("Off") && shaderPalette.is("Custom"));
        shaderB = add(new Setting.Col("Shader color 2", 0x2DD4FF)).showIf(() -> !shaderStyle.is("Off") && shaderPalette.is("Custom"));
        shaderC = add(new Setting.Col("Shader color 3", 0xFF4FD8)).showIf(() -> !shaderStyle.is("Off") && shaderPalette.is("Custom"));
    }

    private final Setting.Mode shaderStyle, shaderPalette, picture;
    private Setting.Num pictureSpin;
    private Setting.Bool pictureEffects;

    // ------------------------------------------------------------ picture sky
    // Your own images as the sky: equirectangular panoramas (2:1) work best. PNG or JPG files in
    // config/1227-skies. Loaded once into a texture; the old one is freed when you switch.
    private String pictureLoaded;
    private net.minecraft.util.ResourceLocation pictureTex;

    private static java.io.File pictureDir() {
        return new java.io.File(net.m1227.core.ConfigManager.dir(), "1227-skies");
    }

    private static String[] pictureNames() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add("Off");
        java.io.File[] fs = pictureDir().listFiles();
        if (fs != null) {
            java.util.Arrays.sort(fs);
            for (java.io.File f : fs) {
                String n = f.getName().toLowerCase();
                if (f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) out.add(f.getName());
            }
        }
        return out.toArray(new String[0]);
    }

    private boolean ensurePicture(Minecraft mc) {
        String want = picture.get();
        if (want.equals("Off")) return false;
        if (want.equals(pictureLoaded)) return pictureTex != null;
        if (pictureTex != null) mc.getTextureManager().deleteTexture(pictureTex);
        pictureTex = null;
        pictureLoaded = want;
        try {
            java.io.File f = new java.io.File(pictureDir(), want);
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            if (img == null) throw new java.io.IOException("not an image");
            int max = Math.min(8192, net.minecraft.client.Minecraft.getGLMaximumTextureSize());
            if (img.getWidth() > max || img.getHeight() > max) {
                float k = Math.min(max / (float) img.getWidth(), max / (float) img.getHeight());
                java.awt.image.BufferedImage sm = new java.awt.image.BufferedImage(Math.max(1, (int) (img.getWidth() * k)),
                        Math.max(1, (int) (img.getHeight() * k)), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = sm.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, sm.getWidth(), sm.getHeight(), null);
                g.dispose();
                img = sm;
            }
            pictureTex = mc.getTextureManager().getDynamicTextureLocation("m1227_picture_sky", new net.minecraft.client.renderer.texture.DynamicTexture(img));
        } catch (Exception ex) {
            net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Picture sky", "Could not load " + want, net.m1227.core.Notifier.BAD, 2200);
        }
        return pictureTex != null;
    }

    /** Textured sphere around the camera (radius 100), panorama mapped by longitude/latitude. */
    private void pictureSphere(Minecraft mc, double t) {
        mc.getTextureManager().bindTexture(pictureTex);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        float b = Math.min(1.5f, brightness.f());
        GL11.glColor4f(b, b, b, 1f);
        float rot = (float) ((t * pictureSpin.f() / 360.0) % 1.0);
        int stacks = 24, slices = 48;
        float r = 100f;
        for (int i = 0; i < stacks; i++) {
            double la0 = Math.PI * (i / (double) stacks - 0.5), la1 = Math.PI * ((i + 1) / (double) stacks - 0.5);
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= slices; j++) {
                double lo = 2 * Math.PI * j / slices;
                float u = j / (float) slices + rot;
                GL11.glTexCoord2f(u, 1f - (i + 1) / (float) stacks);
                GL11.glVertex3f((float) (Math.cos(la1) * Math.cos(lo) * r), (float) (Math.sin(la1) * r), (float) (Math.cos(la1) * Math.sin(lo) * r));
                GL11.glTexCoord2f(u, 1f - i / (float) stacks);
                GL11.glVertex3f((float) (Math.cos(la0) * Math.cos(lo) * r), (float) (Math.sin(la0) * r), (float) (Math.cos(la0) * Math.sin(lo) * r));
            }
            GL11.glEnd();
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
    private final Setting.Num shaderStrength;
    private final Setting.Col shaderA, shaderB, shaderC;

    /** GLSL layer between the gradient and the geometric layers. */
    private void shaderLayer(SkyPreset p, float w, double t) {
        int style = shaderStyle.is("Off") ? p.shader : java.util.Arrays.asList(net.m1227.vfx.SkyShader.STYLES).indexOf(shaderStyle.get());
        if (style <= 0) return;
        int a, b, c;
        boolean own = shaderStyle.is("Off");
        if (shaderPalette.is("Custom") && !own) {
            a = shaderA.rgb();
            b = shaderB.rgb();
            c = shaderC.rgb();
        } else {
            a = tinted(p.aurora > 0 ? p.auroraA : p.nebA);
            b = tinted(p.aurora > 0 ? p.auroraB : p.nebB);
            c = tinted(p.lineGlow);
            if (own && p.shA >= 0) {
                a = tinted(p.shA);
                b = tinted(p.shB);
                c = tinted(p.shC);
            } else if (net.m1227.vfx.SkyShader.opaque(style)) {
                // clouds: lit top, shadowed underside, horizon blends into the sky's own horizon
                a = tinted(p.cloudColor);
                b = tinted(net.m1227.vfx.ParticleSystem.mix(p.cloudColor, p.top, 0.6f));
                c = tinted(p.horizon);
            }
        }
        if (net.m1227.vfx.SkyShader.opaque(style)) GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        else GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        net.m1227.vfx.SkyShader.draw(style, t, a, b, c, w * (own ? 1f : shaderStrength.f()) * Math.min(1.2f, brightness.f()));
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Used by colour presets: tints every layer. */
    public static void applyPalette(int c1, int c2) {
        if (INSTANCE == null) return;
        INSTANCE.tint.setRgb(c1);
        INSTANCE.tintAmount.set(0.35);
    }

    // ----------------------------------------------------------- install

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        WorldClient w = Minecraft.getMinecraft().theWorld;
        if (w != null && w.provider.getSkyRenderer() != renderer) w.provider.setSkyRenderer(renderer);
    }

    @Override
    protected void onDisable() {
        WorldClient w = Minecraft.getMinecraft().theWorld;
        if (w != null && w.provider.getSkyRenderer() == renderer) w.provider.setSkyRenderer(null);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFog(EntityViewRenderEvent.FogColors e) {
        if (!fogMatch.on() || shown == null) return;
        int c = tinted(blend(previous == null ? shown.horizon : previous.horizon, shown.horizon, mix));
        float b = Math.min(1f, brightness.f());
        e.red = ((c >> 16) & 255) / 255f * b;
        e.green = ((c >> 8) & 255) / 255f * b;
        e.blue = (c & 255) / 255f * b;
    }

    /** Colour at the top of the 1227 sky on screen right now (with tint and brightness), or -1 when off. */
    public static int zenithColor() {
        SkyModule s = INSTANCE;
        if (s == null || !s.isEnabled() || s.shown == null) return -1;
        int c = s.tinted(blend(s.previous == null ? s.shown.top : s.previous.top, s.shown.top, s.mix));
        float b = Math.min(1f, s.brightness.f());
        return ((int) (((c >> 16) & 255) * b) << 16) | ((int) (((c >> 8) & 255) * b) << 8) | (int) ((c & 255) * b);
    }

    // ------------------------------------------------------------ colour

    private static int blend(int a, int b, float t) {
        return ParticleSystem.mix(a, b, t);
    }

    private int tinted(int c) {
        float k = tintAmount.f();
        return k <= 0f ? c : ParticleSystem.mix(c, tint.rgb(), k * 0.6f);
    }

    private void color(int c, float a) {
        c = tinted(c);
        float b = brightness.f();
        GL11.glColor4f(Math.min(1f, ((c >> 16) & 255) / 255f * b), Math.min(1f, ((c >> 8) & 255) / 255f * b),
                Math.min(1f, (c & 255) / 255f * b), Math.min(1f, a));
    }

    private static void dir(double az, double el, double r, float[] out) {
        out[0] = (float) (Math.cos(el) * Math.cos(az) * r);
        out[1] = (float) (Math.sin(el) * r);
        out[2] = (float) (Math.cos(el) * Math.sin(az) * r);
    }

    // ------------------------------------------------------------ render

    private final class Renderer extends IRenderHandler {
        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mc) {
            SkyPreset target = SkyPreset.byName(preset.get());
            ParticleSystem ps = ParticleSystem.INSTANCE;
            float dt = Math.max(ps.dt, 0.016f);
            if (shown == null) shown = target;
            if (target != shown) {
                previous = shown;
                shown = target;
                mix = 0f;
            }
            if (mix < 1f) {
                mix = Math.min(1f, mix + dt / transition.f());
                if (mix >= 1f) previous = null;
            }
            float m = mix * mix * (3f - 2f * mix);
            double t = ps.time * speed.f();
            float sunAngle = world.getCelestialAngle(partialTicks);

            GlStateManager.disableAlpha();
            GlStateManager.disableFog();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            boolean pic = ensurePicture(mc);
            if (pic) pictureSphere(mc, ps.time);
            else gradient(previous, shown, m);
            if (!pic || pictureEffects.on()) {
                shaderLayer(shown, 1f, t);
                if (previous != null) layers(previous, 1f - m, t, sunAngle);
                layers(shown, m, t, sunAngle);
                stormFlash(previous, shown, m, t);
            }

            GL11.glColor4f(1f, 1f, 1f, 1f);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableBlend();
            GlStateManager.enableCull();
            GlStateManager.enableAlpha();
            GlStateManager.enableFog();
            GlStateManager.enableTexture2D();
            GlStateManager.depthMask(true);
        }
    }

    private void layers(SkyPreset p, float w, double t, float sunAngle) {
        if (w <= 0.01f) return;
        float dens = starDensity.f();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (p.clouds > 0f) clouds(p, w, t, true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        if (p.glow > 0f) horizonGlow(p, w);
        if (p.stars > 0f && dens > 0f) stars(p, w, t, dens);
        if (p.galaxy > 0f) galaxy(p, w, t, dens);
        if (p.nebula > 0f) nebula(p, w, t);
        if (p.aurora > 0) aurora(p, w, t);
        if (p.lines > 0) flowLines(p, w, t);
        if (p.shooting > 0f) shootingStars(p, w, t);
        if (p.codeRain) codeRain(w, t);
        if (p.grid) grid(p, w, t);
        if (showBody.on() && p.celestial != SkyPreset.NONE) body(p, w, t, sunAngle);
        if (p.clouds > 0f) {
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            clouds(p, w, t, false);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        }
        GlStateManager.disableTexture2D();
    }

    private void gradient(SkyPreset a, SkyPreset b, float m) {
        int top = a == null ? b.top : blend(a.top, b.top, m);
        int hor = a == null ? b.horizon : blend(a.horizon, b.horizon, m);
        int bot = a == null ? b.bottom : blend(a.bottom, b.bottom, m);
        final int lat = 16, lon = 32;
        float r = 95f;
        for (int i = 0; i < lat; i++) {
            double a0 = Math.PI * (-0.5 + (double) i / lat), a1 = Math.PI * (-0.5 + (double) (i + 1) / lat);
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= lon; j++) {
                double az = TAU * j / lon;
                gradColor(a0, top, hor, bot);
                GL11.glVertex3d(Math.cos(a0) * Math.cos(az) * r, Math.sin(a0) * r, Math.cos(a0) * Math.sin(az) * r);
                gradColor(a1, top, hor, bot);
                GL11.glVertex3d(Math.cos(a1) * Math.cos(az) * r, Math.sin(a1) * r, Math.cos(a1) * Math.sin(az) * r);
            }
            GL11.glEnd();
        }
    }

    private void gradColor(double el, int top, int hor, int bot) {
        int c;
        if (el < 0) c = blend(hor, bot, (float) Math.min(1.0, -el / 0.35));
        else c = blend(hor, top, (float) Math.pow(Math.min(1.0, el / 1.2), 0.6));
        color(c, 1f);
    }

    private void horizonGlow(SkyPreset p, float w) {
        float r = 90f;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int j = 0; j <= 48; j++) {
            double az = TAU * j / 48;
            color(p.glowColor, 0.55f * p.glow * w);
            GL11.glVertex3d(Math.cos(az) * r, -2, Math.sin(az) * r);
            color(p.glowColor, 0f);
            GL11.glVertex3d(Math.cos(0.32) * Math.cos(az) * r, Math.sin(0.32) * r, Math.cos(0.32) * Math.sin(az) * r);
        }
        GL11.glEnd();
    }

    private void stars(SkyPreset p, float w, double t, float dens) {
        int n = Math.min(STARS, Math.round(STARS * p.stars * dens));
        for (int pass = 0; pass < 2; pass++) {
            GL11.glPointSize(pass == 0 ? 1.5f : 2.6f);
            GL11.glBegin(GL11.GL_POINTS);
            for (int i = 0; i < n; i++) {
                boolean big = STAR[i * 5 + 4] > 0.85f;
                if (big != (pass == 1)) continue;
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.7 + STAR[i * 5 + 3]);
                color(p.starColor, (0.25f + 0.65f * tw) * w);
                GL11.glVertex3f(STAR[i * 5] * 85f, STAR[i * 5 + 1] * 85f, STAR[i * 5 + 2] * 85f);
            }
            GL11.glEnd();
        }
    }

    /** A tilted great circle of dense stars with a soft glow band. */
    private void galaxy(SkyPreset p, float w, double t, float dens) {
        double tilt = 0.6, rot = t * 0.004;
        GL11.glPointSize(1.3f);
        GL11.glBegin(GL11.GL_POINTS);
        int n = Math.min(GAL, Math.round(700 * p.galaxy * Math.min(1.5f, dens)));
        for (int i = 0; i < n; i++) {
            double az = GALP[i * 4] + rot, off = GALP[i * 4 + 1];
            double x = Math.cos(az), y = Math.sin(az) * Math.sin(tilt) + off, z = Math.sin(az) * Math.cos(tilt);
            double l = Math.sqrt(x * x + y * y + z * z);
            color(blend(p.galaxyColor, 0xFFFFFF, GALP[i * 4 + 2]), GALP[i * 4 + 3] * w);
            GL11.glVertex3d(x / l * 84, y / l * 84, z / l * 84);
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        ParticleSystem.INSTANCE.bindAtlas();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 18; i++) {
            double az = i * TAU / 18 + rot;
            double x = Math.cos(az), y = Math.sin(az) * Math.sin(tilt), z = Math.sin(az) * Math.cos(tilt);
            billboard(x, y, z, 82f, 14f, 0f, Sprite.GLOW, p.galaxyColor, 0.16f * p.galaxy * w);
        }
        GL11.glEnd();
        GlStateManager.disableTexture2D();
    }

    private void nebula(SkyPreset p, float w, double t) {
        GlStateManager.enableTexture2D();
        ParticleSystem.INSTANCE.bindAtlas();
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < NEB; i++) {
            double az = NEBP[i * 4] + t * 0.006, el = NEBP[i * 4 + 1];
            float s = NEBP[i * 4 + 2], k = NEBP[i * 4 + 3];
            double x = Math.cos(el) * Math.cos(az), y = Math.sin(el), z = Math.cos(el) * Math.sin(az);
            int c = blend(p.nebA, p.nebB, k);
            billboard(x, y, z, 80f, s, (float) (t * 0.01 + i), Sprite.SMOKE, c, 0.35f * p.nebula * w);
            billboard(x, y, z, 81f, s * 1.4f, 0f, Sprite.GLOW, c, 0.18f * p.nebula * w);
        }
        GL11.glEnd();
        GlStateManager.disableTexture2D();
    }

    private void clouds(SkyPreset p, float w, double t, boolean underlay) {
        if (underlay) return; // clouds are drawn once, alpha blended, after the glow layers
        GlStateManager.enableTexture2D();
        ParticleSystem.INSTANCE.bindAtlas();
        GL11.glBegin(GL11.GL_QUADS);
        int n = Math.round(CLOUDS * Math.min(1f, p.clouds));
        for (int i = 0; i < n; i++) {
            double az = CLOUD[i * 4] + t * 0.012, el = CLOUD[i * 4 + 1];
            float s = CLOUD[i * 4 + 2];
            double x = Math.cos(el) * Math.cos(az), y = Math.sin(el), z = Math.cos(el) * Math.sin(az);
            billboard(x, y, z, 78f, s, CLOUD[i * 4 + 3] * 6f, Sprite.SMOKE, p.cloudColor, 0.55f * Math.min(1f, p.clouds) * w);
        }
        GL11.glEnd();
        GlStateManager.disableTexture2D();
    }

    /** Textured quad facing the centre of the sky dome (call inside GL_QUADS). */
    private void billboard(double x, double y, double z, float r, float half, float rot, int sprite, int c, float a) {
        double ux = z, uy = 0, uz = -x;
        double l = Math.sqrt(ux * ux + uz * uz);
        if (l < 1e-4) {
            ux = 1;
            uz = 0;
            l = 1;
        }
        ux /= l;
        uz /= l;
        double vx = y * uz - z * uy, vy = z * ux - x * uz, vz = x * uy - y * ux;
        double cr = Math.cos(rot), sr = Math.sin(rot);
        double ax = (ux * cr + vx * sr) * half, ay = (uy * cr + vy * sr) * half, az = (uz * cr + vz * sr) * half;
        double bx = (-ux * sr + vx * cr) * half, by = (-uy * sr + vy * cr) * half, bz = (-uz * sr + vz * cr) * half;
        double cx = x * r, cy = y * r, cz = z * r;
        ParticleSystem.uv(sprite, uv);
        color(c, a);
        GL11.glTexCoord2f(uv[0], uv[3]);
        GL11.glVertex3d(cx - ax - bx, cy - ay - by, cz - az - bz);
        GL11.glTexCoord2f(uv[2], uv[3]);
        GL11.glVertex3d(cx + ax - bx, cy + ay - by, cz + az - bz);
        GL11.glTexCoord2f(uv[2], uv[1]);
        GL11.glVertex3d(cx + ax + bx, cy + ay + by, cz + az + bz);
        GL11.glTexCoord2f(uv[0], uv[1]);
        GL11.glVertex3d(cx - ax + bx, cy - ay + by, cz - az + bz);
    }

    private void shootingStars(SkyPreset p, float w, double t) {
        GL11.glLineWidth(1.8f);
        GL11.glBegin(GL11.GL_LINES);
        for (int k = 0; k < 4; k++) {
            double phaseT = t * p.shooting * 0.35 + k * 0.37;
            double cycle = Math.floor(phaseT), ph = phaseT - cycle;
            if (ph > 0.14) continue;
            float u = (float) (ph / 0.14);
            Random r = rr;
            r.setSeed((long) cycle * 31 + k);
            double az = r.nextDouble() * TAU, el = 0.5 + r.nextDouble() * 0.7, daz = 0.5 + r.nextDouble() * 0.4, del = -0.35;
            float[] a = qa, b = qb;
            dir(az + daz * u, el + del * u, 84, a);
            dir(az + daz * Math.max(0, u - 0.25f), el + del * Math.max(0, u - 0.25f), 84, b);
            float fade = (float) Math.sin(Math.PI * u) * w;
            color(0xFFFFFF, fade);
            GL11.glVertex3f(a[0], a[1], a[2]);
            color(p.starColor, 0f);
            GL11.glVertex3f(b[0], b[1], b[2]);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
    }

    private void codeRain(float w, double t) {
        GL11.glLineWidth(2.2f);
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < CODE; i++) {
            double az = CODEP[i * 3], sp = CODEP[i * 3 + 1], off = CODEP[i * 3 + 2];
            double headEl = 1.35 - ((t * sp + off) % 1.7);
            for (int s = 0; s < 10; s++) {
                double e0 = headEl + s * 0.05, e1 = e0 + 0.03;
                if (e0 < -0.05 || e1 > 1.45) continue;
                float a = (s == 0 ? 1f : 0.75f * (1f - s / 10f)) * w;
                float[] p0 = qa, p1 = qb;
                dir(az, e0, 84, p0);
                dir(az, e1, 84, p1);
                color(s == 0 ? 0xD8FFE0 : 0x2DFF6B, a);
                GL11.glVertex3f(p0[0], p0[1], p0[2]);
                GL11.glVertex3f(p1[0], p1[1], p1[2]);
            }
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
    }

    /** Retro grid: rings that scroll towards the horizon plus meridians. */
    private void grid(SkyPreset p, float w, double t) {
        GL11.glLineWidth(1.5f);
        for (int k = 0; k < 7; k++) {
            double u = ((k + t * 0.35) % 7) / 7.0;
            double el = 0.32 * u * u;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            color(p.glowColor, 0.7f * w * (float) (1 - u));
            for (int j = 0; j < 64; j++) {
                double az = TAU * j / 64;
                GL11.glVertex3d(Math.cos(el) * Math.cos(az) * 86, Math.sin(el) * 86, Math.cos(el) * Math.sin(az) * 86);
            }
            GL11.glEnd();
        }
        GL11.glBegin(GL11.GL_LINES);
        for (int j = 0; j < 40; j++) {
            double az = TAU * j / 40;
            color(p.glowColor, 0.5f * w);
            GL11.glVertex3d(Math.cos(az) * 86, 0, Math.sin(az) * 86);
            color(p.glowColor, 0f);
            GL11.glVertex3d(Math.cos(0.32) * Math.cos(az) * 86, Math.sin(0.32) * 86, Math.cos(0.32) * Math.sin(az) * 86);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
    }

    private void body(SkyPreset p, float w, double t, float sunAngle) {
        double x, y, z;
        if (p.followTime) {
            double th = sunAngle * TAU;
            x = -Math.sin(th);
            y = Math.cos(th);
            z = 0;
            if (p.celestial == SkyPreset.MOON) {
                x = -x;
                y = -y;
            }
        } else {
            double el = p.celestial == SkyPreset.RETRO ? 0.12 : (p.celestial == SkyPreset.SUN ? 0.14 : 0.55);
            x = Math.cos(el) * Math.cos(0.7);
            y = Math.sin(el);
            z = Math.cos(el) * Math.sin(0.7);
        }
        GlStateManager.enableTexture2D();
        ParticleSystem.INSTANCE.bindAtlas();
        GL11.glBegin(GL11.GL_QUADS);
        float halo = p.celestial == SkyPreset.ECLIPSE ? 14f : (p.celestial == SkyPreset.RETRO ? 18f : 16f);
        billboard(x, y, z, 80f, halo, 0f, Sprite.GLOW, p.celColor, 0.5f * w);
        if (p.celestial == SkyPreset.ECLIPSE) {
            billboard(x, y, z, 79f, 9f, (float) (t * 0.05), Sprite.HALO, p.celColor, 0.9f * w);
            billboard(x, y, z, 79f, 7f, (float) (-t * 0.07), Sprite.RING, 0xFFFFFF, 0.8f * w);
        }
        if (p.celestial == SkyPreset.PLANET) {
            billboard(x, y, z, 79f, 13f, 0.35f, Sprite.RING, ParticleSystem.mix(p.celColor, 0xFFFFFF, 0.4f), 0.8f * w);
        }
        GL11.glEnd();
        GlStateManager.disableTexture2D();

        // solid disc (alpha blended so an eclipse can be black)
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float radius = p.celestial == SkyPreset.PLANET ? 7f : (p.celestial == SkyPreset.RETRO ? 9f : 5f);
        int disc = p.celestial == SkyPreset.ECLIPSE ? 0x000000 : p.celColor;
        double ux = z, uz = -x, l = Math.sqrt(ux * ux + uz * uz);
        if (l < 1e-4) {
            ux = 1;
            uz = 0;
            l = 1;
        }
        ux /= l;
        uz /= l;
        double vx = y * uz, vy = z * ux - x * uz, vz = -y * ux;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(disc, w);
        GL11.glVertex3d(x * 78, y * 78, z * 78);
        for (int i = 0; i <= 40; i++) {
            double a = TAU * i / 40, c = Math.cos(a) * radius, s = Math.sin(a) * radius;
            int col = disc;
            if (p.celestial == SkyPreset.RETRO) col = blend(0xFF4FD8, p.celColor, (float) (0.5 + 0.5 * Math.sin(a)));
            if (p.celestial == SkyPreset.PLANET) col = blend(p.celColor, 0x2D1F66, (float) (0.5 - 0.5 * Math.cos(a)));
            color(col, w);
            GL11.glVertex3d(x * 78 + ux * c + vx * s, y * 78 + vy * s, z * 78 + uz * c + vz * s);
        }
        GL11.glEnd();
        if (p.celestial == SkyPreset.RETRO) {
            // horizontal cut-outs across the lower half of the retro sun
            for (int k = 0; k < 5; k++) {
                double off = -radius * (0.15 + k * 0.17), hh = 0.35 + k * 0.12;
                GL11.glBegin(GL11.GL_QUADS);
                color(blend(p.horizon, p.top, 0.2f), w);
                for (int s = 0; s < 4; s++) {
                    double cx = (s == 0 || s == 3) ? -radius : radius, cy = off + ((s < 2) ? 0 : hh);
                    GL11.glVertex3d(x * 77.9 + ux * cx + vx * cy, y * 77.9 + vy * cy, z * 77.9 + uz * cx + vz * cy);
                }
                GL11.glEnd();
            }
        }
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    private void stormFlash(SkyPreset a, SkyPreset b, float m, double t) {
        float storm = (a == null ? 0f : a.storm * (1f - m)) + b.storm * m;
        if (storm <= 0f) return;
        double now = ParticleSystem.INSTANCE.time;
        if (now >= nextFlash) {
            flashTime = now;
            flashSeed = flashRng.nextInt();
            nextFlash = now + 2.0 + flashRng.nextDouble() * 8.0 / storm;
        }
        double age = now - flashTime;
        if (age > 0.45) return;
        float f = (float) (age < 0.08 ? 1.0 : Math.max(0, 1 - (age - 0.08) / 0.37)) * storm;
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        Random r = rr;
        r.setSeed(flashSeed);
        double az = r.nextDouble() * TAU, el = 0.9;
        GL11.glLineWidth(2.5f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        float[] q = qa;
        for (int i = 0; i <= 12; i++) {
            color(0xFFFFFF, f);
            dir(az + (r.nextDouble() - 0.5) * 0.06, el - i * 0.07, 84, q);
            GL11.glVertex3f(q[0], q[1], q[2]);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        // brighten the whole dome briefly
        final int lon = 24;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(0xC8D4FF, 0.25f * f);
        GL11.glVertex3d(0, 90, 0);
        for (int j = 0; j <= lon; j++) {
            double azj = TAU * j / lon;
            GL11.glVertex3d(Math.cos(azj) * 90, 0, Math.sin(azj) * 90);
        }
        GL11.glEnd();
    }

    // ------------------------------------------------- flow lines & aurora

    private void flowLines(SkyPreset p, float w, double t) {
        int n = Math.min(MAXL, p.lines);
        int m = Math.min(MAXSEG, Math.max(24, Math.round(72 * PerformanceModule.qualityScale())));
        float core = 0.006f * (p.ribbons ? 2.4f : 1f);
        int lc = tinted(p.lineColor), gc = tinted(p.lineGlow);
        float bb = brightness.f();
        float lr = Math.min(1f, ((lc >> 16) & 255) / 255f * bb), lg = Math.min(1f, ((lc >> 8) & 255) / 255f * bb), lb = Math.min(1f, (lc & 255) / 255f * bb);
        float gr = Math.min(1f, ((gc >> 16) & 255) / 255f * bb), gg = Math.min(1f, ((gc >> 8) & 255) / 255f * bb), gb = Math.min(1f, (gc & 255) / 255f * bb);
        for (int k = 0; k < n; k++) {
            float[] q = PARAM[k];
            for (int i = 0; i <= m; i++) {
                double u = i / (double) m;
                double e = q[E0] + q[A1] * Math.sin(TAU * q[F1] * u + q[P1] + q[S1] * t)
                        + q[A2] * Math.sin(TAU * q[F2] * u + q[P2] + q[S2] * t * 1.7);
                // spread the lines that are drawn evenly around the sky (not just the first few slots)
                double phi = k * TAU / n + (q[PHI0] - k * TAU / MAXL) + q[SPAN] * u + q[B] * Math.sin(TAU * q[F3] * u + q[P3] + q[S3] * t);
                e = Math.max(0.012, Math.min(1.45, e));
                double ce = Math.cos(e);
                px[i] = (float) (ce * Math.cos(phi));
                py[i] = (float) Math.sin(e);
                pz[i] = (float) (ce * Math.sin(phi));
                env[i] = (float) Math.pow(Math.sin(Math.PI * u), 0.45) * (0.55f + 0.45f * (1f - (float) e / 1.45f));
            }
            for (int i = 0; i <= m; i++) {
                int i0 = Math.max(0, i - 1), i1 = Math.min(m, i + 1);
                float tx = px[i1] - px[i0], ty = py[i1] - py[i0], tz = pz[i1] - pz[i0];
                float cx = py[i] * tz - pz[i] * ty, cy = pz[i] * tx - px[i] * tz, cz = px[i] * ty - py[i] * tx;
                float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (len < 1.0E-6f) len = 1f;
                sx[i] = cx / len;
                sy[i] = cy / len;
                sz[i] = cz / len;
            }
            ribbon(m, core * 5.5f, 80f, gr, gg, gb, 0.30f * w);
            ribbon(m, core, 80f, lr, lg, lb, 0.95f * w);
        }
    }

    private void ribbon(int m, float halfW, float r, float cr, float cg, float cb, float centerAlpha) {
        for (int half = 0; half < 2; half++) {
            float sign = half == 0 ? -1f : 1f;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= m; i++) {
                float a = env[i] * centerAlpha;
                float ex = (px[i] + sx[i] * halfW * sign) * r, ey = (py[i] + sy[i] * halfW * sign) * r,
                        ez = (pz[i] + sz[i] * halfW * sign) * r;
                if (half == 0) {
                    GL11.glColor4f(cr, cg, cb, 0f);
                    GL11.glVertex3f(ex, ey, ez);
                    GL11.glColor4f(cr, cg, cb, a);
                    GL11.glVertex3f(px[i] * r, py[i] * r, pz[i] * r);
                } else {
                    GL11.glColor4f(cr, cg, cb, a);
                    GL11.glVertex3f(px[i] * r, py[i] * r, pz[i] * r);
                    GL11.glColor4f(cr, cg, cb, 0f);
                    GL11.glVertex3f(ex, ey, ez);
                }
            }
            GL11.glEnd();
        }
    }

    /**
     * Aurora curtains that go all the way around the sky. Every wave along
     * the ring uses a whole number of cycles, so the last segment meets the
     * first one without a seam; the bands are spread evenly around you.
     */
    private void aurora(SkyPreset p, float w, double t) {
        int n = Math.max(3, Math.min(10, p.aurora));
        int m = Math.max(64, Math.round(160 * PerformanceModule.qualityScale()));
        float r = 80f;
        for (int k = 0; k < n; k++) {
            float[] q = PARAM[k];
            int band = blend(p.auroraA, p.auroraB, k / (float) Math.max(1, n - 1));
            double base = 0.07 + 0.11 * (k % 3) / 2.0;
            double hgt = 0.22 + 0.18 * q[A2] * 4.0;
            double off = k * TAU / n + q[P3];
            int f1 = Math.max(1, Math.round(q[F1] * 2f)), f2 = Math.max(2, Math.round(q[F2] * 1.5f)),
                    f3 = Math.max(1, Math.round(q[F3])), fe = 2 + k % 3;
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 0) GL11.glBegin(GL11.GL_QUAD_STRIP);
                else {
                    GL11.glLineWidth(1.5f);
                    GL11.glBegin(GL11.GL_LINE_STRIP);
                }
                for (int i = 0; i <= m; i++) {
                    double u = (i % m) / (double) m;
                    double phi = off + TAU * u + 0.18 * Math.sin(TAU * f3 * u + q[P3] + q[S3] * t * 2.0);
                    double e0 = base + 0.04 * Math.sin(TAU * f1 * u + q[P1] + q[S1] * t);
                    double e1 = e0 + hgt * (0.7 + 0.3 * Math.sin(TAU * f2 * u + q[P2] + q[S2] * t));
                    // brightness drifts around the ring instead of fading at the ends
                    float envl = 0.35f + 0.65f * (float) Math.pow(0.5 + 0.5 * Math.sin(TAU * fe * u + q[P2] + t * 0.12 * (k % 2 == 0 ? 1 : -1)), 1.5);
                    float wave = 0.65f + 0.35f * (float) Math.sin(TAU * 24 * u + t * 1.7 + k);
                    float ce0 = (float) Math.cos(e0), se0 = (float) Math.sin(e0);
                    float cp = (float) Math.cos(phi), sp = (float) Math.sin(phi);
                    if (pass == 0) {
                        color(band, 0.32f * w * envl * wave);
                        GL11.glVertex3f(ce0 * cp * r, se0 * r, ce0 * sp * r);
                        color(band, 0f);
                        float ce1 = (float) Math.cos(e1);
                        GL11.glVertex3f(ce1 * cp * r, (float) Math.sin(e1) * r, ce1 * sp * r);
                    } else {
                        color(blend(band, 0xFFFFFF, 0.4f), 0.55f * w * envl);
                        GL11.glVertex3f(ce0 * cp * r, se0 * r, ce0 * sp * r);
                    }
                }
                GL11.glEnd();
            }
            GL11.glLineWidth(1f);
        }
    }
}
