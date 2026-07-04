package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests parse-time security: URL / image scheme allow-lists, {@code [style]}
 * defaulting to off, colour regex pre-check, and the "drop the offending value,
 * keep the children, never throw" downgrade. All headless — validation uses only
 * pure value factories (no toolkit).
 */
public class BBCodeSecurityTest {

    @Test
    public void allowedUrlSchemesProduceSanitizedLinks() {
        RXLinkNode link = (RXLinkNode) inline(parse("[url=https://ex.com/a]click[/url]"));
        assertEquals("https://ex.com/a", link.href());
        assertEquals(RXLinkKind.URL, link.kind());
        assertEquals(List.of(new RXTextNode("click")), link.children());

        RXLinkNode body = (RXLinkNode) inline(parse("[url]http://ex.com[/url]"));
        assertEquals("http://ex.com", body.href());
    }

    @Test
    public void blockedUrlSchemesDegradeToText() {
        for (String bad : List.of("javascript:alert(1)", "vbscript:x", "data:text/html,x",
                "file:///etc/passwd", "jar:x", "ftp://h/f")) {
            RXBBCodeParseResult result = parse("[url=" + bad + "]x[/url]");
            assertFalse(hasNodeOfType(result, RXLinkNode.class),
                    "expected no link node for " + bad);
            assertTrue(codes(result).contains(RXBBWarningCode.BLOCKED_URL),
                    "expected BLOCKED_URL for " + bad);
        }
    }

    @Test
    public void blocklistBacksUpAPermissiveAllowList() {
        // Defense in depth: even if a caller mistakenly allow-lists a dangerous
        // scheme, the blocklist still rejects it (for both links and images).
        RXBBCodeUrlPolicy url = new RXBBCodeUrlPolicy(Set.of("http", "https", "javascript"));
        RXBBCodeImagePolicy image = new RXBBCodeImagePolicy(Set.of("http", "https", "data"), true);
        RXBBCodePolicy permissive = new RXBBCodePolicy(url, image);

        RXBBCodeParseResult link = RXBBCodeParser.parse(
                "[url=javascript:alert(1)]x[/url]", permissive, true, false, 512);
        assertFalse(hasNodeOfType(link, RXLinkNode.class));
        assertTrue(codes(link).contains(RXBBWarningCode.BLOCKED_URL));

        RXBBCodeParseResult img = RXBBCodeParser.parse(
                "[img]data:text/html,x[/img]", permissive, true, false, 512);
        assertFalse(hasNodeOfType(img, RXImageNode.class));
        assertTrue(codes(img).contains(RXBBWarningCode.INVALID_IMAGE_URL));
    }

    @Test
    public void mixedCaseAndWhitespaceSchemesAreRejected() {
        assertTrue(codes(parse("[url=JavaScript:x]y[/url]")).contains(RXBBWarningCode.BLOCKED_URL));
        // A quoted value keeps its embedded whitespace, exercising the control-char guard.
        assertTrue(codes(parse("[url=\"http://ev il.com\"]y[/url]")).contains(RXBBWarningCode.INVALID_URL));
    }

    @Test
    public void nonFiniteSizesAndDimensionsAreRejectedOrNeutralized() {
        assertFalse(hasNodeOfType(parse("[size=NaN]x[/size]"), RXStyleNode.class));
        assertFalse(hasNodeOfType(parse("[size=Infinity]x[/size]"), RXStyleNode.class));

        RXImageNode image = (RXImageNode) inline(parse("[img width=Infinity]https://x/y.png[/img]"));
        assertEquals(0.0, image.width());
    }

    @Test
    public void urlWithoutSchemeIsInvalid() {
        RXBBCodeParseResult result = parse("[url=www.example.com]x[/url]");
        assertFalse(hasNodeOfType(result, RXLinkNode.class));
        assertTrue(codes(result).contains(RXBBWarningCode.INVALID_URL));
    }

    @Test
    public void emailNormalizesToMailto() {
        RXLinkNode link = (RXLinkNode) inline(parse("[email]a@b.com[/email]"));
        assertEquals("mailto:a@b.com", link.href());
        assertEquals(RXLinkKind.EMAIL, link.kind());
    }

    @Test
    public void imageAllowsHttpSchemesAndKeepsSanitizedSrc() {
        RXImageNode image = (RXImageNode) inline(
                parse("[img alt=A width=100 height=80]https://x/y.png[/img]"));
        assertEquals("https://x/y.png", image.src());
        assertEquals("A", image.alt());
        assertEquals(100.0, image.width());
        assertEquals(80.0, image.height());
    }

    @Test
    public void badImageUrlIsDroppedWithoutTextFallback() {
        RXBBCodeParseResult result = parse("[img]file:///etc/passwd[/img]");
        assertFalse(hasNodeOfType(result, RXImageNode.class));
        assertTrue(result.document().isEmpty(), "alt/src must not degrade to visible text");
        assertTrue(codes(result).contains(RXBBWarningCode.INVALID_IMAGE_URL));
    }

    @Test
    public void styleTagIsUnsupportedAndNeverInjected() {
        RXBBCodeParseResult result = parse("[style=-fx-background-image: url(evil)]x[/style]");
        assertEquals(List.of(new RXTextNode("x")), paragraph(result).children());
        assertTrue(codes(result).contains(RXBBWarningCode.UNKNOWN_TAG));
        assertFalse(hasNodeOfType(result, RXStyleNode.class));
    }

    @Test
    public void colorRejectsSemicolonAndInjection() {
        RXBBCodeParseResult result = parse("[color=red;evil]y[/color]");
        assertFalse(hasNodeOfType(result, RXStyleNode.class));
        assertEquals(List.of(new RXTextNode("y")), paragraph(result).children());
        assertTrue(codes(result).contains(RXBBWarningCode.INVALID_COLOR));
    }

    @Test
    public void validColorsAreStoredAsStrings() {
        assertEquals("#ff0000", style(parse("[color=#ff0000]x[/color]")).value());
        assertEquals("red", style(parse("[color=red]x[/color]")).value());
        assertEquals(RXStyleType.COLOR, style(parse("[color=red]x[/color]")).type());
    }

    @Test
    public void sizeIsNotClampedButGarbageIsDropped() {
        assertEquals("99999", style(parse("[size=99999]x[/size]")).value());
        assertEquals(RXStyleType.SIZE, style(parse("[size=99999]x[/size]")).type());

        RXBBCodeParseResult zero = parse("[size=0]x[/size]");
        assertFalse(hasNodeOfType(zero, RXStyleNode.class));
        assertTrue(codes(zero).contains(RXBBWarningCode.INVALID_SIZE));

        RXBBCodeParseResult letters = parse("[size=abc]x[/size]");
        assertFalse(hasNodeOfType(letters, RXStyleNode.class));
        assertTrue(codes(letters).contains(RXBBWarningCode.INVALID_SIZE));
    }

    @Test
    public void fontRejectsStructurallyUnsafeFamilies() {
        assertEquals("Arial", style(parse("[font=Arial]x[/font]")).value());

        RXBBCodeParseResult bad = parse("[font=a;b]x[/font]");
        assertFalse(hasNodeOfType(bad, RXStyleNode.class));
        assertTrue(codes(bad).contains(RXBBWarningCode.INVALID_FONT));
    }

    // ==================== Helpers ====================

    private static RXBBCodeParseResult parse(String content) {
        return RXBBCodeParser.parse(content, RXBBCodePolicy.defaults(), true, false, 512);
    }

    private static RXParagraphNode paragraph(RXBBCodeParseResult result) {
        return (RXParagraphNode) result.document().children().get(0);
    }

    private static RXBBInlineNode inline(RXBBCodeParseResult result) {
        return paragraph(result).children().get(0);
    }

    private static RXStyleNode style(RXBBCodeParseResult result) {
        return (RXStyleNode) inline(result);
    }

    private static List<RXBBWarningCode> codes(RXBBCodeParseResult result) {
        return result.warnings().stream().map(RXBBCodeParseWarning::code).collect(Collectors.toList());
    }

    private static boolean hasNodeOfType(RXBBCodeParseResult result, Class<?> type) {
        return result.document().children().stream().anyMatch(block -> containsType(block, type));
    }

    private static boolean containsType(RXBBBlockNode block, Class<?> type) {
        if (type.isInstance(block)) {
            return true;
        }
        if (block instanceof RXParagraphNode paragraph) {
            return paragraph.children().stream().anyMatch(child -> inlineContainsType(child, type));
        }
        return false;
    }

    private static boolean inlineContainsType(RXBBInlineNode node, Class<?> type) {
        if (type.isInstance(node)) {
            return true;
        }
        if (node instanceof RXStyleNode style) {
            return style.children().stream().anyMatch(child -> inlineContainsType(child, type));
        }
        if (node instanceof RXLinkNode link) {
            return link.children().stream().anyMatch(child -> inlineContainsType(child, type));
        }
        return false;
    }
}
