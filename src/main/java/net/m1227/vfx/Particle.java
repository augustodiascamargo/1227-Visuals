package net.m1227.vfx;

/**
 * Mutable particle, owned by the pools in {@link ParticleSystem}. Instances
 * are preallocated and recycled, so spawning never allocates.
 */
public final class Particle {
    public double x, y, z;
    public float vx, vy, vz;
    public float age, life;
    public float size0, size1;
    public float r0, g0, b0, r1, g1, b1;
    public float alpha;
    /** Velocity damping per second. */
    public float drag;
    /** Downward acceleration (negative values rise). */
    public float gravity;
    /** 0 = no streak. > 0 = quad stretched along its velocity. */
    public float stretch;
    /** Exponent of the fade-out curve (higher = disappears faster). */
    public float fadePow;
    /** Atlas cell, see {@link Sprite}. */
    public int sprite;
    /** Rotation in radians (screen-plane, or around Y when flat) and its speed. */
    public float rot, rotVel;
    /** Lies horizontally in the world instead of facing the camera. */
    public boolean flat;
    /** Normal alpha blending (smoke) instead of additive. */
    public boolean alphaBlend;
    /** Skips the fade curve: constant alpha for its whole life. */
    public boolean steady;
    /** Horizontal wandering (m/s amplitude) and its frequency/phase. */
    public float sway, swayFreq, swayPhase;
    /** Brightness pulsing: 0 = none, 1 = full blink; frequency/phase in radians. */
    public float pulse, pulseFreq, pulsePhase;

    void reset() {
        vx = vy = vz = 0f;
        age = 0f;
        life = 1f;
        size0 = size1 = 0.1f;
        r0 = g0 = b0 = r1 = g1 = b1 = 1f;
        alpha = 1f;
        drag = 0f;
        gravity = 0f;
        stretch = 0f;
        fadePow = 1f;
        sprite = Sprite.GLOW;
        rot = 0f;
        rotVel = 0f;
        flat = false;
        alphaBlend = false;
        steady = false;
        sway = swayFreq = swayPhase = 0f;
        pulse = pulseFreq = pulsePhase = 0f;
    }

    public void setColors(int c0, int c1) {
        r0 = ((c0 >> 16) & 255) / 255f;
        g0 = ((c0 >> 8) & 255) / 255f;
        b0 = (c0 & 255) / 255f;
        r1 = ((c1 >> 16) & 255) / 255f;
        g1 = ((c1 >> 8) & 255) / 255f;
        b1 = (c1 & 255) / 255f;
    }
}
