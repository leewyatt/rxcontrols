package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateTimeRange;
import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeConstraints;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeAnchorSource;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Default skin for {@link RXDateTimeRangePicker}: start and end fields around a
 * separator, and a popup {@link RXDateTimeRangeView} with an OK button.
 */
public class RXDateTimeRangePickerSkin extends RXPickerSkinBase<RXDateTimeRange, RXDateTimeRangePicker> {

    private static final int FIELD_COLUMNS = 13;
    private static final int POPUP_MONTHS = 2;

    private final TextField startEditor = new TextField();
    private final TextField endEditor = new TextField();
    private final Region separator = new Region();
    private final RXDateTimeRangeView rangeView = new RXDateTimeRangeView();
    private final Button okButton;
    private final InvalidationListener anchorListener = observable -> onAnchorChanged();
    private ReadOnlyObjectProperty<LocalDate> observedAnchor;
    private boolean typingDraft;
    private boolean seeding;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the date-time range picker
     */
    public RXDateTimeRangePickerSkin(RXDateTimeRangePicker control) {
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
        disposer.registerBinding(rangeView.minDateTimeProperty(), control.minDateTimeProperty());
        disposer.registerBinding(rangeView.maxDateTimeProperty(), control.maxDateTimeProperty());
        disposer.registerBinding(rangeView.dateFilterProperty(), control.dateFilterProperty());
        disposer.registerBinding(rangeView.timeFilterProperty(), control.timeFilterProperty());
        disposer.registerBinding(rangeView.weekFieldsProperty(), control.weekFieldsProperty());
        disposer.registerBinding(rangeView.showWeekNumbersProperty(), control.showWeekNumbersProperty());
        disposer.registerBinding(rangeView.showAdjacentDaysProperty(), control.showAdjacentDaysProperty());
        disposer.registerBinding(rangeView.showSecondsProperty(), control.showSecondsProperty());
        disposer.registerBinding(rangeView.twelveHourClockProperty(), control.twelveHourClockProperty());
        disposer.registerBinding(rangeView.hourStepProperty(), control.hourStepProperty());
        disposer.registerBinding(rangeView.minuteStepProperty(), control.minuteStepProperty());
        disposer.registerBinding(rangeView.secondStepProperty(), control.secondStepProperty());
        disposer.registerBinding(rangeView.startTimeTextProperty(), control.startTimeTextProperty());
        disposer.registerBinding(rangeView.endTimeTextProperty(), control.endTimeTextProperty());
        // Registered before the base filters so it sees Enter first.
        disposer.registerEventFilter(startEditor, KeyEvent.KEY_PRESSED, this::handleStartEnter);

        okButton = footerButton("ok-button", control.okTextProperty(), this::confirmFromButton);
        install(List.of(startEditor, separator, endEditor), rangeView, okButton);

        disposer.registerListener(rangeView.valueProperty(), this::onDraftChanged);
        disposer.registerListener(startEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(endEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(rangeView.skinProperty(), this::observeAnchor);
        disposer.registerDisposeTask(this::unobserveAnchor);
        for (Observable format : List.of(control.converterProperty(), control.localeProperty(),
                control.showSecondsProperty(), control.twelveHourClockProperty())) {
            disposer.registerListener(format, () -> {
                if (!anyUserEdited()) {
                    refreshFields();
                }
            });
        }
        for (Observable rule : List.of(control.minDateTimeProperty(), control.maxDateTimeProperty(),
                control.dateFilterProperty(), control.timeFilterProperty())) {
            disposer.registerListener(rule, () -> {
                updateInvalid();
                updateOkButton();
            });
        }
        observeAnchor();
        updateOkButton();
        updateInvalid();
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        Skin<?> skin = rangeView.getSkin();
        if (skin instanceof RangeAnchorSource) {
            ((RangeAnchorSource) skin).clearAnchor();
        }
        seeding = true;
        try {
            rangeView.setValue(getSkinnable().getValue());
            rangeView.setDisplayedMonth(null);
        } finally {
            seeding = false;
        }
        if (anyUserEdited()) {
            ParseResult<RXDateTimeRange> typed = parseFields();
            if (typed.status() == ParseResult.Status.VALID && isAllowed(typed.value())) {
                showTypedDraft(typed.value());
            }
        }
        updateOkButton();
    }

    @Override
    protected void writeFieldsFromValue() {
        writeFields(getSkinnable().getValue());
    }

    @Override
    protected ParseResult<RXDateTimeRange> parseFields() {
        ParseResult<LocalDateTime> start = DateTimeTexts.parse(converter(), startEditor.getText());
        ParseResult<LocalDateTime> end = DateTimeTexts.parse(converter(), endEditor.getText());
        if (start.status() == ParseResult.Status.EMPTY && end.status() == ParseResult.Status.EMPTY) {
            return ParseResult.empty();
        }
        if (start.status() != ParseResult.Status.VALID || end.status() != ParseResult.Status.VALID) {
            return ParseResult.invalid();
        }
        return ParseResult.valid(RXDateTimeRange.ofOrdered(start.value(), end.value()));
    }

    @Override
    protected boolean isAllowed(RXDateTimeRange value) {
        DateTimeConstraints rules = constraints();
        return rules.isAcceptable(value.start()) && rules.isAcceptable(value.end());
    }

    @Override
    protected void onViewAction() {
        commitDraft();
    }

    @Override
    protected void showTypedDraft(RXDateTimeRange value) {
        typingDraft = true;
        try {
            rangeView.setValue(value);
        } finally {
            typingDraft = false;
        }
    }

    @Override
    protected void confirmDraft() {
        commitDraft();
    }

    // ==================== Internals ====================

    private void writeFields(RXDateTimeRange range) {
        setFieldText(startEditor, range == null ? "" : DateTimeTexts.format(converter(), range.start()));
        setFieldText(endEditor, range == null ? "" : DateTimeTexts.format(converter(), range.end()));
    }

    private void onDraftChanged() {
        if (!typingDraft && !seeding && getSkinnable().isShowing() && rangeView.getValue() != null) {
            writeFields(rangeView.getValue());
        }
        updateOkButton();
    }

    private void confirmFromButton() {
        if (anyUserEdited()) {
            commitTypedText();
        } else {
            commitDraft();
        }
    }

    private void commitDraft() {
        RXDateTimeRange draft = rangeView.getValue();
        if (draft != null && isAllowed(draft)) {
            commit(draft);
        }
    }

    private void updateOkButton() {
        RXDateTimeRange draft = rangeView.getValue();
        okButton.setDisable(draft == null || !isAllowed(draft));
    }

    private void handleStartEnter(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown() || isViewKeyboardCursorVisible()) {
            return;
        }
        ParseResult<LocalDateTime> start = DateTimeTexts.parse(converter(), startEditor.getText());
        boolean endBlank = endEditor.getText() == null || endEditor.getText().isBlank();
        if (endBlank && start.status() == ParseResult.Status.VALID && constraints().isAcceptable(start.value())) {
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
        if (observedAnchor != null && observedAnchor.get() != null && getSkinnable().isShowing()) {
            endEditor.requestFocus();
        }
        updateOkButton();
    }

    private void updateActiveField() {
        setFieldActive(startEditor, startEditor.isFocused());
        setFieldActive(endEditor, endEditor.isFocused());
    }

    private StringConverter<LocalDateTime> converter() {
        RXDateTimeRangePicker control = getSkinnable();
        StringConverter<LocalDateTime> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.dateTimeConverter(control.getLocale(),
                control.isShowSeconds(), control.isTwelveHourClock());
    }

    private DateTimeConstraints constraints() {
        RXDateTimeRangePicker control = getSkinnable();
        return new DateTimeConstraints(control.getMinDateTime(), control.getMaxDateTime(), control.getDateFilter(),
                control.getTimeFilter(), control.getHourStep(), control.getMinuteStep(), control.getSecondStep(),
                control.isShowSeconds());
    }
}
