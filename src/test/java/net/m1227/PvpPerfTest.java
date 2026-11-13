package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import net.m1227.client.perf.Stats;
import net.m1227.combat.HitEvidence;
import net.m1227.net.StatusPing;
import org.junit.Test;

/** 1.21: statistics, hit classification rules and the status ping exchange. No game needed. */
public class PvpPerfTest {

    @Test
    public void percentilesAreNearestRank() {
        float[] v = new float[100];
        for (int i = 0; i < 100; i++) v[i] = 100 - i; // 100..1, unsorted on purpose
        assertEquals(50, Stats.percentile(v, 100, 50, null), 1e-9);
        assertEquals(95, Stats.percentile(v, 100, 95, null), 1e-9);
        assertEquals(99, Stats.percentile(v, 100, 99, null), 1e-9);
        assertEquals(100, Stats.percentile(v, 100, 100, null), 1e-9);
        assertEquals(100, v[0], 1e-9); // the caller's array keeps its order
        assertTrue(Double.isNaN(Stats.percentile(v, 0, 50, null)));
    }

    @Test
    public void onePercentLowAveragesTheSlowestFrames() {
        float[] t = new float[1000];
        for (int i = 0; i < 1000; i++) t[i] = 5f; // 200 FPS
        for (int i = 990; i < 1000; i++) t[i] = 50f; // the slowest 1%: 20 FPS
        assertEquals(20.0, Stats.lowFps(t, 1000, 1), 1e-6);
        assertEquals(20.0, Stats.lowFps(t, 1000, 0.1), 1e-6);
    }

    @Test
    public void jitterFollowsRfc3550() {
        Stats.Jitter j = new Stats.Jitter();
        for (int i = 0; i < 50; i++) j.add(40);
        assertEquals(0.0, j.value(), 1e-9); // steady: no jitter
        Stats.Jitter k = new Stats.Jitter();
        for (int i = 0; i < 400; i++) k.add(i % 2 == 0 ? 30 : 50); // alternating 20 ms
        assertEquals(20.0, k.value(), 0.5);
        Stats.Jitter few = new Stats.Jitter();
        few.add(1);
        few.add(2);
        assertTrue(Double.isNaN(few.value())); // not enough samples
    }

    @Test
    public void seriesWindowKeepsOrderAndAge() {
        Stats.Series s = new Stats.Series(4);
        for (int i = 0; i < 6; i++) s.add(i * 1_000_000_000L, i);
        assertEquals(4, s.size());
        assertEquals(2f, s.value(0), 0f);
        assertEquals(5f, s.last(), 0f);
        float[] out = new float[4];
        int n = s.window(5_000_000_000L, 2_000_000_000L, out);
        assertEquals(3, n);
        assertEquals(3f, out[0], 0f);
    }

    @Test
    public void windowsAndCooldown() {
        assertEquals(600, HitEvidence.windowMs(Double.NaN), 1e-9);
        assertEquals(150, HitEvidence.windowMs(10), 1e-9);
        assertEquals(270, HitEvidence.windowMs(100), 1e-9);
        assertEquals(1000, HitEvidence.windowMs(900), 1e-9);
        assertEquals(0, HitEvidence.floorMs(Double.NaN), 1e-9);
        assertEquals(50, HitEvidence.floorMs(100), 1e-9);
        // 100 ms round trip: an attempt 300 ms after the last hurt reaches the server in its immunity
        assertTrue(HitEvidence.inCooldown(1300, 1000, 100));
        assertFalse(HitEvidence.inCooldown(1500, 1000, 100));
        assertFalse(HitEvidence.inCooldown(1300, -1, 100));
    }

    @Test
    public void classificationLevels() {
        HitEvidence.Facts f = new HitEvidence.Facts();
        f.hurt = true;
        assertEquals(HitEvidence.Level.MODERATE, HitEvidence.classify(f).level);
        f.knockback = true;
        assertEquals(HitEvidence.Level.STRONG, HitEvidence.classify(f).level);
        f.otherAttacker = true; // someone else could have done it
        assertEquals(HitEvidence.Level.WEAK, HitEvidence.classify(f).level);

        HitEvidence.Facts g = new HitEvidence.Facts();
        g.hurt = true;
        g.tooFast = true; // arrived before any reply could
        assertEquals(HitEvidence.Level.WEAK, HitEvidence.classify(g).level);

        HitEvidence.Facts h = new HitEvidence.Facts();
        assertEquals(HitEvidence.Level.NONE, HitEvidence.classify(h).level);
        h.cooldown = true;
        assertEquals(HitEvidence.Level.UNKNOWN, HitEvidence.classify(h).level);
        h.cooldown = false;
        h.targetGone = true;
        assertEquals(HitEvidence.Level.UNKNOWN, HitEvidence.classify(h).level);

        HitEvidence.Facts i = new HitEvidence.Facts();
        i.healthDrop = true; // stronger hit during immunity: damage without the hurt status
        assertEquals(HitEvidence.Level.MODERATE, HitEvidence.classify(i).level);

        HitEvidence.Facts k = new HitEvidence.Facts();
        k.observer = false;
        assertEquals(HitEvidence.Level.UNKNOWN, HitEvidence.classify(k).level);
        k.clientHurtOnly = true;
        assertEquals(HitEvidence.Level.WEAK, HitEvidence.classify(k).level);
        assertTrue(HitEvidence.Level.STRONG.likely() && HitEvidence.Level.MODERATE.likely() && !HitEvidence.Level.WEAK.likely());
    }

    @Test
    public void feedbackRateLeavesOutWeakAndUnknown() {
        assertTrue(Double.isNaN(HitEvidence.feedbackRate(1, 1, 1)));
        assertEquals(0.75, HitEvidence.feedbackRate(5, 4, 3), 1e-9);
    }

    // ---- status ping against a tiny local server speaking the 1.8 status protocol

    private static int readVarInt(DataInputStream in) throws Exception {
        int v = 0, s = 0;
        while (true) {
            int b = in.readUnsignedByte();
            v |= (b & 0x7F) << s;
            if ((b & 0x80) == 0) return v;
            s += 7;
        }
    }

    private static void writeVarInt(DataOutputStream d, int v) throws Exception {
        while ((v & ~0x7F) != 0) {
            d.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        d.writeByte(v);
    }

    @Test
    public void statusPingMeasuresPingPong() throws Exception {
        final ServerSocket ss = new ServerSocket(0);
        final int[] protocol = new int[1];
        Thread t = new Thread(() -> {
            try (Socket c = ss.accept()) {
                DataInputStream in = new DataInputStream(c.getInputStream());
                DataOutputStream out = new DataOutputStream(c.getOutputStream());
                readVarInt(in); // handshake length
                readVarInt(in); // id
                protocol[0] = readVarInt(in);
                int hl = readVarInt(in);
                in.readFully(new byte[hl]);
                in.readUnsignedShort();
                readVarInt(in); // next state
                readVarInt(in); // request length
                readVarInt(in); // request id
                byte[] json = "{\"version\":{\"name\":\"1.8.9\",\"protocol\":47}}".getBytes("UTF-8");
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream d = new DataOutputStream(b);
                writeVarInt(d, 0);
                writeVarInt(d, json.length);
                d.write(json);
                writeVarInt(out, b.size());
                out.write(b.toByteArray());
                out.flush();
                readVarInt(in); // ping length
                readVarInt(in); // ping id
                long token = in.readLong();
                Thread.sleep(20); // the "network"
                writeVarInt(out, 9);
                writeVarInt(out, 1);
                out.writeLong(token);
                out.flush();
            } catch (Exception ignored) {
                // the test checks the result
            }
        });
        t.start();
        float ms = StatusPing.ping("127.0.0.1", ss.getLocalPort(), 3000);
        t.join(3000);
        ss.close();
        assertEquals(47, protocol[0]);
        assertTrue("round trip " + ms, ms >= 19 && ms < 1000);
    }
}
