package net.m1227.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Typed, persistent setting. The menu is generated from these, so a new
 * option on a module never needs UI code.
 */
public abstract class Setting<T> {
    public final String name;
    protected T value;
    /** Value the setting starts with; restored by {@link #reset()}. */
    public final T def;
    /** Optional: only shown in the menu while this returns true. */
    private Visibility visibility;

    public interface Visibility {
        boolean visible();
    }

    protected Setting(String name, T def) {
        this.name = name;
        this.value = def;
        this.def = def;
    }

    public void reset() {
        value = def;
        ConfigManager.markDirty();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S showIf(Visibility v) {
        visibility = v;
        return (S) this;
    }

    public boolean visible() {
        return visibility == null || visibility.visible();
    }

    public T get() {
        return value;
    }

    public void set(T v) {
        value = v;
        ConfigManager.markDirty();
    }

    /** False for settings that are not saved (buttons). */
    public boolean persistent() {
        return true;
    }

    public abstract JsonElement toJson();

    /** Loads without marking the config dirty. Must tolerate bad input. */
    public abstract void fromJson(JsonElement e);

    // ------------------------------------------------------------------

    public static final class Bool extends Setting<Boolean> {
        public Bool(String name, boolean def) {
            super(name, def);
        }

        public boolean on() {
            return value;
        }

        public void toggle() {
            set(!value);
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        public void fromJson(JsonElement e) {
            if (e.isJsonPrimitive()) value = e.getAsBoolean();
        }
    }

    // ------------------------------------------------------------------

    public static final class Num extends Setting<Double> {
        public final double min, max, step;
        public final String suffix;

        public Num(String name, double def, double min, double max, double step, String suffix) {
            super(name, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix;
        }

        public float f() {
            return value.floatValue();
        }

        public int i() {
            return (int) Math.round(value);
        }

        public void setClamped(double v) {
            set(Double.valueOf(snap(v)));
        }

        private double snap(double v) {
            v = Math.max(min, Math.min(max, v));
            if (step > 0) v = min + Math.round((v - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            return Math.round(v * 10000d) / 10000d;
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        public void fromJson(JsonElement e) {
            if (e.isJsonPrimitive()) value = snap(e.getAsDouble());
        }
    }

    // ------------------------------------------------------------------

    public static final class Mode extends Setting<String> {
        public String[] options;

        public Mode(String name, String def, String... options) {
            super(name, def);
            this.options = options;
        }

        /** Changes the value without saving (used by menu previews, always restored right after). */
        public void setSilently(String v) {
            value = v;
        }

        public boolean is(String s) {
            return value.equals(s);
        }

        /** Replaces the choices (used for the list of custom sound files). */
        public void setOptions(String[] o) {
            options = o;
            for (String s : o) {
                if (s.equals(value)) return;
            }
            if (o.length > 0) value = o[0];
        }

        public void cycle(int dir) {
            int i = 0;
            for (int k = 0; k < options.length; k++) {
                if (options[k].equals(value)) i = k;
            }
            i = (i + dir + options.length) % options.length;
            set(options[i]);
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        public void fromJson(JsonElement e) {
            if (!e.isJsonPrimitive()) return;
            String s = e.getAsString();
            for (String o : options) {
                if (o.equals(s)) {
                    value = s;
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Colour stored as hue + saturation at full brightness. Brightness is
     * fixed because the effects use additive blending, where dark colours
     * are simply invisible.
     */
    public static final class Col extends Setting<Integer> {
        public float hue, sat;

        public Col(String name, int rgb) {
            super(name, rgb & 0xFFFFFF);
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
            hue = hsb[0];
            sat = hsb[1];
        }

        public int rgb() {
            return value;
        }

        public void setRgb(int rgb) {
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
            setHS(hsb[0], hsb[1]);
        }

        @Override
        public void reset() {
            float[] hsb = java.awt.Color.RGBtoHSB((def >> 16) & 255, (def >> 8) & 255, def & 255, null);
            hue = hsb[0];
            sat = hsb[1];
            super.reset();
        }

        public void setHS(float h, float s) {
            hue = Math.max(0f, Math.min(1f, h));
            sat = Math.max(0f, Math.min(1f, s));
            set(java.awt.Color.HSBtoRGB(hue, sat, 1f) & 0xFFFFFF);
        }

        @Override
        public JsonElement toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("h", hue);
            o.addProperty("s", sat);
            return o;
        }

        @Override
        public void fromJson(JsonElement e) {
            if (!e.isJsonObject()) return;
            JsonObject o = e.getAsJsonObject();
            if (!o.has("h") || !o.has("s")) return;
            hue = Math.max(0f, Math.min(1f, o.get("h").getAsFloat()));
            sat = Math.max(0f, Math.min(1f, o.get("s").getAsFloat()));
            value = java.awt.Color.HSBtoRGB(hue, sat, 1f) & 0xFFFFFF;
        }
    }

    // ------------------------------------------------------------------

    /** A button in the menu (previews, presets). Not saved to the config. */
    public static final class Action extends Setting<Boolean> {
        private final Runnable task;

        public Action(String name, Runnable task) {
            super(name, Boolean.FALSE);
            this.task = task;
        }

        public void run() {
            task.run();
        }

        @Override
        public boolean persistent() {
            return false;
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(false);
        }

        @Override
        public void fromJson(JsonElement e) {
            // nothing to load
        }
    }

    // ------------------------------------------------------------------

    /** A keyboard key (LWJGL key code). 0 means "not bound". */
    public static final class Key extends Setting<Integer> {
        public Key(String name, int def) {
            super(name, def);
        }

        public int code() {
            return value;
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(value);
        }

        @Override
        public void fromJson(JsonElement e) {
            if (e.isJsonPrimitive()) value = Math.max(0, Math.min(255, e.getAsInt()));
        }
    }
}
