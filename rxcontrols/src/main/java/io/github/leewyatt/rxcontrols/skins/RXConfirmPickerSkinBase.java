package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPickerBase;
import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Shared skin of the single-field pickers whose popup edits a draft that must be
 * confirmed (time, date-time): the field previews the draft, OK / Enter / the view's
 * action commit it, Now commits the current moment, and closing any other way
 * discards it.
 *
 * @param <T> the value type
 * @param <C> the picker type
 * @param <V> the popup view type
 */
public abstract class RXConfirmPickerSkinBase<T, C extends RXPickerBase<T>, V extends RXTemporalViewBase<T>>
        extends RXPickerSkinBase<T, C> {

    private final TextField editor = new TextField();
    private final V view;
    private Button nowButton;
    private Button okButton;
    private boolean typingDraft;
    private boolean seeding;

    /**
     * Creates the skin. Subclasses bind the view's options and then call
     * {@link #initialize}.
     *
     * @param control      the picker
     * @param view         the popup view
     * @param fieldColumns the preferred column count of the field
     */
    protected RXConfirmPickerSkinBase(C control, V view, int fieldColumns) {
        super(control);
        this.view = view;
        editor.setPrefColumnCount(fieldColumns);
        disposer.registerBinding(view.localeProperty(), control.localeProperty());
        disposer.registerBinding(view.clockProperty(), control.clockProperty());
    }

    /**
     * Installs the field, the view and the Now / OK buttons, and observes the given
     * properties.
     *
     * @param promptText           the picker's prompt text
     * @param showNow              whether the Now button is shown
     * @param nowText              the Now button text
     * @param okText               the OK button text
     * @param formatProperties     properties that change the text format
     * @param constraintProperties properties that change the constraints or the current moment
     */
    protected final void initialize(ObservableValue<String> promptText, ObservableValue<Boolean> showNow,
                                    ObservableValue<String> nowText, ObservableValue<String> okText,
                                    List<Observable> formatProperties, List<Observable> constraintProperties) {
        disposer.registerBinding(editor.promptTextProperty(), promptText);
        nowButton = footerButton("now-button", nowText, this::commitNow);
        nowButton.getStyleClass().add("link-button");
        disposer.registerBinding(nowButton.visibleProperty(), showNow);
        disposer.registerBinding(nowButton.managedProperty(), showNow);
        okButton = footerButton("ok-button", okText, this::confirmFromButton);
        install(List.of(editor), view, nowButton, okButton);

        disposer.registerListener(view.valueProperty(), this::onDraftChanged);
        for (Observable format : formatProperties) {
            disposer.registerListener(format, () -> {
                if (!anyUserEdited()) {
                    refreshFields();
                }
            });
        }
        for (Observable constraint : constraintProperties) {
            disposer.registerListener(constraint, () -> {
                updateInvalid();
                updateButtons();
            });
        }
        updateButtons();
        updateInvalid();
    }

    // ==================== Subclass hooks ====================

    /**
     * Returns the converter in effect.
     *
     * @return the picker's converter, or the default one
     */
    protected abstract StringConverter<T> converter();

    /**
     * Returns the value the Now button commits, already truncated to the steps.
     *
     * @return the current moment as a value
     */
    protected abstract T currentValue();

    /**
     * Returns the popup view.
     *
     * @return the view
     */
    protected final V popupView() {
        return view;
    }

    /**
     * Resets the view's displayed page for a new popup session. The default does nothing.
     */
    protected void resetDisplay() {
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        // Seeding must not preview into the field: it may hold text the user typed
        // while the popup was closed, which then seeds the draft instead.
        seeding = true;
        try {
            view.setValue(getSkinnable().getValue());
            resetDisplay();
        } finally {
            seeding = false;
        }
        if (anyUserEdited()) {
            ParseResult<T> typed = parseFields();
            if (typed.status() == ParseResult.Status.VALID && isAllowed(typed.value())) {
                showTypedDraft(typed.value());
            }
        }
        updateButtons();
    }

    @Override
    protected void writeFieldsFromValue() {
        setFieldText(editor, DateTimeTexts.format(converter(), getSkinnable().getValue()));
    }

    @Override
    protected ParseResult<T> parseFields() {
        return DateTimeTexts.parse(converter(), editor.getText());
    }

    @Override
    protected void onViewAction() {
        commitDraft();
    }

    @Override
    protected void showTypedDraft(T value) {
        typingDraft = true;
        try {
            view.setValue(value);
        } finally {
            typingDraft = false;
        }
    }

    @Override
    protected void confirmDraft() {
        commitDraft();
    }

    // ==================== Internals ====================

    private void onDraftChanged() {
        // Preview the popup draft in the field, but never overwrite what the user types.
        if (!typingDraft && !seeding && getSkinnable().isShowing()) {
            setFieldText(editor, DateTimeTexts.format(converter(), view.getValue()));
        }
        updateButtons();
    }

    private void confirmFromButton() {
        // Typed text wins over the draft: OK behaves like Enter in the field.
        if (anyUserEdited()) {
            commitTypedText();
        } else {
            commitDraft();
        }
    }

    private void commitDraft() {
        T draft = view.getValue();
        if (draft != null && isAllowed(draft)) {
            commit(draft);
        }
    }

    private void commitNow() {
        T now = currentValue();
        if (isAllowed(now)) {
            commit(now);
        }
    }

    private void updateButtons() {
        if (okButton == null) {
            return;
        }
        nowButton.setDisable(!isAllowed(currentValue()));
        T draft = view.getValue();
        okButton.setDisable(draft == null || !isAllowed(draft));
    }
}
