package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the structural behaviour of the stack-based parser: null rejection,
 * blank-input handling, the reference AST example, quoted attributes, self-close
 * tags, newline / blank-line rules, block-boundary auto-close, verbatim code, and
 * the {@code [ul]}/{@code [ol]}/{@code [li]} aliases. All inputs are benign (valid
 * URLs / colours / sizes) so they stay green after PR-04 tightens value checks.
 */
public class BBCodeParserTest {

    @Test
    public void rejectsNullAndTreatsBlankAsEmpty() {
        assertThrows(NullPointerException.class,
                () -> RXBBCodeParser.parse(null, RXBBCodePolicy.defaults(), true, false, 512));
        assertThrows(NullPointerException.class,
                () -> RXBBCodeParser.parse("x", null, true, false, 512));

        RXBBCodeParseResult blank = parse("   \n\t  ");
        assertTrue(blank.document().isEmpty());
        assertTrue(blank.warnings().isEmpty());

        assertTrue(parse("").document().isEmpty());
    }

    @Test
    public void referenceExampleParsesToExpectedTree() {
        RXBBDocument expected = new RXBBDocument(List.of(
                new RXParagraphNode(List.of(
                        new RXTextNode("Hello "),
                        new RXStyleNode(RXStyleType.BOLD, null, List.of(
                                new RXTextNode("wor"),
                                new RXStyleNode(RXStyleType.ITALIC, null, List.of(
                                        new RXTextNode("ld"))))))),
                new RXQuoteNode(null, List.of(
                        new RXParagraphNode(List.of(new RXTextNode("hi")))))));

        assertEquals(expected, parse("Hello [b]wor[i]ld[/i][/b]\n[quote]hi[/quote]").document());
    }

    @Test
    public void quotedAttributePreservesSpaces() {
        RXBBDocument document = parse("[quote=\"John Doe\"]hi[/quote]").document();
        RXQuoteNode quote = (RXQuoteNode) document.children().get(0);
        assertEquals("John Doe", quote.author());
    }

    @Test
    public void bgColorWrapsChildrenInBackgroundBlock() {
        RXBackgroundNode bg = assertInstanceOf(RXBackgroundNode.class,
                parse("[bgcolor=#eee]hi[/bgcolor]").document().children().get(0));
        assertEquals("#eee", bg.color());
        assertInstanceOf(RXParagraphNode.class, bg.children().get(0));
    }

    @Test
    public void bgColorWithInvalidColourKeepsChildrenButDropsTint() {
        RXBBCodeParseResult result = parse("[bgcolor=notacolor]hi[/bgcolor]");
        RXBackgroundNode bg = assertInstanceOf(RXBackgroundNode.class,
                result.document().children().get(0));
        assertNull(bg.color());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    public void horizontalRuleSplitsParagraphs() {
        List<RXBBBlockNode> blocks = parse("a[hr]b").document().children();
        assertEquals(3, blocks.size());
        assertInstanceOf(RXParagraphNode.class, blocks.get(0));
        assertInstanceOf(RXHorizontalRuleNode.class, blocks.get(1));
        assertInstanceOf(RXParagraphNode.class, blocks.get(2));
    }

    @Test
    public void softNewlineAndBrBecomeInParagraphLineBreak() {
        RXParagraphNode fromBr = paragraph(parse("a[br]b"));
        RXParagraphNode fromNewline = paragraph(parse("a\nb"));
        List<RXBBInlineNode> expected = List.of(
                new RXTextNode("a"), new RXLineBreakNode(), new RXTextNode("b"));
        assertEquals(expected, fromBr.children());
        assertEquals(expected, fromNewline.children());
    }

    @Test
    public void blankLineSplitsIntoTwoParagraphs() {
        List<RXBBBlockNode> blocks = parse("a\n\nb").document().children();
        assertEquals(2, blocks.size());
        assertEquals(List.of(new RXTextNode("a")), ((RXParagraphNode) blocks.get(0)).children());
        assertEquals(List.of(new RXTextNode("b")), ((RXParagraphNode) blocks.get(1)).children());
    }

    @Test
    public void blockBoundaryAutoClosesOpenInline() {
        List<RXBBBlockNode> blocks = parse("[b]text[quote]q[/quote]").document().children();
        assertEquals(2, blocks.size());
        RXParagraphNode first = (RXParagraphNode) blocks.get(0);
        assertInstanceOf(RXStyleNode.class, first.children().get(0));
        assertInstanceOf(RXQuoteNode.class, blocks.get(1));
    }

    @Test
    public void codeBlockPreservesRawContentAndDoesNotReparse() {
        RXCodeBlockNode padded = (RXCodeBlockNode) parse("[code]  x\ny[/code]").document().children().get(0);
        assertEquals("  x\ny", padded.content());

        RXCodeBlockNode literal = (RXCodeBlockNode) parse("[code][b]x[/b][/code]").document().children().get(0);
        assertEquals("[b]x[/b]", literal.content());
    }

    @Test
    public void listAliasesProduceTheSameAst() {
        RXListNode unordered = (RXListNode) parse("[ul][li]a[li]b[/ul]").document().children().get(0);
        assertEquals(RXListKind.UNORDERED, unordered.kind());
        assertEquals(2, unordered.items().size());

        RXListNode fromStar = (RXListNode) parse("[list][*]a[*]b[/list]").document().children().get(0);
        assertEquals(RXListKind.UNORDERED, fromStar.kind());
        assertEquals(unordered, fromStar);

        RXListNode ordered = (RXListNode) parse("[ol][li]a[/ol]").document().children().get(0);
        assertEquals(RXListKind.ORDERED, ordered.kind());

        RXListNode orderedList = (RXListNode) parse("[list=1][*]a[/list]").document().children().get(0);
        assertEquals(RXListKind.ORDERED, orderedList.kind());
    }

    @Test
    public void wellFormedTableParsesRowsAndHeaderCells() {
        RXTableNode table = (RXTableNode) parse(
                "[table][tr][th]H[/th][/tr][tr][td]a[/td][td]b[/td][/tr][/table]")
                .document().children().get(0);
        assertEquals(2, table.rows().size());
        assertTrue(table.rows().get(0).cells().get(0).header());
        assertEquals(2, table.rows().get(1).cells().size());
    }

    @Test
    public void nestedInlineStylesComposeCorrectly() {
        RXParagraphNode paragraph = paragraph(parse("[b][i]x[/i][/b]"));
        RXStyleNode bold = (RXStyleNode) paragraph.children().get(0);
        assertEquals(RXStyleType.BOLD, bold.type());
        RXStyleNode italic = (RXStyleNode) bold.children().get(0);
        assertEquals(RXStyleType.ITALIC, italic.type());
        assertEquals(List.of(new RXTextNode("x")), italic.children());
    }

    private static RXBBCodeParseResult parse(String content) {
        return RXBBCodeParser.parse(content, RXBBCodePolicy.defaults(), true, false, 512);
    }

    private static RXParagraphNode paragraph(RXBBCodeParseResult result) {
        return (RXParagraphNode) result.document().children().get(0);
    }
}
