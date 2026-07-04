package io.github.leewyatt.rxcontrols.bbcode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single-pass, left-to-right BBCode tokenizer. Package-private and never throws:
 * any bracket that does not form a well-formed tag head (a lone {@code [}, a lone
 * {@code ]}, an empty {@code []}, a {@code [[}, or a tag with an unterminated
 * quote) is emitted as literal {@link TextToken} text.
 *
 * <p>Tag names and attribute keys are lower-cased ({@link Locale#ROOT}); positional
 * and attribute values are kept verbatim with any surrounding quotes stripped.
 * Quoted values (single or double) may contain whitespace and {@code ]} up to the
 * closing quote.
 */
final class BBCodeLexer {

    private BBCodeLexer() {
    }

    /**
     * Tokenizes the given source into a flat token stream.
     *
     * @param source the raw BBCode source; never {@code null}
     * @return the token stream (never throws; malformed markup becomes text)
     */
    static List<BBToken> tokenize(String source) {
        List<BBToken> tokens = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        int length = source.length();
        while (i < length) {
            char c = source.charAt(i);
            if (c == '[') {
                LexedTag lexed = tryLexTag(source, i);
                if (lexed != null) {
                    flushText(tokens, text);
                    tokens.add(lexed.token());
                    i += lexed.length();
                    continue;
                }
            }
            text.append(c);
            i++;
        }
        flushText(tokens, text);
        return tokens;
    }

    private static void flushText(List<BBToken> tokens, StringBuilder text) {
        if (text.length() > 0) {
            tokens.add(new TextToken(text.toString()));
            text.setLength(0);
        }
    }

    private static LexedTag tryLexTag(String source, int start) {
        int length = source.length();
        int next = start + 1;
        if (next >= length) {
            return null;
        }
        char after = source.charAt(next);
        if (after == '/') {
            return tryLexClose(source, start);
        }
        if (after == '*') {
            if (next + 1 < length && source.charAt(next + 1) == ']') {
                return new LexedTag(new ListItemToken(), 3);
            }
            return null;
        }
        return tryLexOpen(source, start);
    }

    private static LexedTag tryLexClose(String source, int start) {
        int length = source.length();
        int nameStart = start + 2;
        int i = nameStart;
        if (i >= length || !isNameStart(source.charAt(i))) {
            return null;
        }
        i++;
        while (i < length && isNameChar(source.charAt(i))) {
            i++;
        }
        if (i >= length || source.charAt(i) != ']') {
            return null;
        }
        String name = source.substring(nameStart, i).toLowerCase(Locale.ROOT);
        String raw = source.substring(start, i + 1);
        return new LexedTag(new TagCloseToken(name, raw), raw.length());
    }

    private static LexedTag tryLexOpen(String source, int start) {
        int length = source.length();
        int nameStart = start + 1;
        if (nameStart >= length || !isNameStart(source.charAt(nameStart))) {
            return null;
        }
        int close = findTagClose(source, nameStart);
        if (close < 0) {
            return null;
        }
        String body = source.substring(nameStart, close);
        ParsedTag parsed = parseBody(body);
        if (parsed == null) {
            return null;
        }
        String raw = source.substring(start, close + 1);
        return new LexedTag(new TagOpenToken(parsed.name(), parsed.positionalValue(),
                parsed.attributes(), raw), raw.length());
    }

    /**
     * Returns the index of the {@code ]} that closes the tag opened at
     * {@code from}, respecting quoted regions. Returns {@code -1} if an unquoted
     * {@code [} is seen first (nested bracket → not a tag) or the tag/quote is
     * never closed.
     */
    private static int findTagClose(String source, int from) {
        int length = source.length();
        boolean inQuote = false;
        char quote = 0;
        for (int i = from; i < length; i++) {
            char c = source.charAt(i);
            if (inQuote) {
                if (c == quote) {
                    inQuote = false;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quote = c;
            } else if (c == ']') {
                return i;
            } else if (c == '[') {
                return -1;
            }
        }
        return -1;
    }

    private static ParsedTag parseBody(String body) {
        List<String> parts = splitRespectingQuotes(body);
        if (parts.isEmpty()) {
            return null;
        }
        String first = parts.get(0);
        String name;
        String positional;
        int equals = first.indexOf('=');
        if (equals >= 0) {
            name = first.substring(0, equals).toLowerCase(Locale.ROOT);
            positional = unquote(first.substring(equals + 1));
        } else {
            name = first.toLowerCase(Locale.ROOT);
            positional = null;
        }
        if (!isValidName(name)) {
            return null;
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);
            int keyEnd = part.indexOf('=');
            if (keyEnd >= 0) {
                String key = part.substring(0, keyEnd).toLowerCase(Locale.ROOT);
                if (!key.isEmpty()) {
                    attributes.put(key, unquote(part.substring(keyEnd + 1)));
                }
            } else {
                String key = part.toLowerCase(Locale.ROOT);
                if (!key.isEmpty()) {
                    attributes.put(key, "");
                }
            }
        }
        return new ParsedTag(name, positional, attributes);
    }

    private static List<String> splitRespectingQuotes(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        boolean hasContent = false;
        char quote = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inQuote) {
                current.append(c);
                if (c == quote) {
                    inQuote = false;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quote = c;
                current.append(c);
                hasContent = true;
            } else if (Character.isWhitespace(c)) {
                if (hasContent) {
                    parts.add(current.toString());
                    current.setLength(0);
                    hasContent = false;
                }
            } else {
                current.append(c);
                hasContent = true;
            }
        }
        if (hasContent) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean isValidName(String name) {
        if (name.isEmpty() || !isNameStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isNameChar(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isNameChar(char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }

    private record LexedTag(BBToken token, int length) {
    }

    private record ParsedTag(String name, String positionalValue, Map<String, String> attributes) {
    }
}
