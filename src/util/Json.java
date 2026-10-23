package util;

import java.util.*;

/**
 * A tiny JSON reader/writer so the project needs NO external libraries.
 * JSON objects  <-> Map<String,Object>   (HashMap concept)
 * JSON arrays   <-> List<Object>         (ArrayList concept)
 * Numbers become Long (whole) or Double, plus String, Boolean and null.
 */
public class Json {

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipSpaces();
        Object value = p.readValue();
        p.skipSpaces();
        if (p.pos != text.length()) throw new IllegalArgumentException("Extra text after JSON");
        return value;
    }

    private static class Parser {
        final String s;
        int pos = 0;
        Parser(String s) { this.s = s; }

        void skipSpaces() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
            pos++;
        }

        Object readValue() {
            skipSpaces();
            char c = peek();
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            return readNumber();
        }

        Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipSpaces();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipSpaces();
                String key = readString();
                skipSpaces();
                expect(':');
                map.put(key, readValue());
                skipSpaces();
                if (peek() == ',') { pos++; continue; }
                expect('}');
                return map;
            }
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipSpaces();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                list.add(readValue());
                skipSpaces();
                if (peek() == ',') { pos++; continue; }
                expect(']');
                return list;
            }
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = peek();
                    pos++;
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: sb.append(e); // \" \\ \/
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object readNumber() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String num = s.substring(start, pos);
            if (num.isEmpty()) throw new IllegalArgumentException("Bad JSON at " + start);
            if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
            return Long.parseLong(num);
        }
    }

    // ---------- writer ----------
    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb, 0, false);
        return sb.toString();
    }

    /** Pretty printed version - used for the data files so humans can read them. */
    public static String pretty(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb, 0, true);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder sb, int depth, boolean pretty) {
        if (o == null) { sb.append("null"); }
        else if (o instanceof String) { quote((String) o, sb); }
        else if (o instanceof Number || o instanceof Boolean) { sb.append(o); }
        else if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                newline(sb, depth + 1, pretty);
                quote(e.getKey(), sb);
                sb.append(pretty ? ": " : ":");
                write(e.getValue(), sb, depth + 1, pretty);
            }
            newline(sb, depth, pretty);
            sb.append('}');
        } else if (o instanceof Collection) {
            Collection<Object> c = (Collection<Object>) o;
            if (c.isEmpty()) { sb.append("[]"); return; }
            sb.append('[');
            boolean first = true;
            for (Object item : c) {
                if (!first) sb.append(',');
                first = false;
                newline(sb, depth + 1, pretty);
                write(item, sb, depth + 1, pretty);
            }
            newline(sb, depth, pretty);
            sb.append(']');
        } else {
            quote(o.toString(), sb);
        }
    }

    private static void newline(StringBuilder sb, int depth, boolean pretty) {
        if (!pretty) return;
        sb.append('\n');
        for (int i = 0; i < depth; i++) sb.append("  ");
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
