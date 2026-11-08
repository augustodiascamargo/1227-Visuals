package net.m1227.audio;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.m1227.Mod1227;

/**
 * Built-in hit sounds, synthesized at runtime from sine waves and noise.
 * They are original (no samples from games or libraries), so they can be
 * shipped freely. Custom .wav files go in config/1227/sounds.
 */
public final class SoundBank {
    public static final String FILE_PREFIX = "File: ";
    public static final String[] BUILT_IN = {
            "Crisp Click", "Soft Pop", "Glass Tick", "Bass Thump", "Chime", "Snap", "Laser", "Bubble"
    };
    private static final float R = HitSoundEngine.RATE;

    private SoundBank() {}

    public static File folder() {
        File f = new File(new File(Mod1227.configDir, "1227"), "sounds");
        if (!f.isDirectory()) f.mkdirs();
        return f;
    }

    /** Built-in names followed by "File: x.wav" for every audio file in the folder. */
    public static String[] options() {
        List<String> out = new ArrayList<String>(Arrays.asList(BUILT_IN));
        File[] files = folder().listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && (n.endsWith(".wav") || n.endsWith(".aiff") || n.endsWith(".aif") || n.endsWith(".au"))) {
                    out.add(FILE_PREFIX + f.getName());
                }
            }
        }
        return out.toArray(new String[out.size()]);
    }

    // ------------------------------------------------------------- synth

    /** Sound packs: each has 3 hit variants, a critical and a kill sound. */
    public static final String[] PACKS = {"Off", "Neon", "Crystal", "Arcade", "Heavy", "Soft"};

    public static String packSound(String pack, String kind, int variant) {
        return "Pack " + pack + " " + kind + " " + variant;
    }

    private static float[] pack(String name, Random rnd) {
        String[] t = name.split(" ");
        if (t.length < 4) return null;
        String pk = t[1], kind = t[2];
        int v = 0;
        try {
            v = Integer.parseInt(t[3]);
        } catch (NumberFormatException ignored) {
            // variant 0
        }
        float k = new float[]{1f, 1.06f, 0.94f}[Math.abs(v) % 3];
        if (pk.equals("Neon")) {
            if (kind.equals("Hit")) return mix(sweep(0.08f, 1800f * k, 620f * k, 34f, 0.3f), hp(noise(rnd, 0.01f, 300f, 0.3f)));
            if (kind.equals("Crit")) return mix(sweep(0.16f, 2600f, 700f, 20f, 0.35f), bell(0.22f, new float[]{3200f, 4800f}, 14f));
            return seq(new float[][]{bell(0.18f, new float[]{1320f, 2640f}, 16f), bell(0.18f, new float[]{1760f, 3520f}, 16f),
                    bell(0.4f, new float[]{2640f, 3960f}, 9f)}, new float[]{0f, 0.07f, 0.14f});
        }
        if (pk.equals("Crystal")) {
            if (kind.equals("Hit")) return bell(0.18f, new float[]{2600f * k, 4100f * k, 5900f * k}, 36f);
            if (kind.equals("Crit")) return mix(bell(0.35f, new float[]{3100f, 4650f, 6200f, 7800f}, 16f), hp(noise(rnd, 0.02f, 160f, 0.25f)));
            return seq(new float[][]{bell(0.5f, new float[]{1568f, 2349f}, 8f), bell(0.5f, new float[]{1976f, 2960f}, 8f),
                    bell(0.7f, new float[]{2637f, 3951f}, 6f)}, new float[]{0f, 0.09f, 0.18f});
        }
        if (pk.equals("Arcade")) {
            if (kind.equals("Hit")) return sweep(0.07f, 900f * k, 300f * k, 30f, 0.9f);
            if (kind.equals("Crit")) return seq(new float[][]{sweep(0.05f, 1200f, 1200f, 40f, 0.9f), sweep(0.07f, 1800f, 1800f, 30f, 0.9f)},
                    new float[]{0f, 0.05f});
            return seq(new float[][]{sweep(0.08f, 988f, 988f, 18f, 0.9f), sweep(0.3f, 1319f, 1319f, 9f, 0.9f)}, new float[]{0f, 0.08f});
        }
        if (pk.equals("Heavy")) {
            if (kind.equals("Hit")) return mix(sweep(0.16f, 150f * k, 48f, 20f, 0.1f), hp(noise(rnd, 0.02f, 120f, 0.5f)));
            if (kind.equals("Crit")) return mix(sweep(0.2f, 180f, 45f, 16f, 0.15f), hp(noise(rnd, 0.05f, 60f, 0.9f)));
            return mix(sweep(0.6f, 120f, 30f, 6f, 0.2f), noise(rnd, 0.3f, 12f, 0.35f));
        }
        // Soft
        if (kind.equals("Hit")) return sweep(0.07f, 900f * k, 280f * k, 40f, 0f);
        if (kind.equals("Crit")) return sweep(0.12f, 400f, 1400f, 26f, 0f);
        return seq(new float[][]{bell(0.4f, new float[]{1046f, 1568f}, 10f), bell(0.5f, new float[]{1318f, 1976f}, 9f)}, new float[]{0f, 0.1f});
    }

    /** Places sounds at start offsets (seconds) and mixes them. */
    private static float[] seq(float[][] parts, float[] at) {
        int n = 0;
        for (int i = 0; i < parts.length; i++) n = Math.max(n, (int) (at[i] * R) + parts[i].length);
        float[] out = new float[n];
        for (int i = 0; i < parts.length; i++) {
            int o = (int) (at[i] * R);
            for (int j = 0; j < parts[i].length; j++) out[o + j] += parts[i][j];
        }
        return out;
    }

    static short[] synth(String name) {
        float[] s;
        if (name.startsWith("Pack ")) {
            float[] pk = pack(name, new Random(name.hashCode()));
            if (pk != null) return normalize(pk, 0.8f);
        }
        Random rnd = new Random(name.hashCode());
        if (name.equals("Soft Pop")) s = sweep(0.07f, 950f, 260f, 38f, 0f);
        else if (name.equals("Glass Tick")) s = bell(0.16f, new float[]{2400f, 3900f, 5350f, 7100f}, 32f);
        else if (name.equals("Bass Thump")) s = mix(sweep(0.14f, 170f, 52f, 22f, 0f), noise(rnd, 0.012f, 160f, 0.35f));
        else if (name.equals("Chime")) s = bell(0.45f, new float[]{1320f, 1980f, 2640f}, 9f);
        else if (name.equals("Snap")) s = hp(noise(rnd, 0.03f, 140f, 1f));
        else if (name.equals("Laser")) s = sweep(0.13f, 2300f, 380f, 22f, 0.35f);
        else if (name.equals("Bubble")) s = sweep(0.09f, 380f, 1250f, 30f, 0f);
        else s = mix(hp(noise(rnd, 0.025f, 180f, 0.8f)), bell(0.05f, new float[]{3100f}, 90f)); // Crisp Click
        return normalize(s, 0.8f);
    }

    /** Exponential-decay sine sweep; square > 0 blends in odd harmonics. */
    private static float[] sweep(float sec, float f0, float f1, float decay, float square) {
        int n = (int) (sec * R);
        float[] out = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / R, u = i / (float) n;
            float f = f0 * (float) Math.pow(f1 / f0, u);
            ph += 2 * Math.PI * f / R;
            float v = (float) Math.sin(ph);
            if (square > 0f) v += square * (float) (Math.sin(3 * ph) / 3 + Math.sin(5 * ph) / 5);
            out[i] = v * env(t, decay);
        }
        return out;
    }

    private static float[] bell(float sec, float[] freqs, float decay) {
        int n = (int) (sec * R);
        float[] out = new float[n];
        for (int k = 0; k < freqs.length; k++) {
            float a = 1f / (k + 1), d = decay * (1f + k * 0.5f);
            for (int i = 0; i < n; i++) {
                float t = i / R;
                out[i] += a * (float) Math.sin(2 * Math.PI * freqs[k] * t) * env(t, d);
            }
        }
        return out;
    }

    private static float[] noise(Random r, float sec, float decay, float amp) {
        int n = (int) (sec * R);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = (r.nextFloat() * 2f - 1f) * amp * env(i / R, decay);
        return out;
    }

    /** First-order high-pass: keeps the crisp top end of a noise burst. */
    private static float[] hp(float[] s) {
        float[] out = new float[s.length];
        float prevIn = 0f, prevOut = 0f;
        for (int i = 0; i < s.length; i++) {
            prevOut = 0.85f * (prevOut + s[i] - prevIn);
            prevIn = s[i];
            out[i] = prevOut;
        }
        return out;
    }

    /** 2 ms attack, then exponential decay. */
    private static float env(float t, float decay) {
        float a = Math.min(1f, t / 0.002f);
        return a * (float) Math.exp(-decay * t);
    }

    private static float[] mix(float[] a, float[] b) {
        float[] out = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < a.length; i++) out[i] += a[i];
        for (int i = 0; i < b.length; i++) out[i] += b[i];
        return out;
    }

    private static short[] normalize(float[] s, float peak) {
        float m = 1e-6f;
        for (float v : s) m = Math.max(m, Math.abs(v));
        short[] out = new short[s.length];
        float k = peak * 32767f / m;
        for (int i = 0; i < s.length; i++) out[i] = (short) (s[i] * k);
        return out;
    }
}
