package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCalendarView;
import io.github.leewyatt.rxcontrols.RXDatePicker;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;

/**
 * Default skin for {@link RXDatePicker}: one text field and a popup
 * {@link RXCalendarView} with an optional Today button.
 */
public class RXDatePickerSkin extends RXPickerSkinBase<LocalDate, RXDatePicker> {

    private static final int FIELD_COLUMNS = 10;

    private final TextField editor = new TextField();
    private final RXCalendarView calendar = new RXCalendarView();
    private final Button todayButton;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the date picker
     */
    public RXDatePickerSkin(RXDatePicker control) {
        super(control);
        editor.setPrefColumnCount(FIELD_COLUMNS);
        disposer.registerBinding(editor.promptTextProperty(), control.promptTextProperty());
        disposer.registerBinding(calendar.localeProperty(), control.localeProperty());
        disposer.registerBinding(calendar.clockProperty(), control.clockProperty());
        disposer.registerBinding(calendar.minDateProperty(), control.minDateProperty());
        disposer.registerBinding(calendar.maxDateProperty(), control.maxDateProperty());
        disposer.registerBinding(calendar.dateFilterProperty(), control.dateFilterProperty());
        disposer.registerBinding(calendar.weekFieldsProperty(), control.weekFieldsProperty());
        disposer.registerBinding(calendar.showWeekNumbersProperty(), control.showWeekNumbersProperty());
        disposer.registerBinding(calendar.showAdjacentDaysProperty(), control.showAdjacentDaysProperty());

        todayButton = footerButton("today-button", control.todayTextProperty(), this::commitToday);
        install(List.of(editor), calendar, todayButton);

        disposer.registerListener(control.converterProperty(), this::onFormatChanged);
        disposer.registerListener(control.localeProperty(), this::onFormatChanged);
        disposer.registerListener(control.minDateProperty(), this::onConstraintsChanged);
        disposer.registerListener(control.maxDateProperty(), this::onConstraintsChanged);
        disposer.registerListener(control.dateFilterProperty(), this::onConstraintsChanged);
        disposer.registerListener(control.clockProperty(), this::updateTodayButton);
        disposer.registerListener(control.showTodayProperty(), this::updateTodayButton);
        updateTodayButton();
        updateInvalid();
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        RXDatePicker control = getSkinnable();
        calendar.setValue(control.getValue());
        // A null month re-resolves to the value's (or today's) month and resets any
        // drill-up level left from the previous session.
        calendar.setDisplayedMonth(null);
        updateTodayButton();
    }

    @Override
    protected void writeFieldsFromValue() {
        setFieldText(editor, DateTimeTexts.format(converter(), getSkinnable().getValue()));
    }

    @Override
    protected ParseResult<LocalDate> parseFields() {
        return DateTimeTexts.parse(converter(), editor.getText());
    }

    @Override
    protected boolean isAllowed(LocalDate value) {
        RXDatePicker control = getSkinnable();
        return TemporalRules.isSelectable(value, control.getMinDate(), control.getMaxDate(), control.getDateFilter());
    }

    @Override
    protected void onViewAction() {
        commit(calendar.getValue());
    }

    @Override
    protected void showTypedDraft(LocalDate value) {
        calendar.setValue(value);
    }

    // ==================== Internals ====================

    private StringConverter<LocalDate> converter() {
        RXDatePicker control = getSkinnable();
        StringConverter<LocalDate> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.dateConverter(control.getLocale());
    }

    private void onFormatChanged() {
        if (!anyUserEdited()) {
            refreshFields();
        }
    }

    private void onConstraintsChanged() {
        updateInvalid();
        updateTodayButton();
    }

    private LocalDate today() {
        return LocalDate.now(DateTimeTexts.clock(getSkinnable().getClock()));
    }

    private void updateTodayButton() {
        RXDatePicker control = getSkinnable();
        setFooterShown(control.isShowToday());
        todayButton.setDisable(!isAllowed(today()));
    }

    private void commitToday() {
        LocalDate today = today();
        if (isAllowed(today)) {
            commit(today);
        }
    }
}
