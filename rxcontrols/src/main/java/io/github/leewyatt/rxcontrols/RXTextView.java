package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTextViewSkin;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Orientation;
import javafx.scene.control.Control;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Skin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A non-editable, wrapping block of text the user can select and copy.
 *
 * <p>Unlike {@link javafx.scene.control.Label Label}, the text can be selected with the
 * mouse or keyboard and copied to the clipboard, and the read-only selection state
 * ({@link #selectionProperty() selection}, {@link #selectedTextProperty() selectedText},
 * {@link #anchorProperty() anchor}, {@link #caretPositionProperty() caretPosition}) is
 * observable. The text always wraps to the width it is given, so the control is
 * {@link Orientation#HORIZONTAL} content-biased.
 *
 * <p>{@link #selectableProperty() selectable} (default {@code true}) is the master switch
 * for <em>user</em> interaction and the caret: when {@code false} the control no longer
 * responds to mouse / keyboard selection, hides the caret, and does not grab focus on
 * press. The programmatic selection API ({@link #selectRange(int, int)},
 * {@link #selectAll()}, &hellip;) stays in effect and its selection is still painted, so
 * callers can highlight a range regardless of {@code selectable} — like a non-editable
 * {@code TextField} that can still be selected from code.
 *
 * <p>{@code RXTextView} is the public base of {@link RXHighlightTextView}, which adds
 * keyword highlighting on top of the same selection machinery.
 */
public class RXTextView extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-text-view";

    // ==================== Constructors ====================

    /**
     * Creates an empty selectable-text control.
     */
    public RXTextView() {
        this("");
    }

    /**
     * Creates a selectable-text control with the given text.
     *
     * @param text the text to display; {@code null} is treated as empty
     */
    public RXTextView(String text) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setText(text);
        setFocusTraversable(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTextViewSkin(this);
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
     * <p>Returns {@link Orientation#HORIZONTAL} because the text wraps, so the
     * control's height depends on the width allotted to it.
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text", "") {
        @Override
        protected void invalidated() {
            // text changed via any write path (set / bind / FXML): re-clamp the
            // selection into the new length and refresh the derived selectedText.
            // Without this, binding text to a shorter string (or null) would leave
            // anchor / caret out of range and the next substring() would throw.
            doSelectRange(getAnchor(), getCaretPosition());
        }
    };

    /**
     * The text to display.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the displayed text. May be {@code null} if {@code null} was explicitly set
     * or bound; the control treats {@code null} as empty everywhere internally, so callers
     * generally need not null-check.
     *
     * @return the text, possibly {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the displayed text. {@code null} is accepted and read back as {@code null}
     * (pure pass-through); internally the control treats it as empty.
     *
     * @param value the text to display, or {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Selectable ====================

    private final BooleanProperty selectable = new SimpleBooleanProperty(this, "selectable", true);

    /**
     * Master switch for user interaction and the caret. When {@code false} the control
     * ignores mouse / keyboard selection, hides the caret, and does not grab focus on
     * press; the programmatic selection API still works and its selection is still
     * painted.
     *
     * @return the selectable property
     */
    public final BooleanProperty selectableProperty() {
        return selectable;
    }

    /**
     * Returns whether user selection interaction is enabled.
     *
     * @return {@code true} if the user can select text
     */
    public final boolean isSelectable() {
        return selectable.get();
    }

    /**
     * Sets whether user selection interaction is enabled.
     *
     * @param value {@code true} to allow the user to select text
     */
    public final void setSelectable(boolean value) {
        selectable.set(value);
    }

    // ==================== Text Alignment ====================

    private final ObjectProperty<TextAlignment> textAlignment =
            new StyleableObjectProperty<>(TextAlignment.LEFT) {
                @Override
                public Object getBean() {
                    return RXTextView.this;
                }

                @Override
                public String getName() {
                    return "textAlignment";
                }

                @Override
                public CssMetaData<RXTextView, TextAlignment> getCssMetaData() {
                    return StyleableProperties.TEXT_ALIGNMENT;
                }
            };

    /**
     * The horizontal alignment of each line of text. Styleable via
     * {@code -fx-text-alignment}.
     *
     * @return the text-alignment property
     */
    public final ObjectProperty<TextAlignment> textAlignmentProperty() {
        return textAlignment;
    }

    /**
     * Returns the horizontal text alignment.
     *
     * @return the text alignment
     */
    public final TextAlignment getTextAlignment() {
        return textAlignment.get();
    }

    /**
     * Sets the horizontal text alignment.
     *
     * @param value the text alignment
     */
    public final void setTextAlignment(TextAlignment value) {
        textAlignment.set(value);
    }

    // ==================== Line Spacing ====================

    private final DoubleProperty lineSpacing =
            new StyleableDoubleProperty(0) {
                @Override
                public Object getBean() {
                    return RXTextView.this;
                }

                @Override
                public String getName() {
                    return "lineSpacing";
                }

                @Override
                public CssMetaData<RXTextView, Number> getCssMetaData() {
                    return StyleableProperties.LINE_SPACING;
                }
            };

    /**
     * The vertical spacing between text lines, in pixels. Styleable via
     * {@code -fx-line-spacing}.
     *
     * @return the line-spacing property
     */
    public final DoubleProperty lineSpacingProperty() {
        return lineSpacing;
    }

    /**
     * Returns the vertical spacing between text lines.
     *
     * @return the line spacing, in pixels
     */
    public final double getLineSpacing() {
        return lineSpacing.get();
    }

    /**
     * Sets the vertical spacing between text lines.
     *
     * @param value the line spacing, in pixels
     */
    public final void setLineSpacing(double value) {
        lineSpacing.set(value);
    }

    // ==================== Anchor (read-only) ====================

    private final ReadOnlyIntegerWrapper anchor = new ReadOnlyIntegerWrapper(this, "anchor", 0);

    /**
     * The anchor of the current selection: the end that stays put while the caret end
     * moves. May be greater than {@link #caretPositionProperty() caretPosition} for a
     * backward selection.
     *
     * @return the read-only anchor property
     */
    public final ReadOnlyIntegerProperty anchorProperty() {
        return anchor.getReadOnlyProperty();
    }

    /**
     * Returns the selection anchor.
     *
     * @return the anchor index
     */
    public final int getAnchor() {
        return anchor.get();
    }

    // ==================== Caret Position (read-only) ====================

    private final ReadOnlyIntegerWrapper caretPosition = new ReadOnlyIntegerWrapper(this, "caretPosition", 0);

    /**
     * The current caret position: the moving end of the selection.
     *
     * @return the read-only caret-position property
     */
    public final ReadOnlyIntegerProperty caretPositionProperty() {
        return caretPosition.getReadOnlyProperty();
    }

    /**
     * Returns the caret position.
     *
     * @return the caret index
     */
    public final int getCaretPosition() {
        return caretPosition.get();
    }

    // ==================== Selection (read-only) ====================

    private final ReadOnlyObjectWrapper<IndexRange> selection =
            new ReadOnlyObjectWrapper<>(this, "selection", new IndexRange(0, 0));

    /**
     * The current selection as a normalized range ({@code start <= end}). Empty
     * (zero-length) when there is no selection.
     *
     * @return the read-only selection property
     */
    public final ReadOnlyObjectProperty<IndexRange> selectionProperty() {
        return selection.getReadOnlyProperty();
    }

    /**
     * Returns the current selection range.
     *
     * @return the normalized selection range
     */
    public final IndexRange getSelection() {
        return selection.get();
    }

    // ==================== Selected Text (read-only) ====================

    private final ReadOnlyStringWrapper selectedText = new ReadOnlyStringWrapper(this, "selectedText", "");

    /**
     * The currently selected text; {@code ""} when the selection is empty. Never
     * {@code null}.
     *
     * @return the read-only selected-text property
     */
    public final ReadOnlyStringProperty selectedTextProperty() {
        return selectedText.getReadOnlyProperty();
    }

    /**
     * Returns the selected text.
     *
     * @return the selected text, or {@code ""} if nothing is selected
     */
    public final String getSelectedText() {
        return selectedText.get();
    }

    /**
     * Returns the length of the text, treating {@code null} as empty.
     *
     * @return the number of characters in the text
     */
    public final int getLength() {
        return text.getValueSafe().length();
    }

    // ==================== Selection API ====================

    /**
     * Selects the range between {@code anchor} and {@code caret}. Both indices are
     * clamped into {@code [0, length]}; the order is preserved (a backward selection,
     * {@code anchor > caret}, is allowed) and the normalized {@link #getSelection()}
     * is derived from them.
     *
     * @param anchor the fixed end of the selection
     * @param caret  the moving end of the selection (where the caret sits)
     */
    public void selectRange(int anchor, int caret) {
        doSelectRange(anchor, caret);
    }

    /**
     * Selects the whole text.
     */
    public void selectAll() {
        selectRange(0, getLength());
    }

    /**
     * Clears the selection, collapsing it to the current caret position.
     */
    public void deselect() {
        int caret = getCaretPosition();
        selectRange(caret, caret);
    }

    /**
     * Moves the caret to {@code index} (clamped into {@code [0, length]}) and clears
     * the selection.
     *
     * @param index the new caret position
     */
    public void positionCaret(int index) {
        selectRange(index, index);
    }

    /**
     * Extends the selection so the caret moves to {@code index} (clamped into
     * {@code [0, length]}) while the opposite end stays put.
     *
     * @param index the new caret position
     */
    public void extendSelection(int index) {
        int len = text.getValueSafe().length();
        int target = RXMath.clamp(index, 0, len);
        int dot = getCaretPosition();
        int mark = getAnchor();
        int start = Math.min(dot, mark);
        int end = Math.max(dot, mark);
        if (target < start) {
            selectRange(end, target);
        } else {
            selectRange(start, target);
        }
    }

    /**
     * Copies the selected text to the system clipboard. Does nothing when the
     * selection is empty.
     */
    public void copy() {
        String selected = getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(selected);
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    // ==================== Selection internals ====================

    private void doSelectRange(int anchorPos, int caretPos) {
        int len = text.getValueSafe().length();
        int newAnchor = RXMath.clamp(anchorPos, 0, len);
        int newCaret = RXMath.clamp(caretPos, 0, len);
        anchor.set(newAnchor);
        caretPosition.set(newCaret);
        IndexRange range = IndexRange.normalize(newAnchor, newCaret);
        selection.set(range);
        selectedText.set(text.getValueSafe().substring(range.getStart(), range.getEnd()));
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXTextView, TextAlignment> TEXT_ALIGNMENT =
                new CssMetaData<>("-fx-text-alignment",
                        new EnumConverter<>(TextAlignment.class), TextAlignment.LEFT) {

                    @Override
                    public boolean isSettable(RXTextView node) {
                        return !node.textAlignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<TextAlignment> getStyleableProperty(RXTextView node) {
                        return (StyleableProperty<TextAlignment>) node.textAlignmentProperty();
                    }
                };

        private static final CssMetaData<RXTextView, Number> LINE_SPACING =
                new CssMetaData<>("-fx-line-spacing", SizeConverter.getInstance(), 0) {

                    @Override
                    public boolean isSettable(RXTextView node) {
                        return !node.lineSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTextView node) {
                        return (StyleableProperty<Number>) node.lineSpacingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(TEXT_ALIGNMENT);
            styleables.add(LINE_SPACING);
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
