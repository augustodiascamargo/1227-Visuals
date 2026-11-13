package net.m1227.vfx;

/**
 * One of the 58 dynamic skies: a gradient plus a mix of animated layers
 * (stars, galaxy band, nebula clouds, flowing light lines, aurora, sun or
 * moon, stylised clouds, shooting stars, code rain, retro grid, storm).
 * All layers are drawn procedurally every frame; nothing is a static image.
 */
public final class SkyPreset {
    public static final int NONE = 0, SUN = 1, MOON = 2, PLANET = 3, ECLIPSE = 4, RETRO = 5;

    public final String name;
    public int top, horizon, bottom;
    public float stars = 0.3f;
    public int starColor = 0xFFFFFF;
    public int lines;
    public int lineColor = 0xFFFFFF, lineGlow = 0xBFEFFF;
    public boolean ribbons;
    public int aurora;
    public int auroraA = 0x3DFFA0, auroraB = 0x2DD4FF;
    public float galaxy;
    public int galaxyColor = 0xC9B8FF;
    public float nebula;
    public int nebA = 0xFF4FD8, nebB = 0x5C2DFF;
    public int celestial = NONE;
    public int celColor = 0xFFF1B8;
    /** Celestial body follows the world clock (true) or stays at a fixed spot near the horizon. */
    public boolean followTime = true;
    public float clouds;
    public int cloudColor = 0xFFFFFF;
    public float glow;
    public int glowColor = 0xFF8A3D;
    public float shooting;
    public boolean codeRain, grid;
    public float storm;
    /** GLSL layer this sky uses when the shader setting is "Auto" (0 = none). */
    public int shader;
    /** Colours for the sky's own GLSL layer (-1 = taken from the sky's other colours). */
    public int shA = -1, shB = -1, shC = -1;

    private SkyPreset(String name, int top, int horizon, int bottom) {
        this.name = name;
        this.top = top;
        this.horizon = horizon;
        this.bottom = bottom;
    }

    private static SkyPreset p(String n, int top, int hor, int bot) {
        return new SkyPreset(n, top, hor, bot);
    }

    private SkyPreset stars(float d, int c) { stars = d; starColor = c; return this; }
    private SkyPreset lines(int n, int c, int g) { lines = n; lineColor = c; lineGlow = g; return this; }
    private SkyPreset ribbons() { ribbons = true; return this; }
    private SkyPreset aurora(int n, int a, int b) { aurora = n; auroraA = a; auroraB = b; return this; }
    private SkyPreset galaxy(float g, int c) { galaxy = g; galaxyColor = c; return this; }
    private SkyPreset nebula(float v, int a, int b) { nebula = v; nebA = a; nebB = b; return this; }
    private SkyPreset body(int type, int c, boolean follow) { celestial = type; celColor = c; followTime = follow; return this; }
    private SkyPreset clouds(float v, int c) { clouds = v; cloudColor = c; return this; }
    private SkyPreset glow(float v, int c) { glow = v; glowColor = c; return this; }
    private SkyPreset shooting(float v) { shooting = v; return this; }
    private SkyPreset code() { codeRain = true; return this; }
    private SkyPreset grid() { grid = true; return this; }
    private SkyPreset storm(float v) { storm = v; return this; }
    private SkyPreset shader(int v) { shader = v; return this; }
    private SkyPreset shader(int v, int a, int b, int c) { shader = v; shA = a; shB = b; shC = c; return this; }

    public static final SkyPreset[] ALL = {
            p("Night Flow", 0x000000, 0x0B0D10, 0x000000).stars(0.25f, 0xFFFFFF).lines(16, 0xFFFFFF, 0xBFEFFF),
            p("Light Ribbons", 0x000000, 0x0A0B10, 0x000000).stars(0.2f, 0xFFFFFF).lines(12, 0xFFFFFF, 0xC9B8FF).ribbons(),
            p("Aurora Borealis", 0x020812, 0x06231F, 0x010405).stars(0.5f, 0xE0FFF4).aurora(6, 0x3DFFA0, 0x2DD4FF),
            p("Violet Aurora", 0x05020F, 0x1A0B2E, 0x02010A).stars(0.5f, 0xF0E0FF).aurora(6, 0xC77DFF, 0xFF4FD8),
            p("Deep Space", 0x000000, 0x020205, 0x000000).stars(1f, 0xFFFFFF).galaxy(0.6f, 0xD8D0FF),
            p("Galaxy", 0x02030A, 0x0A0B24, 0x010104).stars(0.9f, 0xE8E4FF).galaxy(1f, 0xB8A6FF).nebula(0.35f, 0x5C7DFF, 0xA35CFF),
            p("Rose Nebula", 0x0A0210, 0x2A0626, 0x050008).stars(0.6f, 0xFFE0F4).nebula(1f, 0xFF2DB0, 0x8B1FFF),
            p("Teal Nebula", 0x00090C, 0x002A2E, 0x000405).stars(0.6f, 0xE0FFFF).nebula(1f, 0x00E5D0, 0x0B6BFF),
            p("Cosmic Storm", 0x05010C, 0x14082A, 0x020005).stars(0.5f, 0xFFFFFF).nebula(0.7f, 0x8B5CFF, 0x2DD4FF).lines(10, 0xE0D0FF, 0x8B5CFF),
            p("Starfield", 0x00020A, 0x050A1E, 0x000105).stars(1f, 0xFFFFFF).shooting(0.4f),
            p("Meteor Shower", 0x00010A, 0x0A0A22, 0x000003).stars(0.8f, 0xFFFFFF).shooting(1.6f),
            p("Sunset", 0x241447, 0xFF7A3D, 0x3A1A2E).stars(0.05f, 0xFFFFFF).body(SkyPreset.SUN, 0xFFC36B, false).clouds(0.6f, 0xFF9A7A).glow(0.8f, 0xFF6B3D),
            p("Golden Hour", 0x3D6BA6, 0xFFC36B, 0x6B4A2E).body(SkyPreset.SUN, 0xFFF1B8, false).clouds(0.5f, 0xFFE2B8).glow(0.6f, 0xFFB347),
            p("Dawn", 0x6FA8DC, 0xFFC2D6, 0x9A7A8A).body(SkyPreset.SUN, 0xFFF4E0, false).clouds(0.5f, 0xFFFFFF).glow(0.4f, 0xFFB8C8),
            p("Twilight", 0x0B1640, 0xE0784D, 0x1A0F24).stars(0.35f, 0xFFFFFF).body(SkyPreset.MOON, 0xF4F0E0, true).glow(0.5f, 0xFF8A5C),
            p("Midnight Moon", 0x020617, 0x0E1F45, 0x01030A).stars(0.7f, 0xE0E8FF).body(SkyPreset.MOON, 0xE8EEFF, true),
            p("Blood Moon", 0x0A0000, 0x3A0505, 0x050000).stars(0.4f, 0xFFB8B8).body(SkyPreset.MOON, 0xFF3D2D, true).glow(0.3f, 0xFF2D2D),
            p("Eclipse", 0x000000, 0x0A0A0F, 0x000000).stars(0.6f, 0xFFFFFF).body(SkyPreset.ECLIPSE, 0xFFF1D6, false),
            p("Ringed Planet", 0x020514, 0x0B1A3A, 0x01020A).stars(0.8f, 0xFFFFFF).body(SkyPreset.PLANET, 0xD8A6FF, false),
            p("Synthwave", 0x12002E, 0xFF2D95, 0x1A0024).stars(0.3f, 0xFFD6F4).body(SkyPreset.RETRO, 0xFFD36B, false).grid().glow(0.7f, 0xFF4FD8),
            p("Code Rain", 0x000000, 0x001A05, 0x000000).code(),
            p("Void", 0x000000, 0x000000, 0x000000).stars(0.12f, 0xFFFFFF),
            p("Monochrome", 0x16181C, 0x3A3D44, 0x0E0F12).lines(10, 0xFFFFFF, 0xFFFFFF).stars(0.1f, 0xFFFFFF),
            p("Dreamscape", 0x8AB4FF, 0xFFC2E6, 0xB8A6D9).clouds(0.8f, 0xFFFFFF).stars(0.15f, 0xFFFFFF).glow(0.3f, 0xFFB8F0),
            p("Frozen Sky", 0x0B2A45, 0x9AD8F0, 0x203A4D).aurora(5, 0xFFFFFF, 0x9AF0FF).stars(0.4f, 0xE0F8FF),
            p("Infernal", 0x120000, 0x8A1F05, 0x1A0300).nebula(0.8f, 0xFF4D00, 0xFF0F2D).glow(0.8f, 0xFF3D00).stars(0.1f, 0xFFB86B),
            p("Emerald Sky", 0x001208, 0x0B3D24, 0x00080A).aurora(6, 0x3DFF6B, 0xB8FF3D).stars(0.5f, 0xE0FFE8),
            p("Ocean Depths", 0x000A1F, 0x00365C, 0x00050F).nebula(0.6f, 0x00B8FF, 0x0B3DFF).lines(10, 0x9AE8FF, 0x2D8CFF),
            p("Candy Clouds", 0xFF9AD6, 0xA6D8FF, 0xFFC2E0).clouds(1f, 0xFFFFFF).glow(0.2f, 0xFFFFFF),
            p("Thunderstorm", 0x16191F, 0x353A44, 0x0E1014).clouds(1f, 0x4A505C).storm(0.35f),
            p("Purple Storm", 0x0A0418, 0x2A1450, 0x05020C).stars(0.5f, 0xE8D8FF).lines(14, 0xE8D8FF, 0x8B5CFF).storm(0.25f),
            p("Neon City", 0x05000F, 0x3A0A5C, 0x0A0014).stars(0.3f, 0xFFD6F4).grid().glow(0.6f, 0x2DF6FF).nebula(0.4f, 0xFF2DB0, 0x2DF6FF),
            p("Cyber Grid", 0x000000, 0x00151F, 0x000000).grid().lines(10, 0x2DF6FF, 0x2D8CFF).stars(0.2f, 0x9AF0FF),
            p("Aurora Storm", 0x01060C, 0x082A2A, 0x000305).aurora(8, 0x3DFFA0, 0xC77DFF).stars(0.6f, 0xFFFFFF).shooting(0.6f),
            p("Crimson Galaxy", 0x0A0005, 0x2A0410, 0x050002).stars(0.9f, 0xFFD6D6).galaxy(0.9f, 0xFF6B8A).nebula(0.5f, 0xFF2D4F, 0x7A0A2D),
            p("Ice Galaxy", 0x00040A, 0x0A1E2E, 0x000205).stars(1f, 0xE0F8FF).galaxy(0.9f, 0x9AE8FF).nebula(0.35f, 0x2DD4FF, 0xFFFFFF),
            p("Solar Flare", 0x1A0500, 0xFF8A00, 0x2E0A00).body(SkyPreset.SUN, 0xFFE08A, false).glow(1f, 0xFF5A00).nebula(0.4f, 0xFF3D00, 0xFFC800),
            p("Lunar Eclipse", 0x05000A, 0x1A0A24, 0x020005).stars(0.8f, 0xFFFFFF).body(SkyPreset.ECLIPSE, 0xFF6B4A, false).aurora(4, 0xFF6B4A, 0xC77DFF),
            p("Lavender Dusk", 0x2E1F5C, 0xE0A6FF, 0x3A2A4D).clouds(0.6f, 0xFFE0FF).body(SkyPreset.MOON, 0xFFF4FF, true).stars(0.25f, 0xFFFFFF),
            p("Golden Galaxy", 0x050300, 0x1F1405, 0x020100).stars(0.9f, 0xFFF1B8).galaxy(1f, 0xFFD36B).nebula(0.3f, 0xFFB347, 0xFF6B3D),
            p("Matrix Storm", 0x000000, 0x001A08, 0x000000).code().storm(0.2f),
            p("Starlight Ribbons", 0x02030F, 0x0E1233, 0x01020A).stars(0.9f, 0xFFFFFF).lines(14, 0xFFFFFF, 0x9AE8FF).ribbons().shooting(0.5f),
            p("Toxic Sky", 0x020A00, 0x1F3D05, 0x010500).nebula(0.8f, 0x8BFF2D, 0x2DFF8A).glow(0.4f, 0xB8FF3D).stars(0.2f, 0xE8FFD6),
            p("Pastel Dream", 0xB8C8FF, 0xFFD6F0, 0xD6C8FF).clouds(0.9f, 0xFFFFFF).aurora(4, 0xFFB8F0, 0xB8E8FF).glow(0.2f, 0xFFFFFF),
            // realistic "shader" skies: volumetric cloud decks
            p("Overcast", 0x8C9096, 0xC4C7CC, 0x6A6D72).clouds(0f, 0xF2F3F5).shader(7),
            p("Silver Sky", 0xA9B4C2, 0xE2E6EC, 0x8A939E).clouds(0f, 0xFFFFFF).shader(7).body(SkyPreset.SUN, 0xFFF6E0, true),
            p("Storm Front", 0x2C3036, 0x5C6068, 0x1C1E22).clouds(0f, 0x9EA3AC).shader(8).storm(0.12f),
            p("Night Overcast", 0x14161B, 0x2E3138, 0x0B0C0F).clouds(0f, 0x4A4E57).stars(0.45f, 0xFFFFFF).shader(8),
            p("Ember Night", 0x04050B, 0x9A3A16, 0x0A0604).stars(0.6f, 0xFFFFFF).shooting(0.6f).glow(0.5f, 0xFF5A1F),
            p("Teal Night", 0x020608, 0x0E4A42, 0x010304).aurora(5, 0x2DFFB0, 0x1A8A7A).stars(0.5f, 0xFFFFFF).shooting(0.4f),
            // electric skies: bright plasma veins (GLSL layer 9)
            p("Electric Cyan", 0x020A0E, 0x0B2C30, 0x01060A).stars(0.4f, 0xE0FFFF).shader(9, 0x0E5E5A, 0x5CFFF0, 0xFFFFFF),
            p("Electric Gold", 0x0A0703, 0x2E2210, 0x050301).stars(0.4f, 0xFFF4D6).shader(9, 0x5A3A10, 0xFFD27A, 0xFFF6E0),
            p("Electric Red", 0x0C0204, 0x3A0A10, 0x060102).stars(0.3f, 0xFFD6D6).shader(9, 0x5A0A10, 0xFF3048, 0xFFC8D0),
            p("Electric Violet", 0x06030E, 0x24104A, 0x030108).stars(0.4f, 0xF0E0FF).shader(9, 0x3A1070, 0xB06CFF, 0xF4E8FF),
            p("Energy Blue", 0x01040E, 0x0A1E4A, 0x000208).stars(0.5f, 0xD6E8FF).shader(4, 0x0A2A8A, 0x2D8CFF, 0x9AE8FF),
            // aurora curtains (GLSL layer 10)
            p("Green Aurora", 0x041A10, 0x1E6A3E, 0x03120A).stars(0.35f, 0xE8FFF0).glow(0.4f, 0x2DFF8A).shader(10, 0x2DFF8A, 0xB8FFD6, 0x0E6E3A),
            p("Purple Aurora", 0x0C0418, 0x3A1A6A, 0x060210).stars(0.4f, 0xF0E0FF).shader(10, 0xB06CFF, 0xFFB8F0, 0x4A1A8A),
            p("Blue Aurora", 0x020A1A, 0x0E3A6A, 0x010510).stars(0.4f, 0xE0F0FF).shader(10, 0x2D8CFF, 0x9AF0FF, 0x0E3A8A)
    };

    public static String[] names() {
        String[] out = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) out[i] = ALL[i].name;
        return out;
    }

    /** Gallery collection of a sky, from what it contains: Neon, Aurora, Space, Sunset or Night. */
    public static String collection(SkyPreset s) {
        if (s.codeRain || s.grid || s.celestial == RETRO || s.lines > 0) return "Neon";
        if (s.aurora > 0 || s.shader == 10) return "Aurora";
        if (s.galaxy > 0.2f || s.nebula > 0.2f || s.celestial == PLANET) return "Space";
        if (s.glow > 0.25f || s.celestial == SUN) return "Sunset";
        return "Night";
    }

    public static SkyPreset byName(String n) {
        for (SkyPreset s : ALL) {
            if (s.name.equals(n)) return s;
        }
        return ALL[0];
    }
}
