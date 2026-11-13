package net.m1227.net;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * The server-list status exchange (Minecraft 1.8 protocol 47), used only to time a round trip:
 * handshake (next state: status), status request, read the status reply and ignore it, then a
 * ping with a number and the server's pong with the same number. Only the ping to pong part is
 * timed, so connecting and the status text do not count. This is what the multiplayer list does
 * when it shows a server's ping; it is a separate connection and never touches the game's own.
 * Background thread only.
 */
public final class StatusPing {
    private StatusPing() {}

    /** Round trip in ms; throws on any failure or timeout. */
    public static float ping(String host, int port, int timeoutMs) throws IOException {
        Socket s = new Socket();
        try {
            s.setTcpNoDelay(true);
            s.setSoTimeout(timeoutMs);
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(s.getInputStream());
            // handshake
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            varInt(h, 0x00);
            varInt(h, 47);
            byte[] hb = host.getBytes(StandardCharsets.UTF_8);
            varInt(h, hb.length);
            h.write(hb);
            h.writeShort(port & 0xFFFF);
            varInt(h, 1);
            frame(out, hs.toByteArray());
            // status request
            frame(out, new byte[]{0x00});
            out.flush();
            // status reply: read and discard
            int len = readVarInt(in);
            if (len <= 0 || len > 1 << 21) throw new IOException("bad status length");
            in.readFully(new byte[len]);
            // ping / pong
            long token = System.nanoTime();
            ByteArrayOutputStream ps = new ByteArrayOutputStream();
            DataOutputStream p = new DataOutputStream(ps);
            varInt(p, 0x01);
            p.writeLong(token);
            byte[] pb = ps.toByteArray();
            long t0 = System.nanoTime();
            frame(out, pb);
            out.flush();
            int plen = readVarInt(in);
            int id = readVarInt(in);
            long back = in.readLong();
            long t1 = System.nanoTime();
            if (plen != 9 || id != 0x01 || back != token) throw new IOException("bad pong");
            return (t1 - t0) / 1e6f;
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing a finished probe
            }
        }
    }

    private static void frame(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        varInt(d, body.length);
        d.write(body);
        out.write(b.toByteArray());
    }

    static void varInt(DataOutputStream d, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            d.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        d.writeByte(v);
    }

    static int readVarInt(InputStream in) throws IOException {
        int v = 0, shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("closed");
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
            if (shift > 35) throw new IOException("varint too long");
        }
    }
}
