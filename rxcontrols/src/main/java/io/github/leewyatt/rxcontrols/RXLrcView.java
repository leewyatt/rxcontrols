package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXLrcLineEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.lrc.RXLrcDocument;
import io.github.leewyatt.rxcontrols.lrc.RXLrcLine;
import io.github.leewyatt.rxcontrols.lrc.RXLrcParser;
import io.github.leewyatt.rxcontrols.skins.RXLrcViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Displays an immutable LRC document synchronized to a playback time.
 *
 * <p>The control owns observable state and current-line derivation. Rendering,
 * layout, scrolling, and line-click interaction are handled by
 * {@link RXLrcViewSkin}.</p>
 */
public class RXLrcView extends Control {

    // ==================== Constants ====================

    /**
     * Default style class for this control.
     */
    public static final String DEFAULT_STYLE_CLASS = "rx-lrc-view";

    /**
     * Default placeholder text.
     */
    public static final String DEFAULT_PLACEHOLDER_TEXT = "No lyrics available";

    /**
     * Default scroll animation duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(300.0);

    /**
     * Default vertical anchor position for the current line.
     */
    public static final double DEFAULT_CURRENT_LINE_POSITION = 0.45;

    /**
     * Default spacing between lyric lines.
     */
    public static final double DEFAULT_LINE_SPACING = 8.0;

    /**
     * Default scale applied to the current line by the skin.
     */
    public static final double DEFAULT_CURRENT_LINE_SCALE = 1.1;

    /**
     * Default setting for mouse drag lyric browsing.
     */
    public static final boolean DEFAULT_MANUAL_BROWSE_ENABLED = true;

    /**
     * Default setting for mouse wheel lyric browsing.
     */
    public static final boolean DEFAULT_MOUSE_WHEEL_BROWSE_ENABLED = true;

    /**
     * Default delay before manual browsing recovers to automatic following.
     */
    public static final Duration DEFAULT_BROWSE_RECOVER_DELAY = Duration.seconds(5.0);

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
     * The immutable LRC document to display.
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
     * Whether current-line changes should animate.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether current-line changes should animate.
     *
     * @return {@code true} if scrolling is animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether current-line changes should animate.
     *
     * @param value {@code true} to animate scrolling
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the current-line scroll animation.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the current-line scroll animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the current-line scroll animation duration.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Current Line Position ====================

    private final DoubleProperty currentLinePosition =
            new SimpleStyleableDoubleProperty(StyleableProperties.CURRENT_LINE_POSITION,
                    this, "currentLinePosition", DEFAULT_CURRENT_LINE_POSITION);

    /**
     * Vertical anchor position for the current line center, expressed as a viewport ratio.
     *
     * <p>A value of {@code 0.0} places the current line center at the top edge;
     * {@code 0.5} centers it; {@code 1.0} places the center at the bottom edge.
     * At {@code 0.0} or {@code 1.0}, part of the current line may be clipped.</p>
     *
     * @return the current line position property
     */
    public final DoubleProperty currentLinePositionProperty() {
        return currentLinePosition;
    }

    /**
     * Returns the vertical anchor position for the current line center.
     *
     * @return the current line position
     */
    public final double getCurrentLinePosition() {
        return currentLinePosition.get();
    }

    /**
     * Sets the vertical anchor position for the current line center.
     *
     * @param value the current line position
     */
    public final void setCurrentLinePosition(double value) {
        currentLinePosition.set(value);
    }

    // ==================== Line Spacing ====================

    private final DoubleProperty lineSpacing =
            new SimpleStyleableDoubleProperty(StyleableProperties.LINE_SPACING,
                    this, "lineSpacing", DEFAULT_LINE_SPACING);

    /**
     * Spacing between lyric lines.
     *
     * @return the line spacing property
     */
    public final DoubleProperty lineSpacingProperty() {
        return lineSpacing;
    }

    /**
     * Returns the spacing between lyric lines.
     *
     * @return the line spacing
     */
    public final double getLineSpacing() {
        return lineSpacing.get();
    }

    /**
     * Sets the spacing between lyric lines.
     *
     * @param value the line spacing
     */
    public final void setLineSpacing(double value) {
        lineSpacing.set(value);
    }

    // ==================== Current Line Scale ====================

    private final DoubleProperty currentLineScale =
            new SimpleStyleableDoubleProperty(StyleableProperties.CURRENT_LINE_SCALE,
                    this, "currentLineScale", DEFAULT_CURRENT_LINE_SCALE);

    /**
     * Scale applied to the current line by the skin.
     *
     * @return the current line scale property
     */
    public final DoubleProperty currentLineScaleProperty() {
        return currentLineScale;
    }

    /**
     * Returns the scale applied to the current line by the skin.
     *
     * @return the current line scale
     */
    public final double getCurrentLineScale() {
        return currentLineScale.get();
    }

    /**
     * Sets the scale applied to the current line by the skin.
     *
     * @param value the current line scale
     */
    public final void setCurrentLineScale(double value) {
        currentLineScale.set(value);
    }

    // ==================== Manual Browse Enabled ====================

    private final BooleanProperty manualBrowseEnabled =
            new SimpleBooleanProperty(this, "manualBrowseEnabled", DEFAULT_MANUAL_BROWSE_ENABLED);

    /**
     * Whether mouse drag browsing is enabled.
     *
     * @return the manual browse enabled property
     */
    public final BooleanProperty manualBrowseEnabledProperty() {
        return manualBrowseEnabled;
    }

    /**
     * Returns whether mouse drag browsing is enabled.
     *
     * @return {@code true} if mouse drag browsing is enabled
     */
    public final boolean isManualBrowseEnabled() {
        return manualBrowseEnabled.get();
    }

    /**
     * Sets whether mouse drag browsing is enabled.
     *
     * @param value {@code true} to enable mouse drag browsing
     */
    public final void setManualBrowseEnabled(boolean value) {
        manualBrowseEnabled.set(value);
    }

    // ==================== Mouse Wheel Browse Enabled ====================

    private final BooleanProperty mouseWheelBrowseEnabled =
            new SimpleBooleanProperty(this, "mouseWheelBrowseEnabled", DEFAULT_MOUSE_WHEEL_BROWSE_ENABLED);

    /**
     * Whether mouse wheel browsing is enabled.
     *
     * @return the mouse wheel browse enabled property
     */
    public final BooleanProperty mouseWheelBrowseEnabledProperty() {
        return mouseWheelBrowseEnabled;
    }

    /**
     * Returns whether mouse wheel browsing is enabled.
     *
     * @return {@code true} if mouse wheel browsing is enabled
     */
    public final boolean isMouseWheelBrowseEnabled() {
        return mouseWheelBrowseEnabled.get();
    }

    /**
     * Sets whether mouse wheel browsing is enabled.
     *
     * @param value {@code true} to enable mouse wheel browsing
     */
    public final void setMouseWheelBrowseEnabled(boolean value) {
        mouseWheelBrowseEnabled.set(value);
    }

    // ==================== Browse Recover Delay ====================

    private final ObjectProperty<Duration> browseRecoverDelay =
            new SimpleObjectProperty<>(this, "browseRecoverDelay", DEFAULT_BROWSE_RECOVER_DELAY);

    /**
     * Idle delay before manual browsing recovers to automatic following.
     *
     * <p>The skin treats {@code null}, unknown, indefinite, and non-finite
     * values as {@link #DEFAULT_BROWSE_RECOVER_DELAY}. Zero or negative values
     * recover immediately.</p>
     *
     * @return the browse recover delay property
     */
    public final ObjectProperty<Duration> browseRecoverDelayProperty() {
        return browseRecoverDelay;
    }

    /**
     * Returns the idle delay before manual browsing recovers.
     *
     * @return the browse recover delay
     */
    public final Duration getBrowseRecoverDelay() {
        return browseRecoverDelay.get();
    }

    /**
     * Sets the idle delay before manual browsing recovers.
     *
     * @param value the browse recover delay
     */
    public final void setBrowseRecoverDelay(Duration value) {
        browseRecoverDelay.set(value);
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

    // ==================== On Line Clicked ====================

    private final ObjectProperty<EventHandler<RXLrcLineEvent>> onLineClicked =
            new SimpleObjectProperty<>(this, "onLineClicked", null) {
                @Override
                protected void invalidated() {
                    setEventHandler(RXLrcLineEvent.LINE_CLICKED, get());
                }
            };

    /**
     * Convenience handler for lyric-line click events.
     *
     * @return the line-click handler property
     */
    public final ObjectProperty<EventHandler<RXLrcLineEvent>> onLineClickedProperty() {
        return onLineClicked;
    }

    /**
     * Returns the convenience handler for lyric-line click events.
     *
     * @return the line-click handler, or {@code null}
     */
    public final EventHandler<RXLrcLineEvent> getOnLineClicked() {
        return onLineClicked.get();
    }

    /**
     * Sets the convenience handler for lyric-line click events.
     *
     * @param value the line-click handler, or {@code null}
     */
    public final void setOnLineClicked(EventHandler<RXLrcLineEvent> value) {
        onLineClicked.set(value);
    }

    // ==================== Constructors ====================

    /**
     * Creates an empty LRC view.
     */
    public RXLrcView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        updateEmptyPseudoClass();
        updateCurrentLine();
    }

    // ==================== Skin ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXLrcViewSkin(this);
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
        Label label = new Label(DEFAULT_PLACEHOLDER_TEXT);
        label.getStyleClass().add("placeholder");
        return label;
    }

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

        private static final CssMetaData<RXLrcView, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {

                    @Override
                    public boolean isSettable(RXLrcView control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXLrcView control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXLrcView, Number> CURRENT_LINE_POSITION =
                new CssMetaData<>("-rx-current-line-position",
                        SizeConverter.getInstance(), DEFAULT_CURRENT_LINE_POSITION) {

                    @Override
                    public boolean isSettable(RXLrcView control) {
                        return !control.currentLinePosition.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXLrcView control) {
                        return (StyleableProperty<Number>) control.currentLinePositionProperty();
                    }
                };

        private static final CssMetaData<RXLrcView, Number> LINE_SPACING =
                new CssMetaData<>("-rx-line-spacing",
                        SizeConverter.getInstance(), DEFAULT_LINE_SPACING) {

                    @Override
                    public boolean isSettable(RXLrcView control) {
                        return !control.lineSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXLrcView control) {
                        return (StyleableProperty<Number>) control.lineSpacingProperty();
                    }
                };

        private static final CssMetaData<RXLrcView, Number> CURRENT_LINE_SCALE =
                new CssMetaData<>("-rx-current-line-scale",
                        SizeConverter.getInstance(), DEFAULT_CURRENT_LINE_SCALE) {

                    @Override
                    public boolean isSettable(RXLrcView control) {
                        return !control.currentLineScale.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXLrcView control) {
                        return (StyleableProperty<Number>) control.currentLineScaleProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables,
                    ANIMATION_DURATION,
                    CURRENT_LINE_POSITION,
                    LINE_SPACING,
                    CURRENT_LINE_SCALE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
