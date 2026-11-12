package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.skins.RXCopyButtonSkin;
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
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Button that copies content to the system clipboard when activated and then
 * shows a short "copied" feedback face.
 *
 * <p>Every {@code ActionEvent} reaching the button is one activation: the
 * button calls the {@link #contentFactoryProperty() contentFactory}, writes
 * the result to the system clipboard and, once the write succeeded, swaps to
 * the feedback face (a check mark, plus {@link #copiedTextProperty()
 * copiedText} when set) and carries the {@code copied} pseudo-class for the
 * {@link #feedbackDurationProperty() feedbackDuration}. Activating again
 * while the feedback shows copies again and restarts the feedback.</p>
 *
 * <pre>{@code
 * RXCopyButton copy = new RXCopyButton("Copy");
 * copy.setCopiedText("Copied");
 * copy.setContentFactory(() -> RXCopyButton.textContent(field.getText()));
 *
 * // Several formats in one copy
 * copy.setContentFactory(() -> {
 *     ClipboardContent content = RXCopyButton.textContent(snippet.getPlainText());
 *     content.putHtml(snippet.getHtml());
 *     return content;
 * });
 *
 * // Clear a stale feedback when the data changes
 * field.textProperty().addListener(observable -> copy.resetFeedback());
 * }</pre>
 *
 * <p>The factory runs on the JavaFX application thread, once per activation,
 * and should stay cheap. A {@code null} factory, a {@code null} result and
 * empty content all mean "nothing to copy": the clipboard is left untouched
 * and any feedback still showing is cleared. An exception thrown by the
 * factory propagates to the caller, and {@code onAction} does not run.</p>
 *
 * <p>The copy happens while the {@code ActionEvent} is dispatched, before
 * handlers added later and before {@code onAction}, so {@code onAction} must
 * not copy again. An event filter that consumes the {@code ActionEvent}
 * vetoes the copy. An {@code ActionEvent} fired at the button by application
 * code copies as well.</p>
 *
 * <p>Each face shows an indicator shape (copy, check) next to its text. The
 * button's graphic appears on the normal face only; the feedback face shows
 * the {@link #copiedGraphicProperty() copiedGraphic}. To hide the
 * indicators, give them a zero preferred size from an author stylesheet;
 * hiding by visibility or opacity keeps their space. Set {@code copiedText}
 * when the indicators are hidden, otherwise the color is the only feedback.</p>
 *
 * <pre>{@code
 * .rx-copy-button .copy-face > .indicator,
 * .rx-copy-button .copied-face > .indicator {
 *     -fx-pref-width: 0;
 *     -fx-pref-height: 0;
 * }
 * }</pre>
 *
 * <p>The indicators are colored from the stylesheet and do not follow
 * {@code textFill}: a button with a custom text or background color should
 * set the {@code -fx-background-color} of both indicators as well.</p>
 *
 * <p>Mnemonics are not supported: {@code mnemonicParsing} starts
 * {@code false} and has no effect. An icon-only button should set
 * {@code accessibleText} (for example "Copy API key"). The button never puts
 * the copied content into a tooltip, an accessible text, a log or an
 * exception message.</p>
 */
public class RXCopyButton extends RXButton {

    private static final String DEFAULT_STYLE_CLASS = "rx-copy-button";

    /**
     * Default {@link #feedbackDurationProperty() feedbackDuration}, and the
     * value used in place of a {@code null}, negative or unknown one.
     */
    public static final Duration DEFAULT_FEEDBACK_DURATION = Duration.millis(2000.0);

    // Registered first in the constructor, so the copy runs before handlers
    // added later and before onAction. The target is deliberately not checked:
    // every ActionEvent that reaches onAction copies.
    private final EventHandler<ActionEvent> copyHandler = event -> copy();

    // ==================== Constructors ====================

    /**
     * Creates a copy button with an empty text caption.
     */
    public RXCopyButton() {
        initialize();
    }

    /**
     * Creates a copy button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXCopyButton(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a copy button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXCopyButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        addEventHandler(ActionEvent.ACTION, copyHandler);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setMnemonicParsing(false);
    }

    /**
     * Creates the default two-face skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXCopyButtonSkin(this);
    }

    // ==================== Copy ====================

    private void copy() {
        if (isDisabled()) {
            return;
        }
        boolean copied = false;
        try {
            Supplier<ClipboardContent> factory = getContentFactory();
            ClipboardContent content = factory == null ? null : factory.get();
            // Never pass null on: Clipboard.setContent(null) clears the clipboard.
            if (content != null && !content.isEmpty()) {
                copied = Clipboard.getSystemClipboard().setContent(content);
            }
        } finally {
            getProperties().put(FeedbackSignals.COPY_RESULT, copied);
        }
    }

    /**
     * Returns clipboard content holding the given text as plain text, or
     * empty content when the text is {@code null} or empty; the button copies
     * nothing for empty content.
     *
     * @param text the text to copy, or {@code null}
     * @return new clipboard content, never {@code null}
     */
    public static ClipboardContent textContent(String text) {
        ClipboardContent content = new ClipboardContent();
        if (text != null && !text.isEmpty()) {
            content.putString(text);
        }
        return content;
    }

    /**
     * Returns to the normal face at once and stops the feedback timer. Does
     * not touch the clipboard, fire an {@code ActionEvent} or call the
     * content factory; works on a disabled button too.
     */
    public final void resetFeedback() {
        getProperties().put(FeedbackSignals.COPY_RESULT, Boolean.FALSE);
    }

    // ==================== Content Factory ====================

    private final ObjectProperty<Supplier<ClipboardContent>> contentFactory =
            new SimpleObjectProperty<>(this, "contentFactory");

    /**
     * Supplies the content to copy, called once per activation on the JavaFX
     * application thread. A {@code null} factory (the default), a
     * {@code null} result and empty content all copy nothing. See
     * {@link #textContent(String)} for the plain-text case.
     *
     * @return the content factory property
     */
    public final ObjectProperty<Supplier<ClipboardContent>> contentFactoryProperty() {
        return contentFactory;
    }

    /**
     * Returns the content factory.
     *
     * @return the content factory, or {@code null}
     */
    public final Supplier<ClipboardContent> getContentFactory() {
        return contentFactory.get();
    }

    /**
     * Sets the content factory.
     *
     * @param value the content factory, or {@code null} to copy nothing
     */
    public final void setContentFactory(Supplier<ClipboardContent> value) {
        contentFactory.set(value);
    }

    // ==================== Copied Text ====================

    private final StringProperty copiedText = new SimpleStringProperty(this, "copiedText");

    /**
     * Text of the feedback face. The default {@code null} reuses the button
     * text, so only the indicator changes; an empty string shows no text.
     *
     * @return the copied text property
     */
    public final StringProperty copiedTextProperty() {
        return copiedText;
    }

    /**
     * Returns the text of the feedback face.
     *
     * @return the copied text, or {@code null} when the button text is reused
     */
    public final String getCopiedText() {
        return copiedText.get();
    }

    /**
     * Sets the text of the feedback face.
     *
     * @param value the copied text, or {@code null} to reuse the button text
     */
    public final void setCopiedText(String value) {
        copiedText.set(value);
    }

    // ==================== Copied Graphic ====================

    private final ObjectProperty<Node> copiedGraphic = new SimpleObjectProperty<>(this, "copiedGraphic");

    /**
     * Graphic of the feedback face, placed like the button graphic. The
     * default {@code null} shows none: the button graphic is not reused,
     * because a node cannot be shown on both faces. Must be a different node
     * than the button graphic.
     *
     * @return the copied graphic property
     */
    public final ObjectProperty<Node> copiedGraphicProperty() {
        return copiedGraphic;
    }

    /**
     * Returns the graphic of the feedback face.
     *
     * @return the copied graphic, or {@code null}
     */
    public final Node getCopiedGraphic() {
        return copiedGraphic.get();
    }

    /**
     * Sets the graphic of the feedback face.
     *
     * @param value the copied graphic, or {@code null} for none
     */
    public final void setCopiedGraphic(Node value) {
        copiedGraphic.set(value);
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
                    return RXCopyButton.this;
                }

                @Override
                public String getName() {
                    return "feedbackDuration";
                }
            };

    /**
     * How long the feedback face stays after a successful copy.
     * {@code Duration.ZERO} copies without showing feedback;
     * {@code Duration.INDEFINITE} keeps the feedback until the next
     * activation or {@link #resetFeedback()}; {@code null}, negative and
     * unknown values fall back to {@link #DEFAULT_FEEDBACK_DURATION}. A change
     * made while the feedback shows applies from the next copy. From CSS:
     * {@code -rx-feedback-duration}; a value from an author stylesheet
     * overrides one set from code.
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

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXCopyButton, Duration> FEEDBACK_DURATION =
                new CssMetaData<>("-rx-feedback-duration",
                        DurationConverter.getInstance(), DEFAULT_FEEDBACK_DURATION) {
                    @Override
                    public boolean isSettable(RXCopyButton button) {
                        return !button.feedbackDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXCopyButton button) {
                        return (StyleableProperty<Duration>) button.feedbackDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXButton.getClassCssMetaData());
            styleables.add(FEEDBACK_DURATION);
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
