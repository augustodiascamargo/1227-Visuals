package net.m1227;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import net.m1227.social.LinkProtocol;
import net.m1227.social.WsClient;
import org.junit.Test;

/** The 1227 Link WebSocket client against a tiny local server (handshake, frames, ping, close). */
public class WsClientTest {
    private static byte[] readFrame(DataInputStream in) throws Exception {
        int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte();
        int len = b1 & 0x7F;
        if (len == 126) len = in.readUnsignedShort();
        byte[] mask = new byte[4];
        boolean masked = (b1 & 0x80) != 0;
        if (masked) in.readFully(mask);
        byte[] p = new byte[len];
        in.readFully(p);
        if (masked) for (int i = 0; i < len; i++) p[i] ^= mask[i & 3];
        byte[] out = new byte[len + 1];
        out[0] = (byte) (b0 & 0x0F);
        System.arraycopy(p, 0, out, 1, len);
        if (!masked) throw new AssertionError("client frames must be masked");
        return out;
    }

    private static void frame(OutputStream o, int op, byte[] p) throws Exception {
        o.write(0x80 | op);
        o.write(p.length);
        o.write(p);
        o.flush();
    }

    @Test
    public void handshakeFramesPingAndClose() throws Exception {
        final ServerSocket ss = new ServerSocket(0);
        final BlockingQueue<byte[]> fromClient = new ArrayBlockingQueue<byte[]>(8);
        final String[] path = new String[1];
        Thread server = new Thread(() -> {
            try (Socket c = ss.accept()) {
                DataInputStream in = new DataInputStream(c.getInputStream());
                ByteArrayOutputStream head = new ByteArrayOutputStream();
                while (!head.toString("ISO-8859-1").endsWith("\r\n\r\n")) head.write(in.read());
                String req = head.toString("ISO-8859-1");
                path[0] = req.substring(4, req.indexOf(" HTTP/1.1"));
                String key = null;
                for (String l : req.split("\r\n")) if (l.startsWith("Sec-WebSocket-Key:")) key = l.substring(18).trim();
                String acc = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                OutputStream o = c.getOutputStream();
                o.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + acc + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                frame(o, 0x2, new byte[]{0x27, 1, 0x41});
                frame(o, 0x9, new byte[]{7});
                fromClient.add(readFrame(in)); // pong
                fromClient.add(readFrame(in)); // binary from the client
                frame(o, 0x8, new byte[0]);
                fromClient.add(readFrame(in)); // close reply
            } catch (Exception e) {
                fromClient.add(new byte[]{(byte) 0xEE});
            }
        });
        server.start();
        final BlockingQueue<byte[]> got = new ArrayBlockingQueue<byte[]>(8);
        final BlockingQueue<String> closed = new ArrayBlockingQueue<String>(2);
        WsClient c = WsClient.connect("ws://127.0.0.1:" + ss.getLocalPort() + "/v1/room/abc?u=1&n=x", 3000, null, "test", new WsClient.Listener() {
            @Override
            public void onBinary(byte[] data) {
                got.add(data);
            }

            @Override
            public void onClose(String reason) {
                closed.add(reason);
            }
        });
        assertArrayEquals(new byte[]{0x27, 1, 0x41}, got.poll(3, TimeUnit.SECONDS));
        byte[] pong = fromClient.poll(3, TimeUnit.SECONDS);
        assertEquals(0xA, pong[0]);
        assertEquals(7, pong[1]);
        c.sendBinary(new byte[]{0x27, 1, 2, 9});
        byte[] bin = fromClient.poll(3, TimeUnit.SECONDS);
        assertEquals(0x2, bin[0]);
        assertArrayEquals(new byte[]{0x27, 1, 2, 9}, java.util.Arrays.copyOfRange(bin, 1, bin.length));
        assertEquals(0x8, fromClient.poll(3, TimeUnit.SECONDS)[0]);
        assertTrue(closed.poll(3, TimeUnit.SECONDS) != null);
        assertEquals("/v1/room/abc?u=1&n=x", path[0]);
        ss.close();
    }

    @Test
    public void roomIdHidesTheAddressAndIgnoresCaseAndDefaultPort() {
        String a = LinkProtocol.roomOf("Mc.Example.net"), b = LinkProtocol.roomOf("mc.example.net:25565"), c = LinkProtocol.roomOf("mc.example.net:25566");
        assertEquals(a, b);
        assertTrue(!a.equals(c));
        assertEquals(32, a.length());
        assertTrue(a.matches("[0-9a-f]{32}"));
    }
}
