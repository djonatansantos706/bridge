package com.merito.agynb;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitário robusto de serialização e parsing recursivo de JSON puro (sem dependências externas).
 * Suporta objetos aninhados, arrays de objetos, tipos numéricos, booleanos, nulos e escapes complexos.
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * Converte uma string JSON em sua representação Java correspondente
     * (Map, List, String, Number, Boolean ou null).
     */
    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return new Parser(json.trim()).parseValue();
    }

    /**
     * Faz o parse de uma string JSON garantindo retorno de um Map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object result = parse(json);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * Faz o parse de uma string JSON garantindo retorno de uma List.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object result = parse(json);
        if (result instanceof List) {
            return (List<Object>) result;
        }
        return new ArrayList<>();
    }

    /**
     * Serializa qualquer objeto Java para string JSON formatada.
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Character) {
            return "\"" + escapeJson(String.valueOf(obj)) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Iterable) {
            Iterable<?> iterable = (Iterable<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(toJson(Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    /**
     * Realiza o escape de caracteres especiais para JSON compatível com RFC 8259.
     */
    public static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20 || (ch >= 0x7F && ch <= 0x9F)) {
                        String hex = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - hex.length(); k++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    // --- Parser Recursivo (Recursive Descent Parser) ---

    private static class Parser {
        private final String src;
        private final int len;
        private int pos;

        Parser(String src) {
            this.src = src;
            this.len = src.length();
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= len) {
                return null;
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == '"') {
                return parseString();
            } else if (c == 't' || c == 'T' || c == 'f' || c == 'F') {
                return parseBoolean();
            } else if (c == 'n' || c == 'N') {
                return parseNull();
            } else if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            } else {
                throw new IllegalArgumentException("Caractere inesperado '" + c + "' no índice " + pos);
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();

            if (pos < len && src.charAt(pos) == '}') {
                pos++;
                return map;
            }

            while (pos < len) {
                skipWhitespace();
                if (pos >= len) break;
                if (src.charAt(pos) == '}') {
                    pos++;
                    return map;
                }

                if (src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Esperado início de chave de objeto com '\"' no índice " + pos);
                }
                String key = parseString();

                skipWhitespace();
                expect(':');
                skipWhitespace();

                Object val = parseValue();
                map.put(key, val);

                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                    if (pos < len && src.charAt(pos) == '}') {
                        pos++;
                        return map;
                    }
                } else if (pos < len && src.charAt(pos) == '}') {
                    pos++;
                    return map;
                } else {
                    if (pos < len) {
                        throw new IllegalArgumentException("Esperado ',' ou '}' após valor de chave '" + key + "' no índice " + pos);
                    }
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();

            if (pos < len && src.charAt(pos) == ']') {
                pos++;
                return list;
            }

            while (pos < len) {
                skipWhitespace();
                if (pos >= len) break;
                if (src.charAt(pos) == ']') {
                    pos++;
                    return list;
                }

                Object val = parseValue();
                list.add(val);

                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                    if (pos < len && src.charAt(pos) == ']') {
                        pos++;
                        return list;
                    }
                } else if (pos < len && src.charAt(pos) == ']') {
                    pos++;
                    return list;
                } else {
                    if (pos < len) {
                        throw new IllegalArgumentException("Esperado ',' ou ']' após elemento de array no índice " + pos);
                    }
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == '\\') {
                    if (pos >= len) {
                        throw new IllegalArgumentException("Escape inacabado no final da string");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > len) {
                                throw new IllegalArgumentException("Sequência de escape unicode incompleta no índice " + pos);
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException nfe) {
                                throw new IllegalArgumentException("Sequência de escape unicode inválida '\\u" + hex + "'");
                            }
                            break;
                        default:
                            sb.append(esc);
                            break;
                    }
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("String não terminada com aspas");
        }

        private Boolean parseBoolean() {
            if (matchesIgnoreCase("true")) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (matchesIgnoreCase("false")) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Token booleano inválido no índice " + pos);
        }

        private Object parseNull() {
            if (matchesIgnoreCase("null")) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Token nulo inválido no índice " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (pos < len && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                pos++;
            }
            boolean isFloating = false;
            while (pos < len) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    isFloating = true;
                    pos++;
                    if (pos < len && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                        pos++;
                    }
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, pos);
            try {
                if (isFloating) {
                    return Double.parseDouble(numStr);
                } else {
                    long val = Long.parseLong(numStr);
                    if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                        return (int) val;
                    }
                    return val;
                }
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Formato numérico inválido: '" + numStr + "' no índice " + start);
            }
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void expect(char expected) {
            if (pos >= len || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Esperado '" + expected + "', encontrado '" + (pos < len ? src.charAt(pos) : "EOF") + "' no índice " + pos);
            }
            pos++;
        }

        private boolean matchesIgnoreCase(String keyword) {
            int kLen = keyword.length();
            if (pos + kLen > len) {
                return false;
            }
            return src.substring(pos, pos + kLen).equalsIgnoreCase(keyword);
        }
    }
}
