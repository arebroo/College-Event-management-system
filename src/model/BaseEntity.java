package model;

import java.util.Map;

/**
 * ABSTRACT CLASS: parent of everything stored in a data file.
 * It holds the common field (id) and some helper methods.
 * Child classes MUST write their own toMap() because it is abstract (from Storable).
 */
public abstract class BaseEntity implements Storable {
    protected String id;

    protected BaseEntity(String id) { this.id = id; }

    public String getId() { return id; }

    // Helpers to safely read values from a Map (used when loading from JSON)
    protected static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString().trim();
    }

    protected static int num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    protected static boolean bool(Map<String, Object> m, String key) {
        return Boolean.TRUE.equals(m.get(key));
    }
}
