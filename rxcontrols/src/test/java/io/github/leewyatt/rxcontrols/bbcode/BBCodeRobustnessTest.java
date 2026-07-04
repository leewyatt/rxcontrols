package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §18.D robustness contract for {@link RXBBCodeParser}: fixed-seed fuzz strings parse
 * without ever throwing and with a bounded, deterministic warning count; deep nesting is
 * capped by the depth guard (so render recursion cannot {@code StackOverflow}); and a
 * fixed representative large sample parses without throwing, its total AST node count
 * scaling linearly with input size. No promise is made about arbitrarily large inputs
 * (length / node count are uncapped by design — resource limits are the caller's
 * responsibility), and this class asserts output-size scaling, not wall-clock complexity.
 */
public class BBCodeRobustnessTest {

    private static final long FUZZ_SEED = 20260704L;
    private static final int DEFAULT_DEPTH = 512;

    private static final RXBBCodePolicy PERMISSIVE = new RXBBCodePolicy(
            new RXBBCodeUrlPolicy(Set.of("http", "https", "mailto", "javascript")),
            new RXBBCodeImagePolicy(Set.of("http", "https", "data"), true));

    /**
     * The atoms a fuzz string is assembled from: well-formed and malformed tags, unbalanced
     * brackets, dangerous URLs, dirty attribute values, and raw punctuation / whitespace.
     */
    private static final String[] ATOMS = {
            "[b]", "[/b]", "[i]", "[/i]", "[u]", "[/u]", "[s]", "[/s]",
            "[url=https://ex.com/a]", "[url=javascript:x]", "[url=]", "[/url]",
            "[img]https://ex.com/a.png[/img]", "[img]javascript:x[/img]", "[img][/img]",
            "[quote]", "[quote=\"A B\"]", "[/quote]", "[code]", "[/code]",
            "[list]", "[*]", "[/list]", "[color=#f00]", "[color=red]", "[color=;evil]", "[/color]",
            "[size=12]", "[size=abc]", "[size=-4]", "[/size]", "[font=Arial]", "[font=a;b]", "[/font]",
            "[table]", "[tr]", "[td]", "[/td]", "[/tr]", "[/table]", "[spoiler]", "[/spoiler]",
            "[hr]", "[unknown]", "[/unknown]", "plain text ", "<x>", "]", "[", "=", "\"", "\n", "  "
    };

    // ==================== Fuzz ====================

    @Test
    public void fuzzParsingNeverThrowsAndWarningsAreBounded() {
        Random random = new Random(FUZZ_SEED);
        for (int i = 0; i < 800; i++) {
            String input = randomMarkup(random);
            for (int[] flags : new int[][]{{1, 0, DEFAULT_DEPTH}, {0, 0, DEFAULT_DEPTH},
                    {1, 1, DEFAULT_DEPTH}, {1, 0, 8}, {1, 0, -1}}) {
                boolean lenient = flags[0] == 1;
                boolean showMalformed = flags[1] == 1;
                int depth = flags[2];
                RXBBCodeParseResult result = assertDoesNotThrow(() ->
                        RXBBCodeParser.parse(input, RXBBCodePolicy.defaults(), lenient, showMalformed, depth),
                        () -> "parse threw on fuzz input: " + describe(input));
                assertNotNull(result);
                assertNotNull(result.document());
                assertNotNull(result.warnings());
                // Each warning marks a distinct structural issue, of which there can be at
                // most one per input character — a finite, input-bounded count.
                assertTrue(result.warnings().size() <= input.length() + 1,
                        () -> "unbounded warnings (" + result.warnings().size() + ") for " + describe(input));
            }
            assertDoesNotThrow(() ->
                    RXBBCodeParser.parse(input, PERMISSIVE, true, false, DEFAULT_DEPTH),
                    () -> "parse threw under the permissive policy: " + describe(input));
        }
    }

    @Test
    public void fuzzParsingIsDeterministic() {
        Random random = new Random(FUZZ_SEED);
        for (int i = 0; i < 400; i++) {
            String input = randomMarkup(random);
            RXBBCodeParseResult first = RXBBCodeParser.parse(input, RXBBCodePolicy.defaults(), true, false, DEFAULT_DEPTH);
            RXBBCodeParseResult second = RXBBCodeParser.parse(input, RXBBCodePolicy.defaults(), true, false, DEFAULT_DEPTH);
            assertEquals(first.document(), second.document(), () -> "non-deterministic AST for " + describe(input));
            assertEquals(first.warnings(), second.warnings(), () -> "non-deterministic warnings for " + describe(input));
        }
    }

    // ==================== Deep nesting ====================

    @Test
    public void deepInlineNestingIsDepthBounded() {
        String input = "[b]".repeat(10_000) + "x" + "[/b]".repeat(10_000);
        RXBBCodeParseResult result = assertDoesNotThrow(() ->
                RXBBCodeParser.parse(input, RXBBCodePolicy.defaults(), true, false, DEFAULT_DEPTH));
        int depth = astDepth(result.document());
        assertTrue(depth <= DEFAULT_DEPTH + 8,
                "the depth guard must cap the style chain near maxNestingDepth, was " + depth);
        // Lower bound keeps the test honest: the guard must cap AT ~maxNestingDepth, not
        // collapse the whole chain to a shallow AST (which would pass the upper bound too).
        assertTrue(depth > 400, "nesting should survive up to the guard, was only " + depth);
    }

    @Test
    public void deepBlockNestingIsDepthBounded() {
        String input = "[quote]".repeat(10_000) + "x" + "[/quote]".repeat(10_000);
        RXBBCodeParseResult result = assertDoesNotThrow(() ->
                RXBBCodeParser.parse(input, RXBBCodePolicy.defaults(), true, false, DEFAULT_DEPTH));
        int depth = astDepth(result.document());
        assertTrue(depth <= DEFAULT_DEPTH + 8,
                "the depth guard must cap block recursion near maxNestingDepth, was " + depth);
        assertTrue(depth > 400, "block nesting should survive up to the guard, was only " + depth);
    }

    // ==================== Large sample ====================

    @Test
    public void largeSampleNodeCountScalesLinearly() {
        String unit = "[b]bold[/b] [i]it[/i] plain [url=https://ex.com/a]link[/url]\n"
                + "[quote]q[/quote]\n[list][*]a[*]b[/list]\n[code]x=1[/code]\n";
        int nodesN = totalNodes(unit.repeat(1_000));
        int nodes2N = totalNodes(unit.repeat(2_000));
        assertTrue(nodesN > 0, "sample must produce nodes");
        // Total AST node count (nested nodes included, so a super-linear NESTED blowup would
        // be caught too) scales linearly: doubling the input roughly doubles the node count.
        // This is output-size scaling, not a wall-clock complexity claim.
        double ratio = (double) nodes2N / nodesN;
        assertTrue(ratio > 1.8 && ratio < 2.2,
                "total node count must scale linearly with input size, ratio was " + ratio);
    }

    private static int totalNodes(String input) {
        RXBBCodeParseResult result = assertDoesNotThrow(() ->
                RXBBCodeParser.parse(input, RXBBCodePolicy.defaults(), true, false, DEFAULT_DEPTH));
        return countNodes(result.document());
    }

    // ==================== Helpers ====================

    private static String randomMarkup(Random random) {
        int atoms = 5 + random.nextInt(40);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < atoms; i++) {
            builder.append(ATOMS[random.nextInt(ATOMS.length)]);
        }
        return builder.toString();
    }

    private static String describe(String input) {
        String escaped = input.replace("\n", "\\n");
        return escaped.length() <= 120 ? escaped : escaped.substring(0, 120) + "…";
    }

    private static int astDepth(RXBBDocument document) {
        return maxBlockDepth(document.children());
    }

    private static int maxBlockDepth(List<RXBBBlockNode> blocks) {
        int max = 0;
        for (RXBBBlockNode block : blocks) {
            max = Math.max(max, blockDepth(block));
        }
        return max;
    }

    private static int blockDepth(RXBBBlockNode node) {
        if (node instanceof RXQuoteNode quote) {
            return 1 + maxBlockDepth(quote.children());
        }
        if (node instanceof RXSpoilerNode spoiler) {
            return 1 + maxBlockDepth(spoiler.children());
        }
        if (node instanceof RXListNode list) {
            int max = 0;
            for (RXListItemNode item : list.items()) {
                max = Math.max(max, maxBlockDepth(item.children()));
            }
            return 1 + max;
        }
        if (node instanceof RXTableNode table) {
            int max = 0;
            for (RXTableRowNode row : table.rows()) {
                for (RXTableCellNode cell : row.cells()) {
                    max = Math.max(max, maxBlockDepth(cell.children()));
                }
            }
            return 1 + max;
        }
        if (node instanceof RXParagraphNode paragraph) {
            return 1 + maxInlineDepth(paragraph.children());
        }
        if (node instanceof RXHeadingNode heading) {
            return 1 + maxInlineDepth(heading.children());
        }
        return 1;
    }

    private static int maxInlineDepth(List<RXBBInlineNode> nodes) {
        int max = 0;
        for (RXBBInlineNode node : nodes) {
            max = Math.max(max, inlineDepth(node));
        }
        return max;
    }

    private static int inlineDepth(RXBBInlineNode node) {
        if (node instanceof RXStyleNode style) {
            return 1 + maxInlineDepth(style.children());
        }
        if (node instanceof RXLinkNode link) {
            return 1 + maxInlineDepth(link.children());
        }
        return 1;
    }

    private static int countNodes(RXBBDocument document) {
        int total = 0;
        for (RXBBBlockNode block : document.children()) {
            total += blockNodeCount(block);
        }
        return total;
    }

    private static int blockNodeCount(RXBBBlockNode node) {
        int count = 1;
        if (node instanceof RXQuoteNode quote) {
            count += blockNodeCount(quote.children());
        } else if (node instanceof RXSpoilerNode spoiler) {
            count += blockNodeCount(spoiler.children());
        } else if (node instanceof RXListNode list) {
            for (RXListItemNode item : list.items()) {
                count += blockNodeCount(item.children());
            }
        } else if (node instanceof RXTableNode table) {
            for (RXTableRowNode row : table.rows()) {
                for (RXTableCellNode cell : row.cells()) {
                    count += blockNodeCount(cell.children());
                }
            }
        } else if (node instanceof RXParagraphNode paragraph) {
            count += inlineNodeCount(paragraph.children());
        } else if (node instanceof RXHeadingNode heading) {
            count += inlineNodeCount(heading.children());
        }
        return count;
    }

    private static int blockNodeCount(List<RXBBBlockNode> blocks) {
        int count = 0;
        for (RXBBBlockNode block : blocks) {
            count += blockNodeCount(block);
        }
        return count;
    }

    private static int inlineNodeCount(List<RXBBInlineNode> nodes) {
        int count = 0;
        for (RXBBInlineNode node : nodes) {
            count += inlineNodeCount(node);
        }
        return count;
    }

    private static int inlineNodeCount(RXBBInlineNode node) {
        int count = 1;
        if (node instanceof RXStyleNode style) {
            count += inlineNodeCount(style.children());
        } else if (node instanceof RXLinkNode link) {
            count += inlineNodeCount(link.children());
        }
        return count;
    }
}
