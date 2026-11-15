package net.m1227.weapons;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

/** PNG / JPEG decoding into ARGB for {@link WModel.Tex}. Pure (javax.imageio). */
final class Images {
    private Images() {}

    /** Largest texture side kept; bigger images are halved until they fit (big stitched sheets). */
    static final int MAX_SIDE = 2048;

    static WModel.Tex decode(byte[] data, String name) throws Exception {
        BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) throw new IllegalArgumentException("not an image: " + name);
        return from(img, name);
    }

    static WModel.Tex read(File f) throws Exception {
        BufferedImage img = javax.imageio.ImageIO.read(f);
        if (img == null) throw new IllegalArgumentException("not an image: " + f.getName());
        return from(img, f.getName());
    }

    static WModel.Tex from(BufferedImage img, String name) {
        while (img.getWidth() > MAX_SIDE || img.getHeight() > MAX_SIDE) {
            int w = Math.max(1, img.getWidth() / 2), h = Math.max(1, img.getHeight() / 2);
            BufferedImage s = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = s.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
            img = s;
        }
        WModel.Tex t = new WModel.Tex();
        t.name = name == null ? "" : name;
        t.w = img.getWidth();
        t.h = img.getHeight();
        t.argb = img.getRGB(0, 0, t.w, t.h, null, 0, t.w);
        WModel.classify(t);
        return t;
    }

    static byte[] dataUri(String uri) {
        int c = uri.indexOf(',');
        if (c < 0) return null;
        String head = uri.substring(0, c);
        String body = uri.substring(c + 1);
        if (head.endsWith(";base64")) return java.util.Base64.getMimeDecoder().decode(body);
        try {
            return java.net.URLDecoder.decode(body, "UTF-8").getBytes("ISO-8859-1");
        } catch (Exception e) {
            return null;
        }
    }

    /** Finds an image file named like 'ref' (any folder separators) next to the model or in nearby texture folders. */
    static File find(File modelFile, String ref) {
        if (ref == null || ref.isEmpty()) return null;
        String base = ref.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        if (base.isEmpty()) return null;
        File dir = modelFile.getParentFile();
        File[] tries = {
                new File(dir, ref.replace('\\', '/')),
                new File(dir, base),
                new File(dir, "textures/" + base),
                dir.getParentFile() == null ? null : new File(dir.getParentFile(), base),
                dir.getParentFile() == null ? null : new File(dir.getParentFile(), "textures/" + base)};
        for (File f : tries) if (f != null && f.isFile()) return f;
        return null;
    }
}
