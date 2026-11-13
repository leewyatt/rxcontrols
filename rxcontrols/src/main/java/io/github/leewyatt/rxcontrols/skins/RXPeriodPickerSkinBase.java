package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPickerBase;
import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Predicate;

/**
 * Shared skin of the single-period pickers (month, quarter, year): one text field and
 * a popup period view; picking commits at once.
 *
 * @param <T> the period value type
 * @param <C> the picker type
 * @param <V> the popup view type
 */
public abstract class RXPeriodPickerSkinBase<T extends Comparable<? super T>, C extends RXPickerBase<T>,
        V extends RXTemporalViewBase<T>> extends RXPickerSkinBase<T, C> {

    private static final int FIELD_COLUMNS = 8;

    private final TextField editor = new TextField();
    private final V view;

    /**
     * Creates the skin. Subclasses bind the view's constraints and then call
     * {@link #initialize}.
     *
     * @param control the picker
     * @param view    the popup view
     */
    protected RXPeriodPickerSkinBase(C control, V view) {
        super(control);
        this.view = view;
        editor.setPrefColumnCount(FIELD_COLUMNS);
        disposer.registerBinding(view.localeProperty(), control.localeProperty());
        disposer.registerBinding(view.clockProperty(), control.clockProperty());
    }

    /**
     * Installs the field and the view and observes the given properties.
     *
     * @param promptText           the picker's prompt text
     * @param formatProperties     properties that change the text format
     * @param constraintProperties properties that change the constraints
     */
    protected final void initialize(ObservableValue<String> promptText, List<Observable> formatProperties,
                                    List<Observable> constraintProperties) {
        disposer.registerBinding(editor.promptTextProperty(), promptText);
        install(List.of(editor), view);
        for (Observable format : formatProperties) {
            disposer.registerListener(format, () -> {
                if (!anyUserEdited()) {
                    refreshFields();
                }
            });
        }
        for (Observable constraint : constraintProperties) {
            disposer.registerListener(constraint, this::updateInvalid);
        }
        updateInvalid();
    }

    /**
     * Returns the converter in effect.
     *
     * @return the picker's converter, or the default one
     */
    protected abstract StringConverter<T> converter();

    /**
     * Returns the picker's lower bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract T minValue();

    /**
     * Returns the picker's upper bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract T maxValue();

    /**
     * Returns the picker's filter.
     *
     * @return the filter, or {@code null}
     */
    protected abstract Predicate<T> filter();

    /**
     * Resets the view's displayed page to the default for its value.
     */
    protected abstract void resetDisplay();

    /**
     * Returns the popup view.
     *
     * @return the view
     */
    protected final V periodView() {
        return view;
    }

    @Override
    protected void seedDraft() {
        view.setValue(getSkinnable().getValue());
        resetDisplay();
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
    protected boolean isAllowed(T value) {
        return TemporalRules.isSelectable(value, minValue(), maxValue(), filter());
    }

    @Override
    protected void onViewAction() {
        commit(view.getValue());
    }

    @Override
    protected void showTypedDraft(T value) {
        view.setValue(value);
    }
}
