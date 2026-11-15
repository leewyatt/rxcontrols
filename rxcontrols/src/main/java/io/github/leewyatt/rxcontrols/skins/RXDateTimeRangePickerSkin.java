package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateTimeRange;
import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeConstraints;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.MemoizedTimeFilter;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow.Endpoint;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow.Step;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeEndpointTarget;
import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXDateTimeRangePicker}: start and end fields around a
 * separator, and a popup {@link RXDateTimeView} with an OK button. The view edits one
 * endpoint at a time; confirming it moves on to the other endpoint or commits the range.
 */
public class RXDateTimeRangePickerSkin extends RXPickerSkinBase<RXDateTimeRange, RXDateTimeRangePicker> {

    private static final int FIELD_COLUMNS = 13;

    private final TextField startEditor = new TextField();
    private final TextField endEditor = new TextField();
    private final Region separator = new Region();
    private final RXDateTimeView endpointView = new RXDateTimeView();
    private final Button okButton;
    private final RangeConfirmFlow<LocalDateTime> flow = new RangeConfirmFlow<>();
    /** Set while this skin writes the view's value: such a change is no pick of the user's. */
    private boolean writingView;
    /** The control's time filter with its answers remembered; replaced when the filter is. */
    private Predicate<LocalTime> timeFilter;

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
        disposer.registerBinding(startEditor.promptTextProperty(), control.startPromptTextProperty());
        disposer.registerBinding(endEditor.promptTextProperty(), control.endPromptTextProperty());
        disposer.registerBinding(endpointView.localeProperty(), control.localeProperty());
        disposer.registerBinding(endpointView.clockProperty(), control.clockProperty());
        disposer.registerBinding(endpointView.minDateTimeProperty(), control.minDateTimeProperty());
        disposer.registerBinding(endpointView.maxDateTimeProperty(), control.maxDateTimeProperty());
        disposer.registerBinding(endpointView.dateFilterProperty(), control.dateFilterProperty());
        disposer.registerBinding(endpointView.timeFilterProperty(), control.timeFilterProperty());
        disposer.registerBinding(endpointView.weekFieldsProperty(), control.weekFieldsProperty());
        disposer.registerBinding(endpointView.showWeekNumbersProperty(), control.showWeekNumbersProperty());
        disposer.registerBinding(endpointView.showAdjacentDaysProperty(), control.showAdjacentDaysProperty());
        disposer.registerBinding(endpointView.showSecondsProperty(), control.showSecondsProperty());
        disposer.registerBinding(endpointView.twelveHourClockProperty(), control.twelveHourClockProperty());
        disposer.registerBinding(endpointView.hourStepProperty(), control.hourStepProperty());
        disposer.registerBinding(endpointView.minuteStepProperty(), control.minuteStepProperty());
        disposer.registerBinding(endpointView.secondStepProperty(), control.secondStepProperty());
        // Registered before the base filters so they see the key or the press first.
        disposer.registerEventFilter(startEditor, KeyEvent.KEY_PRESSED, this::handleFieldKey);
        disposer.registerEventFilter(endEditor, KeyEvent.KEY_PRESSED, this::handleFieldKey);
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, this::handleFieldPress);

        okButton = footerButton("ok-button", control.okTextProperty(), this::confirmFromButton);
        install(List.of(startEditor, separator, endEditor), endpointView, okButton);

        disposer.registerListener(endpointView.valueProperty(), this::onViewValueChanged);
        disposer.registerListener(endpointView.skinProperty(), this::showOtherEndpoint);
        // After the base's own listener, which runs the popup hooks.
        disposer.registerListener(control.showingProperty(), this::onShowingChanged);
        disposer.registerListener(startEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(endEditor.focusedProperty(), this::updateActiveField);
        for (Observable format : List.of(control.converterProperty(), control.localeProperty(),
                control.showSecondsProperty(), control.twelveHourClockProperty())) {
            disposer.registerListener(format, this::rewriteFields);
        }
        for (Observable rule : List.of(control.minDateTimeProperty(), control.maxDateTimeProperty(),
                control.dateFilterProperty(), control.timeFilterProperty())) {
            disposer.registerListener(rule, () -> {
                updateInvalid();
                updateOkButton();
            });
        }
        updateOkButton();
        updateInvalid();
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        RXDateTimeRange value = getSkinnable().getValue();
        flow.begin(focusedEndpoint(), value == null ? null : value.start(), value == null ? null : value.end());
        showDraft();
        endpointView.setDisplayedMonth(null);
    }

    @Override
    protected void writeFieldsFromValue() {
        RXDateTimeRange value = getSkinnable().getValue();
        writeFields(value == null ? null : value.start(), value == null ? null : value.end());
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
        confirmEndpoint();
    }

    @Override
    protected void confirmDraft() {
        confirmEndpoint();
    }

    @Override
    protected void previewTypedText() {
        if (!flow.isActive()) {
            return;
        }
        for (Endpoint endpoint : Endpoint.values()) {
            TextField editor = editorOf(endpoint);
            if (isUserEdited(editor)) {
                flow.edit(endpoint, typedDraft(endpoint, editor));
            }
        }
        showDraft();
    }

    @Override
    protected void confirmTypedText() {
        if (isBlank(startEditor) && isBlank(endEditor)) {
            commit(null);
        } else if (canConfirm()) {
            confirmEndpoint();
        } else {
            markTextInvalid();
        }
    }

    @Override
    protected void onPopupClosed() {
        if (flow.close()) {
            RXDateTimeRange range = orderedDrafts();
            if (isAllowed(range)) {
                commit(range);
                return;
            }
        }
        super.onPopupClosed();
    }

    @Override
    protected void onFieldFocused(TextField field) {
        if (!getSkinnable().isShowing() || !flow.isActive()) {
            return;
        }
        Endpoint target = field == endEditor ? Endpoint.END : Endpoint.START;
        if (target == flow.current()) {
            return;
        }
        if (flow.switchTo(target)) {
            showEndpoint();
        } else {
            // The endpoint being edited keeps the focus until it is confirmed.
            editorOf(flow.current()).requestFocus();
        }
    }

    // ==================== Endpoints ====================

    private void confirmFromButton() {
        if (anyUserEdited()) {
            confirmTypedText();
        } else {
            confirmEndpoint();
        }
    }

    private void confirmEndpoint() {
        if (!canConfirm()) {
            return;
        }
        writeDraft(flow.current());
        Step step = flow.confirm();
        if (step == Step.COMPLETED) {
            finishRound();
        } else if (step == Step.ADVANCED) {
            showEndpoint();
            editorOf(flow.current()).requestFocus();
        }
    }

    /** Commits the drafts of a completed round; an endpoint that is not acceptable only closes the popup. */
    private void finishRound() {
        RXDateTimeRange range = orderedDrafts();
        if (isAllowed(range)) {
            commit(range);
        } else {
            getSkinnable().hide();
        }
    }

    private boolean canConfirm() {
        if (!flow.isActive()) {
            return false;
        }
        LocalDateTime draft = flow.draft(flow.current());
        return draft != null && constraints().isAcceptable(draft) && !hasUnusableText(editorOf(flow.current()));
    }

    /** Typed text that is no acceptable date-time leaves the draft as it was; that draft must not be confirmed. */
    private boolean hasUnusableText(TextField editor) {
        if (!isUserEdited(editor)) {
            return false;
        }
        ParseResult<LocalDateTime> typed = DateTimeTexts.parse(converter(), editor.getText());
        return typed.status() != ParseResult.Status.VALID || !constraints().isAcceptable(typed.value());
    }

    /** The draft typed text stands for: none for blank text, the draft as it is for unusable text. */
    private LocalDateTime typedDraft(Endpoint endpoint, TextField editor) {
        ParseResult<LocalDateTime> typed = DateTimeTexts.parse(converter(), editor.getText());
        if (typed.status() == ParseResult.Status.EMPTY) {
            return null;
        }
        if (typed.status() == ParseResult.Status.VALID && constraints().isAcceptable(typed.value())) {
            return typed.value();
        }
        return flow.draft(endpoint);
    }

    private RXDateTimeRange orderedDrafts() {
        return RXDateTimeRange.ofOrdered(flow.draft(Endpoint.START), flow.draft(Endpoint.END));
    }

    /** Moves the view on to the current endpoint, keeping the month on screen. */
    private void showEndpoint() {
        YearMonth shown = endpointView.getDisplayedMonth();
        clearKeyboardCursor();
        showDraft();
        if (shown != null) {
            endpointView.setDisplayedMonth(shown);
        }
    }

    /** Shows the current endpoint's draft in the view; the view reveals its day. */
    private void showDraft() {
        writingView = true;
        try {
            endpointView.setValue(flow.draft(flow.current()));
        } finally {
            writingView = false;
        }
        showOtherEndpoint();
        updateActiveField();
        updateOkButton();
    }

    private void showOtherEndpoint() {
        Skin<?> skin = endpointView.getSkin();
        if (skin instanceof RangeEndpointTarget) {
            LocalDateTime other = flow.isActive() ? flow.draft(flow.current().other()) : null;
            ((RangeEndpointTarget) skin).setOtherEndpoint(other == null ? null : other.toLocalDate());
        }
    }

    private void onViewValueChanged() {
        // Read before any return: a value left unread would mute the next change.
        LocalDateTime picked = endpointView.getValue();
        if (writingView || !flow.isActive()) {
            return;
        }
        flow.edit(flow.current(), picked);
        writeDraft(flow.current());
        updateOkButton();
    }

    private void onShowingChanged() {
        if (!getSkinnable().isShowing()) {
            flow.reset();
        }
        updateActiveField();
    }

    private Endpoint focusedEndpoint() {
        Scene scene = getSkinnable().getScene();
        return scene != null && scene.getFocusOwner() == endEditor ? Endpoint.END : Endpoint.START;
    }

    private TextField editorOf(Endpoint endpoint) {
        return endpoint == Endpoint.END ? endEditor : startEditor;
    }

    // ==================== Fields ====================

    private void handleFieldKey(KeyEvent event) {
        boolean chord = event.isControlDown() || event.isAltDown() || event.isMetaDown();
        KeyCode code = event.getCode();
        if (getSkinnable().isShowing()) {
            if (code == KeyCode.ESCAPE && !chord && !event.isShiftDown()) {
                // The base then restores the fields and closes the popup.
                flow.reset();
            } else if (code == KeyCode.TAB && !chord && canConfirm()) {
                // Tab confirms the endpoint it leaves; the focus then moves on by itself.
                writeDraft(flow.current());
                if (flow.confirmWeakly()) {
                    finishRound();
                }
            }
            return;
        }
        if (code == KeyCode.ENTER && !chord && !event.isShiftDown() && event.getSource() == startEditor) {
            // A start typed alone is carried on to the end field instead of being rejected.
            ParseResult<LocalDateTime> start = DateTimeTexts.parse(converter(), startEditor.getText());
            if (isBlank(endEditor) && start.status() == ParseResult.Status.VALID
                    && constraints().isAcceptable(start.value())) {
                endEditor.requestFocus();
                event.consume();
            }
        }
    }

    /** The popup opens on this press, before the pressed field takes the focus on its own. */
    private void handleFieldPress(MouseEvent event) {
        RXDateTimeRangePicker control = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY || control.isDisabled() || control.isShowing()) {
            return;
        }
        for (Node node = event.getTarget() instanceof Node ? (Node) event.getTarget() : null; node != null;
                node = node.getParent()) {
            if (node == startEditor || node == endEditor) {
                node.requestFocus();
                return;
            }
        }
    }

    private void rewriteFields() {
        if (anyUserEdited()) {
            return;
        }
        if (getSkinnable().isShowing() && flow.isActive()) {
            writeFields(flow.draft(Endpoint.START), flow.draft(Endpoint.END));
        } else {
            refreshFields();
        }
    }

    private void writeFields(LocalDateTime start, LocalDateTime end) {
        setFieldText(startEditor, start == null ? "" : DateTimeTexts.format(converter(), start));
        setFieldText(endEditor, end == null ? "" : DateTimeTexts.format(converter(), end));
    }

    private void writeDraft(Endpoint endpoint) {
        LocalDateTime draft = flow.draft(endpoint);
        setFieldText(editorOf(endpoint), draft == null ? "" : DateTimeTexts.format(converter(), draft));
    }

    private void updateActiveField() {
        // Read before deciding: a focus state left unread would mute the next change.
        boolean startFocused = startEditor.isFocused();
        boolean endFocused = endEditor.isFocused();
        boolean stepping = getSkinnable().isShowing() && flow.isActive();
        setFieldActive(startEditor, stepping ? flow.current() == Endpoint.START : startFocused);
        setFieldActive(endEditor, stepping ? flow.current() == Endpoint.END : endFocused);
    }

    private void updateOkButton() {
        okButton.setDisable(!canConfirm());
    }

    private static boolean isBlank(TextField field) {
        String text = field.getText();
        return text == null || text.isBlank();
    }

    private StringConverter<LocalDateTime> converter() {
        RXDateTimeRangePicker control = getSkinnable();
        StringConverter<LocalDateTime> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.dateTimeConverter(control.getLocale(),
                control.isShowSeconds(), control.isTwelveHourClock());
    }

    private DateTimeConstraints constraints() {
        RXDateTimeRangePicker control = getSkinnable();
        timeFilter = MemoizedTimeFilter.of(control.getTimeFilter(), timeFilter);
        return new DateTimeConstraints(control.getMinDateTime(), control.getMaxDateTime(), control.getDateFilter(),
                timeFilter, control.getHourStep(), control.getMinuteStep(), control.getSecondStep(),
                control.isShowSeconds());
    }
}
