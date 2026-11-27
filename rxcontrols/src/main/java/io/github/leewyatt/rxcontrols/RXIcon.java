package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.css.StyleOrigin;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.scene.layout.Region;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single-color icon in a fixed-size box.
 *
 * <p>The icon is a {@link Region} that fills a shape with its background color,
 * so it follows the theme like any other region. Its minimum and maximum sizes
 * follow its preferred size: a parent short of space does not squeeze it and a
 * parent with spare space does not stretch it. An explicit min or max size, set
 * in code or with {@code -fx-min-*} / {@code -fx-max-*}, still overrides this.</p>
 *
 * <p>The shape comes from one of two sources:</p>
 * <ul>
 *     <li><b>Icon data</b> &mdash; the {@link #iconProperty() icon} property, set
 *     in code, by name ({@code new RXIcon("mi-search")}) or in CSS
 *     ({@code -rx-icon: "mi-search"}). The box is {@link #iconSizeProperty() iconSize}
 *     on its longer side and keeps the icon's view box proportions, so the space
 *     around the drawing is kept. Setting the icon in code takes effect at once,
 *     which suits cells that are reused while scrolling.</li>
 *     <li><b>CSS</b> &mdash; when no icon is set, {@code -fx-shape} gives the
 *     outline and {@code -fx-pref-width} / {@code -fx-pref-height} give the box,
 *     as for the icons inside the library's controls.</li>
 * </ul>
 *
 * <pre>{@code
 * RXIcon search = new RXIcon("mi-search");
 * search.setIconSize(20);
 * }</pre>
 * <pre>{@code
 * .toolbar .rx-icon {
 *     -rx-icon-size: 1.5em;
 *     -fx-background-color: -fx-accent;
 * }
 * }</pre>
 *
 * <p>The default color is {@code -fx-text-base-color}. The icon is not mouse
 * transparent by default; inside a clickable wrapper it should usually be made
 * mouse transparent so the wrapper receives the events.</p>
 */
public class RXIcon extends Region {

    // ==================== Constants ====================

    /**
     * Default {@link #iconSizeProperty() icon size} in pixels.
     */
    public static final double DEFAULT_ICON_SIZE = 16.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-icon";

    // ==================== Internal State ====================

    private final ObjectProperty<Shape> iconShape = new SimpleObjectProperty<>(this, "iconShape");
    private boolean shapeFollowsIcon;
    private boolean applyingIconStyle;

    // ==================== Constructors ====================

    /**
     * Creates an icon with no icon data. Its shape and size come from CSS.
     */
    public RXIcon() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates an icon showing the registered icon with the given name. An unknown
     * name leaves the icon empty and logs a warning once.
     *
     * @param iconName the icon name, such as {@code mi-search}
     */
    public RXIcon(String iconName) {
        this();
        setIcon(RXIcons.resolve(iconName));
    }

    /**
     * Creates an icon showing the given icon data.
     *
     * @param icon the icon data, or {@code null}
     */
    public RXIcon(RXIconData icon) {
        this();
        setIcon(icon);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Icon ====================

    private final ObjectProperty<RXIconData> icon = new StyleableObjectProperty<>() {
        @Override
        protected void invalidated() {
            updateShape();
            requestLayout();
        }

        @Override
        public void applyStyle(StyleOrigin origin, RXIconData value) {
            applyingIconStyle = true;
            try {
                super.applyStyle(origin, value);
            } finally {
                applyingIconStyle = false;
            }
        }

        @Override
        public CssMetaData<RXIcon, RXIconData> getCssMetaData() {
            return StyleableProperties.ICON;
        }

        @Override
        public Object getBean() {
            return RXIcon.this;
        }

        @Override
        public String getName() {
            return "icon";
        }
    };

    /**
     * The icon data to show, or {@code null} to take the shape from CSS. In CSS,
     * {@code -rx-icon} takes a quoted icon name such as {@code "mi-search"}, which
     * is looked up in {@link RXIcons}; an unknown name leaves the icon empty and
     * logs a warning once.
     *
     * <p>While icon data is set, it owns the region's {@link #shapeProperty() shape}:
     * the shape is bound to the icon's, so {@code -fx-shape} rules do not apply and
     * {@link #setShape(Shape)} fails. Setting the icon back to {@code null}
     * releases the shape to CSS.</p>
     *
     * @return the icon property
     */
    public final ObjectProperty<RXIconData> iconProperty() {
        return icon;
    }

    /**
     * Returns the icon data.
     *
     * @return the icon data, or {@code null}
     */
    public final RXIconData getIcon() {
        return icon.get();
    }

    /**
     * Sets the icon data.
     *
     * @param value the icon data, or {@code null}
     */
    public final void setIcon(RXIconData value) {
        icon.set(value);
    }

    // ==================== Icon Size ====================

    private final DoubleProperty iconSize = new StyleableDoubleProperty(DEFAULT_ICON_SIZE) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXIcon, Number> getCssMetaData() {
            return StyleableProperties.ICON_SIZE;
        }

        @Override
        public Object getBean() {
            return RXIcon.this;
        }

        @Override
        public String getName() {
            return "iconSize";
        }
    };

    /**
     * The length of the longer side of the icon box, used when icon data is set.
     * The shorter side follows the icon's view box proportions; padding is added
     * around the box. In CSS, {@code -rx-icon-size} also accepts relative units such
     * as {@code 1.5em}. Initial value is {@value #DEFAULT_ICON_SIZE}; a negative or
     * NaN value uses that default. An explicit preferred size takes precedence.
     *
     * @return the icon size property
     */
    public final DoubleProperty iconSizeProperty() {
        return iconSize;
    }

    /**
     * Returns the icon size.
     *
     * @return the icon size
     */
    public final double getIconSize() {
        return iconSize.get();
    }

    /**
     * Sets the icon size.
     *
     * @param value the icon size
     */
    public final void setIconSize(double value) {
        iconSize.set(value);
    }

    // ==================== Shape ====================

    // The shape is bound while icon data is set so that no -fx-shape rule can
    // replace it. Releasing it resets the value and its origin so that the CSS
    // shape, if any, applies again. When CSS clears -rx-icon, the CSS pass that
    // follows (or the current one, for a state change: -rx-icon comes before
    // -fx-shape in the metadata) styles the shape; when code clears it, applyCss()
    // does, since nothing else would run CSS on this node again.
    @SuppressWarnings("unchecked")
    private void updateShape() {
        RXIconData data = getIcon();
        if (data != null) {
            iconShape.set(data.getShape());
            if (!shapeFollowsIcon) {
                shapeFollowsIcon = true;
                shapeProperty().bind(iconShape);
            }
            return;
        }
        if (!shapeFollowsIcon) {
            return;
        }
        shapeFollowsIcon = false;
        shapeProperty().unbind();
        iconShape.set(null);
        ((StyleableProperty<Shape>) shapeProperty()).applyStyle(null, null);
        if (!applyingIconStyle && getScene() != null) {
            applyCss();
        }
    }

    // ==================== Sizing ====================

    /**
     * Returns the icon box width plus the horizontal insets when icon data is set,
     * otherwise the region's computed width.
     *
     * @param height the height to compute the width for, or -1
     * @return the preferred width
     */
    @Override
    protected double computePrefWidth(double height) {
        RXIconData data = getIcon();
        if (data == null) {
            return super.computePrefWidth(height);
        }
        return snappedLeftInset() + boxSide(data.getWidth(), data) + snappedRightInset();
    }

    /**
     * Returns the icon box height plus the vertical insets when icon data is set,
     * otherwise the region's computed height.
     *
     * @param width the width to compute the height for, or -1
     * @return the preferred height
     */
    @Override
    protected double computePrefHeight(double width) {
        RXIconData data = getIcon();
        if (data == null) {
            return super.computePrefHeight(width);
        }
        return snappedTopInset() + boxSide(data.getHeight(), data) + snappedBottomInset();
    }

    /**
     * Returns the preferred width, so the icon is not squeezed.
     *
     * @param height the height to compute the width for, or -1
     * @return the minimum width
     */
    @Override
    protected double computeMinWidth(double height) {
        return prefWidth(height);
    }

    /**
     * Returns the preferred height, so the icon is not squeezed.
     *
     * @param width the width to compute the height for, or -1
     * @return the minimum height
     */
    @Override
    protected double computeMinHeight(double width) {
        return prefHeight(width);
    }

    /**
     * Returns the preferred width, so the icon is not stretched.
     *
     * @param height the height to compute the width for, or -1
     * @return the maximum width
     */
    @Override
    protected double computeMaxWidth(double height) {
        return prefWidth(height);
    }

    /**
     * Returns the preferred height, so the icon is not stretched.
     *
     * @param width the width to compute the height for, or -1
     * @return the maximum height
     */
    @Override
    protected double computeMaxHeight(double width) {
        return prefHeight(width);
    }

    private double boxSide(double side, RXIconData data) {
        double size = getIconSize();
        if (Double.isNaN(size) || size < 0.0) {
            size = DEFAULT_ICON_SIZE;
        }
        return size * side / Math.max(data.getWidth(), data.getHeight());
    }

    // ==================== CSS ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXIcon, RXIconData> ICON =
                new CssMetaData<>("-rx-icon", IconNameConverter.INSTANCE, null) {

                    @Override
                    public boolean isSettable(RXIcon node) {
                        return !node.icon.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXIconData> getStyleableProperty(RXIcon node) {
                        return (StyleableProperty<RXIconData>) node.iconProperty();
                    }
                };

        private static final CssMetaData<RXIcon, Number> ICON_SIZE =
                new CssMetaData<>("-rx-icon-size", SizeConverter.getInstance(), DEFAULT_ICON_SIZE) {

                    @Override
                    public boolean isSettable(RXIcon node) {
                        return !node.iconSize.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXIcon node) {
                        return (StyleableProperty<Number>) node.iconSizeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            // The icon's own properties come first: when CSS clears -rx-icon, the
            // released shape must still be styled by -fx-shape in the same pass.
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>();
            styleables.add(ICON);
            styleables.add(ICON_SIZE);
            styleables.addAll(Region.getClassCssMetaData());
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata of this class and its superclasses.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    /** Turns a quoted icon name into the registered icon data. */
    private static final class IconNameConverter extends StyleConverter<String, RXIconData> {

        static final IconNameConverter INSTANCE = new IconNameConverter();

        @Override
        public RXIconData convert(ParsedValue<String, RXIconData> value, Font font) {
            Object raw = value.getValue();
            if (!(raw instanceof String name) || name.isBlank()) {
                return null;
            }
            return RXIcons.resolve(name.strip());
        }

        @Override
        public String toString() {
            return "IconNameConverter";
        }
    }
}
