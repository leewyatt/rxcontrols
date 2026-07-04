package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests structural tag-soup recovery (§16.2): auto-close, mismatched-close drop,
 * implicit rows / items, unknown-tag unwrap, and the {@code lenient} ×
 * {@code showMalformedTagsAsText} quadrants. Only structural warning codes are
 * produced here — value/security codes belong to {@code BBCodeSecurityTest}.
 */
public class BBCodeRecoveryTest {

    @Test
    public void unclosedTagAutoClosesAtEof() {
        RXBBCodeParseResult result = parse("[b]hello", true, false);
        RXStyleNode bold = (RXStyleNode) paragraph(result).children().get(0);
        assertEquals(RXStyleType.BOLD, bold.type());
        assertEquals(List.of(new RXTextNode("hello")), bold.children());
        assertTrue(codes(result).contains(RXBBWarningCode.UNCLOSED_TAG));
    }

    @Test
    public void mismatchedCloseIsDropped() {
        RXBBCodeParseResult result = parse("[b]hello[/i]", true, false);
        RXStyleNode bold = (RXStyleNode) paragraph(result).children().get(0);
        assertEquals(RXStyleType.BOLD, bold.type());
        assertEquals(List.of(new RXTextNode("hello")), bold.children());
        assertTrue(codes(result).contains(RXBBWarningCode.MISMATCHED_CLOSE));
    }

    @Test
    public void strayBgColorCloseIsMismatchedNotUnknown() {
        // bgcolor is a real tag, so a stray close reports MISMATCHED_CLOSE like [/quote],
        // not UNKNOWN_TAG.
        RXBBCodeParseResult result = parse("hi [/bgcolor]", true, false);
        assertTrue(codes(result).contains(RXBBWarningCode.MISMATCHED_CLOSE));
        assertFalse(codes(result).contains(RXBBWarningCode.UNKNOWN_TAG));
    }

    @Test
    public void strayTableCellGetsImplicitRow() {
        RXBBCodeParseResult result = parse("[table][td]no tr[/td][/table]", true, false);
        RXTableNode table = (RXTableNode) result.document().children().get(0);
        assertEquals(1, table.rows().size());
        assertEquals(1, table.rows().get(0).cells().size());
        assertTrue(codes(result).contains(RXBBWarningCode.IMPLICIT_TABLE_ROW));
    }

    @Test
    public void looseTableContentIsNotLost() {
        RXBBCodeParseResult result = parse("[table]hello[/table]", true, false);
        RXTableNode table = (RXTableNode) result.document().children().get(0);
        assertEquals(1, table.rows().size());
        assertEquals(1, table.rows().get(0).cells().size());
        assertTrue(codes(result).contains(RXBBWarningCode.IMPLICIT_TABLE_ROW));
    }

    @Test
    public void unclosedRowsAndCellsStartSiblings() {
        RXTableNode rows = (RXTableNode) parse(
                "[table][tr][td]a[/td][tr][td]b[/td][/tr][/table]", true, false).document().children().get(0);
        assertEquals(2, rows.rows().size());

        RXTableNode cells = (RXTableNode) parse(
                "[table][tr][td]a[td]b[/td][/tr][/table]", true, false).document().children().get(0);
        assertEquals(2, cells.rows().get(0).cells().size());
    }

    @Test
    public void looseListContentGetsImplicitItem() {
        RXBBCodeParseResult result = parse("[list] item [/list]", true, false);
        RXListNode list = (RXListNode) result.document().children().get(0);
        assertEquals(1, list.items().size());
        assertTrue(codes(result).contains(RXBBWarningCode.IMPLICIT_LIST_ITEM));
    }

    @Test
    public void unknownTagIsUnwrappedByDefault() {
        RXBBCodeParseResult result = parse("[foo]bar[/foo]", true, false);
        assertEquals(List.of(new RXTextNode("bar")), paragraph(result).children());
        assertTrue(codes(result).contains(RXBBWarningCode.UNKNOWN_TAG));
    }

    @Test
    public void showMalformedEchoesUnknownTagLiterally() {
        RXBBCodeParseResult result = parse("[foo]bar[/foo]", true, true);
        assertEquals(List.of(
                        new RXRawTextNode("[foo]"),
                        new RXTextNode("bar"),
                        new RXRawTextNode("[/foo]")),
                paragraph(result).children());
    }

    @Test
    public void strictModeUnwrapsUnclosedInlineAcrossFourQuadrants() {
        // lenient keeps the auto-closed bold; strict treats the unclosed tag as
        // non-markup and unwraps it (echoing the literal only when showMalformed).
        assertInstanceOf(RXStyleNode.class, paragraph(parse("[b]hi", true, false)).children().get(0));
        assertInstanceOf(RXStyleNode.class, paragraph(parse("[b]hi", true, true)).children().get(0));

        assertEquals(List.of(new RXTextNode("hi")),
                paragraph(parse("[b]hi", false, false)).children());
        assertEquals(List.of(new RXRawTextNode("[b]"), new RXTextNode("hi")),
                paragraph(parse("[b]hi", false, true)).children());
    }

    @Test
    public void strictModeDoesNotChangeValueHandling() {
        // A well-formed colour tag is unaffected by lenient/strict (value checks
        // are orthogonal to structural recovery).
        RXStyleNode lenient = (RXStyleNode) paragraph(parse("[color=red]x[/color]", true, false)).children().get(0);
        RXStyleNode strict = (RXStyleNode) paragraph(parse("[color=red]x[/color]", false, false)).children().get(0);
        assertEquals(lenient, strict);
        assertEquals("red", lenient.value());
    }

    private static RXBBCodeParseResult parse(String content, boolean lenient, boolean show) {
        return RXBBCodeParser.parse(content, RXBBCodePolicy.defaults(), lenient, show, 512);
    }

    private static RXParagraphNode paragraph(RXBBCodeParseResult result) {
        return (RXParagraphNode) result.document().children().get(0);
    }

    private static List<RXBBWarningCode> codes(RXBBCodeParseResult result) {
        return result.warnings().stream().map(RXBBCodeParseWarning::code).collect(Collectors.toList());
    }
}
