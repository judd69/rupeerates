package rupeerates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON parser: enough for the Frankfurter responses RupeeRates
 * consumes (objects, arrays, strings with escapes, numbers, booleans, null).
 * Zero dependencies; numbers are returned as Double.
 */
public final class Json {
    private final String input;
    private int pos;

    private Json(String input) {
        this.input = input;
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw new IllegalArgumentException("Trailing data after JSON value at offset " + parser.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    public static String str(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected a JSON string");
        }
        return (String) value;
    }

    public static double num(Object value) {
        if (!(value instanceof Double)) {
            throw new IllegalArgumentException("Expected a JSON number");
        }
        return (Double) value;
    }

    public static Object field(Map<String, Object> object, String key) {
        if (!object.containsKey(key)) {
            throw new IllegalArgumentException("Missing JSON field: " + key);
        }
        return object.get(key);
    }

    /** JSON string escaping for the small JSON output RupeeRates produces. */
    public static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private Object value() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }
        char c = input.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = string();
            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != ':') {
                throw new IllegalArgumentException("Expected ':' in object at offset " + pos);
            }
            pos++;
            map.put(key, value());
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unterminated object");
            }
            char c = input.charAt(pos++);
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Expected ',' or '}' in object at offset " + pos);
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unterminated array");
            }
            char c = input.charAt(pos++);
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Expected ',' or ']' in array at offset " + pos);
            }
        }
    }

    private String string() {
        if (input.charAt(pos) != '"') {
            throw new IllegalArgumentException("Expected string at offset " + pos);
        }
        pos++;
        StringBuilder out = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                if (pos >= input.length()) {
                    break;
                }
                char esc = input.charAt(pos++);
                switch (esc) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (pos + 4 > input.length()) {
                            throw new IllegalArgumentException("Bad \\u escape");
                        }
                        out.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Bad escape \\" + esc);
                }
            } else {
                out.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private Double number() {
        int start = pos;
        while (pos < input.length() && "-+0123456789.eE".indexOf(input.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Expected a value at offset " + pos);
        }
        try {
            return Double.parseDouble(input.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad number at offset " + start);
        }
    }

    private void expect(String literal) {
        if (!input.startsWith(literal, pos)) {
            throw new IllegalArgumentException("Expected '" + literal + "' at offset " + pos);
        }
        pos += literal.length();
    }
}
