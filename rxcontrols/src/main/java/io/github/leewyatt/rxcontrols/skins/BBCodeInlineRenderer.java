package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBBCodeView;
import io.github.leewyatt.rxcontrols.bbcode.RXBBInlineNode;
import io.github.leewyatt.rxcontrols.bbcode.RXBBInlineNodeVisitor;
import io.github.leewyatt.rxcontrols.bbcode.RXImageNode;
import io.github.leewyatt.rxcontrols.bbcode.RXLineBreakNode;
import io.github.leewyatt.rxcontrols.bbcode.RXLinkNode;
import io.github.leewyatt.rxcontrols.bbcode.RXRawTextNode;
import io.github.leewyatt.rxcontrols.bbcode.RXStyleNode;
import io.github.leewyatt.rxcontrols.bbcode.RXStyleType;
import io.github.leewyatt.rxcontrols.bbcode.RXTextNode;
import io.github.leewyatt.rxcontrols.event.RXBBCodeLinkEvent;
import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Renders a sequence of {@link RXBBInlineNode}s into a target {@link TextFlow} as styled
 * {@link Text} runs.
 *
 * <p>Style is tracked as an immutable {@link Style} on a stack: entering a
 * {@link RXStyleNode} pushes a derived style, its children render under it, and the pop
 * restores the parent. All appearance is applied through typed JavaFX setters
 * ({@link Text#setFont}, {@link Text#setUnderline}, {@link Text#setStrikethrough},
 * {@link Text#setFill}) — never {@code setStyle}, so a validated colour / size / font from
 * untrusted markup can never reach the CSS engine.
 *
 * <p>Adjacent runs that share the exact same {@code Style} are merged into a single
 * {@link Text} node, so a long run of same-styled text (the common case) wraps as one node
 * instead of many.
 */
final class BBCodeInlineRenderer implements RXBBInlineNodeVisitor<Void> {

    private static final String TEXT_STYLE_CLASS = "text";
    private static final String LINK_STYLE_CLASS = "link";
    private static final String IMAGE_STYLE_CLASS = "image";

    private final TextFlow flow;
    private final RenderContext ctx;
    private final Deque<Style> styleStack = new ArrayDeque<>();

    private Text pending;
    private Style pendingStyle;
    private RXLinkNode activeLink;

    BBCodeInlineRenderer(TextFlow flow, RenderContext ctx) {
        this(flow, ctx, Style.base());
    }

    private BBCodeInlineRenderer(TextFlow flow, RenderContext ctx, Style seed) {
        this.flow = flow;
        this.ctx = ctx;
        styleStack.push(seed);
    }

    /**
     * Creates an inline renderer whose base style is bold at the given heading size, so a
     * heading's runs are sized and weighted from the block level while inner {@code [b]} /
     * {@code [size]} / {@code [color]} still stack on top.
     *
     * @param flow the target flow
     * @param ctx  the render context
     * @param size the heading font size in pixels
     * @return the seeded renderer
     */
    static BBCodeInlineRenderer heading(TextFlow flow, RenderContext ctx, double size) {
        return new BBCodeInlineRenderer(flow, ctx, Style.base().withWeight(FontWeight.BOLD).withSize(size));
    }

    /**
     * Renders the given inline nodes into the target flow, flushing the final run.
     *
     * @param nodes the inline nodes to render
     */
    void render(List<RXBBInlineNode> nodes) {
        for (RXBBInlineNode node : nodes) {
            node.accept(this);
        }
        flushPending();
    }

    // ==================== Visitor ====================

    @Override
    public Void visitText(RXTextNode node) {
        appendText(node.text());
        return null;
    }

    @Override
    public Void visitRawText(RXRawTextNode node) {
        appendText(node.literal());
        return null;
    }

    @Override
    public Void visitLineBreak(RXLineBreakNode node) {
        appendText("\n");
        return null;
    }

    @Override
    public Void visitStyle(RXStyleNode node) {
        styleStack.push(applyStyle(currentStyle(), node.type(), node.value()));
        for (RXBBInlineNode child : node.children()) {
            child.accept(this);
        }
        styleStack.pop();
        return null;
    }

    @Override
    public Void visitLink(RXLinkNode node) {
        RXLinkNode previous = activeLink;
        activeLink = node;
        // Flush around the link so its runs never merge into an adjacent non-link run of
        // the same style (which would make plain text clickable or vice versa).
        flushPending();
        for (RXBBInlineNode child : node.children()) {
            child.accept(this);
        }
        flushPending();
        activeLink = previous;
        return null;
    }

    @Override
    public Void visitImage(RXImageNode node) {
        // An image is a node in the flow, not text, so flush the pending run first.
        flushPending();
        flow.getChildren().add(buildImage(node));
        return null;
    }

    // ==================== Runs ====================

    private void appendText(String text) {
        if (text.isEmpty()) {
            return;
        }
        Style style = currentStyle();
        if (pending != null && style.equals(pendingStyle)) {
            pending.setText(pending.getText() + text);
        } else {
            flushPending();
            pending = createRun(text, style);
            pendingStyle = style;
        }
    }

    private void flushPending() {
        if (pending != null) {
            flow.getChildren().add(pending);
            pending = null;
            pendingStyle = null;
        }
    }

    private Text createRun(String text, Style style) {
        Text run = new Text(text);
        run.getStyleClass().add(TEXT_STYLE_CLASS);
        if (style.hasFontModifier()) {
            // A run with no font modifier keeps the CSS-resolved font; only synthesize one
            // when a bold/italic/size/family attribute is actually in scope. An explicit
            // size is capped at maxFontSize so untrusted markup cannot explode layout; an
            // unset size (-1) stays unset and keeps the default.
            double cap = ctx.maxFontSizeOrDefault();
            double size = cap > 0 && style.size() > cap ? cap : style.size();
            run.setFont(Font.font(style.family(), style.weight(), style.posture(), size));
        }
        // Only force these ON when the attribute is in scope. A direct setter pins the
        // property to StyleOrigin.USER, which the user-agent stylesheet can no longer
        // override — so an unconditional setUnderline(false) would kill the CSS
        // `.link { -fx-underline: true }` rule. Leaving them unset keeps CSS in charge.
        if (style.underline()) {
            run.setUnderline(true);
        }
        if (style.strike()) {
            run.setStrikethrough(true);
        }
        if (style.fill() != null) {
            // Null fill keeps the CSS default colour, mirroring Text.setFill's convention.
            run.setFill(style.fill());
        }
        if (activeLink != null) {
            decorateLink(run, activeLink);
        }
        return run;
    }

    // ==================== Images ====================

    private Node buildImage(RXImageNode node) {
        if (!ctx.imagePolicy().loadImages()) {
            // Policy forbids network: show the alt placeholder, load nothing.
            return imagePlaceholder(node.alt());
        }
        // Load at natural size with background loading, exactly like a bare ImageView. No
        // ceiling is imposed: display size is the caller's job via imageMaxWidth/Height
        // (default: no cap); retained-bitmap memory is only bounded by loadImages=false or
        // an upstream downscaling proxy — the bitmap here always decodes at source size.
        Image image;
        try {
            image = new Image(node.src(), true);
        } catch (RuntimeException malformedUrl) {
            // The parser validates only the scheme prefix, so a malformed authority / port
            // ("http://h:abc") still reaches here and makes the Image constructor throw.
            // Degrade to the alt placeholder rather than crashing the whole render.
            return imagePlaceholder(node.alt());
        }
        ctx.registerLiveImage(image);
        if (image.isError()) {
            return imagePlaceholder(node.alt());
        }

        InlineImageView view = new InlineImageView(image);
        view.getStyleClass().add(IMAGE_STYLE_CLASS);
        view.setPreserveRatio(true);
        view.setAccessibleText(node.alt());
        applyFit(view, node, image);
        view.setLoading(image.getProgress() < 1.0);

        // Fit is imperative (set, not bound), so these listeners die with the discarded
        // Image/ImageView on the next rebuild — no unbind / disposer handle needed.
        image.progressProperty().addListener((observable, old, progress) -> {
            applyFit(view, node, image);
            view.setLoading(progress.doubleValue() < 1.0);
        });
        image.widthProperty().addListener((observable, old, width) -> applyFit(view, node, image));
        image.heightProperty().addListener((observable, old, height) -> applyFit(view, node, image));
        image.errorProperty().addListener((observable, old, isError) -> {
            if (Boolean.TRUE.equals(isError)) {
                replaceInFlow(view, imagePlaceholder(node.alt()));
            }
        });
        return view;
    }

    private void applyFit(ImageView view, RXImageNode node, Image image) {
        double requestedWidth = node.width() > 0 ? node.width() : image.getWidth();
        double requestedHeight = node.height() > 0 ? node.height() : image.getHeight();
        double maxWidth = ctx.imageMaxWidthOrDefault();
        double maxHeight = ctx.imageMaxHeightOrDefault();
        // maxW/maxH <= 0 means "no upper bound" — assign the request directly rather than
        // min(request, <=0), which would wrongly collapse the image.
        view.setFitWidth(maxWidth > 0 ? Math.min(requestedWidth, maxWidth) : requestedWidth);
        view.setFitHeight(maxHeight > 0 ? Math.min(requestedHeight, maxHeight) : requestedHeight);
    }

    private void replaceInFlow(Node oldNode, Node newNode) {
        int index = flow.getChildren().indexOf(oldNode);
        if (index >= 0) {
            flow.getChildren().set(index, newNode);
        }
    }

    private Node imagePlaceholder(String alt) {
        Label placeholder = new Label(alt != null ? alt : "");
        placeholder.getStyleClass().add("image-placeholder");
        return placeholder;
    }

    // ==================== Links ====================

    private void decorateLink(Text run, RXLinkNode link) {
        run.getStyleClass().add(LINK_STYLE_CLASS);
        run.setCursor(Cursor.HAND);
        // Handler lives on this throwaway run — discarded wholesale on the next rebuild, so
        // it must NOT go through the skin's disposer. Never a Hyperlink (§11.2).
        RXBBCodeView control = ctx.control();
        run.addEventHandler(MouseEvent.MOUSE_CLICKED, event ->
                control.fireEvent(new RXBBCodeLinkEvent(control, RXBBCodeLinkEvent.LINK_ACTIVATED,
                        link.href(), link.kind())));
    }

    private Style currentStyle() {
        return styleStack.peek();
    }

    private static Style applyStyle(Style base, RXStyleType type, String value) {
        return switch (type) {
            case BOLD -> base.withWeight(FontWeight.BOLD);
            case ITALIC -> base.withPosture(FontPosture.ITALIC);
            case UNDERLINE -> base.withUnderline();
            case STRIKETHROUGH -> base.withStrike();
            case COLOR -> base.withFill(Color.web(value));
            case SIZE -> base.withSize(Double.parseDouble(value));
            case FONT -> base.withFamily(value);
        };
    }

    // ==================== Style ====================

    /**
     * Immutable cumulative inline style. {@code size <= 0} and {@code family == null} mean
     * "unset" (use the default), matching {@link Font#font(String, FontWeight, FontPosture,
     * double)}. Doubles as the run-merge fingerprint via record equality.
     */
    private record Style(FontWeight weight, FontPosture posture, double size, String family,
                         boolean underline, boolean strike, Paint fill) {

        static Style base() {
            return new Style(null, null, -1, null, false, false, null);
        }

        Style withWeight(FontWeight newWeight) {
            return new Style(newWeight, posture, size, family, underline, strike, fill);
        }

        Style withPosture(FontPosture newPosture) {
            return new Style(weight, newPosture, size, family, underline, strike, fill);
        }

        Style withSize(double newSize) {
            return new Style(weight, posture, newSize, family, underline, strike, fill);
        }

        Style withFamily(String newFamily) {
            return new Style(weight, posture, size, newFamily, underline, strike, fill);
        }

        Style withUnderline() {
            return new Style(weight, posture, size, family, true, strike, fill);
        }

        Style withStrike() {
            return new Style(weight, posture, size, family, underline, true, fill);
        }

        Style withFill(Paint newFill) {
            return new Style(weight, posture, size, family, underline, strike, newFill);
        }

        boolean hasFontModifier() {
            return weight != null || posture != null || size > 0 || family != null;
        }
    }

    /**
     * An inline {@link ImageView} that carries a {@code :loading} pseudo-class and reports
     * a baseline offset that optically centres the image on the surrounding text (the
     * image's vertical middle sits near the text's optical middle) rather than resting the
     * image bottom on the text baseline.
     */
    private static final class InlineImageView extends ImageView {

        private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");

        private final double ascent;
        private final double textHeight;

        InlineImageView(Image image) {
            super(image);
            Text reference = new Text("Ag");
            ascent = reference.getBaselineOffset();
            textHeight = reference.getLayoutBounds().getHeight();
        }

        void setLoading(boolean loading) {
            pseudoClassStateChanged(LOADING, loading);
        }

        @Override
        public double getBaselineOffset() {
            double imageHeight = getLayoutBounds().getHeight();
            return imageHeight / 2 + (ascent - textHeight / 2);
        }
    }
}
