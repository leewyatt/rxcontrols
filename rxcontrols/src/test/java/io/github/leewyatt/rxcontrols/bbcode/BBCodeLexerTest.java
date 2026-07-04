package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the single-pass BBCode tokenizer: every token form, quoted values that
 * contain whitespace and {@code ]}, case-folding of names/keys, and the rule that
 * every stray or malformed bracket becomes literal text (the lexer never fails).
 */
public class BBCodeLexerTest {

    @Test
    public void plainTextIsOneTextToken() {
        List<BBToken> tokens = BBCodeLexer.tokenize("hello world");
        assertEquals(1, tokens.size());
        assertEquals("hello world", text(tokens.get(0)));
    }

    @Test
    public void openTagWithoutAttributes() {
        TagOpenToken open = (TagOpenToken) single("[b]");
        assertEquals("b", open.name());
        assertNull(open.positionalValue());
        assertTrue(open.attributes().isEmpty());
        assertEquals("[b]", open.rawSource());
    }

    @Test
    public void openTagWithPositionalValue() {
        TagOpenToken open = (TagOpenToken) single("[url=https://example.com/a?x=y]");
        assertEquals("url", open.name());
        // indexOf('=') splits at the FIRST '=', so the query "x=y" stays in the value.
        assertEquals("https://example.com/a?x=y", open.positionalValue());
        assertTrue(open.attributes().isEmpty());
    }

    @Test
    public void openTagWithNamedAttributes() {
        TagOpenToken open = (TagOpenToken) single("[img alt=A width=100 height=80]");
        assertEquals("img", open.name());
        assertNull(open.positionalValue());
        assertEquals("A", open.attributes().get("alt"));
        assertEquals("100", open.attributes().get("width"));
        assertEquals("80", open.attributes().get("height"));
    }

    @Test
    public void quotedAttributeValueMayContainSpacesAndBracket() {
        TagOpenToken open = (TagOpenToken) single("[url=\"a b]c\"]");
        assertEquals("url", open.name());
        assertEquals("a b]c", open.positionalValue());

        TagOpenToken single = (TagOpenToken) single("[img alt='hello world]']");
        assertEquals("hello world]", single.attributes().get("alt"));
    }

    @Test
    public void nameAndKeyLowercasedButValueVerbatim() {
        TagOpenToken open = (TagOpenToken) single("[URL=HTTP://X]");
        assertEquals("url", open.name());
        assertEquals("HTTP://X", open.positionalValue());

        TagOpenToken attrs = (TagOpenToken) single("[IMG ALT=Foo]");
        assertEquals("img", attrs.name());
        assertEquals("Foo", attrs.attributes().get("alt"));

        TagCloseToken close = (TagCloseToken) single("[/B]");
        assertEquals("b", close.name());
    }

    @Test
    public void headingTagNamesWithDigitsLex() {
        assertEquals("h1", ((TagOpenToken) single("[h1]")).name());
        TagOpenToken h6 = (TagOpenToken) single("[h6=2]");
        assertEquals("h6", h6.name());
        assertEquals("2", h6.positionalValue());
        assertEquals("h3", ((TagCloseToken) single("[/h3]")).name());
    }

    @Test
    public void mixedQuoteCharsStayLiteralInsideValue() {
        TagOpenToken open = (TagOpenToken) single("[url='a\"b']");
        assertEquals("a\"b", open.positionalValue());
    }

    @Test
    public void emptyValueIsDistinctFromAbsentValue() {
        assertEquals("", ((TagOpenToken) single("[a=]")).positionalValue());
        assertNull(((TagOpenToken) single("[a]")).positionalValue());
        assertEquals("", ((TagOpenToken) single("[a b=]")).attributes().get("b"));
    }

    @Test
    public void closeTagAndListItem() {
        assertEquals("quote", ((TagCloseToken) single("[/quote]")).name());
        assertInstanceOf(ListItemToken.class, single("[*]"));
    }

    @Test
    public void strayBracketsBecomeText() {
        assertEquals("[", allText("["));
        assertEquals("]", allText("]"));
        assertEquals("[]", allText("[]"));
        assertEquals("[[", allText("[["));
        assertEquals("][", allText("]["));
    }

    @Test
    public void malformedTagHeadsBecomeText() {
        assertEquals("[1]", allText("[1]"));
        assertEquals("[/]", allText("[/]"));
        assertEquals("[ b]", allText("[ b]"));
        assertEquals("[b", allText("[b"));
    }

    @Test
    public void unterminatedQuoteBecomesText() {
        assertEquals("[url=\"abc]", allText("[url=\"abc]"));
    }

    @Test
    public void nestedOpenBracketInsideTagInvalidatesIt() {
        List<BBToken> tokens = BBCodeLexer.tokenize("[b[i]]");
        // Leading '[' is text; then [i] lexes as a tag; trailing ']' is text.
        assertEquals("[b", text(tokens.get(0)));
        assertEquals("i", ((TagOpenToken) tokens.get(1)).name());
        assertEquals("]", text(tokens.get(2)));
    }

    @Test
    public void mixedContentTokenizesInOrder() {
        List<BBToken> tokens = BBCodeLexer.tokenize("Hello [b]world[/b]!");
        assertEquals("Hello ", text(tokens.get(0)));
        assertEquals("b", ((TagOpenToken) tokens.get(1)).name());
        assertEquals("world", text(tokens.get(2)));
        assertEquals("b", ((TagCloseToken) tokens.get(3)).name());
        assertEquals("!", text(tokens.get(4)));
        assertEquals(5, tokens.size());
    }

    private static BBToken single(String source) {
        List<BBToken> tokens = BBCodeLexer.tokenize(source);
        assertEquals(1, tokens.size(), () -> "expected one token for " + source + " but got " + tokens);
        return tokens.get(0);
    }

    private static String allText(String source) {
        List<BBToken> tokens = BBCodeLexer.tokenize(source);
        StringBuilder builder = new StringBuilder();
        for (BBToken token : tokens) {
            builder.append(text(token));
        }
        return builder.toString();
    }

    private static String text(BBToken token) {
        return ((TextToken) token).text();
    }
}
