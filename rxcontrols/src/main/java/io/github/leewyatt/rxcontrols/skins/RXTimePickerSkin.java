package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTimePicker;
import io.github.leewyatt.rxcontrols.RXTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeConstraints;
import javafx.beans.Observable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.List;

/**
 * Default skin for {@link RXTimePicker}: one text field and a popup
 * {@link RXTimeView} with Now and OK buttons.
 */
public class RXTimePickerSkin extends RXPickerSkinBase<LocalTime, RXTimePicker> {

    private static final int FIELD_COLUMNS = 8;

    private final TextField editor = new TextField();
    private final RXTimeView timeView = new RXTimeView();
    private final Button nowButton;
    private final Button okButton;
    private boolean typingDraft;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the time picker
     */
    public RXTimePickerSkin(RXTimePicker control) {
        super(control);
        editor.setPrefColumnCount(FIELD_COLUMNS);
        disposer.registerBinding(editor.promptTextProperty(), control.promptTextProperty());
        disposer.registerBinding(timeView.localeProperty(), control.localeProperty());
        disposer.registerBinding(timeView.clockProperty(), control.clockProperty());
        disposer.registerBinding(timeView.showSecondsProperty(), control.showSecondsProperty());
        disposer.registerBinding(timeView.twelveHourClockProperty(), control.twelveHourClockProperty());
        disposer.registerBinding(timeView.hourStepProperty(), control.hourStepProperty());
        disposer.registerBinding(timeView.minuteStepProperty(), control.minuteStepProperty());
        disposer.registerBinding(timeView.secondStepProperty(), control.secondStepProperty());
        disposer.registerBinding(timeView.minTimeProperty(), control.minTimeProperty());
        disposer.registerBinding(timeView.maxTimeProperty(), control.maxTimeProperty());
        disposer.registerBinding(timeView.timeFilterProperty(), control.timeFilterProperty());

        nowButton = footerButton("now-button", control.nowTextProperty(), this::commitNow);
        nowButton.getStyleClass().add("link-button");
        okButton = footerButton("ok-button", control.okTextProperty(), this::commitDraft);
        install(List.of(editor), timeView, nowButton, okButton);

        disposer.registerListener(timeView.valueProperty(), this::onDraftChanged);
        for (Observable format : List.of(control.converterProperty(), control.localeProperty(),
                control.showSecondsProperty(), control.twelveHourClockProperty())) {
            disposer.registerListener(format, this::onFormatChanged);
        }
        for (Observable rule : List.of(control.minTimeProperty(), control.maxTimeProperty(),
                control.timeFilterProperty(), control.hourStepProperty(), control.minuteStepProperty(),
                control.secondStepProperty(), control.showSecondsProperty(), control.clockProperty(),
                control.showNowProperty())) {
            disposer.registerListener(rule, this::onConstraintsChanged);
        }
        updateButtons();
        updateInvalid();
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        timeView.setValue(getSkinnable().getValue());
        updateButtons();
    }

    @Override
    protected void writeFieldsFromValue() {
        setFieldText(editor, DateTimeTexts.format(converter(), getSkinnable().getValue()));
    }

    @Override
    protected ParseResult<LocalTime> parseFields() {
        return DateTimeTexts.parse(converter(), editor.getText());
    }

    @Override
    protected boolean isAllowed(LocalTime value) {
        return constraints().isAcceptable(value);
    }

    @Override
    protected void onViewAction() {
        commitDraft();
    }

    @Override
    protected void showTypedDraft(LocalTime value) {
        typingDraft = true;
        try {
            timeView.setValue(value);
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
        if (!typingDraft && getSkinnable().isShowing()) {
            setFieldText(editor, DateTimeTexts.format(converter(), timeView.getValue()));
        }
        updateButtons();
    }

    private void commitDraft() {
        LocalTime draft = timeView.getValue();
        if (draft != null && isAllowed(draft)) {
            commit(draft);
        }
    }

    private void commitNow() {
        LocalTime now = currentTime();
        if (isAllowed(now)) {
            commit(now);
        }
    }

    private LocalTime currentTime() {
        LocalTime now = LocalTime.now(DateTimeTexts.clock(getSkinnable().getClock()));
        return constraints().truncate(now);
    }

    private TimeConstraints constraints() {
        RXTimePicker control = getSkinnable();
        return new TimeConstraints(control.getMinTime(), control.getMaxTime(), control.getTimeFilter(),
                control.getHourStep(), control.getMinuteStep(), control.getSecondStep(), control.isShowSeconds());
    }

    private StringConverter<LocalTime> converter() {
        RXTimePicker control = getSkinnable();
        StringConverter<LocalTime> converter = control.getConverter();
        return converter != null ? converter
                : DateTimeTexts.timeConverter(control.getLocale(), control.isShowSeconds(), control.isTwelveHourClock());
    }

    private void onFormatChanged() {
        if (!anyUserEdited()) {
            refreshFields();
        }
    }

    private void onConstraintsChanged() {
        updateInvalid();
        updateButtons();
    }

    private void updateButtons() {
        RXTimePicker control = getSkinnable();
        nowButton.setVisible(control.isShowNow());
        nowButton.setManaged(control.isShowNow());
        nowButton.setDisable(!isAllowed(currentTime()));
        LocalTime draft = timeView.getValue();
        okButton.setDisable(draft == null || !isAllowed(draft));
    }
}
