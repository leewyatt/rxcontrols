package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPickerBase;
import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow.Endpoint;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow.Step;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Shared skin of the range pickers that confirm one endpoint at a time (time range,
 * date-time range): start and end fields around a separator, and a popup view of a
 * single endpoint with an OK button. The view edits one endpoint at a time; confirming
 * it moves on to the other endpoint or commits the range.
 *
 * @param <E> the endpoint type
 * @param <R> the range type
 * @param <C> the picker type
 * @param <V> the popup view type
 */
abstract class RXStepwiseRangePickerSkinBase<E, R, C extends RXPickerBase<R>, V extends RXTemporalViewBase<E>>
        extends RXPickerSkinBase<R, C> {

    private final TextField startEditor = new TextField();
    private final TextField endEditor = new TextField();
    private final Region separator = new Region();
    private final V endpointView;
    private final RangeConfirmFlow<E> flow = new RangeConfirmFlow<>();
    private Button okButton;
    /** Set while this skin writes the view's value: such a change is no pick of the user's. */
    private boolean writingView;

    /**
     * Creates the skin. Subclasses bind the view's options and then call
     * {@link #initialize}.
     *
     * @param control      the picker
     * @param view         the popup view, which edits one endpoint
     * @param fieldColumns the preferred column count of each field
     */
    protected RXStepwiseRangePickerSkinBase(C control, V view, int fieldColumns) {
        super(control);
        this.endpointView = view;
        startEditor.setPrefColumnCount(fieldColumns);
        endEditor.setPrefColumnCount(fieldColumns);
        separator.getStyleClass().add("separator");
        separator.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        separator.setMouseTransparent(true);
        disposer.registerBinding(view.localeProperty(), control.localeProperty());
        disposer.registerBinding(view.clockProperty(), control.clockProperty());
    }

    /**
     * Installs the fields, the view and the OK button, and observes the given
     * properties.
     *
     * @param startPromptText      the picker's start prompt text
     * @param endPromptText        the picker's end prompt text
     * @param okText               the OK button text
     * @param formatProperties     properties that change the text format
     * @param constraintProperties properties that change the constraints
     */
    protected final void initialize(ObservableValue<String> startPromptText, ObservableValue<String> endPromptText,
                                    ObservableValue<String> okText, List<Observable> formatProperties,
                                    List<Observable> constraintProperties) {
        C control = getSkinnable();
        disposer.registerBinding(startEditor.promptTextProperty(), startPromptText);
        disposer.registerBinding(endEditor.promptTextProperty(), endPromptText);
        // Registered before the base filters so they see the key or the press first.
        disposer.registerEventFilter(startEditor, KeyEvent.KEY_PRESSED, this::handleFieldKey);
        disposer.registerEventFilter(endEditor, KeyEvent.KEY_PRESSED, this::handleFieldKey);
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, this::handleFieldPress);

        okButton = footerButton("ok-button", okText, this::confirmFromButton);
        install(List.of(startEditor, separator, endEditor), endpointView, okButton);

        disposer.registerListener(endpointView.valueProperty(), this::onViewValueChanged);
        disposer.registerListener(endpointView.skinProperty(), this::updateOtherEndpoint);
        // After the base's own listener, which runs the popup hooks.
        disposer.registerListener(control.showingProperty(), this::onShowingChanged);
        disposer.registerListener(startEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(endEditor.focusedProperty(), this::updateActiveField);
        for (Observable format : formatProperties) {
            disposer.registerListener(format, this::rewriteFields);
        }
        for (Observable constraint : constraintProperties) {
            disposer.registerListener(constraint, () -> {
                updateInvalid();
                updateOkButton();
            });
        }
        updateOkButton();
        updateInvalid();
    }

    // ==================== Subclass hooks ====================

    /**
     * Returns the converter in effect for an endpoint.
     *
     * @return the picker's converter, or the default one
     */
    protected abstract StringConverter<E> converter();

    /**
     * Returns whether an endpoint satisfies the picker's constraints.
     *
     * @param endpoint the endpoint, never {@code null}
     * @return {@code true} if the endpoint may be committed
     */
    protected abstract boolean isAcceptable(E endpoint);

    /**
     * Creates the range to commit from the endpoints of the start and end fields,
     * which the user may have given in either order.
     *
     * @param first  the start field's endpoint
     * @param second the end field's endpoint
     * @return the range
     */
    protected abstract R rangeOf(E first, E second);

    /**
     * Returns the start of a range.
     *
     * @param range the range
     * @return its start
     */
    protected abstract E startOf(R range);

    /**
     * Returns the end of a range.
     *
     * @param range the range
     * @return its end
     */
    protected abstract E endOf(R range);

    /**
     * Returns the popup view.
     *
     * @return the view
     */
    protected final V popupView() {
        return endpointView;
    }

    /**
     * Resets the view's displayed page for a new popup session. The default does nothing.
     */
    protected void resetDisplay() {
    }

    /**
     * Tells the view about the endpoint it is not editing. Called whenever the view
     * shows a draft and when the view gets a skin. The default does nothing.
     *
     * @param other the other endpoint's draft; {@code null} if it has none or no round is active
     */
    protected void showOtherEndpoint(E other) {
    }

    /**
     * Moves the view on to the endpoint the round switched to.
     */
    protected void showEndpoint() {
        clearKeyboardCursor();
        showDraft();
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        R value = getSkinnable().getValue();
        flow.begin(focusedEndpoint(), value == null ? null : startOf(value), value == null ? null : endOf(value));
        showDraft();
        resetDisplay();
    }

    @Override
    protected void writeFieldsFromValue() {
        R value = getSkinnable().getValue();
        writeFields(value == null ? null : startOf(value), value == null ? null : endOf(value));
    }

    @Override
    protected ParseResult<R> parseFields() {
        ParseResult<E> start = DateTimeTexts.parse(converter(), startEditor.getText());
        ParseResult<E> end = DateTimeTexts.parse(converter(), endEditor.getText());
        if (start.status() == ParseResult.Status.EMPTY && end.status() == ParseResult.Status.EMPTY) {
            return ParseResult.empty();
        }
        if (start.status() != ParseResult.Status.VALID || end.status() != ParseResult.Status.VALID) {
            return ParseResult.invalid();
        }
        return ParseResult.valid(rangeOf(start.value(), end.value()));
    }

    @Override
    protected boolean isAllowed(R value) {
        return isAcceptable(startOf(value)) && isAcceptable(endOf(value));
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
            R range = draftRange();
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
        TextField left = editorOf(flow.current());
        if (hasUnusableText(left)) {
            // Leaving would drop the typed text or leave it beside a draft it does not match.
            markTextInvalid();
            left.requestFocus();
            return;
        }
        writeDraft(flow.current());
        flow.switchTo(target);
        showEndpoint();
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
        R range = draftRange();
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
        E draft = flow.draft(flow.current());
        return draft != null && isAcceptable(draft) && !hasUnusableText(editorOf(flow.current()));
    }

    /**
     * Typed text that is no acceptable endpoint leaves the draft as it was; that draft
     * must not be confirmed. Blank text stands for no draft and is not unusable.
     */
    private boolean hasUnusableText(TextField editor) {
        if (!isUserEdited(editor)) {
            return false;
        }
        ParseResult<E> typed = DateTimeTexts.parse(converter(), editor.getText());
        return typed.status() == ParseResult.Status.INVALID
                || (typed.status() == ParseResult.Status.VALID && !isAcceptable(typed.value()));
    }

    /** The draft typed text stands for: none for blank text, the draft as it is for unusable text. */
    private E typedDraft(Endpoint endpoint, TextField editor) {
        ParseResult<E> typed = DateTimeTexts.parse(converter(), editor.getText());
        if (typed.status() == ParseResult.Status.EMPTY) {
            return null;
        }
        if (typed.status() == ParseResult.Status.VALID && isAcceptable(typed.value())) {
            return typed.value();
        }
        return flow.draft(endpoint);
    }

    private R draftRange() {
        return rangeOf(flow.draft(Endpoint.START), flow.draft(Endpoint.END));
    }

    /** Shows the current endpoint's draft in the view. */
    private void showDraft() {
        writingView = true;
        try {
            endpointView.setValue(flow.draft(flow.current()));
        } finally {
            writingView = false;
        }
        updateOtherEndpoint();
        updateActiveField();
        updateOkButton();
    }

    private void updateOtherEndpoint() {
        showOtherEndpoint(flow.isActive() ? flow.draft(flow.current().other()) : null);
    }

    private void onViewValueChanged() {
        // Read before any return: a value left unread would mute the next change.
        E picked = endpointView.getValue();
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
            ParseResult<E> start = DateTimeTexts.parse(converter(), startEditor.getText());
            if (isBlank(endEditor) && start.status() == ParseResult.Status.VALID && isAcceptable(start.value())) {
                endEditor.requestFocus();
                event.consume();
            }
        }
    }

    /** The popup opens on this press, before the pressed field takes the focus on its own. */
    private void handleFieldPress(MouseEvent event) {
        C control = getSkinnable();
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

    private void writeFields(E start, E end) {
        setFieldText(startEditor, DateTimeTexts.format(converter(), start));
        setFieldText(endEditor, DateTimeTexts.format(converter(), end));
    }

    private void writeDraft(Endpoint endpoint) {
        setFieldText(editorOf(endpoint), DateTimeTexts.format(converter(), flow.draft(endpoint)));
    }

    private void updateActiveField() {
        // Read before deciding: a focus state left unread would mute the next change.
        boolean startFocused = startEditor.isFocused();
        boolean endFocused = endEditor.isFocused();
        boolean stepping = getSkinnable().isShowing() && flow.isActive();
        setFieldActive(startEditor, stepping ? flow.current() == Endpoint.START : startFocused);
        setFieldActive(endEditor, stepping ? flow.current() == Endpoint.END : endFocused);
        if (stepping) {
            // The popup sits under the field of the endpoint it edits.
            alignPopupTo(editorOf(flow.current()));
        }
    }

    private void updateOkButton() {
        okButton.setDisable(!canConfirm());
    }

    private static boolean isBlank(TextField field) {
        String text = field.getText();
        return text == null || text.isBlank();
    }
}
