package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonPrimitive;
import net.m1227.core.Setting;
import org.junit.Test;

/** Settings: clamping and snapping, cycling, colours and JSON round trips (what the config file relies on). */
public class SettingTest {
    @Test
    public void numClampsAndSnapsToStep() {
        Setting.Num n = new Setting.Num("n", 1.0, 0.5, 2.0, 0.25, "x");
        n.setClamped(9);
        assertEquals(2.0, n.get(), 1e-9);
        n.setClamped(-3);
        assertEquals(0.5, n.get(), 1e-9);
        n.setClamped(1.13);
        assertEquals(1.25, n.get(), 1e-9);
    }

    @Test
    public void numJsonRoundTripAndBadInput() {
        Setting.Num n = new Setting.Num("n", 3, 1, 10, 1, "");
        n.setClamped(7);
        Setting.Num m = new Setting.Num("n", 3, 1, 10, 1, "");
        m.fromJson(n.toJson());
        assertEquals(7, m.i());
        m.fromJson(new JsonPrimitive(500)); // out of range in the file: clamped, never broken
        assertEquals(10, m.i());
        m.fromJson(new com.google.gson.JsonObject()); // wrong type: ignored
        assertEquals(10, m.i());
    }

    @Test
    public void modeCyclesBothWays() {
        Setting.Mode m = new Setting.Mode("m", "B", "A", "B", "C");
        m.cycle(1);
        assertEquals("C", m.get());
        m.cycle(1);
        assertEquals("A", m.get());
        m.cycle(-1);
        assertEquals("C", m.get());
        assertTrue(m.is("C"));
    }

    @Test
    public void modeIgnoresUnknownValueInFile() {
        Setting.Mode m = new Setting.Mode("m", "B", "A", "B", "C");
        m.fromJson(new JsonPrimitive("Removed style"));
        assertTrue(m.is("A") || m.is("B") || m.is("C"));
    }

    @Test
    public void boolToggleAndJson() {
        Setting.Bool b = new Setting.Bool("b", false);
        b.toggle();
        assertTrue(b.on());
        Setting.Bool c = new Setting.Bool("b", false);
        c.fromJson(b.toJson());
        assertTrue(c.on());
        assertFalse(new Setting.Bool("x", false).on());
    }

    @Test
    public void colorRgbAndJsonRoundTrip() {
        Setting.Col c = new Setting.Col("c", 0x112233);
        c.setRgb(0x8B5CFF);
        Setting.Col d = new Setting.Col("c", 0);
        d.fromJson(c.toJson());
        assertEquals(0x8B5CFF, d.rgb());
    }

    @Test
    public void textRespectsMaxLength() {
        Setting.Text t = new Setting.Text("t", "", 5, "");
        t.set("1234567890");
        assertTrue(t.get().length() <= 5);
    }
}
