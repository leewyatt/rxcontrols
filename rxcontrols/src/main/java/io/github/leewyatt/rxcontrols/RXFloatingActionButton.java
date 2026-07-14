package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXFloatingActionButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Material floating action button: a circular, elevated, icon-first button
 * expressing a primary action.
 *
 * <p>The button extends {@link RXButton}, so it inherits bounded ripple,
 * hover/press state overlay, armed-driven keyboard activation, and the
 * {@link #playRipple()} API. Icon-only buttons should set accessible text for
 * screen readers.</p>
 */
public class RXFloatingActionButton extends RXButton {

    private static final String DEFAULT_STYLE_CLASS = "rx-fab";

    private static final PseudoClass SMALL_PSEUDO_CLASS = PseudoClass.getPseudoClass("small");
    private static final PseudoClass LARGE_PSEUDO_CLASS = PseudoClass.getPseudoClass("large");

    // ==================== Constructors ====================

    /**
     * Creates an empty floating action button.
     */
    public RXFloatingActionButton() {
        initialize();
    }

    /**
     * Creates a floating action button with the given graphic.
     *
     * @param graphic the graphic node, or {@code null}
     */
    public RXFloatingActionButton(@NamedArg("graphic") Node graphic) {
        super(null, graphic);
        initialize();
    }

    /**
     * Creates a floating action button with the given text and graphic. The
     * default Core presentation is graphic-only; text is retained for callers
     * that restyle the content display.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXFloatingActionButton(@NamedArg("text") String text,
                                  @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        updateSizePseudoClass();
    }

    /**
     * Creates the default FAB skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXFloatingActionButtonSkin(this);
    }

    // ==================== Size ====================

    private final ObjectProperty<Size> size = new StyleableObjectProperty<>(Size.STANDARD) {
        @Override
        protected void invalidated() {
            updateSizePseudoClass();
        }

        @Override
        public CssMetaData<? extends Styleable, Size> getCssMetaData() {
            return StyleableProperties.SIZE;
        }

        @Override
        public Object getBean() {
            return RXFloatingActionButton.this;
        }

        @Override
        public String getName() {
            return "size";
        }
    };

    /**
     * The FAB size variant. The value is styleable through
     * {@code -rx-fab-size} and drives the {@code :small} / {@code :large}
     * pseudo-classes; a {@code null} value is stored as-is and renders as the
     * standard size.
     *
     * @return the size property
     */
    public final ObjectProperty<Size> sizeProperty() {
        return size;
    }

    /**
     * Returns the FAB size.
     *
     * @return the FAB size, or {@code null}
     */
    public final Size getSize() {
        return size.get();
    }

    /**
     * Sets the FAB size.
     *
     * @param value the FAB size, or {@code null} for the standard rendering
     */
    public final void setSize(Size value) {
        size.set(value);
    }

    private void updateSizePseudoClass() {
        Size current = getSize();
        pseudoClassStateChanged(SMALL_PSEUDO_CLASS, current == Size.SMALL);
        pseudoClassStateChanged(LARGE_PSEUDO_CLASS, current == Size.LARGE);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXFloatingActionButton, Size> SIZE =
                new CssMetaData<>("-rx-fab-size",
                        new EnumConverter<>(Size.class), RXFloatingActionButton.Size.STANDARD) {
                    @Override
                    public boolean isSettable(RXFloatingActionButton node) {
                        return !node.size.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Size> getStyleableProperty(RXFloatingActionButton node) {
                        return (StyleableProperty<Size>) node.sizeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXButton.getClassCssMetaData());
            styleables.add(SIZE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * Size variants for a floating action button.
     */
    public enum Size {

        /**
         * Compact FAB variant.
         */
        SMALL,

        /**
         * Standard FAB variant.
         */
        STANDARD,

        /**
         * Large FAB variant.
         */
        LARGE
    }
}
