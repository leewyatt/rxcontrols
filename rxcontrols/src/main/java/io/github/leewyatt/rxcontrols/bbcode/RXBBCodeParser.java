package io.github.leewyatt.rxcontrols.bbcode;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static, lenient, tag-soup BBCode parser. Consumes the {@link BBCodeLexer} token
 * stream and builds an immutable {@link RXBBDocument}. It never throws (aside from
 * the {@link NullPointerException} contract on null arguments): every structural
 * problem is downgraded to an {@link RXBBCodeParseWarning}.
 *
 * <p>Security-sensitive values (link href, colour, size, font, image src) are
 * routed through private resolver seams so parse-time validation lives in one
 * place, and every scope push goes through {@link #openScope} so a nesting-depth
 * guard can bound recursion into the renderer.
 */
public final class RXBBCodeParser {

    // ==================== Constants ====================

    private static final int DOCUMENT_POSITION = -1;
    private static final int DEFAULT_HEADING_LEVEL = 2;
    private static final int MAX_FONT_LENGTH = 100;
    private static final String MAILTO_PREFIX = "mailto:";

    private static final Set<String> BLOCKED_SCHEMES =
            Set.of("javascript", "vbscript", "data", "file", "jar", "ftp");
    private static final Pattern HEX_COLOR =
            Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern NAMED_COLOR = Pattern.compile("[a-zA-Z]+");

    private final RXBBCodePolicy policy;
    private final boolean lenient;
    private final boolean showMalformedTagsAsText;
    private final int maxNestingDepth;

    private final List<Frame> stack = new ArrayList<>();
    private final List<RXBBCodeParseWarning> warnings = new ArrayList<>();

    private List<BBToken> tokens;
    private int position;
    private int currentOffset;

    private boolean pendingSoftBreak;
    private boolean paragraphNonEmpty;

    private RXBBCodeParser(RXBBCodePolicy policy, boolean lenient,
                           boolean showMalformedTagsAsText, int maxNestingDepth) {
        this.policy = policy;
        this.lenient = lenient;
        this.showMalformedTagsAsText = showMalformedTagsAsText;
        this.maxNestingDepth = maxNestingDepth;
    }

    /**
     * Parses BBCode content into an immutable document and parse warnings.
     *
     * <p>Never throws for malformed content: structural problems become warnings
     * and the document is always non-null. Blank input returns
     * {@link RXBBDocument#empty()} with no warnings.
     *
     * @param content                 the raw BBCode content; never {@code null}
     * @param policy                  the security policy; never {@code null}
     * @param lenient                 whether structural repair is applied
     * @param showMalformedTagsAsText whether unrendered tag tokens are echoed as literal text
     * @param maxNestingDepth         the maximum scope nesting depth; {@code < 0} disables the guard
     * @return the parse result; its document is never {@code null}
     * @throws NullPointerException if {@code content} or {@code policy} is {@code null}
     */
    public static RXBBCodeParseResult parse(String content, RXBBCodePolicy policy, boolean lenient,
                                            boolean showMalformedTagsAsText, int maxNestingDepth) {
        if (content == null) {
            throw new NullPointerException("content");
        }
        if (policy == null) {
            throw new NullPointerException("policy");
        }
        return new RXBBCodeParser(policy, lenient, showMalformedTagsAsText, maxNestingDepth)
                .run(content);
    }

    private RXBBCodeParseResult run(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isBlank()) {
            return new RXBBCodeParseResult(RXBBDocument.empty(), List.of());
        }
        tokens = BBCodeLexer.tokenize(normalized);
        stack.add(Frame.root());

        while (position < tokens.size()) {
            BBToken token = tokens.get(position);
            position++;
            currentOffset += rawTextOf(token).length();
            dispatch(token);
        }
        closeAllAtEof();

        Frame root = stack.get(0);
        RXBBDocument document = new RXBBDocument(root.blocks);
        return new RXBBCodeParseResult(document, warnings);
    }

    // ==================== Dispatch ====================

    private void dispatch(BBToken token) {
        if (token instanceof TextToken text) {
            handleText(text.text());
        } else if (token instanceof TagOpenToken open) {
            handleOpen(open);
        } else if (token instanceof TagCloseToken close) {
            handleClose(close);
        } else if (token instanceof ListItemToken) {
            handleListItemMarker();
        }
    }

    private void handleText(String content) {
        int index = 0;
        int length = content.length();
        while (index < length) {
            int newline = content.indexOf('\n', index);
            int segmentEnd = (newline < 0) ? length : newline;
            if (segmentEnd > index) {
                addTextInline(content.substring(index, segmentEnd));
            }
            if (newline < 0) {
                break;
            }
            int run = newline;
            while (run < length && content.charAt(run) == '\n') {
                run++;
            }
            if (run - newline >= 2) {
                paragraphBreak();
            } else {
                pendingSoftBreak = true;
            }
            index = run;
        }
    }

    private void handleOpen(TagOpenToken open) {
        String name = open.name();
        String value = open.positionalValue();
        String raw = open.rawSource();
        switch (name) {
            case "b" -> openStyle(name, RXStyleType.BOLD, raw);
            case "i" -> openStyle(name, RXStyleType.ITALIC, raw);
            case "u" -> openStyle(name, RXStyleType.UNDERLINE, raw);
            case "s" -> openStyle(name, RXStyleType.STRIKETHROUGH, raw);
            case "color" -> openValuedStyle(name, RXStyleType.COLOR, value, raw);
            case "size" -> openValuedStyle(name, RXStyleType.SIZE, value, raw);
            case "font" -> openValuedStyle(name, RXStyleType.FONT, value, raw);
            case "url" -> openLink(name, RXLinkKind.URL, value, raw);
            case "email" -> openLink(name, RXLinkKind.EMAIL, value, raw);
            case "br" -> handleBreak();
            case "img" -> handleImage(open);
            case "h1" -> openHeading(1, raw);
            case "h2" -> openHeading(2, raw);
            case "h3" -> openHeading(3, raw);
            case "h4" -> openHeading(4, raw);
            case "h5" -> openHeading(5, raw);
            case "h6" -> openHeading(6, raw);
            case "heading" -> openHeading(value != null ? parseLevel(value) : DEFAULT_HEADING_LEVEL, raw);
            case "quote" -> openBlock(Frame.quote(value), raw);
            case "spoiler" -> openBlock(Frame.spoiler(value), raw);
            case "bgcolor" -> openBlock(Frame.background(value != null ? resolveColor(value) : null), raw);
            case "code" -> handleCode(open);
            case "list" -> openBlock(Frame.list(value != null ? RXListKind.ORDERED : RXListKind.UNORDERED), raw);
            case "ul" -> openBlock(Frame.list(RXListKind.UNORDERED), raw);
            case "ol" -> openBlock(Frame.list(RXListKind.ORDERED), raw);
            case "li" -> handleListItemMarker();
            case "table" -> openBlock(Frame.table(), raw);
            case "tr" -> openRow(raw);
            case "th" -> openCell(true, raw);
            case "td" -> openCell(false, raw);
            case "hr" -> emitBlock(new RXHorizontalRuleNode());
            default -> handleUnknownOpen(open);
        }
    }

    private void handleClose(TagCloseToken close) {
        String name = close.name();
        if (name.equals("li") || name.equals("br") || name.equals("hr")) {
            // Self-closing / marker tags have no scope; a stray close is dropped.
            return;
        }
        String key = canonicalCloseKey(name);
        int index = findOpenFrame(key);
        if (index >= 0) {
            Frame target = stack.get(index);
            boolean echo = target.kind == FrameKind.UNWRAP && target.echoLiteral;
            pierceCloseTo(index);
            if (echo && showMalformedTagsAsText) {
                addInlineNode(new RXRawTextNode(close.rawSource()));
            }
            return;
        }
        if (isKnownTag(name)) {
            warnHere(RXBBWarningCode.MISMATCHED_CLOSE, close.rawSource(), "Unmatched closing tag.");
        } else {
            warnHere(RXBBWarningCode.UNKNOWN_TAG, close.rawSource(), "Unknown closing tag.");
        }
        if (showMalformedTagsAsText) {
            addInlineNode(new RXRawTextNode(close.rawSource()));
        }
    }

    // ==================== Inline styles / links ====================

    private void openStyle(String name, RXStyleType type, String raw) {
        openScope(Frame.style(name, type, null), raw);
    }

    private void openValuedStyle(String name, RXStyleType type, String value, String raw) {
        if (value == null) {
            openScope(Frame.unwrap(name, false), raw);
            return;
        }
        String resolved = resolveStyleValue(type, value);
        if (resolved == null) {
            openScope(Frame.unwrap(name, false), raw);
            return;
        }
        openScope(Frame.style(name, type, resolved), raw);
    }

    private void openLink(String name, RXLinkKind kind, String value, String raw) {
        if (value != null) {
            String href = resolveHref(value, kind);
            if (href == null) {
                openScope(Frame.unwrap(name, false), raw);
                return;
            }
            openScope(Frame.link(name, href, kind), raw);
            return;
        }
        String body = captureRawUntilClose(name);
        if (body.isBlank()) {
            return;
        }
        String href = resolveHref(body, kind);
        if (href == null) {
            addTextInline(body);
            return;
        }
        materializePendingBreak();
        addInlineNode(new RXLinkNode(href, kind, List.of(new RXTextNode(body))));
    }

    private void handleBreak() {
        pendingSoftBreak = false;
        addInlineNode(new RXLineBreakNode());
    }

    private void handleImage(TagOpenToken open) {
        String body = captureRawUntilClose(open.name());
        String src = resolveImageSrc(body);
        if (src == null) {
            return;
        }
        String alt = open.attributes().get("alt");
        double width = parseDimension(open.attributes().get("width"));
        double height = parseDimension(open.attributes().get("height"));
        materializePendingBreak();
        addInlineNode(new RXImageNode(src, alt, width, height));
    }

    // ==================== Blocks ====================

    private void openHeading(int level, String raw) {
        openBlock(Frame.heading(level), raw);
    }

    private void handleCode(TagOpenToken open) {
        String content = captureRawUntilClose(open.name());
        emitBlock(new RXCodeBlockNode(content, open.positionalValue()));
    }

    private void openRow(String raw) {
        collapseInlineFrames();
        int tableIndex = findFrameKind(FrameKind.TABLE);
        if (tableIndex < 0) {
            handleUnknownOpenName("tr", raw);
            return;
        }
        // Close any still-open sibling row (and its open cell) so [tr] starts a new row.
        closeAbove(tableIndex);
        openScope(Frame.row(), raw);
    }

    private void openCell(boolean header, String raw) {
        collapseInlineFrames();
        int rowIndex = findFrameKind(FrameKind.ROW);
        if (rowIndex >= 0) {
            // Inside a row: close any still-open sibling cell, then start a new cell.
            closeAbove(rowIndex);
            openScope(Frame.cell(header), raw);
            return;
        }
        int tableIndex = findFrameKind(FrameKind.TABLE);
        if (tableIndex >= 0) {
            closeAbove(tableIndex);
            warnHere(RXBBWarningCode.IMPLICIT_TABLE_ROW, raw, "Table cell outside a row.");
            rawPush(Frame.row());
            openScope(Frame.cell(header), raw);
            return;
        }
        handleUnknownOpenName(header ? "th" : "td", raw);
    }

    private void closeAbove(int index) {
        while (stack.size() - 1 > index) {
            closeTopFrame(true);
        }
    }

    private void openBlock(Frame frame, String raw) {
        collapseInlineFrames();
        ensureBlockHost();
        flushParagraph();
        openScope(frame, raw);
    }

    private void emitBlock(RXBBBlockNode block) {
        collapseInlineFrames();
        ensureBlockHost();
        flushParagraph();
        currentContainer().blocks.add(block);
    }

    // ==================== List items ====================

    private void handleListItemMarker() {
        int listIndex = findFrameKind(FrameKind.LIST);
        if (listIndex < 0) {
            return;
        }
        while (stack.size() - 1 > listIndex) {
            closeTopFrame(false);
        }
        resetParagraphState();
        rawPush(Frame.listItem());
    }

    // ==================== Unknown tags ====================

    private void handleUnknownOpen(TagOpenToken open) {
        handleUnknownOpenName(open.name(), open.rawSource());
    }

    private void handleUnknownOpenName(String name, String raw) {
        warnHere(RXBBWarningCode.UNKNOWN_TAG, raw, "Unknown tag.");
        if (showMalformedTagsAsText) {
            addInlineNode(new RXRawTextNode(raw));
            openScope(Frame.unwrap(name, true), raw);
        } else {
            openScope(Frame.unwrap(name, false), raw);
        }
    }

    // ==================== Parse-time value validation ====================

    // Each resolver validates one security-sensitive value, emits its own
    // downgrade warning, and returns null on rejection so no node/attribute is
    // built from an unsafe value. Values are validated as strings only; no
    // Node / Image / setStyle is ever produced here.

    private String resolveHref(String raw, RXLinkKind kind) {
        return kind == RXLinkKind.EMAIL ? resolveEmail(raw.trim()) : resolveUrl(raw.trim());
    }

    private String resolveUrl(String value) {
        if (value.isEmpty() || containsWhitespaceOrControl(value)) {
            warnHere(RXBBWarningCode.INVALID_URL, value, "Malformed link URL.");
            return null;
        }
        String scheme = schemeOf(value);
        if (scheme == null) {
            warnHere(RXBBWarningCode.INVALID_URL, value, "Link URL has no scheme.");
            return null;
        }
        if (BLOCKED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                || !policy.urlPolicy().isSchemeAllowed(scheme)) {
            warnHere(RXBBWarningCode.BLOCKED_URL, value, "Link URL scheme is not allowed.");
            return null;
        }
        return value;
    }

    private String resolveEmail(String value) {
        String address = value.toLowerCase(Locale.ROOT).startsWith(MAILTO_PREFIX)
                ? value.substring(MAILTO_PREFIX.length()) : value;
        if (address.isEmpty() || containsWhitespaceOrControl(address) || !address.contains("@")) {
            warnHere(RXBBWarningCode.INVALID_URL, value, "Malformed email address.");
            return null;
        }
        if (!policy.urlPolicy().isSchemeAllowed("mailto")) {
            warnHere(RXBBWarningCode.BLOCKED_URL, value, "Email links are not allowed.");
            return null;
        }
        return MAILTO_PREFIX + address;
    }

    private String resolveImageSrc(String raw) {
        String value = raw.trim();
        if (value.isEmpty() || containsWhitespaceOrControl(value)) {
            warnHere(RXBBWarningCode.INVALID_IMAGE_URL, value, "Malformed image URL.");
            return null;
        }
        String scheme = schemeOf(value);
        if (scheme == null || BLOCKED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                || !policy.imagePolicy().isSchemeAllowed(scheme)) {
            warnHere(RXBBWarningCode.INVALID_IMAGE_URL, value, "Image URL scheme is not allowed.");
            return null;
        }
        return value;
    }

    private String resolveStyleValue(RXStyleType type, String raw) {
        return switch (type) {
            case COLOR -> resolveColor(raw);
            case SIZE -> resolveSize(raw);
            case FONT -> resolveFont(raw);
            default -> raw;
        };
    }

    private String resolveColor(String raw) {
        String value = raw.trim();
        if (isCleanColorToken(value) && isValidColor(value)) {
            return value;
        }
        warnHere(RXBBWarningCode.INVALID_COLOR, raw, "Invalid colour value.");
        return null;
    }

    private String resolveSize(String raw) {
        String value = raw.trim();
        String keyword = value.toLowerCase(Locale.ROOT);
        switch (keyword) {
            case "small" -> {
                return "10";
            }
            case "medium" -> {
                return "14";
            }
            case "large" -> {
                return "18";
            }
            default -> {
                // fall through to numeric parsing
            }
        }
        try {
            double pixels = Double.parseDouble(value);
            if (Double.isFinite(pixels) && pixels > 0) {
                // Positive finite sizes are rendered verbatim (no clamp); resource
                // cost of a huge size is the caller's concern, like a huge image.
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        warnHere(RXBBWarningCode.INVALID_SIZE, raw, "Invalid size value.");
        return null;
    }

    private String resolveFont(String raw) {
        String value = raw.trim();
        if (isValidFontFamily(value)) {
            return value;
        }
        warnHere(RXBBWarningCode.INVALID_FONT, raw, "Invalid font family.");
        return null;
    }

    private static boolean isCleanColorToken(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == ';' || c == '(' || c == ')') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidColor(String value) {
        if (HEX_COLOR.matcher(value).matches()) {
            return true;
        }
        if (NAMED_COLOR.matcher(value).matches()) {
            // Color.web is a pure value factory (no toolkit); it owns the named-colour set.
            try {
                Color.web(value);
                return true;
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        return false;
    }

    private static boolean isValidFontFamily(String value) {
        if (value.isEmpty() || value.length() > MAX_FONT_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f || c == ';' || c == '{' || c == '}'
                    || c == '(' || c == ')' || c == '<' || c == '>' || c == '"') {
                return false;
            }
        }
        return true;
    }

    private static boolean containsWhitespaceOrControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c < 0x20 || c == 0x7f) {
                return true;
            }
        }
        return false;
    }

    private static String schemeOf(String url) {
        int colon = url.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String scheme = url.substring(0, colon);
        for (int i = 0; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '+' && c != '-' && c != '.') {
                return null;
            }
        }
        return scheme;
    }

    // ==================== Scope push / pierce / close ====================

    private void openScope(Frame frame, String raw) {
        if (maxNestingDepth >= 0 && stack.size() >= maxNestingDepth) {
            // Too deep: do not push the real scope (which would let the renderer
            // recurse into a StackOverflowError). Degrade to an UNWRAP with the
            // same close key so the matching close tag still balances, flattening
            // children (and echoing the literal when showMalformed).
            warnHere(RXBBWarningCode.MAX_DEPTH_EXCEEDED, raw, "Maximum nesting depth exceeded.");
            if (showMalformedTagsAsText) {
                addInlineNode(new RXRawTextNode(raw));
            }
            Frame degraded = Frame.unwrap(frame.closeKey, showMalformedTagsAsText);
            degraded.openRaw = raw;
            degraded.openOffset = currentTokenOffset();
            rawPush(degraded);
            return;
        }
        frame.openRaw = raw;
        frame.openOffset = currentTokenOffset();
        rawPush(frame);
    }

    private void rawPush(Frame frame) {
        stack.add(frame);
    }

    private int findOpenFrame(String key) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (key.equals(stack.get(i).closeKey)) {
                return i;
            }
        }
        return -1;
    }

    private int findFrameKind(FrameKind kind) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).kind == kind) {
                return i;
            }
        }
        return -1;
    }

    private void pierceCloseTo(int index) {
        while (stack.size() - 1 > index) {
            Frame above = stack.get(stack.size() - 1);
            if (isUserOpened(above)) {
                warn(RXBBWarningCode.UNCLOSED_TAG, above.openOffset, above.openRaw, "Implicitly closed tag.");
            }
            closeTopFrame(false);
        }
        closeTopFrame(true);
    }

    private void collapseInlineFrames() {
        while (isInline(stack.get(stack.size() - 1).kind)) {
            closeTopFrame(false);
        }
    }

    private void closeAllAtEof() {
        while (stack.size() > 1) {
            Frame top = stack.get(stack.size() - 1);
            if (isUserOpened(top)) {
                warn(RXBBWarningCode.UNCLOSED_TAG, top.openOffset, top.openRaw, "Unclosed tag at end of input.");
            }
            closeTopFrame(false);
        }
        flushParagraph();
    }

    private void closeTopFrame(boolean explicit) {
        Frame frame = stack.get(stack.size() - 1);
        if (isParagraphContainer(frame.kind)) {
            flushOwnParagraph(frame);
        }
        stack.remove(stack.size() - 1);
        // In strict mode (lenient == false) an inline tag closed implicitly (at
        // EOF, by a pierce, or at a block boundary) is treated as non-markup: its
        // children are unwrapped instead of wrapped in a style / link node.
        boolean strictUnwrap = !lenient && !explicit;
        switch (frame.kind) {
            case STYLE -> {
                if (strictUnwrap) {
                    unwrapInline(frame);
                } else if (!frame.inlineChildren.isEmpty()) {
                    addInlineNode(new RXStyleNode(frame.styleType, frame.styleValue, frame.inlineChildren));
                }
            }
            case LINK -> {
                if (strictUnwrap) {
                    unwrapInline(frame);
                } else if (!frame.inlineChildren.isEmpty()) {
                    addInlineNode(new RXLinkNode(frame.href, frame.linkKind, frame.inlineChildren));
                }
            }
            case UNWRAP -> {
                for (RXBBInlineNode child : frame.inlineChildren) {
                    addInlineNode(child);
                }
            }
            case HEADING -> {
                if (!frame.inlineChildren.isEmpty()) {
                    addBlockToParent(new RXHeadingNode(frame.headingLevel, frame.inlineChildren));
                }
            }
            case QUOTE -> addBlockToParent(new RXQuoteNode(frame.author, frame.blocks));
            case SPOILER -> addBlockToParent(new RXSpoilerNode(frame.label, frame.blocks));
            case BACKGROUND -> addBlockToParent(new RXBackgroundNode(frame.bgColor, frame.blocks));
            case LIST -> {
                if (!frame.items.isEmpty()) {
                    addBlockToParent(new RXListNode(frame.listKind, frame.items));
                }
            }
            case LIST_ITEM -> addItemToParent(new RXListItemNode(frame.blocks));
            case TABLE -> {
                if (!frame.rows.isEmpty()) {
                    addBlockToParent(new RXTableNode(frame.rows));
                }
            }
            case ROW -> {
                if (!frame.cells.isEmpty()) {
                    addRowToParent(new RXTableRowNode(frame.cells));
                }
            }
            case CELL -> addCellToParent(new RXTableCellNode(frame.header, frame.blocks));
            case ROOT -> {
                // never closed
            }
        }
    }

    private void unwrapInline(Frame frame) {
        if (showMalformedTagsAsText && !frame.openRaw.isEmpty()) {
            addInlineNode(new RXRawTextNode(frame.openRaw));
        }
        for (RXBBInlineNode child : frame.inlineChildren) {
            addInlineNode(child);
        }
    }

    // ==================== Content routing ====================

    private void addTextInline(String text) {
        materializePendingBreak();
        addInlineNode(new RXTextNode(text));
    }

    private void addInlineNode(RXBBInlineNode node) {
        Frame top = stack.get(stack.size() - 1);
        List<RXBBInlineNode> target;
        if (isInline(top.kind)) {
            target = top.inlineChildren;
        } else {
            target = inlineHostList();
        }
        if (target == null) {
            return;
        }
        target.add(node);
        paragraphNonEmpty = true;
    }

    private List<RXBBInlineNode> inlineHostList() {
        Frame container = currentContainer();
        if (container.kind == FrameKind.HEADING) {
            return container.inlineChildren;
        }
        if (!isParagraphContainer(container.kind)) {
            // LIST / TABLE / ROW cannot hold inline content directly; synthesize
            // the implicit list item / table row + cell so loose text is never lost.
            ensureBlockHost();
        }
        Frame host = currentContainer();
        return isParagraphContainer(host.kind) ? host.paragraph : null;
    }

    private void materializePendingBreak() {
        if (pendingSoftBreak) {
            if (paragraphNonEmpty) {
                addInlineNode(new RXLineBreakNode());
            }
            pendingSoftBreak = false;
        }
    }

    private void paragraphBreak() {
        collapseInlineFrames();
        flushParagraph();
    }

    private void flushParagraph() {
        Frame container = currentContainer();
        if (isParagraphContainer(container.kind)) {
            flushOwnParagraph(container);
        }
        resetParagraphState();
    }

    private void flushOwnParagraph(Frame container) {
        trimEdgeBreaks(container.paragraph);
        if (!container.paragraph.isEmpty()) {
            container.blocks.add(new RXParagraphNode(container.paragraph));
            container.paragraph = new ArrayList<>();
        }
    }

    private void resetParagraphState() {
        pendingSoftBreak = false;
        paragraphNonEmpty = false;
    }

    private void trimEdgeBreaks(List<RXBBInlineNode> nodes) {
        while (!nodes.isEmpty() && nodes.get(nodes.size() - 1) instanceof RXLineBreakNode) {
            nodes.remove(nodes.size() - 1);
        }
        while (!nodes.isEmpty() && nodes.get(0) instanceof RXLineBreakNode) {
            nodes.remove(0);
        }
    }

    // ==================== Structural hosts ====================

    private void ensureBlockHost() {
        while (true) {
            Frame container = currentContainer();
            switch (container.kind) {
                case LIST -> synthListItem();
                case TABLE -> {
                    warn(RXBBWarningCode.IMPLICIT_TABLE_ROW, currentTokenOffset(), "", "Table content outside a row.");
                    rawPush(Frame.row());
                }
                case ROW -> rawPush(Frame.cell(false));
                case HEADING -> closeTopFrame(true);
                default -> {
                    return;
                }
            }
        }
    }

    private Frame synthListItem() {
        warn(RXBBWarningCode.IMPLICIT_LIST_ITEM, currentTokenOffset(), "", "List content outside an item.");
        Frame item = Frame.listItem();
        rawPush(item);
        resetParagraphState();
        return item;
    }

    private void addBlockToParent(RXBBBlockNode block) {
        Frame parent = currentContainer();
        if (parent.blocks != null) {
            parent.blocks.add(block);
        }
    }

    private void addItemToParent(RXListItemNode item) {
        Frame parent = currentContainer();
        if (parent.kind == FrameKind.LIST) {
            parent.items.add(item);
        }
    }

    private void addRowToParent(RXTableRowNode row) {
        Frame parent = currentContainer();
        if (parent.kind == FrameKind.TABLE) {
            parent.rows.add(row);
        }
    }

    private void addCellToParent(RXTableCellNode cell) {
        Frame parent = currentContainer();
        if (parent.kind == FrameKind.ROW) {
            parent.cells.add(cell);
        }
    }

    private Frame currentContainer() {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (!isInline(stack.get(i).kind)) {
                return stack.get(i);
            }
        }
        return stack.get(0);
    }

    // ==================== Raw capture ====================

    private String captureRawUntilClose(String tagName) {
        String key = canonicalCloseKey(tagName);
        StringBuilder raw = new StringBuilder();
        while (position < tokens.size()) {
            BBToken token = tokens.get(position);
            if (token instanceof TagCloseToken close && canonicalCloseKey(close.name()).equals(key)) {
                position++;
                currentOffset += close.rawSource().length();
                return raw.toString();
            }
            raw.append(rawTextOf(token));
            position++;
            currentOffset += rawTextOf(token).length();
        }
        warnHere(RXBBWarningCode.UNCLOSED_TAG, "[" + tagName + "]", "Unclosed tag at end of input.");
        return raw.toString();
    }

    // ==================== Warnings ====================

    private void warnHere(RXBBWarningCode code, String fragment, String message) {
        warn(code, currentTokenOffset(), fragment, message);
    }

    private void warn(RXBBWarningCode code, int position, String fragment, String message) {
        warnings.add(new RXBBCodeParseWarning(position, fragment == null ? "" : fragment, code, message));
    }

    private int currentTokenOffset() {
        return position <= 0 ? DOCUMENT_POSITION : currentOffset - rawLengthOf(tokens.get(position - 1));
    }

    // ==================== Helpers ====================

    private static boolean isInline(FrameKind kind) {
        return kind == FrameKind.STYLE || kind == FrameKind.LINK || kind == FrameKind.UNWRAP;
    }

    private static boolean isUserOpened(Frame frame) {
        // Synthesized frames (implicit rows / cells / list items) carry an empty
        // openRaw and are never reported as unclosed; UNWRAP frames already warned
        // as UNKNOWN_TAG at open time.
        return frame.kind != FrameKind.UNWRAP && !frame.openRaw.isEmpty();
    }

    private static boolean isParagraphContainer(FrameKind kind) {
        return kind == FrameKind.ROOT || kind == FrameKind.QUOTE || kind == FrameKind.SPOILER
                || kind == FrameKind.BACKGROUND || kind == FrameKind.LIST_ITEM || kind == FrameKind.CELL;
    }

    private static String canonicalCloseKey(String name) {
        return switch (name) {
            case "h1", "h2", "h3", "h4", "h5", "h6", "heading" -> "heading";
            case "list", "ul", "ol" -> "list";
            case "th", "td" -> "cell";
            default -> name;
        };
    }

    private static boolean isKnownTag(String name) {
        return switch (name) {
            case "b", "i", "u", "s", "color", "size", "font", "url", "email", "br", "img",
                 "h1", "h2", "h3", "h4", "h5", "h6", "heading", "quote", "code", "list", "ul",
                 "ol", "li", "table", "tr", "th", "td", "spoiler", "bgcolor", "hr" -> true;
            default -> false;
        };
    }

    private static int parseLevel(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_HEADING_LEVEL;
        }
    }

    private static double parseDimension(String value) {
        if (value == null) {
            return 0;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String rawTextOf(BBToken token) {
        if (token instanceof TextToken text) {
            return text.text();
        }
        if (token instanceof TagOpenToken open) {
            return open.rawSource();
        }
        if (token instanceof TagCloseToken close) {
            return close.rawSource();
        }
        return "[*]";
    }

    private static int rawLengthOf(BBToken token) {
        return rawTextOf(token).length();
    }

    // ==================== Frame ====================

    private enum FrameKind {
        ROOT, STYLE, LINK, UNWRAP, HEADING, QUOTE, SPOILER, BACKGROUND, LIST, LIST_ITEM, TABLE, ROW, CELL
    }

    private static final class Frame {

        private final FrameKind kind;
        private final String closeKey;

        private List<RXBBBlockNode> blocks;
        private List<RXBBInlineNode> paragraph;
        private List<RXBBInlineNode> inlineChildren;
        private List<RXListItemNode> items;
        private List<RXTableRowNode> rows;
        private List<RXTableCellNode> cells;

        private RXStyleType styleType;
        private String styleValue;
        private String href;
        private RXLinkKind linkKind;
        private String author;
        private String label;
        private String bgColor;
        private RXListKind listKind;
        private int headingLevel;
        private boolean header;
        private boolean echoLiteral;

        private String openRaw = "";
        private int openOffset = DOCUMENT_POSITION;

        private Frame(FrameKind kind, String closeKey) {
            this.kind = kind;
            this.closeKey = closeKey;
        }

        static Frame root() {
            Frame frame = new Frame(FrameKind.ROOT, "");
            frame.blocks = new ArrayList<>();
            frame.paragraph = new ArrayList<>();
            return frame;
        }

        static Frame style(String name, RXStyleType type, String value) {
            Frame frame = new Frame(FrameKind.STYLE, name);
            frame.inlineChildren = new ArrayList<>();
            frame.styleType = type;
            frame.styleValue = value;
            return frame;
        }

        static Frame link(String name, String href, RXLinkKind kind) {
            Frame frame = new Frame(FrameKind.LINK, name);
            frame.inlineChildren = new ArrayList<>();
            frame.href = href;
            frame.linkKind = kind;
            return frame;
        }

        static Frame unwrap(String name, boolean echoLiteral) {
            Frame frame = new Frame(FrameKind.UNWRAP, name);
            frame.inlineChildren = new ArrayList<>();
            frame.echoLiteral = echoLiteral;
            return frame;
        }

        static Frame heading(int level) {
            Frame frame = new Frame(FrameKind.HEADING, "heading");
            frame.inlineChildren = new ArrayList<>();
            frame.headingLevel = level;
            return frame;
        }

        static Frame quote(String author) {
            Frame frame = new Frame(FrameKind.QUOTE, "quote");
            frame.blocks = new ArrayList<>();
            frame.paragraph = new ArrayList<>();
            frame.author = author;
            return frame;
        }

        static Frame spoiler(String label) {
            Frame frame = new Frame(FrameKind.SPOILER, "spoiler");
            frame.blocks = new ArrayList<>();
            frame.paragraph = new ArrayList<>();
            frame.label = label;
            return frame;
        }

        static Frame background(String color) {
            Frame frame = new Frame(FrameKind.BACKGROUND, "bgcolor");
            frame.blocks = new ArrayList<>();
            frame.paragraph = new ArrayList<>();
            frame.bgColor = color;
            return frame;
        }

        static Frame list(RXListKind kind) {
            Frame frame = new Frame(FrameKind.LIST, "list");
            frame.items = new ArrayList<>();
            frame.listKind = kind;
            return frame;
        }

        static Frame listItem() {
            Frame frame = new Frame(FrameKind.LIST_ITEM, "li");
            frame.blocks = new ArrayList<>();
            frame.paragraph = new ArrayList<>();
            return frame;
        }

        static Frame table() {
            Frame frame = new Frame(FrameKind.TABLE, "table");
            frame.rows = new ArrayList<>();
            return frame;
        }

        static Frame row() {
            Frame frame = new Frame(FrameKind.ROW, "tr");
            frame.cells = new ArrayList<>();
            return frame;
        }

        static Frame cell(boolean header) {
            Frame frame = new Frame(FrameKind.CELL, "cell");
            frame.blocks = new ArrayList<>();
            frame.paragraph = new ArrayList<>();
            frame.header = header;
            return frame;
        }
    }
}
