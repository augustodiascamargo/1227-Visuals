package net.m1227.weapons;

import java.util.ArrayList;
import java.util.List;

/**
 * The small part of Molang that keyframes in Blockbench animations use: numbers, + - * /,
 * parentheses, comparisons, the ternary ?:, query.anim_time / query.life_time (seconds) and the
 * common math.* functions (sin and cos take degrees, as in Minecraft). Anything else reads as 0
 * and is reported once, so an exotic expression never breaks a model. Pure.
 */
public final class Molang {
    private Molang() {}

    public interface Expr {
        float eval(float animTime, float lifeTime);
    }

    public static final class Const implements Expr {
        public final float v;

        Const(float v) {
            this.v = v;
        }

        @Override
        public float eval(float a, float l) {
            return v;
        }
    }

    /** Parses s; unknown names are added to 'unknown' (may be null). Never throws. */
    public static Expr parse(String s, List<String> unknown) {
        if (s == null) return new Const(0f);
        String t = s.trim();
        if (t.isEmpty()) return new Const(0f);
        try {
            return new Const(Float.parseFloat(t));
        } catch (NumberFormatException ignored) {
        }
        try {
            P p = new P(t.toLowerCase(java.util.Locale.ROOT), unknown);
            Expr e = p.ternary();
            p.ws();
            if (p.i < p.s.length() && p.s.charAt(p.i) == ';') p.i++;
            p.ws();
            if (p.i != p.s.length()) {
                if (unknown != null) unknown.add(s);
                return new Const(0f);
            }
            return e;
        } catch (RuntimeException ex) {
            if (unknown != null) unknown.add(s);
            return new Const(0f);
        }
    }

    private static final class P {
        final String s;
        final List<String> unknown;
        int i;

        P(String s, List<String> unknown) {
            this.s = s.startsWith("return ") ? s.substring(7) : s;
            this.unknown = unknown;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        boolean eat(String tok) {
            ws();
            if (s.startsWith(tok, i)) {
                i += tok.length();
                return true;
            }
            return false;
        }

        Expr ternary() {
            final Expr c = compare();
            if (eat("?")) {
                final Expr a = ternary();
                if (!eat(":")) throw new IllegalStateException(": expected");
                final Expr b = ternary();
                return (t, l) -> c.eval(t, l) != 0f ? a.eval(t, l) : b.eval(t, l);
            }
            return c;
        }

        Expr compare() {
            Expr a = sum();
            while (true) {
                final Expr l = a;
                if (eat("<=")) {
                    final Expr r = sum();
                    a = (t, q) -> l.eval(t, q) <= r.eval(t, q) ? 1f : 0f;
                } else if (eat(">=")) {
                    final Expr r = sum();
                    a = (t, q) -> l.eval(t, q) >= r.eval(t, q) ? 1f : 0f;
                } else if (eat("==")) {
                    final Expr r = sum();
                    a = (t, q) -> l.eval(t, q) == r.eval(t, q) ? 1f : 0f;
                } else if (eat("!=")) {
                    final Expr r = sum();
                    a = (t, q) -> l.eval(t, q) != r.eval(t, q) ? 1f : 0f;
                } else if (eat("<")) {
                    final Expr r = sum();
                    a = (t, q) -> l.eval(t, q) < r.eval(t, q) ? 1f : 0f;
                } else if (eat(">")) {
                    final Expr r = sum();
                    a = (t, q) -> l.eval(t, q) > r.eval(t, q) ? 1f : 0f;
                } else {
                    return a;
                }
            }
        }

        Expr sum() {
            Expr a = product();
            while (true) {
                final Expr l = a;
                if (eat("+")) {
                    final Expr r = product();
                    a = (t, q) -> l.eval(t, q) + r.eval(t, q);
                } else if (eat("-")) {
                    final Expr r = product();
                    a = (t, q) -> l.eval(t, q) - r.eval(t, q);
                } else {
                    return a;
                }
            }
        }

        Expr product() {
            Expr a = unary();
            while (true) {
                final Expr l = a;
                if (eat("*")) {
                    final Expr r = unary();
                    a = (t, q) -> l.eval(t, q) * r.eval(t, q);
                } else if (eat("/")) {
                    final Expr r = unary();
                    a = (t, q) -> {
                        float d = r.eval(t, q);
                        return d == 0f ? 0f : l.eval(t, q) / d;
                    };
                } else {
                    return a;
                }
            }
        }

        Expr unary() {
            if (eat("-")) {
                final Expr e = unary();
                return (t, l) -> -e.eval(t, l);
            }
            if (eat("!")) {
                final Expr e = unary();
                return (t, l) -> e.eval(t, l) == 0f ? 1f : 0f;
            }
            return primary();
        }

        Expr primary() {
            ws();
            if (eat("(")) {
                Expr e = ternary();
                if (!eat(")")) throw new IllegalStateException(") expected");
                return e;
            }
            int st = i;
            if (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == 'e'
                        || ((s.charAt(i) == '-' || s.charAt(i) == '+') && s.charAt(i - 1) == 'e'))) i++;
                return new Const(Float.parseFloat(s.substring(st, i)));
            }
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
            String name = s.substring(st, i);
            if (name.isEmpty()) throw new IllegalStateException("value expected");
            List<Expr> args = new ArrayList<>();
            if (eat("(")) {
                if (!eat(")")) {
                    do {
                        args.add(ternary());
                    } while (eat(","));
                    if (!eat(")")) throw new IllegalStateException(") expected");
                }
            }
            return name(name, args);
        }

        Expr name(String n, final List<Expr> a) {
            if (n.startsWith("query.")) n = "q." + n.substring(6);
            if (n.startsWith("variable.")) n = "v." + n.substring(9);
            final Expr x = a.size() > 0 ? a.get(0) : new Const(0f), y = a.size() > 1 ? a.get(1) : new Const(0f), z = a.size() > 2 ? a.get(2) : new Const(0f);
            switch (n) {
                case "q.anim_time":
                    return (t, l) -> t;
                case "q.life_time":
                    return (t, l) -> l;
                case "math.pi":
                    return new Const((float) Math.PI);
                case "math.sin":
                    return (t, l) -> (float) Math.sin(Math.toRadians(x.eval(t, l)));
                case "math.cos":
                    return (t, l) -> (float) Math.cos(Math.toRadians(x.eval(t, l)));
                case "math.abs":
                    return (t, l) -> Math.abs(x.eval(t, l));
                case "math.sqrt":
                    return (t, l) -> (float) Math.sqrt(Math.max(0f, x.eval(t, l)));
                case "math.floor":
                    return (t, l) -> (float) Math.floor(x.eval(t, l));
                case "math.ceil":
                    return (t, l) -> (float) Math.ceil(x.eval(t, l));
                case "math.round":
                    return (t, l) -> (float) Math.round(x.eval(t, l));
                case "math.trunc":
                    return (t, l) -> (float) (long) x.eval(t, l);
                case "math.exp":
                    return (t, l) -> (float) Math.exp(x.eval(t, l));
                case "math.pow":
                    return (t, l) -> (float) Math.pow(x.eval(t, l), y.eval(t, l));
                case "math.min":
                    return (t, l) -> Math.min(x.eval(t, l), y.eval(t, l));
                case "math.max":
                    return (t, l) -> Math.max(x.eval(t, l), y.eval(t, l));
                case "math.mod":
                    return (t, l) -> {
                        float d = y.eval(t, l);
                        return d == 0f ? 0f : x.eval(t, l) % d;
                    };
                case "math.clamp":
                    return (t, l) -> Math.max(y.eval(t, l), Math.min(z.eval(t, l), x.eval(t, l)));
                case "math.lerp":
                    return (t, l) -> x.eval(t, l) + (y.eval(t, l) - x.eval(t, l)) * z.eval(t, l);
                case "math.random":
                case "math.random_integer":
                    return (t, l) -> (x.eval(t, l) + y.eval(t, l)) * 0.5f; // deterministic: the middle of the range
                default:
                    if (unknown != null) unknown.add(n);
                    return new Const(0f);
            }
        }
    }
}
