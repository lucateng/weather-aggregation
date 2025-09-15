package com.weather.json;

/**
 * Minimal functional JSON parser supporting objects, arrays, strings, numbers, booleans and null.
 */
public final class JsonParser {
    public static JsonValue parse(String json) {
        if (json == null) throw new IllegalArgumentException("json null");
        Cursor c = new Cursor(json);
        JsonValue v = parseValue(c);
        c.skipWhitespace();
        if (!c.isEnd()) throw new IllegalArgumentException("Trailing content at position " + c.pos);
        return v;
    }

    private static JsonValue parseValue(Cursor c) {
        c.skipWhitespace();
        if (c.isEnd()) throw new IllegalArgumentException("Unexpected end of input");
        char ch = c.peek();
        switch (ch) {
            case '{': return parseObject(c);
            case '[': return parseArray(c);
            case '"': return new JsonString(parseString(c));
            case 't': return parseTrue(c);
            case 'f': return parseFalse(c);
            case 'n': return parseNull(c);
            default:
                if (ch == '-' || (ch >= '0' && ch <= '9')) return parseNumber(c);
                throw new IllegalArgumentException("Unexpected char '" + ch + "' at pos " + c.pos);
        }
    }

    private static JsonValue parseObject(Cursor c) {
        c.expect('{');
        JsonObject obj = new JsonObject();
        c.skipWhitespace();
        if (c.peekOr('}') == '}') { c.expect('}'); return obj; }
        while (true) {
            String key = parseString(c);
            c.skipWhitespace();
            c.expect(':');
            JsonValue value = parseValue(c);
            obj.put(key, value);
            c.skipWhitespace();
            char sep = c.expect(',', '}');
            if (sep == '}') break;
            c.skipWhitespace();
        }
        return obj;
    }

    private static JsonValue parseArray(Cursor c) {
        c.expect('[');
        JsonArray arr = new JsonArray();
        c.skipWhitespace();
        if (c.peekOr(']') == ']') { c.expect(']'); return arr; }
        while (true) {
            arr.add(parseValue(c));
            c.skipWhitespace();
            char sep = c.expect(',', ']');
            if (sep == ']') break;
            c.skipWhitespace();
        }
        return arr;
    }

    private static String parseString(Cursor c) {
        c.expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (c.isEnd()) throw new IllegalArgumentException("Unterminated string at pos " + c.pos);
            char ch = c.next();
            if (ch == '"') break;
            if (ch == '\\') {
                char e = c.next();
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            char h = c.next();
                            int val = hex(h);
                            if (val < 0) throw new IllegalArgumentException("Bad unicode escape at pos " + c.pos);
                            code = (code << 4) | val;
                        }
                        sb.append((char) code);
                        break;
                    default: throw new IllegalArgumentException("Bad escape '" + e + "' at pos " + c.pos);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static JsonValue parseTrue(Cursor c) {
        c.expect('t'); c.expect('r'); c.expect('u'); c.expect('e');
        return JsonBoolean.TRUE;
    }

    private static JsonValue parseFalse(Cursor c) {
        c.expect('f'); c.expect('a'); c.expect('l'); c.expect('s'); c.expect('e');
        return JsonBoolean.FALSE;
    }

    private static JsonValue parseNull(Cursor c) {
        c.expect('n'); c.expect('u'); c.expect('l'); c.expect('l');
        return JsonNull.INSTANCE;
    }

    private static JsonValue parseNumber(Cursor c) {
        int start = c.pos;
        if (c.peek() == '-') c.next();
        if (!c.isEnd() && c.peek() == '0') {
            c.next();
        } else {
            if (!c.isEnd() && isDigit(c.peek())) {
                while (!c.isEnd() && isDigit(c.peek())) c.next();
            } else {
                throw new IllegalArgumentException("Invalid number at pos " + c.pos);
            }
        }
        if (!c.isEnd() && c.peek() == '.') {
            c.next();
            if (c.isEnd() || !isDigit(c.peek())) throw new IllegalArgumentException("Invalid fraction at pos " + c.pos);
            while (!c.isEnd() && isDigit(c.peek())) c.next();
        }
        if (!c.isEnd() && (c.peek() == 'e' || c.peek() == 'E')) {
            c.next();
            if (!c.isEnd() && (c.peek() == '+' || c.peek() == '-')) c.next();
            if (c.isEnd() || !isDigit(c.peek())) throw new IllegalArgumentException("Invalid exponent at pos " + c.pos);
            while (!c.isEnd() && isDigit(c.peek())) c.next();
        }
        return new JsonNumber(c.slice(start, c.pos));
    }

    private static boolean isDigit(char ch) { return ch >= '0' && ch <= '9'; }
    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
        if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
        return -1;
    }

    private static final class Cursor {
        final String s;
        int pos;
        Cursor(String s) { this.s = s; }
        boolean isEnd() { return pos >= s.length(); }
        char peek() { return s.charAt(pos); }
        char peekOr(char def) { return isEnd() ? def : s.charAt(pos); }
        char next() { return s.charAt(pos++); }
        void skipWhitespace() { while (!isEnd()) { char c = s.charAt(pos); if (c==' '||c=='\n'||c=='\r'||c=='\t') pos++; else break; } }
        void expect(char expected) { if (isEnd() || s.charAt(pos) != expected) throw new IllegalArgumentException("Expected '" + expected + "' at pos " + pos); pos++; }
        char expect(char a, char b) { if (isEnd()) throw new IllegalArgumentException("Unexpected end at pos " + pos); char c = s.charAt(pos++); if (c!=a && c!=b) throw new IllegalArgumentException("Expected '"+a+"' or '"+b+"' at pos "+(pos-1)); return c; }
        String slice(int start, int end) { return s.substring(start, end); }
    }
}


