package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXWeekPicker;
import io.github.leewyatt.rxcontrols.RXWeekView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;

/**
 * Default skin for {@link RXWeekPicker}: one text field and a popup {@link RXWeekView}.
 */
public class RXWeekPickerSkin extends RXPickerSkinBase<LocalDate, RXWeekPicker> {

    private static final int FIELD_COLUMNS = 10;

    private final TextField editor = new TextField();
    private final RXWeekView weekView = new RXWeekView();

    /**
     * Creates the skin for the given picker.
     *
     * @param control the week picker
     */
    public RXWeekPickerSkin(RXWeekPicker control) {
        super(control);
        editor.setPrefColumnCount(FIELD_COLUMNS);
        disposer.registerBinding(editor.promptTextProperty(), control.promptTextProperty());
        disposer.registerBinding(weekView.localeProperty(), control.localeProperty());
        disposer.registerBinding(weekView.clockProperty(), control.clockProperty());
        disposer.registerBinding(weekView.minWeekProperty(), control.minWeekProperty());
        disposer.registerBinding(weekView.maxWeekProperty(), control.maxWeekProperty());
        disposer.registerBinding(weekView.weekFilterProperty(), control.weekFilterProperty());
        disposer.registerBinding(weekView.weekFieldsProperty(), control.weekFieldsProperty());
        disposer.registerBinding(weekView.showWeekNumbersProperty(), control.showWeekNumbersProperty());

        install(List.of(editor), weekView);

        disposer.registerListener(control.converterProperty(), this::onWeekTextChanged);
        disposer.registerListener(control.localeProperty(), this::onWeekTextChanged);
        disposer.registerListener(control.weekFieldsProperty(), this::onWeekTextChanged);
        disposer.registerListener(control.minWeekProperty(), this::updateInvalid);
        disposer.registerListener(control.maxWeekProperty(), this::updateInvalid);
        disposer.registerListener(control.weekFilterProperty(), this::updateInvalid);
        updateInvalid();
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        weekView.setValue(getSkinnable().getValue());
        // A null month re-resolves to the value's (or today's) month and resets any
        // drill-up level left from the previous session.
        weekView.setDisplayedMonth(null);
    }

    @Override
    protected void writeFieldsFromValue() {
        LocalDate value = getSkinnable().getValue();
        // The value may be any day of its week; the text is that of the week's first day.
        setFieldText(editor, DateTimeTexts.format(converter(),
                value == null ? null : TemporalRules.weekStart(value, rules())));
    }

    @Override
    protected ParseResult<LocalDate> parseFields() {
        ParseResult<LocalDate> parsed = DateTimeTexts.parse(converter(), editor.getText());
        // A custom converter may parse to any day of the week.
        return parsed.status() == ParseResult.Status.VALID
                ? ParseResult.valid(TemporalRules.weekStart(parsed.value(), rules())) : parsed;
    }

    @Override
    protected boolean isAllowed(LocalDate value) {
        RXWeekPicker control = getSkinnable();
        WeekFields rules = rules();
        LocalDate min = control.getMinWeek();
        LocalDate max = control.getMaxWeek();
        return TemporalRules.isSelectable(TemporalRules.weekStart(value, rules),
                min == null ? null : TemporalRules.weekStart(min, rules),
                max == null ? null : TemporalRules.weekStart(max, rules), control.getWeekFilter());
    }

    @Override
    protected void onViewAction() {
        commit(weekView.getValue());
    }

    @Override
    protected void showTypedDraft(LocalDate value) {
        weekView.setValue(value);
    }

    // ==================== Internals ====================

    private StringConverter<LocalDate> converter() {
        StringConverter<LocalDate> converter = getSkinnable().getConverter();
        return converter != null ? converter : DateTimeTexts.weekConverter(rules());
    }

    private WeekFields rules() {
        RXWeekPicker control = getSkinnable();
        return DateTimeTexts.weekFields(control.getWeekFields(), control.getLocale());
    }

    /** The converter, or the rules that number the weeks, changed. */
    private void onWeekTextChanged() {
        if (!anyUserEdited()) {
            refreshFields();
        }
        updateInvalid();
    }
}
