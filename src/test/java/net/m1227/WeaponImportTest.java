package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.m1227.weapons.BbModelReader;
import net.m1227.weapons.GltfReader;
import net.m1227.weapons.M4;
import net.m1227.weapons.Molang;
import net.m1227.weapons.ModelFiles;
import net.m1227.weapons.WModel;
import net.m1227.weapons.WeaponAnimator;
import net.m1227.weapons.WeaponFit;
import net.m1227.weapons.WeaponProfile;
import org.junit.Test;

/** Weapon model import: Blockbench cubes and animations, glTF, Molang, fitting and the clip slots. */
public class WeaponImportTest {

    /** A 2x2x2 cube at the origin with one textured face, a group pivot and a two-key animation. */
    private static String bb(String interp, String extraAnim) {
        return "{\"meta\":{\"format_version\":\"5.0\",\"model_format\":\"free\"},\"name\":\"t\",\"resolution\":{\"width\":16,\"height\":16},"
                + "\"elements\":[{\"name\":\"c\",\"type\":\"cube\",\"uuid\":\"e1\",\"from\":[0,0,0],\"to\":[2,2,2],\"origin\":[0,0,0],"
                + "\"faces\":{\"north\":{\"uv\":[0,0,8,8],\"texture\":0},\"up\":{\"uv\":[0,0,16,16],\"texture\":0,\"rotation\":90},\"down\":{\"uv\":[0,0,0,0],\"texture\":null}}}],"
                + "\"groups\":[{\"name\":\"blade\",\"uuid\":\"g1\",\"origin\":[1,0,1],\"rotation\":[0,0,0]}],"
                + "\"outliner\":[{\"uuid\":\"g1\",\"children\":[\"e1\"]}],"
                + "\"textures\":[],"
                + "\"animations\":[{\"name\":\"animation.sword.swing\",\"loop\":\"once\",\"length\":1,\"animators\":{\"g1\":{\"name\":\"blade\",\"type\":\"bone\",\"keyframes\":["
                + "{\"channel\":\"rotation\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"0\"}],\"time\":0,\"interpolation\":\"" + interp + "\"},"
                + "{\"channel\":\"rotation\",\"data_points\":[{\"x\":\"0\",\"y\":\"0\",\"z\":\"90\"}],\"time\":1,\"interpolation\":\"" + interp + "\"}" + extraAnim
                + "]}}}]}";
    }

    @Test
    public void bbmodelCubesFacesAndGroups() {
        WModel m = BbModelReader.parse(bb("linear", ""), null);
        assertEquals(2, m.bones.size());
        assertEquals("blade", m.bones.get(1).name);
        assertEquals(1, m.parts.size());
        WModel.Part p = m.parts.get(0);
        assertEquals(1, p.bone);
        // north + up faces (down has no texture) = 4 triangles
        assertEquals(12, p.verts);
        // north face, first corner is top-left = (x2, y2, z1) with uv (0, 0); textures missing -> uv / resolution
        assertEquals(2f, p.v[0], 1e-6);
        assertEquals(2f, p.v[1], 1e-6);
        assertEquals(0f, p.v[2], 1e-6);
        assertEquals(0f, p.v[3], 1e-6);
        assertEquals(0f, p.v[4], 1e-6);
        // the last corner of the first triangle is top-right (x1, y2, z1) with uv (8/16, 0)
        assertEquals(0f, p.v[40], 1e-6);
        assertEquals(0.5f, p.v[43], 1e-6);
        // north normal points out (-Z)
        assertEquals(-1f, p.v[7], 1e-5);
        // up face rotated 90: its top-left corner takes the bottom-left uv (0, 1)
        int up = 6 * 8;
        assertEquals(0f, p.v[up + 3], 1e-6);
        assertEquals(1f, p.v[up + 4], 1e-6);
        assertEquals(1f, p.v[up + 6], 1e-5);
        assertEquals("swing", m.clips.get(0).name);
        assertEquals(0, m.clips.get(0).loop);
    }

    @Test
    public void elementRotationTurnsAroundOrigin() {
        String j = "{\"meta\":{\"model_format\":\"free\"},\"elements\":[{\"type\":\"cube\",\"uuid\":\"e\",\"from\":[0,0,0],\"to\":[1,1,1],\"origin\":[0,0,0],"
                + "\"rotation\":[0,0,90],\"faces\":{\"east\":{\"uv\":[0,0,1,1],\"texture\":0}}}],\"outliner\":[\"e\"]}";
        WModel m = BbModelReader.parse(j, null);
        // east face (x = 1) turned 90 degrees around Z ends at y = 1
        WModel.Part p = m.parts.get(0);
        for (int i = 0; i < p.verts; i++) assertEquals(1f, p.v[i * 8 + 1], 1e-5);
    }

    @Test
    public void linearAndStepAndCatmullSampling() {
        WModel m = BbModelReader.parse(bb("linear", ""), null);
        WModel.Pose pose = new WModel.Pose(m.bones.size());
        m.sample(m.clips.get(0), 0.5f, 0f, pose);
        assertEquals(45f, pose.rot[1 * 3 + 2], 1e-4);
        WModel s = BbModelReader.parse(bb("step", ""), null);
        s.sample(s.clips.get(0), 0.5f, 0f, pose);
        assertEquals(0f, pose.rot[5], 1e-4);
        WModel c = BbModelReader.parse(bb("catmullrom", ""), null);
        c.sample(c.clips.get(0), 0.5f, 0f, pose);
        assertEquals(45f, pose.rot[5], 1e-3); // two keys: Catmull-Rom with clamped ends passes the middle
        c.sample(c.clips.get(0), 1f, 0f, pose);
        assertEquals(90f, pose.rot[5], 1e-4);
    }

    @Test
    public void blockbenchAnimationSignsAndPivot() {
        WModel m = BbModelReader.parse(bb("linear", ""), null);
        WModel.Pose pose = new WModel.Pose(m.bones.size());
        m.sample(m.clips.get(0), 1f, 0f, pose);
        float[][] bm = m.boneMatrices(pose);
        float[] o = new float[3];
        // +90 around Z about the pivot (1, 0, 1): the point (2, 0, 1) goes to (1, 1, 1)
        M4.point(bm[1], 2f, 0f, 1f, o);
        assertEquals(1f, o[0], 1e-5);
        assertEquals(1f, o[1], 1e-5);
        assertEquals(1f, o[2], 1e-5);
    }

    @Test
    public void molangSubset() {
        java.util.List<String> unk = new java.util.ArrayList<>();
        assertEquals(7f, Molang.parse("1 + 2 * 3", unk).eval(0f, 0f), 1e-6);
        assertEquals(1f, Molang.parse("math.sin(90)", unk).eval(0f, 0f), 1e-6);
        assertEquals(20f, Molang.parse("query.anim_time * 10", unk).eval(2f, 0f), 1e-6);
        assertEquals(3f, Molang.parse("q.anim_time > 1 ? 3 : 4", unk).eval(2f, 0f), 1e-6);
        assertEquals(2f, Molang.parse("math.clamp(5, 0, 2)", unk).eval(0f, 0f), 1e-6);
        assertTrue(unk.isEmpty());
        assertEquals(0f, Molang.parse("variable.foo + 1 *", unk).eval(0f, 0f), 1e-6);
        assertFalse(unk.isEmpty());
    }

    @Test
    public void gltfEmbeddedTriangle() throws Exception {
        ByteBuffer b = ByteBuffer.allocate(36 + 6).order(ByteOrder.LITTLE_ENDIAN);
        float[] pos = {0, 0, 0, 1, 0, 0, 0, 2, 0};
        for (float f : pos) b.putFloat(f);
        b.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        String data = java.util.Base64.getEncoder().encodeToString(b.array());
        String j = "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":42,\"uri\":\"data:application/octet-stream;base64," + data + "\"}],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1}]}],"
                + "\"nodes\":[{\"mesh\":0,\"translation\":[5,0,0],\"name\":\"blade\"}],\"scenes\":[{\"nodes\":[0]}]}";
        WModel m = GltfReader.parse(j, null, null);
        assertEquals(2, m.bones.size());
        assertEquals(3, m.parts.get(0).verts);
        float[] o = new float[3];
        M4.point(m.boneMatrices(null)[1], 1f, 0f, 0f, o);
        assertEquals(6f, o[0], 1e-6);
        assertEquals(1f, m.unit, 0f);
    }

    /** A sword standing on +Y: thin handle (y 0..4), wide guard (y 4..5), blade (y 5..20). */
    private static String sword(boolean upsideDown) {
        String[][] boxes = {{"-0.5", "0", "-0.5", "0.5", "4", "0.5"}, {"-3", "4", "-0.5", "3", "5", "0.5"}, {"-1", "5", "-0.25", "1", "20", "0.25"}};
        StringBuilder e = new StringBuilder();
        for (int i = 0; i < boxes.length; i++) {
            String[] q = boxes[i];
            float y0 = Float.parseFloat(q[1]), y1 = Float.parseFloat(q[4]);
            if (upsideDown) {
                float a = -y1, c = -y0;
                y0 = a;
                y1 = c;
            }
            if (i > 0) e.append(',');
            e.append("{\"type\":\"cube\",\"uuid\":\"e").append(i).append("\",\"from\":[").append(q[0]).append(',').append(y0).append(',').append(q[2])
                    .append("],\"to\":[").append(q[3]).append(',').append(y1).append(',').append(q[5]).append("],\"origin\":[0,0,0],\"faces\":{");
            String[] d = {"north", "south", "east", "west", "up", "down"};
            for (int k = 0; k < 6; k++) e.append(k > 0 ? "," : "").append('"').append(d[k]).append("\":{\"uv\":[0,0,1,1],\"texture\":0}");
            e.append("}}");
        }
        return "{\"meta\":{\"model_format\":\"free\"},\"elements\":[" + e + "]}";
    }

    @Test
    public void fitFindsBladeAxisTipAndGrip() {
        WModel m = BbModelReader.parse(sword(false), null);
        WeaponFit f = WeaponFit.compute(m, false, -1f);
        assertEquals(1f, f.a[1], 1e-4);         // blade along +Y, tip up
        assertEquals(1f, Math.abs(f.n[0]), 1e-4); // width along X
        assertTrue("grip in the handle: " + f.grip[1], f.grip[1] > 0.5f && f.grip[1] < 3.5f);
        // weapon space: the tip ends 1.3 * (1 - gripAt) item units along +X
        float[] w = f.matrix(new WeaponProfile());
        float[] o = new float[3];
        M4.point(w, 0f, 20f, 0f, o);
        assertEquals(WeaponFit.TARGET * (1f - f.gripAt), o[0], 1e-3);
        assertEquals(0f, o[1], 1e-3);
        // the same sword upside down: Y still points to the tip after the fit flips it
        WeaponFit g = WeaponFit.compute(BbModelReader.parse(sword(true), null), true, -1f);
        assertEquals(-1f, g.a[1], 1e-4);
        assertTrue(g.grip[1] < -0.5f && g.grip[1] > -3.5f);
    }

    @Test
    public void animatorSlotsByName() {
        WModel m = BbModelReader.parse(bb("linear", ""), null);
        WModel.Clip idle = new WModel.Clip();
        idle.name = "idle";
        idle.loop = 1;
        idle.length = 2f;
        m.clips.add(idle);
        WeaponAnimator a = new WeaponAnimator();
        a.bind(m, new WeaponProfile());
        assertNotNull(a.clip(WeaponAnimator.SWING));
        assertEquals("swing", a.clip(WeaponAnimator.SWING).name);
        assertEquals("idle", a.clip(WeaponAnimator.IDLE).name);
        assertNull(a.clip(WeaponAnimator.DRAW));
        WeaponProfile p = new WeaponProfile();
        p.swing = "-";
        a.bind(m, p);
        assertNull(a.clip(WeaponAnimator.SWING));
        // a triggered swing blends in and ends by itself
        a.bind(m, new WeaponProfile());
        a.trigger(WeaponAnimator.SWING, 10f);
        assertTrue(a.busy(10.5f));
        WModel.Pose mid = a.pose(10.5f);
        assertTrue(mid.rot[5] > 30f && mid.rot[5] < 60f);
        a.pose(11.2f);
        assertEquals(-1, a.action());
    }

    @Test
    public void displayNames() {
        assertEquals("M48 Kukri", ModelFiles.displayName(new File("m48-kukri.bbmodel")));
        assertEquals("Karambit", ModelFiles.displayName(new File("karambit/source/model.gltf")));
        assertTrue(ModelFiles.isModel(new File("a.GLB")));
        assertFalse(ModelFiles.isModel(new File("a.png")));
    }
}
