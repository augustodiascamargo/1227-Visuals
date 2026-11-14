package net.m1227.social;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 1227 Link protocol (version 1): how 1227 players see each other's emotes and cosmetics through
 * a server that runs the 1227 Relay plugin. Plain Java, unit-tested. Purely cosmetic data: no
 * position, input or combat information is ever sent.
 *
 * Channel "1227|fx". Every message: [0x27 magic][version 1][type][body], at most 256 bytes.
 *
 * Client to server: HELLO (mod version, capabilities), STATE (shared cosmetics and the current
 * emote), EMOTE (an emote started), STOP (the emote ended).
 * Server to client: WELCOME (the relay is there: rate limit, name), RELAY (another player's
 * message: their UUID + the original type and body), LEAVE (a player left or changed world).
 *
 * Everything read is checked: magic, version, type, length, ranges (emote ids, flags, times) and
 * string lengths; anything else is dropped without an error.
 */
public final class LinkProtocol {
    private LinkProtocol() {}

    public static final String CHANNEL = "1227|fx";
    public static final byte MAGIC = 0x27, VERSION = 1;
    public static final byte HELLO = 1, STATE = 2, EMOTE = 3, STOP = 4;
    public static final byte WELCOME = 0x41, RELAY = 0x42, LEAVE = 0x43;
    public static final int MAX_BYTES = 256;
    /** Shared cosmetics. */
    public static final int F_HALO = 1, F_HAT = 2, F_WINGS = 4, F_KNOWN = 7;
    /** Emote ids are indexes into EmotesModule.EMOTES; checked against this bound. */
    public static int emoteCount = 22;

    /** What a player shares. */
    public static final class State {
        public int seq, flags, emote = -1, emoteMs;
        public final int[] colors = new int[6]; // halo 1/2, hat 1/2, wings 1/2 (RGB)

        public State copy() {
            State s = new State();
            s.seq = seq;
            s.flags = flags;
            s.emote = emote;
            s.emoteMs = emoteMs;
            System.arraycopy(colors, 0, s.colors, 0, 6);
            return s;
        }
    }

    /** A decoded server message. */
    public static final class Message {
        public byte type, innerType;
        public long msb, lsb;
        public int rate;
        public String text = "";
        public State state;
        public int seq, emote = -1, emoteMs;
    }

    private static DataOutputStream head(ByteArrayOutputStream b, byte type) throws IOException {
        DataOutputStream d = new DataOutputStream(b);
        d.writeByte(MAGIC);
        d.writeByte(VERSION);
        d.writeByte(type);
        return d;
    }

    private static void str(DataOutputStream d, String s, int max) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int n = Math.min(max, b.length);
        d.writeByte(n);
        d.write(b, 0, n);
    }

    private static String str(DataInputStream in, int max) throws IOException {
        int n = in.readUnsignedByte();
        if (n > max) throw new IOException("string too long");
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    public static byte[] hello(String version, int caps) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = head(b, HELLO);
            str(d, version, 16);
            d.writeInt(caps);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void writeState(DataOutputStream d, State s) throws IOException {
        d.writeInt(s.seq);
        d.writeInt(s.flags & F_KNOWN);
        for (int c : s.colors) d.writeInt(c & 0xFFFFFF);
        d.writeByte(s.emote);
        d.writeInt(Math.max(0, Math.min(600000, s.emoteMs)));
    }

    public static byte[] state(State s) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            writeState(head(b, STATE), s);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] emote(int seq, int emote, int ms) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = head(b, EMOTE);
            d.writeInt(seq);
            d.writeByte(emote);
            d.writeInt(Math.max(0, ms));
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] stop(int seq) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            head(b, STOP).writeInt(seq);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Server side helpers (the relay plugin uses the same layout). */
    public static byte[] welcome(int rate, String name) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = head(b, WELCOME);
            d.writeByte(Math.max(1, Math.min(50, rate)));
            str(d, name, 32);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** RELAY wrapping a client message (whole client message after its 3-byte header). */
    public static byte[] relay(long msb, long lsb, byte[] clientMessage) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = head(b, RELAY);
            d.writeLong(msb);
            d.writeLong(lsb);
            d.writeByte(clientMessage[2]);
            d.write(clientMessage, 3, clientMessage.length - 3);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] leave(long msb, long lsb) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = head(b, LEAVE);
            d.writeLong(msb);
            d.writeLong(lsb);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static State readState(DataInputStream in) throws IOException {
        State s = new State();
        s.seq = in.readInt();
        s.flags = in.readInt() & F_KNOWN;
        for (int i = 0; i < 6; i++) s.colors[i] = in.readInt() & 0xFFFFFF;
        s.emote = in.readByte();
        if (s.emote < -1 || s.emote >= emoteCount) s.emote = -1;
        s.emoteMs = Math.max(0, Math.min(600000, in.readInt()));
        return s;
    }

    /** Decodes a message from the server; null when it is not a valid 1227 Link message. */
    public static Message decode(byte[] data) {
        if (data == null || data.length < 3 || data.length > MAX_BYTES || data[0] != MAGIC || data[1] != VERSION) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 3, data.length - 3));
            Message m = new Message();
            m.type = data[2];
            if (m.type == WELCOME) {
                m.rate = in.readUnsignedByte();
                m.text = str(in, 32);
            } else if (m.type == LEAVE) {
                m.msb = in.readLong();
                m.lsb = in.readLong();
            } else if (m.type == RELAY) {
                m.msb = in.readLong();
                m.lsb = in.readLong();
                m.innerType = in.readByte();
                if (m.innerType == STATE) {
                    m.state = readState(in);
                    m.seq = m.state.seq;
                } else if (m.innerType == EMOTE) {
                    m.seq = in.readInt();
                    m.emote = in.readByte();
                    m.emoteMs = Math.max(0, Math.min(600000, in.readInt()));
                    if (m.emote < 0 || m.emote >= emoteCount) return null;
                } else if (m.innerType == STOP) {
                    m.seq = in.readInt();
                } else if (m.innerType == HELLO) {
                    m.text = str(in, 16);
                } else {
                    return null;
                }
            } else {
                return null;
            }
            return m;
        } catch (IOException e) {
            return null; // truncated or malformed
        }
    }

    /** Server-side check of a client message: header, size and a known client type. */
    public static boolean validClientMessage(byte[] data) {
        return data != null && data.length >= 3 && data.length <= MAX_BYTES && data[0] == MAGIC && data[1] == VERSION
                && data[2] >= HELLO && data[2] <= STOP;
    }
}
