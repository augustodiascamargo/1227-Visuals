package net.m1227.weapons;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Wavefront .obj with its .mtl: polygons (fan-triangulated), UVs, normals, Kd colour and map_Kd texture. */
public final class ObjReader {
    private ObjReader() {}

    public static WModel read(File f) throws Exception {
        List<String> lines = java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
        WModel m = new WModel();
        m.format = "OBJ";
        m.unit = 1f;
        List<float[]> v = new ArrayList<>(), vt = new ArrayList<>(), vn = new ArrayList<>();
        Map<String, int[]> mtl = new HashMap<>(); // name -> {tex, color}
        Map<String, Boolean> mtlGlow = new HashMap<>();
        String cur = "";
        String group = "";
        Map<String, WModel.Part> parts = new HashMap<>();
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] t = l.split("\\s+");
            switch (t[0]) {
                case "v":
                    v.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                    break;
                case "vt":
                    vt.add(new float[]{Float.parseFloat(t[1]), t.length > 2 ? 1f - Float.parseFloat(t[2]) : 0f});
                    break;
                case "vn":
                    vn.add(new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                    break;
                case "mtllib":
                    loadMtl(m, f, l.substring(7).trim(), mtl, mtlGlow);
                    break;
                case "usemtl":
                    cur = t.length > 1 ? l.substring(6).trim() : "";
                    break;
                case "o":
                case "g":
                    group = t.length > 1 ? l.substring(2).trim() : "";
                    break;
                case "f": {
                    int n = t.length - 1;
                    float[][] P = new float[n][], U = new float[n][], N = new float[n][];
                    for (int k = 0; k < n; k++) {
                        String[] c = t[k + 1].split("/", -1);
                        P[k] = v.get(idx(c[0], v.size()));
                        U[k] = c.length > 1 && !c[1].isEmpty() ? vt.get(idx(c[1], vt.size())) : new float[]{0f, 0f};
                        N[k] = c.length > 2 && !c[2].isEmpty() ? vn.get(idx(c[2], vn.size())) : null;
                    }
                    String key = cur + "\u0000" + group;
                    WModel.Part p = parts.get(key);
                    if (p == null) {
                        p = new WModel.Part();
                        int[] mm = mtl.get(cur);
                        p.tex = mm != null ? mm[0] : -1;
                        p.color = mm != null ? mm[1] : 0xFFFFFFFF;
                        p.name = group;
                        p.emissive = BbModelReader.glowName(group) || Boolean.TRUE.equals(mtlGlow.get(cur));
                        p.translucent = p.tex >= 0 && m.textures.get(p.tex).translucent;
                        parts.put(key, p);
                        m.parts.add(p);
                    }
                    for (int k = 1; k + 1 < n; k++) {
                        if (N[0] != null && N[k] != null && N[k + 1] != null) {
                            for (int c : new int[]{0, k, k + 1}) p.add(P[c][0], P[c][1], P[c][2], U[c][0], U[c][1], N[c][0], N[c][1], N[c][2]);
                        } else {
                            p.tri(P[0], P[k], P[k + 1], U[0], U[k], U[k + 1]);
                        }
                    }
                    break;
                }
                default:
            }
        }
        return m;
    }

    private static int idx(String s, int size) {
        int i = Integer.parseInt(s);
        return i < 0 ? size + i : i - 1;
    }

    private static void loadMtl(WModel m, File obj, String name, Map<String, int[]> out, Map<String, Boolean> glow) {
        File f = new File(obj.getParentFile(), name);
        if (!f.isFile()) {
            m.warnings.add("material file " + name + " not found");
            return;
        }
        try {
            String cur = null;
            for (String raw : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1)) {
                String l = raw.trim();
                String[] t = l.split("\\s+");
                if (t[0].equals("newmtl")) {
                    cur = l.substring(6).trim();
                    out.put(cur, new int[]{-1, 0xFFFFFFFF});
                } else if (cur != null && t[0].equals("Kd") && t.length >= 4) {
                    int r = Math.round(Float.parseFloat(t[1]) * 255), g = Math.round(Float.parseFloat(t[2]) * 255), b = Math.round(Float.parseFloat(t[3]) * 255);
                    if (out.get(cur)[0] < 0) out.get(cur)[1] = 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
                } else if (cur != null && t[0].equals("Ke") && t.length >= 4) {
                    glow.put(cur, Float.parseFloat(t[1]) + Float.parseFloat(t[2]) + Float.parseFloat(t[3]) > 0.3f);
                } else if (cur != null && t[0].equals("map_Kd") && t.length >= 2) {
                    String img = t[t.length - 1];
                    File imf = Images.find(f, img);
                    if (imf != null) {
                        m.textures.add(Images.read(imf));
                        out.get(cur)[0] = m.textures.size() - 1;
                        out.get(cur)[1] = 0xFFFFFFFF;
                    } else {
                        m.warnings.add("texture " + img + " not found");
                    }
                }
            }
        } catch (Exception e) {
            m.warnings.add("material file: " + e.getMessage());
        }
    }
}
