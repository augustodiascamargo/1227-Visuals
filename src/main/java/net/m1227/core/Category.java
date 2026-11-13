package net.m1227.core;

/** Sidebar entries. "group" is the caption shown above a run of entries. */
public enum Category {
    SKY("Skies", "Dynamic Skies", "VISUALS", 0),
    AMBIENCE("Ambience", "Ambience", "VISUALS", 1),
    AURAS("Auras", "Auras", "VISUALS", 2),
    COSMETICS("Cosmetics", "Cosmetics", "VISUALS", 3),
    MOVEMENT("Movement", "Trails and Jumps", "VISUALS", 4),
    COMBAT("Combat", "Hit, Kill and Shockwave", "VISUALS", 5),
    WEAPONS("Weapons", "Weapon Effects", "VISUALS", 10),
    CAMERA("Camera", "Camera", "CLIENT", 6),
    ANIMATIONS("Animations", "Animations", "CLIENT", 11),
    GAMEPLAY("Gameplay", "Gameplay", "CLIENT", 4),
    HITSOUNDS("Hit Sounds", "Hit Sounds", "CLIENT", 8),
    MUSIC("Music", "Music and Integrations", "CLIENT", 8),
    HUD("HUD", "HUD", "CLIENT", 9),
    SETTINGS("Settings", "Settings and Presets", "CLIENT", 7);

    public final String label, title, group;
    /** Index of the glyph drawn next to it in the menu. */
    public final int icon;

    Category(String label, String title, String group, int icon) {
        this.label = label;
        this.title = title;
        this.group = group;
        this.icon = icon;
    }
}
