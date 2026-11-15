package net.m1227.weapons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockbench .bbmodel (Java block/item, Generic "free", Bedrock): cubes (with inflate, face UV
 * rotation, per-element rotation and Java "rescale"), mesh elements, nested groups with pivots,
 * embedded or neighbouring PNG textures (animated strips included), the Java item "display"
 * transforms and keyframe animations (linear, Catmull-Rom, step, bezier; numbers or Molang).
 */
public final class BbModelReader {
    private BbModelReader() {}

    private static final String[] DIRS = {"north", "south", "east", "west", "up", "down"};

    public static WModel read(File f) throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
        return parse(json, f);
    }

    /** f is only used to find textures next to the file (may be null). */
    public static WModel parse(String json, File f) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        WModel m = new WModel();
        m.format = "bbmodel";
        m.unit = 16f;
        m.name = str(root, "name", "");
        m.credit = str(root, "credit", "");
        JsonObject meta = obj(root, "meta");
        String fmt = meta != null ? str(meta, "model_format", "") : "";
        boolean bedrock = fmt.startsWith("bedrock");
        float resW = 16f, resH = 16f;
        JsonObject res = obj(root, "resolution");
        if (res != null) {
            resW = num(res, "width", 16f);
            resH = num(res, "height", 16f);
        }

        // ---- textures
        List<float[]> uvSize = new ArrayList<>();
        JsonArray texs = arr(root, "textures");
        if (texs != null) {
            for (JsonElement te : texs) {
                JsonObject t = te.getAsJsonObject();
                String tname = str(t, "name", "texture");
                WModel.Tex tex = null;
                try {
                    String src = str(t, "source", "");
                    if (src.startsWith("data:")) {
                        byte[] b = Images.dataUri(src);
                        if (b != null) tex = Images.decode(b, tname);
                    }
                    if (tex == null && f != null) {
                        File img = Images.find(f, str(t, "relative_path", ""));
                        if (img == null) img = Images.find(f, str(t, "path", ""));
                        if (img == null) img = Images.find(f, tname.endsWith(".png") ? tname : tname + ".png");
                        if (img != null) tex = Images.read(img);
                    }
                } catch (Exception e) {
                    m.warnings.add("texture " + tname + ": " + e.getMessage());
                }
                if (tex == null) {
                    m.warnings.add("texture " + tname + " is missing (drawn white)");
                    tex = new WModel.Tex();
                    tex.name = tname;
                    tex.w = tex.h = 1;
                    tex.argb = new int[]{0xFFFFFFFF};
                }
                float uw = num(t, "uv_width", resW), uh = num(t, "uv_height", resH);
                // animated strip: frames of (w x w*uh/uw) stacked vertically
                float fh = tex.w * uh / Math.max(1e-3f, uw);
                if (fh >= 1f && tex.h > fh * 1.5f) {
                    int frames = Math.round(tex.h / fh);
                    if (frames > 1 && Math.abs(frames * fh - tex.h) < 1.5f) {
                        tex.frames = frames;
                        tex.frameTicks = Math.max(1, (int) num(t, "frame_time", 1f));
                        tex.order = order(str(t, "frame_order_type", "loop"), str(t, "frame_order", ""), frames);
                    }
                }
                String mode = str(t, "render_mode", "default");
                tex.emissive = mode.equals("emissive");
                tex.additive = mode.equals("additive");
                m.textures.add(tex);
                uvSize.add(new float[]{uw, uh});
            }
        }

        // ---- elements and groups
        Map<String, JsonObject> elements = new HashMap<>();
        JsonArray els = arr(root, "elements");
        if (els != null) for (JsonElement e : els) {
            JsonObject o = e.getAsJsonObject();
            elements.put(str(o, "uuid", "e" + elements.size()), o);
        }
        Map<String, JsonObject> groups = new HashMap<>();
        JsonArray gs = arr(root, "groups");
        if (gs != null) for (JsonElement g : gs) {
            if (!g.isJsonObject()) continue;
            JsonObject o = g.getAsJsonObject();
            groups.put(str(o, "uuid", ""), o);
        }
        Map<String, Integer> boneOf = new HashMap<>();
        Map<String, Integer> boneByUuid = new HashMap<>();
        java.util.Set<String> placed = new java.util.HashSet<>();
        JsonArray outliner = arr(root, "outliner");
        if (outliner != null) for (JsonElement n : outliner) walk(n, 0, m, groups, boneOf, boneByUuid, placed);
        for (JsonObject e : elements.values()) {
            String id = str(e, "uuid", "");
            if (!boneOf.containsKey(id)) boneOf.put(id, 0);
        }
        if (els != null) for (JsonElement ee : els) {
            JsonObject e = ee.getAsJsonObject();
            if (e.has("export") && !e.get("export").getAsBoolean()) continue;
            if (e.has("visibility") && !e.get("visibility").getAsBoolean()) continue;
            String id = str(e, "uuid", "");
            int bone = boneOf.containsKey(id) ? boneOf.get(id) : 0;
            boolean glow = num(e, "light_emission", 0f) > 0f || glowName(str(e, "name", "")) || glowName(m.bones.get(bone).name)
                    || inGlowGroup(m, bone);
            String type = str(e, "type", "cube");
            try {
                if (type.equals("cube")) cube(m, e, bone, glow, uvSize, resW, resH);
                else if (type.equals("mesh")) mesh(m, e, bone, glow, uvSize, resW, resH);
            } catch (RuntimeException ex) {
                m.warnings.add("element " + str(e, "name", "?") + ": " + ex);
            }
        }

        // ---- Java item display (first person scale)
        JsonObject disp = obj(root, "display");
        if (disp != null) {
            JsonObject fp = obj(disp, "firstperson_righthand");
            if (fp != null) {
                JsonArray sc = arr(fp, "scale");
                m.displayScale = sc == null || sc.size() < 3 ? 1f : (sc.get(0).getAsFloat() + sc.get(1).getAsFloat() + sc.get(2).getAsFloat()) / 3f;
            }
        }

        // ---- animations
        JsonArray an = arr(root, "animations");
        if (an != null) for (JsonElement ae : an) {
            JsonObject a = ae.getAsJsonObject();
            WModel.Clip c = new WModel.Clip();
            c.name = str(a, "name", "animation");
            if (c.name.startsWith("animation.")) c.name = c.name.substring(c.name.lastIndexOf('.') + 1);
            c.length = num(a, "length", 0f);
            String loop = str(a, "loop", "once");
            c.loop = loop.equals("loop") ? 1 : loop.equals("hold") ? 2 : 0;
            JsonObject animators = obj(a, "animators");
            float maxT = 0f;
            List<String> unknown = new ArrayList<>();
            if (animators != null) for (Map.Entry<String, JsonElement> en : animators.entrySet()) {
                Integer bone = boneByUuid.get(en.getKey());
                JsonObject anim = en.getValue().getAsJsonObject();
                if (bone == null) {
                    String nm = str(anim, "name", "");
                    for (int i = 0; i < m.bones.size(); i++) if (m.bones.get(i).name.equals(nm)) bone = i;
                }
                if (bone == null) continue;
                JsonArray kf = arr(anim, "keyframes");
                if (kf == null) continue;
                Map<Integer, WModel.Track> byCh = new HashMap<>();
                for (JsonElement ke : kf) {
                    JsonObject k = ke.getAsJsonObject();
                    String ch = str(k, "channel", "");
                    int channel = ch.equals("rotation") ? WModel.ROT : ch.equals("position") ? WModel.POS : ch.equals("scale") ? WModel.SCALE : -1;
                    if (channel < 0) continue;
                    WModel.Track tr = byCh.get(channel);
                    if (tr == null) {
                        tr = new WModel.Track();
                        tr.bone = bone;
                        tr.channel = channel;
                        byCh.put(channel, tr);
                        c.tracks.add(tr);
                    }
                    WModel.Key key = new WModel.Key();
                    key.t = num(k, "time", 0f);
                    maxT = Math.max(maxT, key.t);
                    String ip = str(k, "interpolation", "linear");
                    key.interp = ip.equals("catmullrom") ? WModel.CATMULL : ip.equals("step") ? WModel.STEP : ip.equals("bezier") ? WModel.BEZIER : WModel.LINEAR;
                    JsonArray dp = arr(k, "data_points");
                    if (dp == null || dp.size() == 0) continue;
                    key.pre = point(dp.get(0).getAsJsonObject(), channel, unknown);
                    key.post = point(dp.get(dp.size() - 1).getAsJsonObject(), channel, unknown);
                    tr.keys.add(key);
                }
            }
            for (WModel.Track tr : c.tracks) tr.keys.sort((x, y) -> Float.compare(x.t, y.t));
            if (c.length <= 0f) c.length = Math.max(0.05f, maxT);
            if (!unknown.isEmpty()) m.warnings.add("animation " + c.name + ": unsupported expression " + unknown.get(0) + " (reads as 0)");
            m.clips.add(c);
        }
        if (bedrock) m.warnings.add("Bedrock model: animations use the Bedrock axes (should look the same)");
        return m;
    }

    private static boolean inGlowGroup(WModel m, int bone) {
        for (int b = bone; b > 0; b = m.bones.get(b).parent) {
            if (glowName(m.bones.get(b).name)) return true;
        }
        return false;
    }

    /** Names that mark a part as glowing (drawn full-bright): glow, fx, emissive, neon, luminous. */
    static boolean glowName(String n) {
        String s = n.toLowerCase(java.util.Locale.ROOT);
        return s.matches(".*(glow|emissi|neon|lumin|(^|[^a-z])fx([^a-z]|$)).*");
    }

    private static int[] order(String type, String custom, int frames) {
        if (type.equals("custom") && !custom.trim().isEmpty()) {
            String[] p = custom.trim().replace("[", "").replace("]", "").split("[ ,]+");
            List<Integer> l = new ArrayList<>();
            for (String s : p) {
                try {
                    l.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            int[] r = new int[l.size()];
            for (int i = 0; i < r.length; i++) r[i] = l.get(i);
            return r.length > 0 ? r : null;
        }
        if (type.equals("backwards")) {
            int[] r = new int[frames];
            for (int i = 0; i < frames; i++) r[i] = frames - 1 - i;
            return r;
        }
        if (type.equals("back_and_forth") && frames > 2) {
            int[] r = new int[frames * 2 - 2];
            for (int i = 0; i < frames; i++) r[i] = i;
            for (int i = 1; i < frames - 1; i++) r[frames - 1 + i] = frames - 1 - i;
            return r;
        }
        return null;
    }

    private static Molang.Expr[] point(JsonObject p, int channel, List<String> unknown) {
        Molang.Expr[] e = new Molang.Expr[3];
        String[] ax = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            JsonElement v = p.get(ax[i]);
            String s = v == null || v.isJsonNull() ? (channel == WModel.SCALE ? "1" : "0") : v.getAsString();
            if (s.trim().isEmpty()) s = channel == WModel.SCALE ? "1" : "0";
            e[i] = Molang.parse(s, unknown);
        }
        return e;
    }

    private static void walk(JsonElement n, int parent, WModel m, Map<String, JsonObject> groups, Map<String, Integer> boneOf,
                             Map<String, Integer> boneByUuid, java.util.Set<String> placed) {
        if (n.isJsonPrimitive()) {
            boneOf.put(n.getAsString(), parent);
            placed.add(n.getAsString());
            return;
        }
        if (!n.isJsonObject()) return;
        JsonObject o = n.getAsJsonObject();
        String uuid = str(o, "uuid", "");
        JsonObject g = groups.containsKey(uuid) ? groups.get(uuid) : o;
        if (g.has("export") && !g.get("export").getAsBoolean()) return;
        if (g.has("visibility") && !g.get("visibility").getAsBoolean()) return;
        WModel.Bone b = new WModel.Bone();
        b.name = str(g, "name", "group");
        b.parent = parent;
        b.euler = true;
        float[] org = vec(g, "origin", 0f);
        b.px = org[0];
        b.py = org[1];
        b.pz = org[2];
        float[] rot = vec(g, "rotation", 0f);
        b.rx = rot[0];
        b.ry = rot[1];
        b.rz = rot[2];
        int idx = m.bones.size();
        m.bones.add(b);
        boneByUuid.put(uuid, idx);
        JsonArray ch = arr(o, "children");
        if (ch != null) for (JsonElement c : ch) walk(c, idx, m, groups, boneOf, boneByUuid, placed);
    }

    // ------------------------------------------------------------------ cubes

    private static void cube(WModel m, JsonObject e, int bone, boolean glow, List<float[]> uvSize, float resW, float resH) {
        float[] from = vec(e, "from", 0f), to = vec(e, "to", 0f), org = vec(e, "origin", 0f), rot = vec(e, "rotation", 0f);
        float inf = num(e, "inflate", 0f);
        float x1 = Math.min(from[0], to[0]) - inf, y1 = Math.min(from[1], to[1]) - inf, z1 = Math.min(from[2], to[2]) - inf;
        float x2 = Math.max(from[0], to[0]) + inf, y2 = Math.max(from[1], to[1]) + inf, z2 = Math.max(from[2], to[2]) + inf;
        float[] mt = M4.identity();
        M4.translate(mt, org[0], org[1], org[2]);
        M4.rotateZYX(mt, rot[0], rot[1], rot[2]);
        if (e.has("rescale") && e.get("rescale").getAsBoolean()) {
            // Java models: a rotated element is stretched to keep its size on the two other axes
            for (int a = 0; a < 3; a++) {
                if (Math.abs(rot[a]) < 1e-4f) continue;
                float k = 1f / (float) Math.cos(Math.toRadians(Math.abs(rot[a])));
                M4.scale(mt, a == 0 ? 1f : k, a == 1 ? 1f : k, a == 2 ? 1f : k);
                break;
            }
        }
        M4.translate(mt, -org[0], -org[1], -org[2]);
        JsonObject faces = obj(e, "faces");
        if (faces == null) return;
        for (String dir : DIRS) {
            JsonObject fc = obj(faces, dir);
            if (fc == null) continue;
            JsonElement te = fc.get("texture");
            if (te == null || te.isJsonNull() || (te.isJsonPrimitive() && te.getAsJsonPrimitive().isBoolean())) continue;
            int tex;
            try {
                tex = te.getAsInt();
            } catch (RuntimeException ex) {
                tex = 0;
            }
            if (tex < 0 || tex >= m.textures.size()) tex = m.textures.isEmpty() ? -1 : 0;
            float[] uv = vec4(fc, "uv");
            if (uv == null) continue;
            float[][] c = corners(dir, x1, y1, z1, x2, y2, z2);
            float[][] w = new float[4][3];
            for (int i = 0; i < 4; i++) M4.point(mt, c[i][0], c[i][1], c[i][2], w[i]);
            float uw = tex >= 0 ? uvSize.get(tex)[0] : resW, uh = tex >= 0 ? uvSize.get(tex)[1] : resH;
            int frames = tex >= 0 ? m.textures.get(tex).frames : 1;
            float[][] uc = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
            int r = ((int) num(fc, "rotation", 0f) / 90) & 3;
            float[][] u4 = new float[4][2];
            for (int i = 0; i < 4; i++) {
                float[] s = uc[(i - r + 4) & 3];
                u4[i][0] = s[0] / uw;
                u4[i][1] = s[1] / uh / frames;
            }
            boolean em = glow || (tex >= 0 && (m.textures.get(tex).emissive || m.textures.get(tex).additive));
            WModel.Part p = m.part(bone, tex, em);
            // counter-clockwise seen from outside, so the normals point out
            p.tri(w[0], w[3], w[2], u4[0], u4[3], u4[2]);
            p.tri(w[0], w[2], w[1], u4[0], u4[2], u4[1]);
        }
    }

    /** Face corners top-left, top-right, bottom-right, bottom-left as Minecraft maps a face's UV. */
    static float[][] corners(String dir, float x1, float y1, float z1, float x2, float y2, float z2) {
        switch (dir) {
            case "north":
                return new float[][]{{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}};
            case "south":
                return new float[][]{{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}};
            case "east":
                return new float[][]{{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}};
            case "west":
                return new float[][]{{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}};
            case "up":
                return new float[][]{{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
            default: // down
                return new float[][]{{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}};
        }
    }

    // ------------------------------------------------------------------ meshes

    private static void mesh(WModel m, JsonObject e, int bone, boolean glow, List<float[]> uvSize, float resW, float resH) {
        float[] org = vec(e, "origin", 0f), rot = vec(e, "rotation", 0f);
        float[] mt = M4.identity();
        M4.translate(mt, org[0], org[1], org[2]);
        M4.rotateZYX(mt, rot[0], rot[1], rot[2]);
        JsonObject verts = obj(e, "vertices");
        JsonObject faces = obj(e, "faces");
        if (verts == null || faces == null) return;
        Map<String, float[]> pos = new HashMap<>();
        for (Map.Entry<String, JsonElement> v : verts.entrySet()) {
            JsonArray a = v.getValue().getAsJsonArray();
            float[] w = new float[3];
            M4.point(mt, a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), w);
            pos.put(v.getKey(), w);
        }
        for (Map.Entry<String, JsonElement> fe : faces.entrySet()) {
            JsonObject fc = fe.getValue().getAsJsonObject();
            JsonElement te = fc.get("texture");
            if (te == null || te.isJsonNull()) continue;
            int tex = te.getAsInt();
            if (tex < 0 || tex >= m.textures.size()) tex = m.textures.isEmpty() ? -1 : 0;
            JsonArray vk = arr(fc, "vertices");
            JsonObject uvs = obj(fc, "uv");
            if (vk == null || vk.size() < 3) continue;
            List<String> keys = new ArrayList<>();
            for (JsonElement k : vk) keys.add(k.getAsString());
            if (keys.size() == 4) keys = sortQuad(keys, pos);
            float uw = tex >= 0 ? uvSize.get(tex)[0] : resW, uh = tex >= 0 ? uvSize.get(tex)[1] : resH;
            int frames = tex >= 0 ? m.textures.get(tex).frames : 1;
            float[][] p = new float[keys.size()][], u = new float[keys.size()][];
            for (int i = 0; i < keys.size(); i++) {
                p[i] = pos.get(keys.get(i));
                JsonArray q = uvs != null && uvs.has(keys.get(i)) ? uvs.getAsJsonArray(keys.get(i)) : null;
                u[i] = q == null ? new float[]{0f, 0f} : new float[]{q.get(0).getAsFloat() / uw, q.get(1).getAsFloat() / uh / frames};
                if (p[i] == null) return;
            }
            boolean em = glow || (tex >= 0 && (m.textures.get(tex).emissive || m.textures.get(tex).additive));
            WModel.Part part = m.part(bone, tex, em);
            for (int i = 1; i + 1 < p.length; i++) part.tri(p[0], p[i], p[i + 1], u[0], u[i], u[i + 1]);
        }
    }

    /** Orders a quad's 4 corners around their centre (Blockbench does not store them in order). */
    private static List<String> sortQuad(List<String> k, Map<String, float[]> pos) {
        float[] a = pos.get(k.get(0)), b = pos.get(k.get(1)), c = pos.get(k.get(2)), d = pos.get(k.get(3));
        if (a == null || b == null || c == null || d == null) return k;
        float cx = (a[0] + b[0] + c[0] + d[0]) / 4f, cy = (a[1] + b[1] + c[1] + d[1]) / 4f, cz = (a[2] + b[2] + c[2] + d[2]) / 4f;
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float[] e1 = {a[0] - cx, a[1] - cy, a[2] - cz};
        float[] e2 = {ny * e1[2] - nz * e1[1], nz * e1[0] - nx * e1[2], nx * e1[1] - ny * e1[0]};
        List<String> out = new ArrayList<>(k);
        out.sort((p, q) -> {
            float[] P = pos.get(p), Q = pos.get(q);
            double ap = Math.atan2(dot(P, cx, cy, cz, e2), dot(P, cx, cy, cz, e1));
            double aq = Math.atan2(dot(Q, cx, cy, cz, e2), dot(Q, cx, cy, cz, e1));
            return Double.compare(ap, aq);
        });
        return out;
    }

    private static float dot(float[] p, float cx, float cy, float cz, float[] e) {
        return (p[0] - cx) * e[0] + (p[1] - cy) * e[1] + (p[2] - cz) * e[2];
    }

    // ------------------------------------------------------------------ json helpers

    static String str(JsonObject o, String k, String d) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? d : e.getAsString();
    }

    static float num(JsonObject o, String k, float d) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return d;
        try {
            return e.getAsFloat();
        } catch (RuntimeException ex) {
            return d;
        }
    }

    static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    static float[] vec(JsonObject o, String k, float d) {
        JsonArray a = arr(o, k);
        float[] r = {d, d, d};
        if (a != null) for (int i = 0; i < 3 && i < a.size(); i++) r[i] = a.get(i).getAsFloat();
        return r;
    }

    static float[] vec4(JsonObject o, String k) {
        JsonArray a = arr(o, k);
        if (a == null || a.size() < 4) return null;
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()};
    }
}
