package io.github.leewyatt.rxcontrols.internal.color;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * A color sample: the color over a checkerboard, or a diagonal slash for no
 * color. Used for the color view's preview, the preset swatches and the
 * picker's trigger.
 *
 * <p>The swatch's own background belongs to CSS and gives the shape: its first
 * fill's corner radii are copied to the {@code .sample} child, which code
 * paints with the checkerboard and the color. A {@code .slash} child shows
 * while empty, and an {@code .outline} child on top draws the frame from CSS.
 * Pseudo-classes: {@code :empty} for no color, {@code :bright} when a mark
 * drawn on the color must be dark, {@code :translucent} when what shows
 * through decides the contrast, {@code :selected} when its owner marks it.
 * Assistive technologies read the color as hex digits.
 */
public final class ColorSwatch extends Region {

    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass BRIGHT = PseudoClass.getPseudoClass("bright");
    private static final PseudoClass TRANSLUCENT = PseudoClass.getPseudoClass("translucent");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Region sample = new Region();
    private final Region slash = new Region();
    private final Region outline = new Region();

    private Color color;
    private CornerRadii sampleRadii;
    private boolean selected;

    /**
     * Creates an empty swatch.
     *
     * @param styleClass the swatch's style class, such as {@code "preview"}
     */
    public ColorSwatch(String styleClass) {
        getStyleClass().addAll("swatch", styleClass);
        sample.getStyleClass().add("sample");
        slash.getStyleClass().add("slash");
        outline.getStyleClass().add("outline");
        for (Region part : new Region[] {sample, slash, outline}) {
            part.setManaged(false);
            part.setMouseTransparent(true);
        }
        getChildren().addAll(sample, slash, outline);
        applyColor(null);
    }

    /**
     * Returns the color shown.
     *
     * @return the color, or {@code null} for no color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Shows a color.
     *
     * @param value the color, or {@code null} for no color
     */
    public void showColor(Color value) {
        if (value == null ? color == null : value.equals(color)) {
            return;
        }
        applyColor(value);
    }

    /**
     * Returns whether the swatch is marked selected.
     *
     * @return {@code true} if selected
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Marks the swatch selected or not.
     *
     * @param value whether the swatch is selected
     */
    public void markSelected(boolean value) {
        if (selected != value) {
            selected = value;
            pseudoClassStateChanged(SELECTED, value);
            notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
        }
    }

    /**
     * Adds a decoration above the outline, such as a check mark. It is laid out
     * at its preferred size in the center.
     *
     * @param node the decoration
     */
    public void addDecoration(Node node) {
        node.setManaged(false);
        node.setMouseTransparent(true);
        getChildren().add(node);
    }

    private void applyColor(Color value) {
        color = value;
        pseudoClassStateChanged(EMPTY, value == null);
        pseudoClassStateChanged(BRIGHT, value != null && ColorMath.isBright(value));
        pseudoClassStateChanged(TRANSLUCENT, value != null && ColorMath.isTranslucent(value));
        slash.setVisible(value == null);
        paintSample();
        notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }

    private void paintSample() {
        sampleRadii = cssRadii();
        if (color == null) {
            sample.setBackground(null);
            return;
        }
        sample.setBackground(new Background(
                new BackgroundFill(Checkerboard.pattern(), sampleRadii, Insets.EMPTY),
                new BackgroundFill(color, sampleRadii, Insets.EMPTY)));
    }

    private CornerRadii cssRadii() {
        Background background = getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return CornerRadii.EMPTY;
        }
        return background.getFills().get(0).getRadii();
    }

    /** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.TEXT && getAccessibleText() == null) {
            return color == null ? "No color" : "#" + ColorMath.toHex(color, color.getOpacity() < 1.0);
        }
        if (attribute == AccessibleAttribute.SELECTED) {
            return selected;
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    @Override
    protected void layoutChildren() {
        if (!cssRadii().equals(sampleRadii)) {
            paintSample();
        }
        double w = getWidth();
        double h = getHeight();
        for (Node child : getChildren()) {
            if (child == sample || child == slash || child == outline) {
                child.resizeRelocate(0.0, 0.0, w, h);
            } else {
                double cw = child.prefWidth(-1);
                double ch = child.prefHeight(-1);
                child.resizeRelocate((w - cw) / 2.0, (h - ch) / 2.0, cw, ch);
            }
        }
    }
}
