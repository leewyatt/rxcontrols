package io.github.leewyatt.rxcontrols.internal.chip;

import javafx.scene.control.TextField;

import java.util.function.DoubleSupplier;

/**
 * The inline text editor of an {@code RXChipInput}: a {@link TextField} tamed so it
 * can shrink into the remainder of a chip row instead of demanding its default
 * multi-column minimum width.
 *
 * <p>A plain {@code TextField} reports a minimum width of roughly its preferred
 * column count, which would force the editor to keep a wide slot even when only a
 * sliver of the row remains. This subclass overrides {@link #computeMinWidth(double)}
 * to return a caller-supplied floor (the control's {@code -rx-editor-min-width}) so
 * the chip-flow layout can pack the editor tightly and wrap it to a fresh row only
 * when the remainder drops below that floor.</p>
 */
public final class ChipEditor extends TextField {

    private static final String STYLE_CLASS = "editor";

    private final DoubleSupplier minWidthSupplier;

    /**
     * Creates a chip-input editor.
     *
     * @param minWidthSupplier supplies the minimum usable width in pixels (the
     *                         control's {@code editorMinWidth}); never {@code null}
     */
    public ChipEditor(DoubleSupplier minWidthSupplier) {
        this.minWidthSupplier = minWidthSupplier;
        getStyleClass().add(STYLE_CLASS);
        setPrefColumnCount(1);
    }

    @Override
    protected double computeMinWidth(double height) {
        return Math.max(0, minWidthSupplier.getAsDouble());
    }
}
