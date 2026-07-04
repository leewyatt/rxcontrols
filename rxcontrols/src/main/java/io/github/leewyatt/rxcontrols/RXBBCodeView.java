package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeParseResult;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeParseWarning;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeParser;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodePolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBDocument;
import io.github.leewyatt.rxcontrols.event.RXBBCodeLinkEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXBBCodeViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A read-only, wrapping view that renders a safe subset of BBCode markup.
 *
 * <p>{@code RXBBCodeView} takes an untrusted BBCode {@link #contentProperty() content}
 * string and renders it as styled, laid-out rich text: bold / italic / underline /
 * strikethrough runs, coloured and sized text, links, images, quotes, code blocks,
 * lists, tables, spoilers and rules. Parsing is total and never throws — malformed
 * markup is recovered leniently and surfaced as {@link #warningsProperty() warnings}
 * rather than exceptions, and the derived {@link #documentProperty() document} tree is
 * always non-null.
 *
 * <p>Safety is enforced at parse time: link and image URLs are checked against the
 * {@link #policyProperty() policy}'s scheme allow-lists, and colours / sizes / fonts are
 * validated and applied through typed JavaFX setters — never through {@code -fx-…} style
 * injection — so hostile markup cannot reach the CSS engine. The control is a static
 * viewer, not an editor; the only user interaction is activating a link, reported via
 * {@code onLinkActivated}.
 *
 * <p>The content wraps to the width it is given, so the control is
 * {@link Orientation#HORIZONTAL} content-biased. When the document is empty the optional
 * {@link #placeholderProperty() placeholder} is shown and the {@code :empty} pseudo-class
 * is set.
 */
public class RXBBCodeView extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-bbcode-view";

    /**
     * Default {@link #paragraphSpacingProperty() paragraphSpacing}, in pixels.
     */
    private static final double DEFAULT_PARAGRAPH_SPACING = 8;

    /**
     * Default {@link #maxFontSizeProperty() maxFontSize} ceiling, in pixels.
     */
    private static final double DEFAULT_MAX_FONT_SIZE = 64;

    /**
     * Default {@link #maxNestingDepthProperty() maxNestingDepth}: the render-recursion
     * crash guard. {@code < 0} disables the guard.
     */
    private static final int DEFAULT_MAX_NESTING_DEPTH = 512;

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");

    // ==================== Constructors ====================

    /**
     * Creates an empty BBCode view.
     */
    public RXBBCodeView() {
        this("");
    }

    /**
     * Creates a BBCode view rendering the given markup.
     *
     * @param content the BBCode markup to render; {@code null} is treated as empty
     */
    public RXBBCodeView(String content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(false);
        setContent(content);
        reparse();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXBBCodeViewSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link Orientation#HORIZONTAL} because the content wraps, so the
     * control's height depends on the width allotted to it.
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    // ==================== Content ====================

    private final StringProperty content = new SimpleStringProperty(this, "content", "") {
        @Override
        protected void invalidated() {
            reparse();
        }
    };

    /**
     * The BBCode markup to render. {@code null} is accepted (pure pass-through) and
     * rendered as empty.
     *
     * @return the content property
     */
    public final StringProperty contentProperty() {
        return content;
    }

    /**
     * Returns the BBCode markup.
     *
     * @return the content, possibly {@code null}
     */
    public final String getContent() {
        return content.get();
    }

    /**
     * Sets the BBCode markup to render.
     *
     * @param value the content, or {@code null} for empty
     */
    public final void setContent(String value) {
        content.set(value);
    }

    // ==================== Lenient ====================

    private final BooleanProperty lenient = new SimpleBooleanProperty(this, "lenient", true) {
        @Override
        protected void invalidated() {
            reparse();
        }
    };

    /**
     * Whether malformed markup is recovered leniently (default {@code true}). When
     * {@code false}, an unbalanced tag is treated as literal text instead of being
     * auto-closed.
     *
     * @return the lenient property
     */
    public final BooleanProperty lenientProperty() {
        return lenient;
    }

    /**
     * Returns whether lenient recovery is enabled.
     *
     * @return {@code true} if malformed markup is recovered leniently
     */
    public final boolean isLenient() {
        return lenient.get();
    }

    /**
     * Sets whether malformed markup is recovered leniently.
     *
     * @param value {@code true} to recover leniently
     */
    public final void setLenient(boolean value) {
        lenient.set(value);
    }

    // ==================== Show Malformed Tags As Text ====================

    private final BooleanProperty showMalformedTagsAsText =
            new SimpleBooleanProperty(this, "showMalformedTagsAsText", false) {
                @Override
                protected void invalidated() {
                    reparse();
                }
            };

    /**
     * Whether unknown or unbalanced tags are echoed as literal text (default
     * {@code false}). When {@code false} such tags are dropped, keeping their inner
     * content.
     *
     * @return the show-malformed-tags-as-text property
     */
    public final BooleanProperty showMalformedTagsAsTextProperty() {
        return showMalformedTagsAsText;
    }

    /**
     * Returns whether malformed tags are echoed as literal text.
     *
     * @return {@code true} if malformed tags are shown literally
     */
    public final boolean isShowMalformedTagsAsText() {
        return showMalformedTagsAsText.get();
    }

    /**
     * Sets whether malformed tags are echoed as literal text.
     *
     * @param value {@code true} to show malformed tags literally
     */
    public final void setShowMalformedTagsAsText(boolean value) {
        showMalformedTagsAsText.set(value);
    }

    // ==================== Max Nesting Depth ====================

    private final IntegerProperty maxNestingDepth =
            new SimpleIntegerProperty(this, "maxNestingDepth", DEFAULT_MAX_NESTING_DEPTH) {
                @Override
                protected void invalidated() {
                    reparse();
                }
            };

    /**
     * The maximum tag-nesting depth (default {@value #DEFAULT_MAX_NESTING_DEPTH}). This is
     * a crash guard: markup nested deeper than this is flattened so the renderer cannot
     * recurse into a {@code StackOverflowError}. A negative value disables the guard. Not
     * styleable — it is a safety limit, not an appearance.
     *
     * @return the max-nesting-depth property
     */
    public final IntegerProperty maxNestingDepthProperty() {
        return maxNestingDepth;
    }

    /**
     * Returns the maximum tag-nesting depth.
     *
     * @return the maximum nesting depth, or a negative value if the guard is disabled
     */
    public final int getMaxNestingDepth() {
        return maxNestingDepth.get();
    }

    /**
     * Sets the maximum tag-nesting depth.
     *
     * @param value the maximum nesting depth; negative disables the guard
     */
    public final void setMaxNestingDepth(int value) {
        maxNestingDepth.set(value);
    }

    // ==================== Policy ====================

    private final ObjectProperty<RXBBCodePolicy> policy =
            new SimpleObjectProperty<>(this, "policy", RXBBCodePolicy.defaults()) {
                @Override
                protected void invalidated() {
                    reparse();
                }
            };

    /**
     * The security policy: the URL / image scheme allow-lists.
     * Lax {@code null} handling — {@code null} is read back verbatim, and parsing falls
     * back to {@link RXBBCodePolicy#defaults()} when it is {@code null}.
     *
     * @return the policy property
     */
    public final ObjectProperty<RXBBCodePolicy> policyProperty() {
        return policy;
    }

    /**
     * Returns the security policy.
     *
     * @return the policy, possibly {@code null}
     */
    public final RXBBCodePolicy getPolicy() {
        return policy.get();
    }

    /**
     * Sets the security policy.
     *
     * @param value the policy, or {@code null} to fall back to the defaults
     */
    public final void setPolicy(RXBBCodePolicy value) {
        policy.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder", null);

    /**
     * The node shown when the {@link #documentProperty() document} is empty. Default
     * {@code null} — an empty view simply shows nothing.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the placeholder node.
     *
     * @return the placeholder, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the node shown when the document is empty.
     *
     * @param value the placeholder node, or {@code null}
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== On Link Activated ====================

    private final ObjectProperty<EventHandler<RXBBCodeLinkEvent>> onLinkActivated =
            new SimpleObjectProperty<>(this, "onLinkActivated", null) {
                @Override
                protected void invalidated() {
                    setEventHandler(RXBBCodeLinkEvent.LINK_ACTIVATED, get());
                }
            };

    /**
     * Convenience handler for link-activation events. Equivalent to registering a handler
     * for {@link RXBBCodeLinkEvent#LINK_ACTIVATED}.
     *
     * @return the link-activated handler property
     */
    public final ObjectProperty<EventHandler<RXBBCodeLinkEvent>> onLinkActivatedProperty() {
        return onLinkActivated;
    }

    /**
     * Returns the convenience handler for link-activation events.
     *
     * @return the link-activated handler, or {@code null}
     */
    public final EventHandler<RXBBCodeLinkEvent> getOnLinkActivated() {
        return onLinkActivated.get();
    }

    /**
     * Sets the convenience handler for link-activation events.
     *
     * @param value the link-activated handler, or {@code null}
     */
    public final void setOnLinkActivated(EventHandler<RXBBCodeLinkEvent> value) {
        onLinkActivated.set(value);
    }

    // ==================== Document (read-only) ====================

    private final ReadOnlyObjectWrapper<RXBBDocument> document =
            new ReadOnlyObjectWrapper<>(this, "document", RXBBDocument.empty());

    /**
     * The parsed document tree derived from {@link #contentProperty() content}. Never
     * {@code null}; empty content yields {@link RXBBDocument#empty()}. Immutable, so it is
     * safe to read and cache.
     *
     * @return the read-only document property
     */
    public final ReadOnlyObjectProperty<RXBBDocument> documentProperty() {
        return document.getReadOnlyProperty();
    }

    /**
     * Returns the parsed document tree.
     *
     * @return the document, never {@code null}
     */
    public final RXBBDocument getDocument() {
        return document.get();
    }

    // ==================== Warnings (read-only) ====================

    private final ReadOnlyObjectWrapper<List<RXBBCodeParseWarning>> warnings =
            new ReadOnlyObjectWrapper<>(this, "warnings", List.of());

    /**
     * The parse warnings produced by the most recent parse, in document order. Never
     * {@code null} and always an unmodifiable list; empty when the markup is clean.
     *
     * @return the read-only warnings property
     */
    public final ReadOnlyObjectProperty<List<RXBBCodeParseWarning>> warningsProperty() {
        return warnings.getReadOnlyProperty();
    }

    /**
     * Returns the parse warnings.
     *
     * @return an unmodifiable list of warnings, never {@code null}
     */
    public final List<RXBBCodeParseWarning> getWarnings() {
        return warnings.get();
    }

    // ==================== Paragraph Spacing ====================

    private final DoubleProperty paragraphSpacing =
            new SimpleStyleableDoubleProperty(StyleableProperties.PARAGRAPH_SPACING,
                    this, "paragraphSpacing", DEFAULT_PARAGRAPH_SPACING);

    /**
     * The vertical spacing between top-level blocks, in pixels. Styleable via
     * {@code -rx-paragraph-spacing}.
     *
     * @return the paragraph-spacing property
     */
    public final DoubleProperty paragraphSpacingProperty() {
        return paragraphSpacing;
    }

    /**
     * Returns the vertical spacing between blocks.
     *
     * @return the paragraph spacing, in pixels
     */
    public final double getParagraphSpacing() {
        return paragraphSpacing.get();
    }

    /**
     * Sets the vertical spacing between blocks.
     *
     * @param value the paragraph spacing, in pixels
     */
    public final void setParagraphSpacing(double value) {
        paragraphSpacing.set(value);
    }

    // ==================== Image Max Width ====================

    private final DoubleProperty imageMaxWidth =
            new SimpleStyleableDoubleProperty(StyleableProperties.IMAGE_MAX_WIDTH,
                    this, "imageMaxWidth", Region.USE_COMPUTED_SIZE);

    /**
     * The maximum display width of an inline image, in pixels. Styleable via
     * {@code -rx-image-max-width}. A value {@code <= 0} (including the default
     * {@link Region#USE_COMPUTED_SIZE}) means no upper bound, matching
     * {@code ImageView.fitWidth}.
     *
     * @return the image-max-width property
     */
    public final DoubleProperty imageMaxWidthProperty() {
        return imageMaxWidth;
    }

    /**
     * Returns the maximum inline-image display width.
     *
     * @return the image max width, in pixels; {@code <= 0} means unbounded
     */
    public final double getImageMaxWidth() {
        return imageMaxWidth.get();
    }

    /**
     * Sets the maximum inline-image display width.
     *
     * @param value the image max width in pixels; {@code <= 0} means unbounded
     */
    public final void setImageMaxWidth(double value) {
        imageMaxWidth.set(value);
    }

    // ==================== Image Max Height ====================

    private final DoubleProperty imageMaxHeight =
            new SimpleStyleableDoubleProperty(StyleableProperties.IMAGE_MAX_HEIGHT,
                    this, "imageMaxHeight", Region.USE_COMPUTED_SIZE);

    /**
     * The maximum display height of an inline image, in pixels. Styleable via
     * {@code -rx-image-max-height}. A value {@code <= 0} (including the default
     * {@link Region#USE_COMPUTED_SIZE}) means no upper bound, matching
     * {@code ImageView.fitHeight}.
     *
     * @return the image-max-height property
     */
    public final DoubleProperty imageMaxHeightProperty() {
        return imageMaxHeight;
    }

    /**
     * Returns the maximum inline-image display height.
     *
     * @return the image max height, in pixels; {@code <= 0} means unbounded
     */
    public final double getImageMaxHeight() {
        return imageMaxHeight.get();
    }

    /**
     * Sets the maximum inline-image display height.
     *
     * @param value the image max height in pixels; {@code <= 0} means unbounded
     */
    public final void setImageMaxHeight(double value) {
        imageMaxHeight.set(value);
    }

    // ==================== Max Font Size ====================

    private final DoubleProperty maxFontSize =
            new SimpleStyleableDoubleProperty(StyleableProperties.MAX_FONT_SIZE,
                    this, "maxFontSize", DEFAULT_MAX_FONT_SIZE);

    /**
     * The ceiling applied to any explicit font size ({@code [size]} or a heading),
     * in pixels, so untrusted markup cannot request a font large enough to explode
     * layout. Styleable via {@code -rx-max-font-size}. A value {@code <= 0} means no
     * ceiling; the default is {@value #DEFAULT_MAX_FONT_SIZE}. This caps display only —
     * a run that requests no size keeps its CSS-resolved font.
     *
     * @return the max-font-size property
     */
    public final DoubleProperty maxFontSizeProperty() {
        return maxFontSize;
    }

    /**
     * Returns the font-size ceiling.
     *
     * @return the max font size, in pixels; {@code <= 0} means unbounded
     */
    public final double getMaxFontSize() {
        return maxFontSize.get();
    }

    /**
     * Sets the font-size ceiling.
     *
     * @param value the max font size in pixels; {@code <= 0} means unbounded
     */
    public final void setMaxFontSize(double value) {
        maxFontSize.set(value);
    }

    // ==================== Reparse ====================

    private void reparse() {
        RXBBCodePolicy activePolicy = policyOrDefault();
        // content.getValueSafe() coerces a null content to "" — the public parse API
        // rejects null, but the control treats null content as empty like RXTextView.
        RXBBCodeParseResult result = RXBBCodeParser.parse(content.getValueSafe(), activePolicy,
                isLenient(), isShowMalformedTagsAsText(), getMaxNestingDepth());
        RXBBDocument parsed = result.document();
        if (!parsed.equals(getDocument())) {
            // Structural equality suppresses a needless skin rebuild when a value-equal
            // content string is set (route B: the immutable AST is the change signal).
            document.set(parsed);
        }
        warnings.set(result.warnings());
        updateEmptyPseudoClass();
    }

    private RXBBCodePolicy policyOrDefault() {
        RXBBCodePolicy current = getPolicy();
        return current != null ? current : RXBBCodePolicy.defaults();
    }

    private void updateEmptyPseudoClass() {
        RXBBDocument current = getDocument();
        pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, current == null || current.isEmpty());
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXBBCodeView, Number> PARAGRAPH_SPACING =
                new CssMetaData<>("-rx-paragraph-spacing", SizeConverter.getInstance(), DEFAULT_PARAGRAPH_SPACING) {

                    @Override
                    public boolean isSettable(RXBBCodeView node) {
                        return !node.paragraphSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBBCodeView node) {
                        return (StyleableProperty<Number>) node.paragraphSpacingProperty();
                    }
                };

        private static final CssMetaData<RXBBCodeView, Number> IMAGE_MAX_WIDTH =
                new CssMetaData<>("-rx-image-max-width", SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {

                    @Override
                    public boolean isSettable(RXBBCodeView node) {
                        return !node.imageMaxWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBBCodeView node) {
                        return (StyleableProperty<Number>) node.imageMaxWidthProperty();
                    }
                };

        private static final CssMetaData<RXBBCodeView, Number> IMAGE_MAX_HEIGHT =
                new CssMetaData<>("-rx-image-max-height", SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {

                    @Override
                    public boolean isSettable(RXBBCodeView node) {
                        return !node.imageMaxHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBBCodeView node) {
                        return (StyleableProperty<Number>) node.imageMaxHeightProperty();
                    }
                };

        private static final CssMetaData<RXBBCodeView, Number> MAX_FONT_SIZE =
                new CssMetaData<>("-rx-max-font-size", SizeConverter.getInstance(), DEFAULT_MAX_FONT_SIZE) {

                    @Override
                    public boolean isSettable(RXBBCodeView node) {
                        return !node.maxFontSize.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBBCodeView node) {
                        return (StyleableProperty<Number>) node.maxFontSizeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables,
                    PARAGRAPH_SPACING,
                    IMAGE_MAX_WIDTH,
                    IMAGE_MAX_HEIGHT,
                    MAX_FONT_SIZE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CssMetaData associated with this class, including that of its
     * superclasses.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
