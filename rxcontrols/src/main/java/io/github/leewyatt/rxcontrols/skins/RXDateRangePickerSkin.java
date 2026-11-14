package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import io.github.leewyatt.rxcontrols.RXDateRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeAnchorSource;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;

/**
 * Default skin for {@link RXDateRangePicker}: start and end fields around a separator,
 * and a popup {@link RXDateRangeView}.
 */
public class RXDateRangePickerSkin extends RXPickerSkinBase<RXDateRange, RXDateRangePicker> {

    private static final int FIELD_COLUMNS = 8;
    private static final int POPUP_MONTHS = 2;

    private final TextField startEditor = new TextField();
    private final TextField endEditor = new TextField();
    private final Region separator = new Region();
    private final RXDateRangeView rangeView = new RXDateRangeView();
    private final InvalidationListener anchorListener = observable -> onAnchorChanged();
    private ReadOnlyObjectProperty<LocalDate> observedAnchor;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the range picker
     */
    public RXDateRangePickerSkin(RXDateRangePicker control) {
        super(control);
        startEditor.setPrefColumnCount(FIELD_COLUMNS);
        endEditor.setPrefColumnCount(FIELD_COLUMNS);
        separator.getStyleClass().add("separator");
        separator.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        separator.setMouseTransparent(true);
        rangeView.setMonthCount(POPUP_MONTHS);
        disposer.registerBinding(startEditor.promptTextProperty(), control.startPromptTextProperty());
        disposer.registerBinding(endEditor.promptTextProperty(), control.endPromptTextProperty());
        disposer.registerBinding(rangeView.localeProperty(), control.localeProperty());
        disposer.registerBinding(rangeView.clockProperty(), control.clockProperty());
        disposer.registerBinding(rangeView.minDateProperty(), control.minDateProperty());
        disposer.registerBinding(rangeView.maxDateProperty(), control.maxDateProperty());
        disposer.registerBinding(rangeView.dateFilterProperty(), control.dateFilterProperty());
        disposer.registerBinding(rangeView.weekFieldsProperty(), control.weekFieldsProperty());
        disposer.registerBinding(rangeView.showWeekNumbersProperty(), control.showWeekNumbersProperty());
        disposer.registerBinding(rangeView.showAdjacentDaysProperty(), control.showAdjacentDaysProperty());
        // Registered before the base filters so it sees Enter first.
        disposer.registerEventFilter(startEditor, KeyEvent.KEY_PRESSED, this::handleStartEnter);

        install(List.of(startEditor, separator, endEditor), rangeView);

        disposer.registerListener(startEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(endEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(rangeView.skinProperty(), this::observeAnchor);
        disposer.registerDisposeTask(this::unobserveAnchor);
        disposer.registerListener(control.converterProperty(), this::onFormatChanged);
        disposer.registerListener(control.localeProperty(), this::onFormatChanged);
        disposer.registerListener(control.minDateProperty(), this::updateInvalid);
        disposer.registerListener(control.maxDateProperty(), this::updateInvalid);
        disposer.registerListener(control.dateFilterProperty(), this::updateInvalid);
        observeAnchor();
        updateInvalid();
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        // A half-picked range from the previous session never survives a reopen, even
        // when the value itself is unchanged.
        Skin<?> skin = rangeView.getSkin();
        if (skin instanceof RangeAnchorSource) {
            ((RangeAnchorSource) skin).clearAnchor();
        }
        rangeView.setValue(getSkinnable().getValue());
        rangeView.setDisplayedMonth(null);
    }

    @Override
    protected void writeFieldsFromValue() {
        RXDateRange value = getSkinnable().getValue();
        setFieldText(startEditor, value == null ? "" : DateTimeTexts.format(converter(), value.start()));
        setFieldText(endEditor, value == null ? "" : DateTimeTexts.format(converter(), value.end()));
    }

    @Override
    protected ParseResult<RXDateRange> parseFields() {
        ParseResult<LocalDate> start = DateTimeTexts.parse(converter(), startEditor.getText());
        ParseResult<LocalDate> end = DateTimeTexts.parse(converter(), endEditor.getText());
        if (start.status() == ParseResult.Status.EMPTY && end.status() == ParseResult.Status.EMPTY) {
            return ParseResult.empty();
        }
        if (start.status() != ParseResult.Status.VALID || end.status() != ParseResult.Status.VALID) {
            return ParseResult.invalid();
        }
        return ParseResult.valid(RXDateRange.ofOrdered(start.value(), end.value()));
    }

    @Override
    protected boolean isAllowed(RXDateRange value) {
        return isSelectable(value.start()) && isSelectable(value.end());
    }

    @Override
    protected void onViewAction() {
        commit(rangeView.getValue());
    }

    @Override
    protected void showTypedDraft(RXDateRange value) {
        rangeView.setValue(value);
    }

    // ==================== Internals ====================

    private void handleStartEnter(KeyEvent event) {
        // While the calendar shows its keyboard cursor, Enter picks the cursor day.
        if (event.getCode() != KeyCode.ENTER || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown() || isViewKeyboardCursorVisible()) {
            return;
        }
        ParseResult<LocalDate> start = DateTimeTexts.parse(converter(), startEditor.getText());
        boolean endBlank = endEditor.getText() == null || endEditor.getText().isBlank();
        if (endBlank && start.status() == ParseResult.Status.VALID && isSelectable(start.value())) {
            endEditor.requestFocus();
            event.consume();
        }
    }

    private void observeAnchor() {
        unobserveAnchor();
        Skin<?> skin = rangeView.getSkin();
        if (skin instanceof RangeAnchorSource) {
            observedAnchor = ((RangeAnchorSource) skin).anchorProperty();
            observedAnchor.addListener(anchorListener);
        }
    }

    private void unobserveAnchor() {
        if (observedAnchor != null) {
            observedAnchor.removeListener(anchorListener);
            observedAnchor = null;
        }
    }

    private void onAnchorChanged() {
        LocalDate anchor = observedAnchor == null ? null : observedAnchor.get();
        if (anchor != null && getSkinnable().isShowing()) {
            // Preview the half-picked range and hand the end over to the end field.
            setFieldText(startEditor, DateTimeTexts.format(converter(), anchor));
            setFieldText(endEditor, "");
            endEditor.requestFocus();
        }
    }

    private void updateActiveField() {
        setFieldActive(startEditor, startEditor.isFocused());
        setFieldActive(endEditor, endEditor.isFocused());
    }

    private StringConverter<LocalDate> converter() {
        RXDateRangePicker control = getSkinnable();
        StringConverter<LocalDate> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.dateConverter(control.getLocale());
    }

    private void onFormatChanged() {
        if (!anyUserEdited()) {
            refreshFields();
        }
    }

    private boolean isSelectable(LocalDate date) {
        RXDateRangePicker control = getSkinnable();
        return TemporalRules.isSelectable(date, control.getMinDate(), control.getMaxDate(), control.getDateFilter());
    }
}
