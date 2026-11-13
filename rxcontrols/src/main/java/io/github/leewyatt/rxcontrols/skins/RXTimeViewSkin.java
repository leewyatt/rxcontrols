package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeColumns;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeConstraints;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.time.LocalTime;

/**
 * Default skin for {@link RXTimeView}: scrolling time columns.
 */
public class RXTimeViewSkin extends RXSkinBase<RXTimeView> implements TemporalKeyTarget {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final TimeColumns columns = new TimeColumns();

    /**
     * Creates the skin for the given time view.
     *
     * @param control the time view
     */
    public RXTimeViewSkin(RXTimeView control) {
        super(control);
        columns.setOnChange(this::onPick);
        columns.setOnCommit(() -> control.fireEvent(new ActionEvent()));
        getChildren().add(columns);
        disposer.registerDisposeTask(() -> getChildren().remove(columns));
        disposer.registerDisposeTask(columns::dispose);

        updateConstraints();
        disposer.registerListener(control.valueProperty(), () -> updateValue(true));
        disposer.registerListener(control.localeProperty(), this::updateConstraints);
        disposer.registerListener(control.showSecondsProperty(), this::updateConstraints);
        disposer.registerListener(control.twelveHourClockProperty(), this::updateConstraints);
        disposer.registerListener(control.hourStepProperty(), this::updateConstraints);
        disposer.registerListener(control.minuteStepProperty(), this::updateConstraints);
        disposer.registerListener(control.secondStepProperty(), this::updateConstraints);
        disposer.registerListener(control.minTimeProperty(), this::updateConstraints);
        disposer.registerListener(control.maxTimeProperty(), this::updateConstraints);
        disposer.registerListener(control.timeFilterProperty(), this::updateConstraints);
        disposer.registerListener(control.focusedProperty(), () -> {
            if (!control.isFocused()) {
                clearKeyboardCursor();
            }
        });
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, event -> {
            if (handleKey(event)) {
                event.consume();
            }
        });
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, event -> {
            if (control.isFocusTraversable() && !control.isFocused()) {
                control.requestFocus();
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleKey(KeyEvent event) {
        return columns.handleKey(event);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyboardCursorVisible() {
        return columns.isKeyboardCursorVisible();
    }

    /** {@inheritDoc} */
    @Override
    public void clearKeyboardCursor() {
        columns.clearKeyboardCursor();
    }

    private void onPick(LocalTime time) {
        getSkinnable().setValue(time);
    }

    private TimeConstraints constraints() {
        RXTimeView control = getSkinnable();
        return new TimeConstraints(control.getMinTime(), control.getMaxTime(), control.getTimeFilter(),
                control.getHourStep(), control.getMinuteStep(), control.getSecondStep(), control.isShowSeconds());
    }

    private void updateConstraints() {
        RXTimeView control = getSkinnable();
        columns.configure(constraints(), control.isTwelveHourClock(), control.getLocale());
        updateValue(false);
    }

    private void updateValue(boolean animate) {
        RXTimeView control = getSkinnable();
        LocalTime value = control.getValue();
        columns.setValue(value, animate);
        control.pseudoClassStateChanged(INVALID, value != null && !constraints().isAcceptable(value));
    }
}
