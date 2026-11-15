package net.m1227.weapons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * glTF 2.0 (.gltf with embedded or neighbouring buffers, and binary .glb): the node tree
 * (TRS or matrix), triangle meshes with UVs and normals, the base colour texture and factor,
 * emissive and blended materials, and node animations (translation, rotation, scale; linear,
 * step, cubic spline read as linear). Skins and morph targets are not used (reported).
 */
public final class GltfReader {
    private GltfReader() {}

    public static WModel read(File f) throws Exception {
        byte[] all = java.nio.file.Files.readAllBytes(f.toPath());
        String json;
        byte[] bin = null;
        if (all.length >= 12 && all[0] == 'g' && all[1] == 'l' && all[2] == 'T' && all[3] == 'F') {
            ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
            int pos = 12;
            String j = null;
            while (pos + 8 <= all.length) {
                int len = b.getInt(pos), type = b.getInt(pos + 4);
                if (type == 0x4E4F534A) j = new String(all, pos + 8, len, "UTF-8");
                else if (type == 0x004E4942) bin = java.util.Arrays.copyOfRange(all, pos + 8, pos + 8 + len);
                pos += 8 + len;
            }
            if (j == null) throw new IllegalArgumentException("GLB without JSON chunk");
            json = j;
        } else {
            json = new String(all, "UTF-8");
        }
        return parse(json, bin, f);
    }

    public static WModel parse(String json, byte[] glbBin, File f) throws Exception {
        JsonObject g = new JsonParser().parse(json).getAsJsonObject();
        WModel m = new WModel();
        m.format = "glTF";
        m.unit = 1f;
        JsonObject asset = BbModelReader.obj(g, "asset");
        if (asset != null) m.credit = BbModelReader.str(asset, "copyright", "");

        // buffers
        JsonArray bufs = BbModelReader.arr(g, "buffers");
        List<byte[]> buffers = new ArrayList<>();
        if (bufs != null) for (JsonElement be : bufs) {
            JsonObject bo = be.getAsJsonObject();
            String uri = BbModelReader.str(bo, "uri", "");
            byte[] data;
            if (uri.isEmpty()) data = glbBin;
            else if (uri.startsWith("data:")) data = Images.dataUri(uri);
            else {
                File bf = f == null ? null : new File(f.getParentFile(), java.net.URLDecoder.decode(uri, "UTF-8"));
                data = bf != null && bf.isFile() ? java.nio.file.Files.readAllBytes(bf.toPath()) : null;
            }
            if (data == null) throw new IllegalArgumentException("missing buffer " + uri);
            buffers.add(data);
        }
        Acc acc = new Acc(g, buffers);

        // images -> textures (index by glTF texture index)
        JsonArray images = BbModelReader.arr(g, "images");
        JsonArray textures = BbModelReader.arr(g, "textures");
        int[] texOfGltf = new int[textures == null ? 0 : textures.size()];
        java.util.Arrays.fill(texOfGltf, -1);
        if (textures != null) for (int i = 0; i < textures.size(); i++) {
            JsonObject t = textures.get(i).getAsJsonObject();
            if (!t.has("source") || images == null) continue;
            int src = t.get("source").getAsInt();
            if (src < 0 || src >= images.size()) continue;
            JsonObject im = images.get(src).getAsJsonObject();
            String name = BbModelReader.str(im, "name", "image" + src);
            try {
                WModel.Tex tex = null;
                String uri = BbModelReader.str(im, "uri", "");
                if (uri.startsWith("data:")) tex = Images.decode(Images.dataUri(uri), name);
                else if (!uri.isEmpty() && f != null) {
                    File img = new File(f.getParentFile(), java.net.URLDecoder.decode(uri, "UTF-8"));
                    if (!img.isFile()) img = Images.find(f, uri);
                    if (img != null) tex = Images.read(img);
                } else if (im.has("bufferView")) {
                    tex = Images.decode(acc.view(im.get("bufferView").getAsInt()), name);
                }
                if (tex != null) {
                    texOfGltf[i] = m.textures.size();
                    m.textures.add(tex);
                } else {
                    m.warnings.add("image " + name + " not found");
                }
            } catch (Exception e) {
                m.warnings.add("image " + name + ": " + e.getMessage());
            }
        }
        JsonArray mats = BbModelReader.arr(g, "materials");

        // nodes -> bones (bone index = node index + 1, root 0)
        JsonArray nodes = BbModelReader.arr(g, "nodes");
        int nn = nodes == null ? 0 : nodes.size();
        int[] parentOf = new int[nn];
        java.util.Arrays.fill(parentOf, -1);
        for (int i = 0; i < nn; i++) {
            JsonArray ch = BbModelReader.arr(nodes.get(i).getAsJsonObject(), "children");
            if (ch != null) for (JsonElement c : ch) parentOf[c.getAsInt()] = i;
        }
        // parents must come first in the bone list
        int[] boneOf = new int[nn];
        java.util.Arrays.fill(boneOf, -1);
        for (int pass = 0; pass < nn + 1; pass++) {
            boolean any = false;
            for (int i = 0; i < nn; i++) {
                if (boneOf[i] >= 0) continue;
                int p = parentOf[i];
                if (p >= 0 && boneOf[p] < 0) continue;
                JsonObject n = nodes.get(i).getAsJsonObject();
                WModel.Bone b = new WModel.Bone();
                b.name = BbModelReader.str(n, "name", "node" + i);
                b.parent = p >= 0 ? boneOf[p] : 0;
                float[] t = floats(n, "translation", 3, 0f), r = floats(n, "rotation", 4, 0f), s = floats(n, "scale", 3, 1f);
                if (!n.has("rotation")) r[3] = 1f;
                b.tx = t[0];
                b.ty = t[1];
                b.tz = t[2];
                b.qx = r[0];
                b.qy = r[1];
                b.qz = r[2];
                b.qw = r[3];
                b.sx = s[0];
                b.sy = s[1];
                b.sz = s[2];
                if (n.has("matrix")) {
                    b.post = floats(n, "matrix", 16, 0f);
                }
                boneOf[i] = m.bones.size();
                m.bones.add(b);
                any = true;
            }
            if (!any) break;
        }

        JsonArray meshes = BbModelReader.arr(g, "meshes");
        boolean skinned = false;
        for (int i = 0; i < nn; i++) {
            JsonObject n = nodes.get(i).getAsJsonObject();
            if (n.has("skin")) skinned = true;
            if (!n.has("mesh") || meshes == null) continue;
            JsonObject mesh = meshes.get(n.get("mesh").getAsInt()).getAsJsonObject();
            JsonArray prims = BbModelReader.arr(mesh, "primitives");
            if (prims == null) continue;
            for (JsonElement pe : prims) {
                JsonObject pr = pe.getAsJsonObject();
                int mode = pr.has("mode") ? pr.get("mode").getAsInt() : 4;
                if (mode != 4 && mode != 5 && mode != 6) continue;
                JsonObject at = BbModelReader.obj(pr, "attributes");
                if (at == null || !at.has("POSITION")) continue;
                float[] P = acc.floats(at.get("POSITION").getAsInt());
                float[] N = at.has("NORMAL") ? acc.floats(at.get("NORMAL").getAsInt()) : null;
                float[] T = at.has("TEXCOORD_0") ? acc.floats(at.get("TEXCOORD_0").getAsInt()) : null;
                int nv = P.length / 3;
                int[] idx;
                if (pr.has("indices")) idx = acc.ints(pr.get("indices").getAsInt());
                else {
                    idx = new int[nv];
                    for (int k = 0; k < nv; k++) idx[k] = k;
                }
                idx = triangles(idx, mode);
                int tex = -1, color = 0xFFFFFFFF;
                boolean emissive = false, blend = false;
                if (pr.has("material") && mats != null) {
                    JsonObject mat = mats.get(pr.get("material").getAsInt()).getAsJsonObject();
                    JsonObject pbr = BbModelReader.obj(mat, "pbrMetallicRoughness");
                    if (pbr != null) {
                        JsonObject bt = BbModelReader.obj(pbr, "baseColorTexture");
                        if (bt != null && bt.has("index")) {
                            int ti = bt.get("index").getAsInt();
                            if (ti >= 0 && ti < texOfGltf.length) tex = texOfGltf[ti];
                        }
                        float[] bc = floats(pbr, "baseColorFactor", 4, 1f);
                        color = argb(bc);
                    }
                    float[] ef = floats(mat, "emissiveFactor", 3, 0f);
                    emissive = ef[0] + ef[1] + ef[2] > 0.3f || mat.has("emissiveTexture")
                            || BbModelReader.glowName(BbModelReader.str(mat, "name", ""));
                    blend = BbModelReader.str(mat, "alphaMode", "OPAQUE").equals("BLEND");
                }
                if (BbModelReader.glowName(BbModelReader.str(n, "name", ""))) emissive = true;
                WModel.Part part = new WModel.Part();
                part.bone = boneOf[i];
                part.tex = tex;
                part.emissive = emissive;
                part.color = color;
                part.name = BbModelReader.str(n, "name", "");
                part.translucent = blend || (tex >= 0 && m.textures.get(tex).translucent);
                for (int k = 0; k + 2 < idx.length; k += 3) {
                    boolean flat = N == null;
                    float[][] pv = new float[3][], uv = new float[3][];
                    for (int c = 0; c < 3; c++) {
                        int v = idx[k + c];
                        if (v < 0 || v >= nv) continue;
                        pv[c] = new float[]{P[v * 3], P[v * 3 + 1], P[v * 3 + 2]};
                        uv[c] = T != null ? new float[]{T[v * 2], T[v * 2 + 1]} : new float[]{0f, 0f};
                    }
                    if (pv[0] == null || pv[1] == null || pv[2] == null) continue;
                    if (flat) part.tri(pv[0], pv[1], pv[2], uv[0], uv[1], uv[2]);
                    else for (int c = 0; c < 3; c++) {
                        int v = idx[k + c];
                        part.add(pv[c][0], pv[c][1], pv[c][2], uv[c][0], uv[c][1], N[v * 3], N[v * 3 + 1], N[v * 3 + 2]);
                    }
                }
                if (part.verts > 0) m.parts.add(part);
            }
        }
        if (skinned) m.warnings.add("skinned mesh: drawn in its rest pose (skins are not supported)");

        // animations
        JsonArray anims = BbModelReader.arr(g, "animations");
        if (anims != null) for (int ai = 0; ai < anims.size(); ai++) {
            JsonObject a = anims.get(ai).getAsJsonObject();
            WModel.Clip c = new WModel.Clip();
            c.name = BbModelReader.str(a, "name", "animation" + ai);
            c.loop = 0;
            JsonArray samplers = BbModelReader.arr(a, "samplers"), chans = BbModelReader.arr(a, "channels");
            if (samplers == null || chans == null) continue;
            for (JsonElement ce : chans) {
                JsonObject ch = ce.getAsJsonObject();
                JsonObject tg = BbModelReader.obj(ch, "target");
                if (tg == null || !tg.has("node")) continue;
                String path = BbModelReader.str(tg, "path", "");
                int channel = path.equals("rotation") ? WModel.ROT : path.equals("translation") ? WModel.POS : path.equals("scale") ? WModel.SCALE : -1;
                if (channel < 0) continue;
                JsonObject s = samplers.get(ch.get("sampler").getAsInt()).getAsJsonObject();
                float[] times = acc.floats(s.get("input").getAsInt()), vals = acc.floats(s.get("output").getAsInt());
                String ip = BbModelReader.str(s, "interpolation", "LINEAR");
                int comps = channel == WModel.ROT ? 4 : 3;
                boolean cubic = ip.equals("CUBICSPLINE");
                WModel.Track tr = new WModel.Track();
                tr.bone = boneOf[tg.get("node").getAsInt()];
                tr.channel = channel;
                for (int k = 0; k < times.length; k++) {
                    WModel.Key key = new WModel.Key();
                    key.t = times[k];
                    key.interp = ip.equals("STEP") ? WModel.STEP : WModel.LINEAR;
                    int o = cubic ? (k * 3 + 1) * comps : k * comps; // cubic: in-tangent, value, out-tangent
                    if (o + comps > vals.length) break;
                    if (channel == WModel.ROT) {
                        key.quat = new float[]{vals[o], vals[o + 1], vals[o + 2], vals[o + 3]};
                    } else {
                        key.pre = new Molang.Expr[]{new Molang.Const(vals[o]), new Molang.Const(vals[o + 1]), new Molang.Const(vals[o + 2])};
                        key.post = key.pre;
                    }
                    tr.keys.add(key);
                    c.length = Math.max(c.length, key.t);
                }
                if (!tr.keys.isEmpty()) c.tracks.add(tr);
            }
            if (!c.tracks.isEmpty()) m.clips.add(c);
        }
        return m;
    }

    private static int argb(float[] c) {
        int a = clamp(c[3]), r = clamp(c[0]), gg = clamp(c[1]), b = clamp(c[2]);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private static int[] triangles(int[] idx, int mode) {
        if (mode == 4) return idx;
        List<Integer> out = new ArrayList<>();
        for (int i = 2; i < idx.length; i++) {
            if (mode == 5) {
                if ((i & 1) == 0) {
                    out.add(idx[i - 2]);
                    out.add(idx[i - 1]);
                } else {
                    out.add(idx[i - 1]);
                    out.add(idx[i - 2]);
                }
                out.add(idx[i]);
            } else {
                out.add(idx[0]);
                out.add(idx[i - 1]);
                out.add(idx[i]);
            }
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static float[] floats(JsonObject o, String k, int n, float d) {
        float[] r = new float[n];
        java.util.Arrays.fill(r, d);
        JsonArray a = BbModelReader.arr(o, k);
        if (a != null) for (int i = 0; i < n && i < a.size(); i++) r[i] = a.get(i).getAsFloat();
        return r;
    }

    /** Accessor reading (bufferView / byteStride / normalized integer types). */
    private static final class Acc {
        final JsonArray accessors, views;
        final List<byte[]> buffers;

        Acc(JsonObject g, List<byte[]> buffers) {
            accessors = BbModelReader.arr(g, "accessors");
            views = BbModelReader.arr(g, "bufferViews");
            this.buffers = buffers;
        }

        byte[] view(int i) {
            JsonObject v = views.get(i).getAsJsonObject();
            byte[] b = buffers.get(v.get("buffer").getAsInt());
            int off = v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0;
            return java.util.Arrays.copyOfRange(b, off, off + v.get("byteLength").getAsInt());
        }

        private static int comps(String type) {
            switch (type) {
                case "SCALAR":
                    return 1;
                case "VEC2":
                    return 2;
                case "VEC3":
                    return 3;
                case "VEC4":
                case "MAT2":
                    return 4;
                case "MAT3":
                    return 9;
                default:
                    return 16;
            }
        }

        private static int size(int ct) {
            return ct == 5126 || ct == 5125 ? 4 : ct == 5123 || ct == 5122 ? 2 : 1;
        }

        double[] read(int ai) {
            JsonObject a = accessors.get(ai).getAsJsonObject();
            int count = a.get("count").getAsInt(), ct = a.get("componentType").getAsInt();
            int nc = comps(a.get("type").getAsString());
            boolean norm = a.has("normalized") && a.get("normalized").getAsBoolean();
            double[] out = new double[count * nc];
            if (!a.has("bufferView")) return out;
            JsonObject v = views.get(a.get("bufferView").getAsInt()).getAsJsonObject();
            byte[] b = buffers.get(v.get("buffer").getAsInt());
            int base = (v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0) + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
            int stride = v.has("byteStride") ? v.get("byteStride").getAsInt() : nc * size(ct);
            ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) {
                for (int c = 0; c < nc; c++) {
                    int p = base + i * stride + c * size(ct);
                    double x;
                    switch (ct) {
                        case 5126:
                            x = bb.getFloat(p);
                            break;
                        case 5125:
                            x = bb.getInt(p) & 0xFFFFFFFFL;
                            break;
                        case 5123:
                            x = bb.getShort(p) & 0xFFFF;
                            if (norm) x /= 65535.0;
                            break;
                        case 5122:
                            x = bb.getShort(p);
                            if (norm) x = Math.max(-1.0, x / 32767.0);
                            break;
                        case 5121:
                            x = b[p] & 0xFF;
                            if (norm) x /= 255.0;
                            break;
                        default:
                            x = b[p];
                            if (norm) x = Math.max(-1.0, x / 127.0);
                    }
                    out[i * nc + c] = x;
                }
            }
            return out;
        }

        float[] floats(int ai) {
            double[] d = read(ai);
            float[] f = new float[d.length];
            for (int i = 0; i < d.length; i++) f[i] = (float) d[i];
            return f;
        }

        int[] ints(int ai) {
            double[] d = read(ai);
            int[] r = new int[d.length];
            for (int i = 0; i < d.length; i++) r[i] = (int) d[i];
            return r;
        }
    }
}
