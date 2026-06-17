package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.lrc.RXLrcDocument;
import io.github.leewyatt.rxcontrols.lrc.RXLrcLine;
import io.github.leewyatt.rxcontrols.lrc.RXLrcParser;
import io.github.leewyatt.rxcontrols.skins.RXLrcLineViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Displays the single current line of an LRC document, transitioning between
 * lines with a {@link PageAnimation}.
 *
 * <p>The control is driven exclusively by {@link #currentTimeProperty() currentTime}:
 * the current line is derived from the {@link #documentProperty() document} the same
 * way as in {@link RXLrcView}, and every current-line change plays the configured
 * {@link #animationProperty() animation}. Animations that keep a multi-page layout
 * ({@link PageAnimation#isMultiPageDisplay()}) are not supported and fall back
 * to a direct cut.</p>
 *
 * <p>While the playback time is before the first timed line, the control shows a
 * blank line (and animates the first line in); the {@link #placeholderProperty()
 * placeholder} is reserved for an absent or empty document.</p>
 */
public class RXLrcLineView extends Control {

    // ==================== Constants ====================

    /**
     * Default style class for this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-lrc-line-view";

    /**
     * Default line transition duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(500.0);

    private static final int NO_LINE_INDEX = -1;
    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");

    // ==================== Document ====================

    private final ObjectProperty<RXLrcDocument> document =
            new SimpleObjectProperty<>(this, "document", null) {
                @Override
                protected void invalidated() {
                    updateEmptyPseudoClass();
                    updateCurrentLine();
                }
            };

    /**
     * The immutable LRC document whose current line is displayed.
     *
     * @return the document property
     */
    public final ObjectProperty<RXLrcDocument> documentProperty() {
        return document;
    }

    /**
     * Returns the immutable LRC document.
     *
     * @return the document, or {@code null}
     */
    public final RXLrcDocument getDocument() {
        return document.get();
    }

    /**
     * Sets the immutable LRC document.
     *
     * @param value the document, or {@code null}
     */
    public final void setDocument(RXLrcDocument value) {
        document.set(value);
    }

    /**
     * Parses LRC text and sets the resulting document.
     *
     * <p>Parse warnings are discarded. Use {@link RXLrcParser#parse(String)}
     * directly when warnings are needed.</p>
     *
     * @param lyrics the raw LRC text
     * @throws NullPointerException if {@code lyrics} is {@code null}
     */
    public final void setLyrics(String lyrics) {
        setDocument(RXLrcParser.parse(lyrics).document());
    }

    // ==================== Current Time ====================

    private final ObjectProperty<Duration> currentTime =
            new SimpleObjectProperty<>(this, "currentTime", Duration.ZERO) {
                @Override
                protected void invalidated() {
                    updateCurrentLine();
                }
            };

    /**
     * Playback time used to locate the current lyric line.
     *
     * @return the current time property
     */
    public final ObjectProperty<Duration> currentTimeProperty() {
        return currentTime;
    }

    /**
     * Returns the playback time used to locate the current lyric line.
     *
     * @return the current time
     */
    public final Duration getCurrentTime() {
        return currentTime.get();
    }

    /**
     * Sets the playback time used to locate the current lyric line.
     *
     * @param value the current time
     */
    public final void setCurrentTime(Duration value) {
        currentTime.set(value);
    }

    // ==================== Time Offset ====================

    private final ObjectProperty<Duration> timeOffset =
            new SimpleObjectProperty<>(this, "timeOffset", Duration.ZERO) {
                @Override
                protected void invalidated() {
                    updateCurrentLine();
                }
            };

    /**
     * Runtime lyric timing adjustment. Positive values make lyrics appear earlier.
     *
     * @return the time offset property
     */
    public final ObjectProperty<Duration> timeOffsetProperty() {
        return timeOffset;
    }

    /**
     * Returns the runtime lyric timing adjustment.
     *
     * @return the time offset
     */
    public final Duration getTimeOffset() {
        return timeOffset.get();
    }

    /**
     * Sets the runtime lyric timing adjustment.
     *
     * @param value the time offset
     */
    public final void setTimeOffset(Duration value) {
        timeOffset.set(value);
    }

    // ==================== Current Line Index ====================

    private final ReadOnlyIntegerWrapper currentLineIndex =
            new ReadOnlyIntegerWrapper(this, "currentLineIndex", NO_LINE_INDEX);

    /**
     * Read-only current lyric line index.
     *
     * @return the read-only current line index property
     */
    public final ReadOnlyIntegerProperty currentLineIndexProperty() {
        return currentLineIndex.getReadOnlyProperty();
    }

    /**
     * Returns the current lyric line index.
     *
     * @return the current line index, or {@code -1} if no line is current
     */
    public final int getCurrentLineIndex() {
        return currentLineIndex.get();
    }

    // ==================== Current Line ====================

    private final ReadOnlyObjectWrapper<RXLrcLine> currentLine =
            new ReadOnlyObjectWrapper<>(this, "currentLine", null);

    /**
     * Read-only current lyric line.
     *
     * @return the read-only current line property
     */
    public final ReadOnlyObjectProperty<RXLrcLine> currentLineProperty() {
        return currentLine.getReadOnlyProperty();
    }

    /**
     * Returns the current lyric line.
     *
     * @return the current line, or {@code null}
     */
    public final RXLrcLine getCurrentLine() {
        return currentLine.get();
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether current-line changes should animate. When {@code false}, lines
     * switch with a direct cut.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether current-line changes should animate.
     *
     * @return {@code true} if line transitions are animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether current-line changes should animate.
     *
     * @param value {@code true} to animate line transitions
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation", new AnimFade());

    /**
     * The animation used for line transitions. Any {@link PageAnimation}
     * preset shared with {@link RXCarousel} can be used, except multi-page
     * display animations ({@link PageAnimation#isMultiPageDisplay()}) and
     * animations requiring more than two pages
     * ({@link PageAnimation#getMinimumPageCount()}), which fall back to a
     * direct cut. Setting {@code null} also falls back to a direct cut.
     *
     * @return the animation property
     */
    public final ObjectProperty<PageAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation used for line transitions.
     *
     * @return the animation, or {@code null}
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation used for line transitions.
     *
     * @param value the animation, or {@code null} for direct cuts
     */
    public final void setAnimation(PageAnimation value) {
        animation.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the line transition animation. Non-positive, unknown,
     * indefinite, or {@code null} values fall back to a direct cut.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the line transition animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the line transition animation duration.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder =
            new SimpleObjectProperty<>(this, "placeholder", createDefaultPlaceholder());

    /**
     * Node shown when the document is empty or absent.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the node shown when the document is empty or absent.
     *
     * @return the placeholder node, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the node shown when the document is empty or absent.
     *
     * @param value the placeholder node, or {@code null}
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== Constructors ====================

    /**
     * Creates an empty single-line LRC view.
     */
    @SuppressWarnings("unchecked")
    public RXLrcLineView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Display-only control: initialize focusTraversable to false with a
        // null StyleOrigin so CSS can still override it, mirroring Label and
        // ProgressIndicator.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        updateEmptyPseudoClass();
        updateCurrentLine();
    }

    // ==================== Skin ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXLrcLineViewSkin(this);
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
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * Returns the CSS metadata supported by this control.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    private static Node createDefaultPlaceholder() {
        Label label = new Label("No lyrics available");
        label.getStyleClass().add("placeholder");
        return label;
    }

    // Mirrors the current-line derivation in RXLrcView so both lyric
    // components resolve the same line for the same document and time.
    private void updateCurrentLine() {
        RXLrcDocument currentDocument = getDocument();
        int index = NO_LINE_INDEX;
        RXLrcLine line = null;
        if (currentDocument != null && !currentDocument.isEmpty()) {
            Duration lookupTime = lookupTime();
            if (lookupTime != null) {
                index = currentDocument.lineIndexAt(lookupTime);
                if (index >= 0 && index < currentDocument.lines().size()) {
                    line = currentDocument.lines().get(index);
                } else {
                    index = NO_LINE_INDEX;
                }
            }
        }
        currentLineIndex.set(index);
        currentLine.set(line);
    }

    private Duration lookupTime() {
        Duration time = getCurrentTime();
        if (!isFinite(time)) {
            return null;
        }
        Duration offset = getTimeOffset();
        if (offset == null) {
            offset = Duration.ZERO;
        }
        Duration lookup = time.add(offset);
        if (!isFinite(lookup)) {
            return null;
        }
        return lookup;
    }

    private void updateEmptyPseudoClass() {
        RXLrcDocument currentDocument = getDocument();
        pseudoClassStateChanged(EMPTY_PSEUDO_CLASS,
                currentDocument == null || currentDocument.isEmpty());
    }

    private static boolean isFinite(Duration duration) {
        return duration != null
                && !duration.isUnknown()
                && !duration.isIndefinite()
                && Double.isFinite(duration.toMillis());
    }

    // ==================== Styleable Properties ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXLrcLineView, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {

                    @Override
                    public boolean isSettable(RXLrcLineView control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXLrcLineView control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
