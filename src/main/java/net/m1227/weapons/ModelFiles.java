package net.m1227.weapons;

import java.io.File;

/** Picks the reader by file extension. Pure. */
public final class ModelFiles {
    private ModelFiles() {}

    public static final String[] EXTENSIONS = {".bbmodel", ".gltf", ".glb", ".fbx", ".obj"};

    public static boolean isModel(File f) {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        for (String e : EXTENSIONS) if (n.endsWith(e)) return true;
        return false;
    }

    public static WModel load(File f) throws Exception {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        WModel m;
        if (n.endsWith(".bbmodel")) m = BbModelReader.read(f);
        else if (n.endsWith(".gltf") || n.endsWith(".glb")) m = GltfReader.read(f);
        else if (n.endsWith(".fbx")) m = FbxReader.read(f);
        else if (n.endsWith(".obj")) m = ObjReader.read(f);
        else throw new IllegalArgumentException("not a model file");
        if (m.parts.isEmpty() || m.triangles() == 0) throw new IllegalArgumentException("the file has no visible geometry");
        return m;
    }

    /** "m48-kukri.bbmodel" -> "M48 Kukri"; generic names (model, scene, source) use the folder's name. */
    public static String displayName(File f) {
        String base = f.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        File dir = f.getParentFile();
        while (dir != null && isGeneric(base)) {
            base = dir.getName();
            dir = dir.getParentFile();
        }
        String[] w = base.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String s : w) {
            if (s.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return b.length() == 0 ? "Model" : b.toString();
    }

    private static boolean isGeneric(String s) {
        String l = s.toLowerCase(java.util.Locale.ROOT);
        return l.equals("model") || l.equals("scene") || l.equals("source") || l.equals("mesh") || l.equals("untitled") || l.equals("textures");
    }
}
