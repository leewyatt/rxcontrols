package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXDigitSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single seven-segment numeric glyph. Renders one digit (0–9) as seven
 * beveled segments; lit segments use {@link #lightFillProperty() lightFill} and
 * unlit segments use {@link #darkFillProperty() darkFill}, giving the classic
 * LED/LCD readout look. The control is purely decorative — the displayed value
 * is driven by application code (for example a clock that calls
 * {@link #setDigit(int)} every second).
 *
 * <p>Compose several instances in an {@code HBox} for multi-digit displays;
 * separators such as a colon or decimal point are the caller's responsibility.
 *
 * <p><b>Sizing.</b> The glyph has an intrinsic {@code 1 : 2} (width : height)
 * aspect ratio with a default preferred size of {@code 50 x 100}. By default the
 * constructor locks {@code min = max = pref} via {@link Region#USE_PREF_SIZE},
 * so the control is neither compressed nor stretched by its parent and follows
 * {@link #setPrefSize(double, double) setPrefSize}. The skin always draws the
 * glyph contain-fit and centered within the content box, so it never distorts —
 * even if the caller opts out of the lock (for example {@code setMinSize(0, 0)}
 * to allow compression or {@code setMaxSize(MAX_VALUE, MAX_VALUE)} to allow
 * stretching).
 */
public class RXDigit extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-digit";

    /**
     * Default displayed digit.
     */
    public static final int DEFAULT_DIGIT = 0;

    /**
     * Default fill for lit segments.
     */
    public static final Paint DEFAULT_LIGHT_FILL = Color.BLACK;

    /**
     * Default fill for unlit segments.
     */
    public static final Paint DEFAULT_DARK_FILL = Color.web("#dddddd");

    // ==================== Constructors ====================

    /**
     * Creates a digit showing {@link #DEFAULT_DIGIT}.
     */
    public RXDigit() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Fixed visual unit: lock min/max to the effective preferred size so the
        // glyph keeps its size (and follows setPrefSize) instead of being
        // compressed or stretched by the parent. USE_PREF_SIZE follows pref at
        // the property layer, avoiding the computeMin* size-cache pitfall. The
        // skin's computeMin*/computeMax* only take over if a caller resets
        // min/max to USE_COMPUTED_SIZE.
        setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
    }

    /**
     * Creates a digit showing the given value.
     *
     * @param digit the digit to display; values outside {@code [0, 9]} are
     *              clamped when rendered
     */
    public RXDigit(@NamedArg("digit") int digit) {
        this();
        setDigit(digit);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDigitSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Digit ====================

    private final IntegerProperty digit = new SimpleIntegerProperty(this, "digit", DEFAULT_DIGIT);

    /**
     * The displayed digit. The value is stored verbatim; values outside
     * {@code [0, 9]} are clamped to the nearest valid digit only when the skin
     * renders, so {@code getDigit()} returns exactly what was set.
     *
     * @return the digit property
     */
    public final IntegerProperty digitProperty() {
        return digit;
    }

    public final int getDigit() {
        return digit.get();
    }

    public final void setDigit(int value) {
        digit.set(value);
    }

    // ==================== Light Fill ====================

    private final ObjectProperty<Paint> lightFill =
            new SimpleStyleableObjectProperty<>(StyleableProperties.LIGHT_FILL, this, "lightFill", DEFAULT_LIGHT_FILL);

    /**
     * Fill applied to lit segments. Initial value is {@link #DEFAULT_LIGHT_FILL};
     * setting {@code null} renders the lit segments with no fill (transparent)
     * per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the light-fill property
     */
    public final ObjectProperty<Paint> lightFillProperty() {
        return lightFill;
    }

    public final Paint getLightFill() {
        return lightFill.get();
    }

    public final void setLightFill(Paint value) {
        lightFill.set(value);
    }

    // ==================== Dark Fill ====================

    private final ObjectProperty<Paint> darkFill =
            new SimpleStyleableObjectProperty<>(StyleableProperties.DARK_FILL, this, "darkFill", DEFAULT_DARK_FILL);

    /**
     * Fill applied to unlit segments. Initial value is {@link #DEFAULT_DARK_FILL};
     * setting {@code null} renders the unlit segments with no fill (transparent)
     * per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the dark-fill property
     */
    public final ObjectProperty<Paint> darkFillProperty() {
        return darkFill;
    }

    public final Paint getDarkFill() {
        return darkFill.get();
    }

    public final void setDarkFill(Paint value) {
        darkFill.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXDigit, Paint> LIGHT_FILL =
                new CssMetaData<>("-rx-light-fill", PaintConverter.getInstance(), DEFAULT_LIGHT_FILL) {
                    @Override
                    public boolean isSettable(RXDigit n) {
                        return !n.lightFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXDigit n) {
                        return (StyleableProperty<Paint>) n.lightFillProperty();
                    }
                };

        private static final CssMetaData<RXDigit, Paint> DARK_FILL =
                new CssMetaData<>("-rx-dark-fill", PaintConverter.getInstance(), DEFAULT_DARK_FILL) {
                    @Override
                    public boolean isSettable(RXDigit n) {
                        return !n.darkFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXDigit n) {
                        return (StyleableProperty<Paint>) n.darkFillProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, LIGHT_FILL, DARK_FILL);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
