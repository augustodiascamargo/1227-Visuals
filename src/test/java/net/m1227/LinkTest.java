package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import net.m1227.social.LinkProtocol;
import org.junit.Test;

/** 1227 Link protocol: round trips and the checks on everything received. */
public class LinkTest {
    @Test
    public void stateSurvivesTheRelay() {
        LinkProtocol.State s = new LinkProtocol.State();
        s.seq = 42;
        s.flags = LinkProtocol.F_HAT | LinkProtocol.F_WINGS | 0x100; // unknown bit is dropped
        for (int i = 0; i < 6; i++) s.colors[i] = 0x10203 * (i + 1);
        s.emote = 7;
        s.emoteMs = 2500;
        byte[] client = LinkProtocol.state(s);
        assertTrue(LinkProtocol.validClientMessage(client));
        UUID id = UUID.randomUUID();
        LinkProtocol.Message m = LinkProtocol.decode(LinkProtocol.relay(id.getMostSignificantBits(), id.getLeastSignificantBits(), client));
        assertNotNull(m);
        assertEquals(LinkProtocol.RELAY, m.type);
        assertEquals(LinkProtocol.STATE, m.innerType);
        assertEquals(id, new UUID(m.msb, m.lsb));
        assertEquals(LinkProtocol.F_HAT | LinkProtocol.F_WINGS, m.state.flags);
        assertEquals(7, m.state.emote);
        assertEquals(2500, m.state.emoteMs);
        for (int i = 0; i < 6; i++) assertEquals(0x10203 * (i + 1), m.state.colors[i]);
    }

    @Test
    public void welcomeLeaveEmoteStop() {
        LinkProtocol.Message w = LinkProtocol.decode(LinkProtocol.welcome(8, "Arena"));
        assertEquals(LinkProtocol.WELCOME, w.type);
        assertEquals(8, w.rate);
        assertEquals("Arena", w.text);
        LinkProtocol.Message l = LinkProtocol.decode(LinkProtocol.leave(1L, 2L));
        assertEquals(LinkProtocol.LEAVE, l.type);
        assertEquals(1L, l.msb);
        LinkProtocol.Message e = LinkProtocol.decode(LinkProtocol.relay(1, 2, LinkProtocol.emote(3, 12, 0)));
        assertEquals(12, e.emote);
        LinkProtocol.Message st = LinkProtocol.decode(LinkProtocol.relay(1, 2, LinkProtocol.stop(9)));
        assertEquals(LinkProtocol.STOP, st.innerType);
        assertEquals(9, st.seq);
    }

    @Test
    public void badInputIsDropped() {
        assertNull(LinkProtocol.decode(null));
        assertNull(LinkProtocol.decode(new byte[]{0x27, 1}));
        assertNull(LinkProtocol.decode(new byte[]{0x28, 1, LinkProtocol.WELCOME, 1, 0})); // magic
        assertNull(LinkProtocol.decode(new byte[]{0x27, 2, LinkProtocol.WELCOME, 1, 0})); // version
        assertNull(LinkProtocol.decode(new byte[300])); // too big
        byte[] truncated = LinkProtocol.relay(1, 2, LinkProtocol.state(new LinkProtocol.State()));
        assertNull(LinkProtocol.decode(java.util.Arrays.copyOf(truncated, truncated.length - 6)));
        assertNull(LinkProtocol.decode(LinkProtocol.relay(1, 2, LinkProtocol.emote(1, 99, 0)))); // unknown emote
        byte[] badEmoteState = LinkProtocol.relay(1, 2, LinkProtocol.state(new LinkProtocol.State()));
        badEmoteState[badEmoteState.length - 5] = 120; // emote byte out of range -> no emote
        assertEquals(-1, LinkProtocol.decode(badEmoteState).state.emote);
        // a client may not send server types
        assertTrue(!LinkProtocol.validClientMessage(LinkProtocol.welcome(1, "x")));
        // name length is capped
        assertEquals(32, LinkProtocol.decode(LinkProtocol.welcome(1, "0123456789012345678901234567890123456789")).text.length());
    }
}
