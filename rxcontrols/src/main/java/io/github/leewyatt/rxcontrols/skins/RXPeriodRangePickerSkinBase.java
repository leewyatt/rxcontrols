package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPickerBase;
import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeAnchorSource;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared skin of the period range pickers (month, quarter, year): start and end fields
 * around a separator, and a popup period range view.
 *
 * @param <P> the period type
 * @param <R> the range type
 * @param <C> the picker type
 * @param <V> the popup view type
 */
abstract class RXPeriodRangePickerSkinBase<P extends Comparable<? super P>, R, C extends RXPickerBase<R>,
        V extends RXTemporalViewBase<R>> extends RXPickerSkinBase<R, C> {

    private static final int FIELD_COLUMNS = 8;

    private final PeriodRangeType<P, R> type;
    private final TextField startEditor = new TextField();
    private final TextField endEditor = new TextField();
    private final Region separator = new Region();
    private final V view;
    private final InvalidationListener anchorListener = observable -> onAnchorChanged();
    private ReadOnlyObjectProperty<LocalDate> observedAnchor;

    /**
     * Creates the skin. Subclasses bind the view's constraints and then call
     * {@link #initialize}.
     *
     * @param control the picker
     * @param view    the popup view
     * @param type    the kind of range the picker edits
     */
    protected RXPeriodRangePickerSkinBase(C control, V view, PeriodRangeType<P, R> type) {
        super(control);
        this.view = view;
        this.type = type;
        startEditor.setPrefColumnCount(FIELD_COLUMNS);
        endEditor.setPrefColumnCount(FIELD_COLUMNS);
        separator.getStyleClass().add("separator");
        separator.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        separator.setMouseTransparent(true);
        disposer.registerBinding(view.localeProperty(), control.localeProperty());
        disposer.registerBinding(view.clockProperty(), control.clockProperty());
    }

    /**
     * Installs the fields and the view and observes the given properties.
     *
     * @param startPromptText      the picker's start prompt text
     * @param endPromptText        the picker's end prompt text
     * @param formatProperties     properties that change the text format
     * @param constraintProperties properties that change the constraints
     */
    protected final void initialize(ObservableValue<String> startPromptText, ObservableValue<String> endPromptText,
                                    List<Observable> formatProperties, List<Observable> constraintProperties) {
        disposer.registerBinding(startEditor.promptTextProperty(), startPromptText);
        disposer.registerBinding(endEditor.promptTextProperty(), endPromptText);
        // Registered before the base filters so it sees Enter first.
        disposer.registerEventFilter(startEditor, KeyEvent.KEY_PRESSED, this::handleStartEnter);

        install(List.of(startEditor, separator, endEditor), view);

        disposer.registerListener(startEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(endEditor.focusedProperty(), this::updateActiveField);
        disposer.registerListener(view.skinProperty(), this::observeAnchor);
        disposer.registerDisposeTask(this::unobserveAnchor);
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
        observeAnchor();
        updateInvalid();
    }

    // ==================== Picker properties ====================

    /**
     * Returns the converter in effect.
     *
     * @return the picker's converter, or the default one
     */
    protected abstract StringConverter<P> converter();

    /**
     * Returns the picker's lower bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract P minValue();

    /**
     * Returns the picker's upper bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract P maxValue();

    /**
     * Returns the picker's filter.
     *
     * @return the filter, or {@code null}
     */
    protected abstract Predicate<P> filter();

    /**
     * Resets the view's displayed pages to the default for its value.
     */
    protected abstract void resetDisplay();

    /**
     * Returns the popup view.
     *
     * @return the view
     */
    protected final V rangeView() {
        return view;
    }

    // ==================== Picker hooks ====================

    @Override
    protected void seedDraft() {
        // A half-picked range from the previous session never survives a reopen, even
        // when the value itself is unchanged.
        Skin<?> skin = view.getSkin();
        if (skin instanceof RangeAnchorSource) {
            ((RangeAnchorSource) skin).clearAnchor();
        }
        view.setValue(getSkinnable().getValue());
        resetDisplay();
    }

    @Override
    protected void writeFieldsFromValue() {
        R value = getSkinnable().getValue();
        setFieldText(startEditor, value == null ? "" : DateTimeTexts.format(converter(), type.start(value)));
        setFieldText(endEditor, value == null ? "" : DateTimeTexts.format(converter(), type.end(value)));
    }

    @Override
    protected ParseResult<R> parseFields() {
        ParseResult<P> start = DateTimeTexts.parse(converter(), startEditor.getText());
        ParseResult<P> end = DateTimeTexts.parse(converter(), endEditor.getText());
        if (start.status() == ParseResult.Status.EMPTY && end.status() == ParseResult.Status.EMPTY) {
            return ParseResult.empty();
        }
        if (start.status() != ParseResult.Status.VALID || end.status() != ParseResult.Status.VALID) {
            return ParseResult.invalid();
        }
        return ParseResult.valid(type.rangeOf(start.value(), end.value()));
    }

    @Override
    protected boolean isAllowed(R value) {
        return isSelectable(type.start(value)) && isSelectable(type.end(value));
    }

    @Override
    protected void onViewAction() {
        commit(view.getValue());
    }

    @Override
    protected void showTypedDraft(R value) {
        view.setValue(value);
    }

    // ==================== Internals ====================

    private void handleStartEnter(KeyEvent event) {
        // While the view shows its keyboard cursor, Enter picks the cursor period.
        if (event.getCode() != KeyCode.ENTER || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown() || isViewKeyboardCursorVisible()) {
            return;
        }
        ParseResult<P> start = DateTimeTexts.parse(converter(), startEditor.getText());
        boolean endBlank = endEditor.getText() == null || endEditor.getText().isBlank();
        if (endBlank && start.status() == ParseResult.Status.VALID && isSelectable(start.value())) {
            endEditor.requestFocus();
            event.consume();
        }
    }

    private void observeAnchor() {
        unobserveAnchor();
        Skin<?> skin = view.getSkin();
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
            setFieldText(startEditor, DateTimeTexts.format(converter(), type.periodOf(anchor)));
            setFieldText(endEditor, "");
            endEditor.requestFocus();
        }
    }

    private void updateActiveField() {
        setFieldActive(startEditor, startEditor.isFocused());
        setFieldActive(endEditor, endEditor.isFocused());
    }

    private boolean isSelectable(P period) {
        return TemporalRules.isSelectable(period, minValue(), maxValue(), filter());
    }
}
