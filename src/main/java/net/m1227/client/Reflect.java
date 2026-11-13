package net.m1227.client;

import java.lang.reflect.Field;

/**
 * Reads private fields of Minecraft classes by TYPE and position instead of by name, so it works
 * with both the development (MCP) and the obfuscated (SRG) names. Returns null when not found;
 * callers always have a fallback.
 */
public final class Reflect {
    private Reflect() {}

    /** The index-th field (0-based) of the given type declared in 'declaring', read from o. */
    @SuppressWarnings("unchecked")
    public static <T> T get(Object o, Class<?> declaring, Class<T> type, int index) {
        try {
            int n = 0;
            for (Field f : declaring.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!type.isAssignableFrom(f.getType())) continue;
                if (n++ == index) {
                    f.setAccessible(true);
                    return (T) f.get(o);
                }
            }
        } catch (Throwable ignored) {
            // fall through to null
        }
        return null;
    }
}
