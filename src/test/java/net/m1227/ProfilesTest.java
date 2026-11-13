package net.m1227;

import static org.junit.Assert.assertEquals;

import net.m1227.client.settings.Profiles;
import org.junit.Test;

/** Profile names become file names: nothing that could leave the profiles folder gets through. */
public class ProfilesTest {
    @Test
    public void namesAreSafeFileNames() {
        assertEquals("My PvP setup", Profiles.clean("My PvP setup"));
        assertEquals("evil", Profiles.clean("../../evil"));
        assertEquals("ab", Profiles.clean("a/b"));
        assertEquals("", Profiles.clean("   "));
        assertEquals(32, Profiles.clean("abcdefghijklmnopqrstuvwxyzabcdefghijkl").length());
    }
}
