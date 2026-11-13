package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.internal.KeywordConverter;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXHoldButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Button that fires only after it has been held down for the
 * {@link #holdDurationProperty() holdDuration}: a guard against accidental
 * activation of destructive or irreversible actions.
 *
 * <p>A plain press of the primary mouse button, or of SPACE (and ENTER on
 * platforms other than macOS), starts a hold; other buttons and modified
 * presses do not. A progress fill sweeps across the button, and the
 * {@code ActionEvent} fires once, the moment the fill completes. Releasing
 * early, moving the pointer out, disabling, removing or hiding the button,
 * the window losing focus, or the button losing focus during a key hold
 * cancels the hold without firing.</p>
 *
 * <p>The button has three faces, each with its own text and graphic:</p>
 * <ul>
 *   <li>idle: {@code text} and {@code graphic};</li>
 *   <li>holding: {@link #holdingTextProperty() holdingText} and
 *       {@link #holdingGraphicProperty() holdingGraphic}, with the
 *       {@code holding} pseudo-class;</li>
 *   <li>completed: {@link #completedTextProperty() completedText} and
 *       {@link #completedGraphicProperty() completedGraphic}, with the
 *       {@code completed} pseudo-class.</li>
 * </ul>
 *
 * <p>A {@code null} text or graphic shows nothing on that face; nothing is
 * borrowed from the idle face. To keep the idle text while holding, set the
 * same text, or bind it with
 * {@code holdingTextProperty().bind(textProperty())}. All three faces are in
 * the scene at once, so {@code graphic}, {@code holdingGraphic} and
 * {@code completedGraphic} must be different nodes. The button is sized to the
 * largest face and keeps that size when the face changes; the default holding
 * and completed texts count toward its width too, so an icon-only button
 * should set both texts to {@code null}. The default texts are English and
 * not localized; set them for other locales.</p>
 *
 * <p>The idle and holding faces swap instantly. The completed face comes in,
 * and goes back to the idle face, with the
 * {@link #animationProperty() animation} when one is set.</p>
 *
 * <p>After a hold completes, the completed face stays for the
 * {@link #feedbackDurationProperty() feedbackDuration} and no new hold can
 * start until it ends. The default, {@code Duration.INDEFINITE}, keeps the
 * completed face until {@link #resetFeedback()} is called, which suits a
 * one-time confirmation. Actions that may be repeated should use a finite
 * duration, or {@code Duration.ZERO} to skip the completed face. If a
 * listener vetoes the action while the hold completes, for example by
 * disabling the button when it disarms, nothing fires and no completed face
 * shows.</p>
 *
 * <pre>{@code
 * RXHoldButton delete = new RXHoldButton("Hold to delete");
 * delete.setCompletedText("Deleted");
 * delete.setOnAction(event -> items.remove(item));
 *
 * // A repeatable action: back to the idle face after two seconds
 * clearLog.setFeedbackDuration(Duration.seconds(2));
 *
 * // No completed face: ready for the next hold once released
 * nextTrack.setFeedbackDuration(Duration.ZERO);
 *
 * // A one-time confirmation that reopens once the work is done
 * submit.setOnAction(event -> service.submit().thenRun(
 *         () -> Platform.runLater(submit::resetFeedback)));
 * }</pre>
 *
 * <p>{@link #fire()}, {@code AccessibleAction.FIRE} and any
 * {@code ActionEvent} that reaches the button are untimed: they fire at once, and
 * cancel a hold in progress, which then does not fire again. A hold is a
 * guard against slips, not a security boundary: give destructive actions an
 * untimed alternative as well, such as a confirmation dialog, for users who
 * cannot hold a key or button. Handlers added with
 * {@code addEventHandler(ActionEvent.ACTION, ...)} before the skin is
 * installed may run before the hold is cancelled.</p>
 *
 * <p>JavaFX does not tell a key's auto-repeat from a new press. If
 * {@code onAction} removes the button, focus may move on to another hold
 * button, where a key still held down would start a new hold: move focus to
 * a neutral node first.</p>
 *
 * <p>Mnemonics are not supported. An icon-only button should set
 * {@code accessibleText}.</p>
 */
public class RXHoldButton extends ButtonBase {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-hold-button";

    /**
     * Default {@link #holdDurationProperty() holdDuration}, and the value used
     * in place of an unusable one.
     */
    public static final Duration DEFAULT_HOLD_DURATION = Duration.millis(1500.0);

    /**
     * Default {@link #feedbackDurationProperty() feedbackDuration}, and the
     * value used in place of a {@code null}, negative or unknown one.
     */
    public static final Duration DEFAULT_FEEDBACK_DURATION = Duration.INDEFINITE;

    /**
     * Default {@link #animationDurationProperty() animationDuration}, and the
     * value used in place of a {@code null}, negative or otherwise unusable one.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    /**
     * Default {@link #fillAnimationProperty() fillAnimation}, and the value
     * used in place of a {@code null} one.
     */
    public static final FillAnimation DEFAULT_FILL_ANIMATION = FillAnimation.LEFT_TO_RIGHT;

    // ==================== Constructors ====================

    /**
     * Creates a hold button with the text caption {@code "Hold to confirm"}.
     */
    public RXHoldButton() {
        this("Hold to confirm");
    }

    /**
     * Creates a hold button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXHoldButton(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a hold button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXHoldButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.BUTTON);
    }

    /**
     * Creates the default three-face skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXHoldButtonSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Actions ====================

    /**
     * Fires an {@code ActionEvent} at once, without a hold, unless the button
     * is disabled. A hold in progress is cancelled and does not fire again.
     */
    @Override
    public void fire() {
        if (!isDisabled()) {
            fireEvent(new ActionEvent());
        }
    }

    /**
     * Ends the completed feedback: stops its timer, clears the
     * {@code completed} pseudo-class and goes back to the idle face, playing
     * the {@link #animationProperty() animation} when one is set. A new hold
     * can start once the mouse button or key that completed the hold is
     * released. Does not fire an {@code ActionEvent} and does not affect a
     * hold in progress; works on a disabled button too.
     */
    public final void resetFeedback() {
        getProperties().put(FeedbackSignals.HOLD_RESET, Boolean.TRUE);
    }

    // ==================== Hold Duration ====================

    private final ObjectProperty<Duration> holdDuration =
            new SimpleObjectProperty<>(this, "holdDuration", DEFAULT_HOLD_DURATION);

    /**
     * How long the button must be held before it fires. {@code null}, zero,
     * negative, unknown and indefinite values fall back to
     * {@link #DEFAULT_HOLD_DURATION}. A change made during a hold applies from
     * the next hold. Not settable from CSS: the duration is part of the
     * action's contract, not of its look.
     *
     * @return the hold duration property
     */
    public final ObjectProperty<Duration> holdDurationProperty() {
        return holdDuration;
    }

    /**
     * Returns the hold duration.
     *
     * @return the hold duration, or {@code null}
     */
    public final Duration getHoldDuration() {
        return holdDuration.get();
    }

    /**
     * Sets the hold duration.
     *
     * @param value the hold duration
     */
    public final void setHoldDuration(Duration value) {
        holdDuration.set(value);
    }

    // ==================== Holding Text ====================

    private final StringProperty holdingText =
            new SimpleStringProperty(this, "holdingText", "Keep holding…");

    /**
     * Text of the holding face. Defaults to {@code "Keep holding…"};
     * {@code null} or an empty string shows no text while holding.
     *
     * @return the holding text property
     */
    public final StringProperty holdingTextProperty() {
        return holdingText;
    }

    /**
     * Returns the text of the holding face.
     *
     * @return the holding text, or {@code null}
     */
    public final String getHoldingText() {
        return holdingText.get();
    }

    /**
     * Sets the text of the holding face.
     *
     * @param value the holding text, or {@code null} for none
     */
    public final void setHoldingText(String value) {
        holdingText.set(value);
    }

    // ==================== Holding Graphic ====================

    private final ObjectProperty<Node> holdingGraphic = new SimpleObjectProperty<>(this, "holdingGraphic");

    /**
     * Graphic of the holding face, placed like the button graphic. The default
     * {@code null} shows none. Must be a different node than the
     * {@code graphic} and the {@link #completedGraphicProperty()
     * completedGraphic}.
     *
     * @return the holding graphic property
     */
    public final ObjectProperty<Node> holdingGraphicProperty() {
        return holdingGraphic;
    }

    /**
     * Returns the graphic of the holding face.
     *
     * @return the holding graphic, or {@code null}
     */
    public final Node getHoldingGraphic() {
        return holdingGraphic.get();
    }

    /**
     * Sets the graphic of the holding face.
     *
     * @param value the holding graphic, or {@code null} for none
     */
    public final void setHoldingGraphic(Node value) {
        holdingGraphic.set(value);
    }

    // ==================== Completed Text ====================

    private final StringProperty completedText =
            new SimpleStringProperty(this, "completedText", "Done");

    /**
     * Text of the completed face. Defaults to {@code "Done"};
     * {@code null} or an empty string shows no text on the completed face.
     *
     * @return the completed text property
     */
    public final StringProperty completedTextProperty() {
        return completedText;
    }

    /**
     * Returns the text of the completed face.
     *
     * @return the completed text, or {@code null}
     */
    public final String getCompletedText() {
        return completedText.get();
    }

    /**
     * Sets the text of the completed face.
     *
     * @param value the completed text, or {@code null} for none
     */
    public final void setCompletedText(String value) {
        completedText.set(value);
    }

    // ==================== Completed Graphic ====================

    private final ObjectProperty<Node> completedGraphic =
            new SimpleObjectProperty<>(this, "completedGraphic");

    /**
     * Graphic of the completed face, placed like the button graphic. The
     * default {@code null} shows none. Must be a different node than the
     * {@code graphic} and the {@link #holdingGraphicProperty()
     * holdingGraphic}.
     *
     * @return the completed graphic property
     */
    public final ObjectProperty<Node> completedGraphicProperty() {
        return completedGraphic;
    }

    /**
     * Returns the graphic of the completed face.
     *
     * @return the completed graphic, or {@code null}
     */
    public final Node getCompletedGraphic() {
        return completedGraphic.get();
    }

    /**
     * Sets the graphic of the completed face.
     *
     * @param value the completed graphic, or {@code null} for none
     */
    public final void setCompletedGraphic(Node value) {
        completedGraphic.set(value);
    }

    // ==================== Feedback Duration ====================

    private final ObjectProperty<Duration> feedbackDuration =
            new StyleableObjectProperty<>(DEFAULT_FEEDBACK_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.FEEDBACK_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXHoldButton.this;
                }

                @Override
                public String getName() {
                    return "feedbackDuration";
                }
            };

    /**
     * How long the completed face stays after a hold completes; no new hold
     * can start meanwhile. The default {@code Duration.INDEFINITE} keeps it
     * until {@link #resetFeedback()}; {@code Duration.ZERO} skips the
     * completed face, so the button can be held again once released, and the
     * {@code completed} pseudo-class lasts only until that release;
     * {@code null}, negative and unknown values fall back to
     * {@link #DEFAULT_FEEDBACK_DURATION}. A change made while the completed
     * face shows applies from the next completion. From CSS:
     * {@code -rx-feedback-duration}.
     *
     * @return the feedback duration property
     */
    public final ObjectProperty<Duration> feedbackDurationProperty() {
        return feedbackDuration;
    }

    /**
     * Returns the feedback duration.
     *
     * @return the feedback duration, or {@code null}
     */
    public final Duration getFeedbackDuration() {
        return feedbackDuration.get();
    }

    /**
     * Sets the feedback duration.
     *
     * @param value the feedback duration
     */
    public final void setFeedbackDuration(Duration value) {
        feedbackDuration.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation");

    /**
     * The animation played when the completed face comes in and when it goes
     * back to the idle face. The default {@code null} swaps with a direct cut,
     * and so do multi-page display animations and animations requiring more
     * than two pages. The idle and holding faces always swap instantly.
     *
     * @return the animation property
     */
    public final ObjectProperty<PageAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation played when the completed face swaps.
     *
     * @return the animation, or {@code null} for a direct cut
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation played when the completed face swaps.
     *
     * @param value the animation, or {@code null} for a direct cut
     */
    public final void setAnimation(PageAnimation value) {
        animation.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXHoldButton.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of one swap to or from the completed face when an
     * {@link #animationProperty() animation} is set. {@code Duration.ZERO}
     * swaps with a direct cut; {@code null}, negative and otherwise unusable
     * values fall back to {@link #DEFAULT_ANIMATION_DURATION}. The swap to the
     * completed face counts toward the
     * {@link #feedbackDurationProperty() feedbackDuration}. From CSS:
     * {@code -rx-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the duration of one swap to or from the completed face.
     *
     * @return the animation duration, or {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the duration of one swap to or from the completed face.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Fill Animation ====================

    private final ObjectProperty<FillAnimation> fillAnimation =
            new StyleableObjectProperty<>(DEFAULT_FILL_ANIMATION) {
                @Override
                public CssMetaData<? extends Styleable, FillAnimation> getCssMetaData() {
                    return StyleableProperties.FILL_ANIMATION;
                }

                @Override
                public Object getBean() {
                    return RXHoldButton.this;
                }

                @Override
                public String getName() {
                    return "fillAnimation";
                }
            };

    /**
     * Geometry of the progress fill: a {@link FillAnimation} preset constant,
     * a parameterized instance or a custom implementation. From CSS,
     * {@code -rx-fill-animation} selects presets by keyword. A {@code null} or
     * unknown-keyword value falls back to {@link #DEFAULT_FILL_ANIMATION}.
     *
     * @return the fill animation property
     */
    public final ObjectProperty<FillAnimation> fillAnimationProperty() {
        return fillAnimation;
    }

    /**
     * Returns the fill animation.
     *
     * @return the fill animation, or {@code null}
     */
    public final FillAnimation getFillAnimation() {
        return fillAnimation.get();
    }

    /**
     * Sets the fill animation.
     *
     * @param value the fill animation, or {@code null} for the default
     */
    public final void setFillAnimation(FillAnimation value) {
        fillAnimation.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXHoldButton, Duration> FEEDBACK_DURATION =
                new CssMetaData<>("-rx-feedback-duration",
                        DurationConverter.getInstance(), DEFAULT_FEEDBACK_DURATION) {
                    @Override
                    public boolean isSettable(RXHoldButton button) {
                        return !button.feedbackDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXHoldButton button) {
                        return (StyleableProperty<Duration>) button.feedbackDurationProperty();
                    }
                };

        private static final CssMetaData<RXHoldButton, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXHoldButton button) {
                        return !button.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXHoldButton button) {
                        return (StyleableProperty<Duration>) button.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXHoldButton, FillAnimation> FILL_ANIMATION =
                new CssMetaData<>("-rx-fill-animation",
                        new KeywordConverter<>(FillAnimation::valueOf), DEFAULT_FILL_ANIMATION) {
                    @Override
                    public boolean isSettable(RXHoldButton button) {
                        return !button.fillAnimation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<FillAnimation> getStyleableProperty(RXHoldButton button) {
                        return (StyleableProperty<FillAnimation>) button.fillAnimationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Labeled.getClassCssMetaData());
            styleables.add(FEEDBACK_DURATION);
            styleables.add(ANIMATION_DURATION);
            styleables.add(FILL_ANIMATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
