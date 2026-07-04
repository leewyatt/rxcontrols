package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the nesting-depth crash guard and confirms there is no input-length /
 * node-count limit (slow, not crash, is the caller's concern). The parser itself
 * is iterative, so these assert the depth guard fires (bounding the AST the
 * renderer would recurse over) and that fixed large samples parse without
 * throwing — not that arbitrary input never runs out of memory.
 */
public class BBCodeLimitsTest {

    @Test
    public void deepNestingIsGuardedAndDoesNotThrow() {
        String deep = repeat("[b]", 2000) + "x" + repeat("[/b]", 2000);
        RXBBCodeParseResult result =
                assertDoesNotThrow(() -> RXBBCodeParser.parse(deep, RXBBCodePolicy.defaults(), true, false, 512));
        assertTrue(codes(result).contains(RXBBWarningCode.MAX_DEPTH_EXCEEDED));
    }

    @Test
    public void overDepthTagsFollowShowMalformed() {
        // A small depth keeps the AST shallow so the echoed literal is easy to find.
        String deep = repeat("[b]", 20) + "x" + repeat("[/b]", 20);
        RXBBCodeParseResult echoed =
                RXBBCodeParser.parse(deep, RXBBCodePolicy.defaults(), true, true, 8);
        assertTrue(codes(echoed).contains(RXBBWarningCode.MAX_DEPTH_EXCEEDED));
        // showMalformed echoes the over-depth open tags as literal text.
        assertTrue(containsRawText(echoed), "over-depth tags should be echoed with showMalformed");
    }

    @Test
    public void negativeDepthDisablesTheGuard() {
        String deep = repeat("[b]", 2000) + "x" + repeat("[/b]", 2000);
        RXBBCodeParseResult result =
                assertDoesNotThrow(() -> RXBBCodeParser.parse(deep, RXBBCodePolicy.defaults(), true, false, -1));
        assertFalse(codes(result).contains(RXBBWarningCode.MAX_DEPTH_EXCEEDED));
    }

    @Test
    public void largeFlatSampleParsesWithoutThrowing() {
        String flat = repeat("[b]a[/b]", 5000);
        RXBBCodeParseResult result =
                assertDoesNotThrow(() -> RXBBCodeParser.parse(flat, RXBBCodePolicy.defaults(), true, false, 512));
        assertFalse(result.document().isEmpty());
        assertTrue(codes(result).isEmpty(), "a valid flat sample produces no warnings");
    }

    private static String repeat(String token, int count) {
        StringBuilder builder = new StringBuilder(token.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(token);
        }
        return builder.toString();
    }

    private static List<RXBBWarningCode> codes(RXBBCodeParseResult result) {
        return result.warnings().stream().map(RXBBCodeParseWarning::code).collect(Collectors.toList());
    }

    private static boolean containsRawText(RXBBCodeParseResult result) {
        return result.document().children().stream().anyMatch(block ->
                block instanceof RXParagraphNode paragraph && inlinesContainRawText(paragraph.children()));
    }

    private static boolean inlinesContainRawText(List<RXBBInlineNode> nodes) {
        for (RXBBInlineNode node : nodes) {
            if (node instanceof RXRawTextNode) {
                return true;
            }
            if (node instanceof RXStyleNode style && inlinesContainRawText(style.children())) {
                return true;
            }
            if (node instanceof RXLinkNode link && inlinesContainRawText(link.children())) {
                return true;
            }
        }
        return false;
    }
}
