package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the immutable BBCode AST: the empty document, record equality as a cache
 * key, double-visitor dispatch, and the defensive-copy contract for records that
 * hold a {@code List}.
 */
public class BBDocumentTest {

    @Test
    public void emptyDocumentHasNoChildren() {
        assertTrue(RXBBDocument.empty().isEmpty());
        assertTrue(RXBBDocument.empty().children().isEmpty());
        assertEquals(RXBBDocument.empty(), new RXBBDocument(List.of()));
    }

    @Test
    public void structurallyEqualDocumentsAreEqualAndShareHashCode() {
        RXBBDocument first = new RXBBDocument(List.of(
                new RXParagraphNode(List.of(new RXTextNode("Hello "),
                        new RXStyleNode(RXStyleType.BOLD, null, List.of(new RXTextNode("world")))))));
        RXBBDocument second = new RXBBDocument(List.of(
                new RXParagraphNode(List.of(new RXTextNode("Hello "),
                        new RXStyleNode(RXStyleType.BOLD, null, List.of(new RXTextNode("world")))))));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        RXBBDocument different = new RXBBDocument(List.of(
                new RXParagraphNode(List.of(new RXTextNode("Hello ")))));
        assertNotEquals(first, different);
    }

    @Test
    public void mutatingSourceListDoesNotAffectRecord() {
        List<RXBBBlockNode> source = new ArrayList<>();
        source.add(new RXParagraphNode(List.of(new RXTextNode("a"))));
        RXBBDocument document = new RXBBDocument(source);

        source.add(new RXHorizontalRuleNode());
        source.clear();

        assertEquals(1, document.children().size());

        List<RXBBInlineNode> inlineSource = new ArrayList<>();
        inlineSource.add(new RXTextNode("x"));
        RXStyleNode style = new RXStyleNode(RXStyleType.ITALIC, null, inlineSource);
        inlineSource.add(new RXTextNode("y"));
        assertEquals(1, style.children().size());
    }

    @Test
    public void inlineVisitorDispatchesToMatchingMethod() {
        RXBBInlineNodeVisitor<String> namer = new RXBBInlineNodeVisitor<>() {
            @Override
            public String visitText(RXTextNode node) {
                return "text:" + node.text();
            }

            @Override
            public String visitLineBreak(RXLineBreakNode node) {
                return "break";
            }

            @Override
            public String visitStyle(RXStyleNode node) {
                return "style:" + node.type();
            }

            @Override
            public String visitLink(RXLinkNode node) {
                return "link:" + node.href();
            }

            @Override
            public String visitImage(RXImageNode node) {
                return "image:" + node.src();
            }

            @Override
            public String visitRawText(RXRawTextNode node) {
                return "raw:" + node.literal();
            }
        };

        assertEquals("text:hi", new RXTextNode("hi").accept(namer));
        assertEquals("break", new RXLineBreakNode().accept(namer));
        assertEquals("style:COLOR", new RXStyleNode(RXStyleType.COLOR, "#fff", List.of()).accept(namer));
        assertEquals("link:https://x", new RXLinkNode("https://x", RXLinkKind.URL, List.of()).accept(namer));
        assertEquals("image:https://y.png", new RXImageNode("https://y.png", null, 0, 0).accept(namer));
        assertEquals("raw:[x]", new RXRawTextNode("[x]").accept(namer));
    }

    @Test
    public void blockVisitorDispatchesToMatchingMethod() {
        RXBBBlockNodeVisitor<String> namer = new RXBBBlockNodeVisitor<>() {
            @Override
            public String visitParagraph(RXParagraphNode node) {
                return "paragraph";
            }

            @Override
            public String visitHeading(RXHeadingNode node) {
                return "heading:" + node.level();
            }

            @Override
            public String visitQuote(RXQuoteNode node) {
                return "quote";
            }

            @Override
            public String visitCodeBlock(RXCodeBlockNode node) {
                return "code";
            }

            @Override
            public String visitList(RXListNode node) {
                return "list:" + node.kind();
            }

            @Override
            public String visitTable(RXTableNode node) {
                return "table";
            }

            @Override
            public String visitSpoiler(RXSpoilerNode node) {
                return "spoiler";
            }

            @Override
            public String visitBackground(RXBackgroundNode node) {
                return "background:" + node.color();
            }

            @Override
            public String visitHorizontalRule(RXHorizontalRuleNode node) {
                return "hr";
            }
        };

        assertEquals("paragraph", new RXParagraphNode(List.of()).accept(namer));
        assertEquals("heading:2", new RXHeadingNode(2, List.of()).accept(namer));
        assertEquals("quote", new RXQuoteNode(null, List.of()).accept(namer));
        assertEquals("code", new RXCodeBlockNode("x", null).accept(namer));
        assertEquals("list:ORDERED", new RXListNode(RXListKind.ORDERED, List.of()).accept(namer));
        assertEquals("table", new RXTableNode(List.of()).accept(namer));
        assertEquals("spoiler", new RXSpoilerNode(null, List.of()).accept(namer));
        assertEquals("background:#eee", new RXBackgroundNode("#eee", List.of()).accept(namer));
        assertEquals("hr", new RXHorizontalRuleNode().accept(namer));
    }

    @Test
    public void headingLevelIsClampedIntoOneToSix() {
        assertEquals(1, new RXHeadingNode(0, List.of()).level());
        assertEquals(1, new RXHeadingNode(-3, List.of()).level());
        assertEquals(6, new RXHeadingNode(9, List.of()).level());
        assertEquals(4, new RXHeadingNode(4, List.of()).level());
    }
}
