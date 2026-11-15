package net.m1227.weapons;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary FBX (7.x, as Blender, Maya and Sketchfab export it): the model tree with its full local
 * transform (translation, rotation with pre/post rotation, pivots, scale, rotation order),
 * polygon meshes (fan-triangulated) with UVs and normals, and each material's diffuse colour or
 * texture (embedded, or an image found next to the file / in a "textures" folder). FBX
 * animations are not read: export to glTF to keep them. ASCII FBX is not supported.
 */
public final class FbxReader {
    private FbxReader() {}

    static final class Node {
        String name;
        final List<Object> props = new ArrayList<>();
        final List<Node> kids = new ArrayList<>();

        Node child(String n) {
            for (Node k : kids) if (k.name.equals(n)) return k;
            return null;
        }

        Object prop(int i) {
            return i < props.size() ? props.get(i) : null;
        }
    }

    public static WModel read(File f) throws Exception {
        byte[] d = java.nio.file.Files.readAllBytes(f.toPath());
        if (d.length < 27 || !new String(d, 0, 18, "ISO-8859-1").equals("Kaydara FBX Binary"))
            throw new IllegalArgumentException("only binary FBX files are supported (export as binary, or use glTF)");
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int ver = b.getInt(23);
        boolean big = ver >= 7500;
        List<Node> top = new ArrayList<>();
        int[] pos = {27};
        while (pos[0] < d.length - 13) {
            Node n = node(d, b, pos, big);
            if (n == null) break;
            top.add(n);
        }
        return build(top, f);
    }

    private static Node node(byte[] d, ByteBuffer b, int[] pos, boolean big) throws Exception {
        int p = pos[0];
        long end, nprops;
        if (big) {
            end = b.getLong(p);
            nprops = b.getLong(p + 8);
            p += 24;
        } else {
            end = b.getInt(p) & 0xFFFFFFFFL;
            nprops = b.getInt(p + 4) & 0xFFFFFFFFL;
            p += 12;
        }
        int nl = d[p] & 0xFF;
        p++;
        if (end == 0) {
            pos[0] = p + nl;
            return null;
        }
        Node n = new Node();
        n.name = new String(d, p, nl, "ISO-8859-1");
        p += nl;
        for (long i = 0; i < nprops; i++) {
            char t = (char) d[p++];
            switch (t) {
                case 'Y':
                    n.props.add((double) b.getShort(p));
                    p += 2;
                    break;
                case 'C':
                    n.props.add((double) d[p]);
                    p += 1;
                    break;
                case 'I':
                    n.props.add((double) b.getInt(p));
                    p += 4;
                    break;
                case 'F':
                    n.props.add((double) b.getFloat(p));
                    p += 4;
                    break;
                case 'D':
                    n.props.add(b.getDouble(p));
                    p += 8;
                    break;
                case 'L':
                    n.props.add(b.getLong(p));
                    p += 8;
                    break;
                case 'f':
                case 'd':
                case 'l':
                case 'i':
                case 'b': {
                    int cnt = b.getInt(p), enc = b.getInt(p + 4), cl = b.getInt(p + 8);
                    p += 12;
                    byte[] raw = java.util.Arrays.copyOfRange(d, p, p + cl);
                    p += cl;
                    if (enc == 1) {
                        java.util.zip.Inflater inf = new java.util.zip.Inflater();
                        inf.setInput(raw);
                        int es = t == 'd' || t == 'l' ? 8 : t == 'b' ? 1 : 4;
                        byte[] out = new byte[cnt * es];
                        int got = 0;
                        while (got < out.length && !inf.finished()) {
                            int k = inf.inflate(out, got, out.length - got);
                            if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                            got += k;
                        }
                        inf.end();
                        raw = out;
                    }
                    ByteBuffer rb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                    if (t == 'd' || t == 'f') {
                        double[] a = new double[cnt];
                        for (int k = 0; k < cnt; k++) a[k] = t == 'd' ? rb.getDouble(k * 8) : rb.getFloat(k * 4);
                        n.props.add(a);
                    } else if (t == 'i' || t == 'l') {
                        long[] a = new long[cnt];
                        for (int k = 0; k < cnt; k++) a[k] = t == 'l' ? rb.getLong(k * 8) : rb.getInt(k * 4);
                        n.props.add(a);
                    } else {
                        n.props.add(raw);
                    }
                    break;
                }
                case 'S':
                case 'R': {
                    int len = b.getInt(p);
                    p += 4;
                    byte[] raw = java.util.Arrays.copyOfRange(d, p, p + len);
                    p += len;
                    n.props.add(t == 'S' ? new String(raw, "ISO-8859-1") : raw);
                    break;
                }
                default:
                    throw new IllegalArgumentException("bad FBX property type " + t);
            }
        }
        int[] cp = {p};
        while (cp[0] < end) {
            Node c = node(d, b, cp, big);
            if (c == null) break;
            n.kids.add(c);
        }
        pos[0] = (int) end;
        return n;
    }

    private static String clean(Object o) {
        if (!(o instanceof String)) return "";
        String s = (String) o;
        int z = s.indexOf('\0');
        return z >= 0 ? s.substring(0, z) : s;
    }

    private static long id(Node n) {
        Object o = n.prop(0);
        return o instanceof Long ? (Long) o : o instanceof Double ? ((Double) o).longValue() : 0L;
    }

    private static Map<String, double[]> props70(Node n) {
        Map<String, double[]> r = new HashMap<>();
        Node p = n.child("Properties70");
        if (p == null) return r;
        for (Node e : p.kids) {
            if (!e.name.equals("P")) continue;
            String key = clean(e.prop(0));
            List<Double> v = new ArrayList<>();
            for (int i = 4; i < e.props.size(); i++) if (e.props.get(i) instanceof Double) v.add((Double) e.props.get(i));
            double[] a = new double[v.size()];
            for (int i = 0; i < a.length; i++) a[i] = v.get(i);
            r.put(key, a);
            if (e.props.size() > 4 && e.props.get(4) instanceof String) r.put(key + "#s", new double[0]);
        }
        return r;
    }

    private static String propString(Node n, String key) {
        Node p = n.child("Properties70");
        if (p == null) return null;
        for (Node e : p.kids) if (e.name.equals("P") && clean(e.prop(0)).equals(key) && e.props.size() > 4) return clean(e.prop(4));
        return null;
    }

    private static float[] v3(Map<String, double[]> p, String k, float d) {
        double[] a = p.get(k);
        if (a == null || a.length < 3) return new float[]{d, d, d};
        return new float[]{(float) a[0], (float) a[1], (float) a[2]};
    }

    /** m = m * rotation for an FBX Euler order (0 XYZ .. 5 ZYX; first letter applied first). */
    private static void euler(float[] m, float[] r, int order) {
        String[] o = {"XYZ", "XZY", "YZX", "YXZ", "ZXY", "ZYX"};
        String s = o[Math.max(0, Math.min(5, order))];
        for (int i = 2; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == 'X') M4.rotate(m, r[0], 1, 0, 0);
            else if (c == 'Y') M4.rotate(m, r[1], 0, 1, 0);
            else M4.rotate(m, r[2], 0, 0, 1);
        }
    }

    private static float[] inverseRot(float[] r, int order) {
        // inverse of an Euler rotation = the reverse sequence with negated angles
        float[] m = M4.identity();
        String[] o = {"XYZ", "XZY", "YZX", "YXZ", "ZXY", "ZYX"};
        String s = o[Math.max(0, Math.min(5, order))];
        for (int i = 0; i < 3; i++) {
            char c = s.charAt(i);
            if (c == 'X') M4.rotate(m, -r[0], 1, 0, 0);
            else if (c == 'Y') M4.rotate(m, -r[1], 0, 1, 0);
            else M4.rotate(m, -r[2], 0, 0, 1);
        }
        return m;
    }

    private static WModel build(List<Node> top, File f) throws Exception {
        WModel m = new WModel();
        m.format = "FBX";
        Node objects = null, conns = null, global = null;
        for (Node n : top) {
            if (n.name.equals("Objects")) objects = n;
            else if (n.name.equals("Connections")) conns = n;
            else if (n.name.equals("GlobalSettings")) global = n;
        }
        if (objects == null) throw new IllegalArgumentException("FBX without objects");
        float unitCm = 1f;
        int up = 1;
        if (global != null) {
            Map<String, double[]> gp = props70(global);
            if (gp.containsKey("UnitScaleFactor") && gp.get("UnitScaleFactor").length > 0) unitCm = (float) gp.get("UnitScaleFactor")[0];
            if (gp.containsKey("UpAxis") && gp.get("UpAxis").length > 0) up = (int) gp.get("UpAxis")[0];
        }
        m.unit = 100f / Math.max(1e-6f, unitCm);

        Map<Long, Node> byId = new HashMap<>();
        for (Node n : objects.kids) byId.put(id(n), n);
        Map<Long, Long> modelParent = new HashMap<>();
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        Map<Long, List<Long>> matsOf = new HashMap<>();
        Map<Long, Long> geomOf = new HashMap<>();
        Map<Long, Long> texOfMat = new HashMap<>();
        Map<Long, Long> videoOfTex = new HashMap<>();
        if (conns != null) for (Node c : conns.kids) {
            if (!c.name.equals("C") || c.props.size() < 3) continue;
            String type = clean(c.prop(0));
            long child = c.prop(1) instanceof Long ? (Long) c.prop(1) : 0L, parent = c.prop(2) instanceof Long ? (Long) c.prop(2) : 0L;
            Node cn = byId.get(child), pn = byId.get(parent);
            if (cn == null) continue;
            if (cn.name.equals("Model") && (pn == null || pn.name.equals("Model"))) {
                modelParent.put(child, pn == null ? 0L : parent);
                List<Long> l = childrenOf.get(pn == null ? 0L : parent);
                if (l == null) childrenOf.put(pn == null ? 0L : parent, l = new ArrayList<>());
                l.add(child);
            } else if (cn.name.equals("Geometry") && pn != null && pn.name.equals("Model")) {
                geomOf.put(parent, child);
            } else if (cn.name.equals("Material") && pn != null && pn.name.equals("Model")) {
                List<Long> l = matsOf.get(parent);
                if (l == null) matsOf.put(parent, l = new ArrayList<>());
                l.add(child);
            } else if (cn.name.equals("Texture") && pn != null && pn.name.equals("Material")) {
                String what = c.props.size() > 3 ? clean(c.prop(3)) : "";
                if (!texOfMat.containsKey(parent) || what.contains("Diffuse") || what.contains("Base")) texOfMat.put(parent, child);
            } else if (cn.name.equals("Video") && pn != null && pn.name.equals("Texture")) {
                videoOfTex.put(parent, child);
            }
        }
        if (up == 2) {
            // Z-up file: turn it so Z points up (+Y)
            WModel.Bone b = m.bones.get(0);
            b.post = M4.identity();
            M4.rotate(b.post, -90f, 1f, 0f, 0f);
            b.euler = false;
        } else {
            m.bones.get(0).euler = false;
        }
        Map<Long, Integer> matTex = new HashMap<>();
        Map<String, Integer> texByFile = new HashMap<>();
        // breadth-first so parents come first
        List<Long> queue = new ArrayList<>();
        Map<Long, Integer> boneOf = new HashMap<>();
        boneOf.put(0L, 0);
        queue.add(0L);
        for (int qi = 0; qi < queue.size(); qi++) {
            long pid = queue.get(qi);
            List<Long> kids = childrenOf.get(pid);
            if (kids == null) continue;
            for (long mid : kids) {
                if (boneOf.containsKey(mid)) continue;
                Node mn = byId.get(mid);
                Map<String, double[]> p = props70(mn);
                int order = p.containsKey("RotationOrder") && p.get("RotationOrder").length > 0 ? (int) p.get("RotationOrder")[0] : 0;
                float[] T = v3(p, "Lcl Translation", 0f), R = v3(p, "Lcl Rotation", 0f), S = v3(p, "Lcl Scaling", 1f);
                float[] Rpre = v3(p, "PreRotation", 0f), Rpost = v3(p, "PostRotation", 0f);
                float[] Rp = v3(p, "RotationPivot", 0f), Ro = v3(p, "RotationOffset", 0f), Sp = v3(p, "ScalingPivot", 0f), So = v3(p, "ScalingOffset", 0f);
                float[] local = M4.identity();
                M4.translate(local, T[0] + Ro[0] + Rp[0], T[1] + Ro[1] + Rp[1], T[2] + Ro[2] + Rp[2]);
                euler(local, Rpre, 0);
                euler(local, R, order);
                M4.mul(local, inverseRot(Rpost, 0), local);
                M4.translate(local, -Rp[0] + So[0] + Sp[0], -Rp[1] + So[1] + Sp[1], -Rp[2] + So[2] + Sp[2]);
                M4.scale(local, S[0], S[1], S[2]);
                M4.translate(local, -Sp[0], -Sp[1], -Sp[2]);
                WModel.Bone b = new WModel.Bone();
                b.name = clean(mn.prop(1));
                b.parent = boneOf.get(pid);
                b.post = local;
                int bi = m.bones.size();
                m.bones.add(b);
                boneOf.put(mid, bi);
                queue.add(mid);

                Long gid = geomOf.get(mid);
                if (gid == null) continue;
                float[] geo = M4.identity();
                float[] GT = v3(p, "GeometricTranslation", 0f), GR = v3(p, "GeometricRotation", 0f), GS = v3(p, "GeometricScaling", 1f);
                M4.translate(geo, GT[0], GT[1], GT[2]);
                euler(geo, GR, 0);
                M4.scale(geo, GS[0], GS[1], GS[2]);
                List<Long> mats = matsOf.containsKey(mid) ? matsOf.get(mid) : new ArrayList<Long>();
                int[] texOfSlot = new int[Math.max(1, mats.size())];
                int[] colOfSlot = new int[texOfSlot.length];
                java.util.Arrays.fill(texOfSlot, -1);
                java.util.Arrays.fill(colOfSlot, 0xFFFFFFFF);
                boolean[] glowSlot = new boolean[texOfSlot.length];
                for (int s = 0; s < mats.size(); s++) {
                    long mat = mats.get(s);
                    Node matN = byId.get(mat);
                    Map<String, double[]> mp = props70(matN);
                    float[] dc = v3(mp, mp.containsKey("DiffuseColor") ? "DiffuseColor" : "Diffuse", 1f);
                    colOfSlot[s] = 0xFF000000 | (Math.round(Math.min(1f, dc[0]) * 255) << 16) | (Math.round(Math.min(1f, dc[1]) * 255) << 8) | Math.round(Math.min(1f, dc[2]) * 255);
                    float[] em = v3(mp, "EmissiveColor", 0f);
                    float ef = mp.containsKey("EmissiveFactor") && mp.get("EmissiveFactor").length > 0 ? (float) mp.get("EmissiveFactor")[0] : 1f;
                    glowSlot[s] = (em[0] + em[1] + em[2]) * ef > 0.3f || BbModelReader.glowName(clean(matN.prop(1)));
                    if (matTex.containsKey(mat)) {
                        texOfSlot[s] = matTex.get(mat);
                    } else {
                        Long tid = texOfMat.get(mat);
                        int ti = tid == null ? -1 : texture(m, f, byId.get(tid), videoOfTex.containsKey(tid) ? byId.get(videoOfTex.get(tid)) : null, texByFile);
                        matTex.put(mat, ti);
                        texOfSlot[s] = ti;
                    }
                    if (texOfSlot[s] >= 0) colOfSlot[s] = 0xFFFFFFFF;
                }
                mesh(m, byId.get(gid), geo, bi, texOfSlot, colOfSlot, glowSlot, b.name);
            }
        }
        if (m.parts.isEmpty()) m.warnings.add("no meshes found");
        for (Node n : objects.kids) {
            if (n.name.equals("AnimationStack")) {
                m.warnings.add("FBX animations are not imported (export the model as glTF to keep them)");
                break;
            }
        }
        return m;
    }

    private static int texture(WModel m, File f, Node tex, Node video, Map<String, Integer> byFile) {
        if (tex == null) return -1;
        try {
            if (video != null) {
                Node content = video.child("Content");
                if (content != null && content.prop(0) instanceof byte[] && ((byte[]) content.prop(0)).length > 16) {
                    WModel.Tex t = Images.decode((byte[]) content.prop(0), clean(video.prop(1)));
                    m.textures.add(t);
                    return m.textures.size() - 1;
                }
            }
            List<String> names = new ArrayList<>();
            for (Node src : new Node[]{tex, video}) {
                if (src == null) continue;
                for (String k : new String[]{"RelativeFilename", "FileName", "Filename"}) {
                    Node n = src.child(k);
                    if (n != null) names.add(clean(n.prop(0)));
                }
                String path = propString(src, "Path");
                if (path != null) names.add(path);
            }
            for (String nm : names) {
                if (byFile.containsKey(nm)) return byFile.get(nm);
                File img = Images.find(f, nm);
                if (img != null) {
                    WModel.Tex t = Images.read(img);
                    m.textures.add(t);
                    byFile.put(nm, m.textures.size() - 1);
                    return m.textures.size() - 1;
                }
            }
            if (!names.isEmpty()) m.warnings.add("texture not found next to the model: " + names.get(0).replace('\\', '/').replaceAll(".*/", ""));
        } catch (Exception e) {
            m.warnings.add("texture: " + e.getMessage());
        }
        return -1;
    }

    private static void mesh(WModel m, Node g, float[] geo, int bone, int[] texOfSlot, int[] colOfSlot, boolean[] glowSlot, String name) {
        Node vn = g.child("Vertices"), pn = g.child("PolygonVertexIndex");
        if (vn == null || pn == null || !(vn.prop(0) instanceof double[]) || !(pn.prop(0) instanceof long[])) return;
        double[] V = (double[]) vn.prop(0);
        long[] PI = (long[]) pn.prop(0);
        if (V.length == 0) return;
        Node uvL = g.child("LayerElementUV"), nL = g.child("LayerElementNormal"), mL = g.child("LayerElementMaterial");
        double[] UV = uvL != null && uvL.child("UV") != null ? (double[]) uvL.child("UV").prop(0) : null;
        long[] UVI = uvL != null && uvL.child("UVIndex") != null ? (long[]) uvL.child("UVIndex").prop(0) : null;
        String uvMap = uvL != null && uvL.child("MappingInformationType") != null ? clean(uvL.child("MappingInformationType").prop(0)) : "";
        String uvRef = uvL != null && uvL.child("ReferenceInformationType") != null ? clean(uvL.child("ReferenceInformationType").prop(0)) : "";
        double[] NN = nL != null && nL.child("Normals") != null ? (double[]) nL.child("Normals").prop(0) : null;
        long[] NI = nL != null && nL.child("NormalsIndex") != null ? (long[]) nL.child("NormalsIndex").prop(0) : null;
        String nMap = nL != null && nL.child("MappingInformationType") != null ? clean(nL.child("MappingInformationType").prop(0)) : "";
        long[] MI = mL != null && mL.child("Materials") != null && mL.child("Materials").prop(0) instanceof long[] ? (long[]) mL.child("Materials").prop(0) : null;
        String mMap = mL != null && mL.child("MappingInformationType") != null ? clean(mL.child("MappingInformationType").prop(0)) : "AllSame";
        WModel.Part[] parts = new WModel.Part[texOfSlot.length];
        List<int[]> poly = new ArrayList<>(); // [vertex, polygon-vertex index]
        int polyIndex = 0;
        float[] tp = new float[3];
        for (int i = 0; i < PI.length; i++) {
            long raw = PI[i];
            boolean last = raw < 0;
            int vi = (int) (last ? ~raw : raw);
            poly.add(new int[]{vi, i});
            if (!last) continue;
            int slot = 0;
            if (MI != null && MI.length > 0) slot = (int) (mMap.equals("AllSame") ? MI[0] : MI[Math.min(MI.length - 1, polyIndex)]);
            slot = Math.max(0, Math.min(parts.length - 1, slot));
            if (parts[slot] == null) {
                parts[slot] = new WModel.Part();
                parts[slot].bone = bone;
                parts[slot].tex = texOfSlot[slot];
                parts[slot].color = colOfSlot[slot];
                parts[slot].emissive = glowSlot[slot] || BbModelReader.glowName(name);
                parts[slot].name = name;
                parts[slot].translucent = texOfSlot[slot] >= 0 && m.textures.get(texOfSlot[slot]).translucent;
                m.parts.add(parts[slot]);
            }
            int n = poly.size();
            float[][] P = new float[n][3], U = new float[n][2], N = new float[n][];
            for (int k = 0; k < n; k++) {
                int v = poly.get(k)[0], pv = poly.get(k)[1];
                if (v * 3 + 2 >= V.length) continue;
                M4.point(geo, (float) V[v * 3], (float) V[v * 3 + 1], (float) V[v * 3 + 2], P[k]);
                if (UV != null) {
                    int ui = uvMap.equals("ByControlPoint") ? v : pv;
                    if (uvRef.equals("IndexToDirect") && UVI != null && ui < UVI.length) ui = (int) UVI[ui];
                    if (ui >= 0 && ui * 2 + 1 < UV.length) {
                        U[k][0] = (float) UV[ui * 2];
                        U[k][1] = 1f - (float) UV[ui * 2 + 1]; // FBX v goes up
                    }
                }
                if (NN != null) {
                    int ni = nMap.equals("ByControlPoint") || nMap.equals("ByVertice") ? v : pv;
                    if (NI != null && ni < NI.length) ni = (int) NI[ni];
                    if (ni >= 0 && ni * 3 + 2 < NN.length) {
                        M4.dir(geo, (float) NN[ni * 3], (float) NN[ni * 3 + 1], (float) NN[ni * 3 + 2], tp);
                        float l = (float) Math.sqrt(tp[0] * tp[0] + tp[1] * tp[1] + tp[2] * tp[2]);
                        if (l > 1e-6f) N[k] = new float[]{tp[0] / l, tp[1] / l, tp[2] / l};
                    }
                }
            }
            for (int k = 1; k + 1 < n; k++) {
                int[] tri = {0, k, k + 1};
                if (N[0] != null && N[k] != null && N[k + 1] != null) {
                    for (int c : tri) parts[slot].add(P[c][0], P[c][1], P[c][2], U[c][0], U[c][1], N[c][0], N[c][1], N[c][2]);
                } else {
                    parts[slot].tri(P[0], P[k], P[k + 1], U[0], U[k], U[k + 1]);
                }
            }
            poly.clear();
            polyIndex++;
        }
    }
}
