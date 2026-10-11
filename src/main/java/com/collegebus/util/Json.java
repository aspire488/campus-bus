package com.collegebus.util;

import com.collegebus.model.Displayable;
import java.util.Collection;
import java.util.Map;

/** Converts Java objects to JSON text so JavaScript can read them (JSON.parse). No external library needed. */
public final class Json {
    private Json() { }

    public static String of(Object o) {
        if (o == null) return "null";
        if (o instanceof Displayable d) return of(d.toMap());          // any model object, via the interface
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (sb.length() > 1) sb.append(',');
                sb.append(of(e.getKey().toString())).append(':').append(of(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (o instanceof Collection<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (Object item : list) sb.append(sb.length() > 1 ? "," : "").append(of(item));
            return sb.append(']').toString();
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : o.toString().toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.append('"').toString();
    }
}
