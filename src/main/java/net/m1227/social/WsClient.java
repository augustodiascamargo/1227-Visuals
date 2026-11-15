package net.m1227.social;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A small WebSocket client (RFC 6455) for binary messages, in plain Java 8 with no library: the
 * 1227 Link online relay is the only user. ws:// and wss:// (TLS with SNI through the given socket
 * factory). One reader thread per connection; sending is synchronized. Client frames are masked as
 * the RFC requires; pings are answered; messages over 64 KB close the connection.
 */
public final class WsClient implements Closeable {
    public interface Listener {
        void onBinary(byte[] data);

        void onClose(String reason);
    }

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_MESSAGE = 65536;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Listener listener;
    private volatile boolean closed;

    private WsClient(Socket socket, Listener listener) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.listener = listener;
    }

    /** Connects, does the opening handshake and starts the reader thread. */
    public static WsClient connect(String url, int timeoutMs, SSLSocketFactory tls, String userAgent, Listener l) throws IOException {
        URI u = URI.create(url);
        boolean secure = "wss".equalsIgnoreCase(u.getScheme());
        if (!secure && !"ws".equalsIgnoreCase(u.getScheme())) throw new IOException("not a ws:// or wss:// address");
        String host = u.getHost();
        if (host == null) throw new IOException("no host");
        int port = u.getPort() > 0 ? u.getPort() : secure ? 443 : 80;
        Socket raw = new Socket();
        raw.setTcpNoDelay(true);
        raw.connect(new InetSocketAddress(host, port), timeoutMs);
        Socket s = raw;
        try {
            if (secure) {
                SSLSocket ssl = (SSLSocket) tls.createSocket(raw, host, port, true); // host name = SNI
                // check that the certificate is for this host name (plain SSL sockets do not by default)
                javax.net.ssl.SSLParameters sp = ssl.getSSLParameters();
                sp.setEndpointIdentificationAlgorithm("HTTPS");
                ssl.setSSLParameters(sp);
                ssl.startHandshake();
                s = ssl;
            }
            s.setSoTimeout(timeoutMs);
            WsClient c = new WsClient(s, l);
            c.handshake(u, host, port, secure, userAgent);
            s.setSoTimeout(0);
            Thread t = new Thread(c::readLoop, "1227-Link-WebSocket");
            t.setDaemon(true);
            t.start();
            return c;
        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignored) {
                // already failing
            }
            throw e;
        }
    }

    private void handshake(URI u, String host, int port, boolean secure, String ua) throws IOException {
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String path = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath()) + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
        boolean defPort = (secure && port == 443) || (!secure && port == 80);
        String req = "GET " + path + " HTTP/1.1\r\nHost: " + host + (defPort ? "" : ":" + port) + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\nUser-Agent: " + ua + "\r\n\r\n";
        out.write(req.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        // response head
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int b = in.read();
            if (b < 0) throw new EOFException("closed during handshake");
            head.write(b);
            if (head.size() > 8192) throw new IOException("handshake response too long");
            state = (b == '\r' && (state == 0 || state == 2)) || (b == '\n' && (state == 1 || state == 3)) ? state + 1 : (b == '\r' ? 1 : 0);
        }
        String[] lines = new String(head.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        if (lines.length == 0 || !lines[0].matches("HTTP/1\\.1 101.*")) throw new IOException("refused: " + (lines.length > 0 ? lines[0] : "?"));
        String accept = null;
        for (String l : lines) {
            int i = l.indexOf(':');
            if (i > 0 && l.substring(0, i).trim().equalsIgnoreCase("Sec-WebSocket-Accept")) accept = l.substring(i + 1).trim();
        }
        try {
            String want = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            if (!want.equals(accept)) throw new IOException("bad Sec-WebSocket-Accept");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public boolean isOpen() {
        return !closed;
    }

    public void sendBinary(byte[] data) throws IOException {
        frame(0x2, data);
    }

    public void ping() throws IOException {
        frame(0x9, new byte[0]);
    }

    private synchronized void frame(int opcode, byte[] data) throws IOException {
        if (closed) throw new IOException("closed");
        ByteArrayOutputStream b = new ByteArrayOutputStream(data.length + 14);
        b.write(0x80 | opcode);
        int n = data.length;
        if (n < 126) b.write(0x80 | n);
        else if (n < 65536) {
            b.write(0x80 | 126);
            b.write(n >>> 8);
            b.write(n);
        } else {
            b.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) b.write((int) ((long) n >>> (8 * i)));
        }
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        b.write(mask, 0, 4);
        for (int i = 0; i < n; i++) b.write(data[i] ^ mask[i & 3]);
        out.write(b.toByteArray());
        out.flush();
    }

    private int read() throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return b;
    }

    private void readLoop() {
        String reason = "closed";
        ByteArrayOutputStream msg = new ByteArrayOutputStream();
        int msgOp = 0;
        try {
            while (!closed) {
                int b0 = read(), b1 = read();
                boolean fin = (b0 & 0x80) != 0;
                int op = b0 & 0x0F;
                long len = b1 & 0x7F;
                if (len == 126) len = (read() << 8) | read();
                else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | read();
                }
                if (len > MAX_MESSAGE) throw new IOException("message too large");
                byte[] mask = null;
                if ((b1 & 0x80) != 0) {
                    mask = new byte[4];
                    for (int i = 0; i < 4; i++) mask[i] = (byte) read();
                }
                byte[] p = new byte[(int) len];
                int off = 0;
                while (off < p.length) {
                    int r = in.read(p, off, p.length - off);
                    if (r < 0) throw new EOFException();
                    off += r;
                }
                if (mask != null) for (int i = 0; i < p.length; i++) p[i] ^= mask[i & 3];
                if (op == 0x8) {
                    reason = "closed by the relay";
                    try {
                        frame(0x8, new byte[0]);
                    } catch (IOException ignored) {
                        // already going away
                    }
                    break;
                } else if (op == 0x9) {
                    frame(0xA, p);
                } else if (op == 0xA) {
                    // pong
                } else if (op == 0x0 || op == 0x1 || op == 0x2) {
                    if (op != 0) {
                        msg.reset();
                        msgOp = op;
                    }
                    msg.write(p, 0, p.length);
                    if (msg.size() > MAX_MESSAGE) throw new IOException("message too large");
                    if (fin) {
                        if (msgOp == 0x2) listener.onBinary(msg.toByteArray());
                        msg.reset();
                    }
                }
            }
        } catch (IOException e) {
            if (!closed) reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        } catch (RuntimeException e) {
            reason = "error: " + e;
        } finally {
            closeQuietly();
            listener.onClose(reason);
        }
    }

    private void closeQuietly() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing
        }
    }

    @Override
    public void close() {
        if (closed) return;
        try {
            frame(0x8, new byte[0]);
        } catch (IOException ignored) {
            // the reader will notice
        }
        closeQuietly();
    }
}
