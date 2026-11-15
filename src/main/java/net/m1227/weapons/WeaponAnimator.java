package net.m1227.weapons;

/**
 * Plays an imported model's own animation clips in four slots: idle (loops all the time),
 * swing, inspect and draw (one-shots started by the game). One-shots fade in and out over the
 * idle pose so nothing snaps. Which clip goes in which slot comes from the profile, or from the
 * clip names (idle / swing / attack / inspect / draw / equip ...). Pure: time is passed in.
 */
public final class WeaponAnimator {
    public static final int IDLE = 0, SWING = 1, INSPECT = 2, DRAW = 3;
    public static final String[] SLOT_NAMES = {"Idle", "Swing", "Inspect", "Draw"};

    private final WModel.Clip[] slot = new WModel.Clip[4];
    private WModel model;
    private int action = -1;
    private float actionStart, speed = 1f;
    private WModel.Pose idle, act, out;

    public void bind(WModel m, WeaponProfile p) {
        model = m;
        String[] chosen = {p.idle, p.swing, p.inspect, p.draw};
        for (int s = 0; s < 4; s++) slot[s] = resolve(m, chosen[s], s);
        // one clip should not fill two slots unless the profile asks for it
        if (chosen[INSPECT].isEmpty() && slot[INSPECT] != null && (slot[INSPECT] == slot[IDLE] || slot[INSPECT] == slot[SWING])) slot[INSPECT] = null;
        if (chosen[INSPECT].isEmpty() && slot[INSPECT] == null) slot[INSPECT] = auto(m, INSPECT, slot[IDLE], slot[SWING]);
        int n = m.bones.size();
        idle = new WModel.Pose(n);
        act = new WModel.Pose(n);
        out = new WModel.Pose(n);
        action = -1;
    }

    public WModel.Clip clip(int s) {
        return slot[s];
    }

    public boolean has(int s) {
        return slot[s] != null;
    }

    public boolean any() {
        return slot[0] != null || slot[1] != null || slot[2] != null || slot[3] != null;
    }

    public void setSpeed(float k) {
        speed = Math.max(0.1f, k);
    }

    /** Starts a one-shot slot (no-op when the slot is empty). A swing restarts a running swing. */
    public void trigger(int s, float now) {
        if (s <= IDLE || slot[s] == null) return;
        if (action == INSPECT && s == SWING) return; // the inspect keeps going until it ends
        action = s;
        actionStart = now;
    }

    /** Plays a named clip once (editor preview), or stops when null. */
    public void play(WModel.Clip c, float now) {
        previewClip = c;
        actionStart = now;
        action = c == null ? -1 : 4;
    }

    private WModel.Clip previewClip;

    public boolean busy(float now) {
        WModel.Clip c = current();
        return c != null && (now - actionStart) * speed < c.length;
    }

    public int action() {
        return action;
    }

    private WModel.Clip current() {
        if (action == 4) return previewClip;
        return action > 0 ? slot[action] : null;
    }

    /** The pose at 'now' (seconds), or null when the model has no clips in use. */
    public WModel.Pose pose(float now) {
        if (model == null) return null;
        WModel.Clip c = current();
        if (slot[IDLE] == null && c == null) return null;
        float life = now;
        model.sample(slot[IDLE], now * speed, life, idle);
        if (c == null) return idle;
        float t = (now - actionStart) * speed;
        if (t >= c.length || t < 0f) {
            action = -1;
            return idle;
        }
        model.sample(c, t, life, act);
        float fadeIn = Math.min(0.06f, c.length * 0.2f), fadeOut = Math.min(0.1f, c.length * 0.25f);
        float w = 1f;
        if (t < fadeIn) w = t / fadeIn;
        if (c.loop != 2 && t > c.length - fadeOut) w = Math.min(w, (c.length - t) / fadeOut);
        out.copy(idle);
        out.blend(act, smooth(w), model);
        return out;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    /** "" = automatic, "-" = none, otherwise the clip with that name. */
    static WModel.Clip resolve(WModel m, String chosen, int s) {
        if ("-".equals(chosen)) return null;
        if (chosen != null && !chosen.isEmpty()) return m.clip(chosen);
        return auto(m, s, null, null);
    }

    private static boolean has(String n, String... words) {
        for (String w : words) if (n.contains(w)) return true;
        return false;
    }

    static WModel.Clip auto(WModel m, int s, WModel.Clip notA, WModel.Clip notB) {
        WModel.Clip best = null;
        for (WModel.Clip c : m.clips) {
            if (c == notA || c == notB) continue;
            String n = c.name.toLowerCase(java.util.Locale.ROOT);
            boolean ok;
            switch (s) {
                case IDLE:
                    ok = has(n, "idle", "hold", "rest", "breath") || (n.contains("preview") && c.loop == 1 && c.length <= 4f);
                    break;
                case SWING:
                    ok = has(n, "swing", "attack", "slash", "strike", "hit", "stab") && c.length <= 1.2f;
                    break;
                case INSPECT:
                    ok = has(n, "inspect", "showcase", "show", "twirl", "spin", "flourish") || (has(n, "swing", "attack", "slash") && c.length > 1.2f);
                    break;
                default:
                    ok = has(n, "draw", "equip", "unsheath", "pull", "deploy");
            }
            if (ok && (best == null || (s == IDLE && c.loop == 1 && best.loop != 1))) best = c;
        }
        return best;
    }
}
