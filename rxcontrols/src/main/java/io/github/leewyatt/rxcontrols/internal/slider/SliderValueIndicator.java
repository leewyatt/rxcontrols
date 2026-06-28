package io.github.leewyatt.rxcontrols.internal.slider;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * In-skin value indicator bubble: a rounded body ({@code value-indicator}
 * style class, background and padding from CSS) holding a centered text
 * {@link Label}, plus a small {@code caret} {@link Region} that points at the
 * thumb. The caret is an unmanaged child positioned just outside the body edge,
 * so it never enlarges the body and is free to overflow.
 *
 * <p>The node is unmanaged and mouse-transparent; the slider skin sizes and
 * places it each layout pass and drives its show / hide transition. It never
 * installs a clip, so it may overflow the slider bounds.</p>
 */
public final class SliderValueIndicator extends StackPane {

    private static final String STYLE_CLASS = "value-indicator";

    private final Label label = new Label();
    private final Region caret = new Region();

    private boolean caretBelow = true;

    /**
     * Creates an unmanaged, mouse-transparent value indicator.
     */
    public SliderValueIndicator() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);

        caret.getStyleClass().add("caret");
        caret.setMouseTransparent(true);
        caret.setManaged(false);
        caret.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        getChildren().setAll(label, caret);
    }

    /**
     * Sets the indicator text.
     *
     * @param text the formatted value text
     */
    public void setText(String text) {
        label.setText(text);
    }

    /**
     * Selects the caret side. {@code true} places the caret below the body
     * pointing down (the bubble sits above the track); {@code false} places it
     * above the body pointing up (the bubble sits below the track).
     *
     * @param below whether the caret is below the body
     */
    public void setCaretBelow(boolean below) {
        if (caretBelow != below) {
            caretBelow = below;
            requestLayout();
        }
    }

    /**
     * Shows or hides the caret. A vertical slider places the bubble beside the
     * thumb, where a down / up caret would not point at it, so it hides the caret.
     *
     * @param visible whether the caret is shown
     */
    public void setCaretVisible(boolean visible) {
        caret.setVisible(visible);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        double caretW = snapSizeX(caret.prefWidth(-1));
        double caretH = snapSizeY(caret.prefHeight(-1));
        double caretX = snapPositionX((getWidth() - caretW) / 2.0);
        double caretY = caretBelow ? snapPositionY(getHeight()) : snapPositionY(-caretH);
        caret.resizeRelocate(caretX, caretY, caretW, caretH);
        caret.setRotate(caretBelow ? 0.0 : 180.0);
    }
}
