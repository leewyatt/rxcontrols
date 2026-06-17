package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.CoercedStyleableProperty;
import io.github.leewyatt.rxcontrols.internal.CornerRadiiCoercion;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single-content container that renders a bounded ripple overlay on mouse
 * press.
 *
 * <p>The pane owns a single {@linkplain #contentProperty() content} node and
 * an internal unmanaged ripple layer covering the full pane bounds, so the
 * ripple reaches the painted background edge even when padding is set. The
 * ripple is clipped to a geometry snapshot of the pane's {@code shape} if a
 * plain filled shape (no stroke, no transforms, not part of a scene graph) is
 * set, otherwise to the geometry (corner radii and insets) of the background
 * fills. Mouse presses start at the pointer location
 * unless {@link #rippleCenteredProperty() rippleCentered} is true; release and
 * exit fade the active ripple out. Existing fading ripples may coexist with a
 * new press, with an internal cap to prevent buildup.</p>
 *
 * <p>The pane listens for bubbling mouse events on itself; if the content
 * consumes {@code MOUSE_PRESSED}, no ripple starts. A ripple's radius is fixed
 * at press time, so resizing the pane while a ripple is live does not expand
 * that ripple. Replacing the {@code shape} instance refreshes the ripple clip;
 * mutating the geometry of an installed shape instance only updates the pane
 * itself.</p>
 *
 * <p>Beyond the press ripple, a low-opacity hover state overlay tints the pane
 * while the pointer is inside, using {@link #rippleFillProperty() rippleFill}
 * and gated by {@link #rippleEnabledProperty() rippleEnabled}.
 * {@link #rippleInsetsProperty() rippleInsets} insets (or, when negative,
 * bleeds) the ripple region, and {@link #rippleCornerRadiusProperty()
 * rippleCornerRadius} overrides the mirrored clip corners with explicit
 * radii.</p>
 */
@DefaultProperty("content")
public class RXRipplePane extends Region {

    // ==================== Constants ====================

    /**
     * Default ripple fill.
     */
    public static final Paint DEFAULT_RIPPLE_FILL = Color.BLACK;

    /**
     * Default peak ripple opacity.
     */
    public static final double DEFAULT_RIPPLE_OPACITY = 0.12;

    /**
     * Default ripple enabled state.
     */
    public static final boolean DEFAULT_RIPPLE_ENABLED = true;

    /**
     * Default pointer-origin mode.
     */
    public static final boolean DEFAULT_RIPPLE_CENTERED = false;

    private static final String DEFAULT_STYLE_CLASS = "rx-ripple-pane";

    // ==================== Internal State ====================

    private final RippleDecoration ripple;

    private Node currentContent;

    // ==================== Constructors ====================

    /**
     * Creates an empty ripple pane.
     */
    public RXRipplePane() {
        this(null);
    }

    /**
     * Creates a ripple pane with the given content.
     *
     * @param content the content node, or {@code null}
     */
    public RXRipplePane(@NamedArg("content") Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        ripple = new RippleDecoration(this, rippleEnabledProperty(),
                rippleFillProperty(), this::getRippleOpacity,
                rippleInsetsProperty(), rippleCornerRadiusProperty());

        ripple.setHoverOverlayEnabled(isHoverOverlayEnabled());
        getChildren().add(ripple.getLayer());

        // Pointer-press trigger; the decoration owns hover, overlay, the
        // disabled / scene-detach / ripple-enabled lifecycle and clip refresh.
        addEventHandler(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                ripple.release();
            }
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            if (event.isPrimaryButtonDown()) {
                ripple.release();
            }
        });

        setContent(content);
    }

    /**
     * Returns the user-agent stylesheet used by RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Programmatic Playback ====================

    /**
     * Plays one centered ripple (press and immediate release). No effect when
     * ripples are disabled or the host is disabled.
     */
    public final void playRipple() {
        // The pane holds its decoration directly (no skin), so no event
        // channel is needed; the behavior contract matches RXButton.
        if (isRippleEnabled() && !isDisabled()) {
            ripple.press(0.0, 0.0, true);
            ripple.release();
        }
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content =
            new SimpleObjectProperty<>(this, "content") {
                @Override
                protected void invalidated() {
                    updateContent();
                }
            };

    /**
     * Content displayed below the ripple layer. May be {@code null}.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the displayed content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the displayed content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Ripple Fill ====================

    private final ObjectProperty<Paint> rippleFill =
            new StyleableObjectProperty<>(DEFAULT_RIPPLE_FILL) {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.RIPPLE_FILL;
                }

                @Override
                public Object getBean() {
                    return RXRipplePane.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for newly created ripple circles. Initial value is
     * {@link #DEFAULT_RIPPLE_FILL}; setting {@code null} renders no fill
     * (transparent) per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the ripple fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the ripple fill.
     *
     * @return the ripple fill, or {@code null}
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the ripple fill.
     *
     * @param value the ripple fill, or {@code null} for no fill
     */
    public final void setRippleFill(Paint value) {
        rippleFill.set(value);
    }

    // ==================== Ripple Opacity ====================

    private final DoubleProperty rippleOpacity =
            new StyleableDoubleProperty(DEFAULT_RIPPLE_OPACITY) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.RIPPLE_OPACITY;
                }

                @Override
                public Object getBean() {
                    return RXRipplePane.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * Peak opacity for newly created ripple circles. Values outside
     * {@code [0, 1]} are stored as-is and clamped at render time.
     *
     * @return the ripple opacity property
     */
    public final DoubleProperty rippleOpacityProperty() {
        return rippleOpacity;
    }

    /**
     * Returns the ripple opacity.
     *
     * @return the ripple opacity
     */
    public final double getRippleOpacity() {
        return rippleOpacity.get();
    }

    /**
     * Sets the ripple opacity.
     *
     * @param value the ripple opacity
     */
    public final void setRippleOpacity(double value) {
        rippleOpacity.set(value);
    }

    // ==================== Ripple Enabled ====================

    private final BooleanProperty rippleEnabled =
            new StyleableBooleanProperty(DEFAULT_RIPPLE_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXRipplePane.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether mouse interaction creates ripples. Turning this off immediately
     * clears existing ripple nodes and running ripple animations.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether ripple interaction is enabled.
     *
     * @return whether ripple interaction is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether ripple interaction is enabled.
     *
     * @param value {@code true} to enable ripple interaction
     */
    public final void setRippleEnabled(boolean value) {
        rippleEnabled.set(value);
    }

    // ==================== Hover Overlay Enabled ====================

    private final BooleanProperty hoverOverlayEnabled =
            new SimpleBooleanProperty(this, "hoverOverlayEnabled", true) {
                @Override
                protected void invalidated() {
                    ripple.setHoverOverlayEnabled(get());
                }
            };

    /**
     * Whether the low-opacity hover state overlay may show while the pointer is
     * inside. The press ripple is unaffected (it stays gated only by
     * {@link #rippleEnabledProperty() rippleEnabled}). Disable this on a host
     * that already represents the hovered / active state another way, so the
     * overlay does not tint it.
     *
     * @return the hover-overlay-enabled property
     */
    public final BooleanProperty hoverOverlayEnabledProperty() {
        return hoverOverlayEnabled;
    }

    /**
     * Returns whether the hover state overlay may show.
     *
     * @return whether the hover state overlay may show
     */
    public final boolean isHoverOverlayEnabled() {
        return hoverOverlayEnabled.get();
    }

    /**
     * Sets whether the hover state overlay may show.
     *
     * @param value {@code true} to allow the hover overlay
     */
    public final void setHoverOverlayEnabled(boolean value) {
        hoverOverlayEnabled.set(value);
    }

    // ==================== Ripple Centered ====================

    private final BooleanProperty rippleCentered =
            new StyleableBooleanProperty(DEFAULT_RIPPLE_CENTERED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_CENTERED;
                }

                @Override
                public Object getBean() {
                    return RXRipplePane.this;
                }

                @Override
                public String getName() {
                    return "rippleCentered";
                }
            };

    /**
     * Whether pointer-triggered ripples start from the pane center instead of
     * the mouse press location.
     *
     * @return the ripple-centered property
     */
    public final BooleanProperty rippleCenteredProperty() {
        return rippleCentered;
    }

    /**
     * Returns whether pointer-triggered ripples start from the center.
     *
     * @return whether pointer-triggered ripples start from the center
     */
    public final boolean isRippleCentered() {
        return rippleCentered.get();
    }

    /**
     * Sets whether pointer-triggered ripples start from the center.
     *
     * @param value {@code true} to start pointer-triggered ripples from center
     */
    public final void setRippleCentered(boolean value) {
        rippleCentered.set(value);
    }

    // ==================== Ripple Insets ====================

    private final ObjectProperty<Insets> rippleInsets =
            new StyleableObjectProperty<>(null) {
                @Override
                public CssMetaData<? extends Styleable, Insets> getCssMetaData() {
                    return StyleableProperties.RIPPLE_INSETS;
                }

                @Override
                public Object getBean() {
                    return RXRipplePane.this;
                }

                @Override
                public String getName() {
                    return "rippleInsets";
                }
            };

    /**
     * Insets of the ripple effect area measured from the pane bounds, mirroring
     * the {@code -fx-background-insets} convention: zero covers the full bounds,
     * positive values shrink the ripple region inward, negative values let it
     * bleed outside as a pure visual effect that never affects the pane's size.
     * The default {@code null} follows the inner edge of the pane's real border
     * automatically. Only the ripple clip is inset; the layer keeps full bounds,
     * so press location and radius are unaffected and the hover overlay shares
     * the same inset region. Shape-based clips ignore these insets.
     *
     * @return the ripple insets property
     */
    public final ObjectProperty<Insets> rippleInsetsProperty() {
        return rippleInsets;
    }

    /**
     * Returns the ripple insets.
     *
     * @return the ripple insets, or {@code null} for automatic border following
     */
    public final Insets getRippleInsets() {
        return rippleInsets.get();
    }

    /**
     * Sets the ripple insets.
     *
     * @param value the ripple insets, or {@code null} for automatic border
     *              following
     */
    public final void setRippleInsets(Insets value) {
        rippleInsets.set(value);
    }

    // ==================== Ripple Corner Radius ====================

    private final ObjectProperty<CornerRadii> rippleCornerRadius =
            new SimpleObjectProperty<>(this, "rippleCornerRadius", null);

    /**
     * CSS facade for {@link #rippleCornerRadius}: the engine can only deliver
     * multi-value custom properties through the special-cased
     * {@code InsetsConverter} (RT-37727), so the CSS type is {@link Insets}
     * and gets coerced into {@link CornerRadii} here. CSS order follows the
     * {@code border-radius} convention: top-left, top-right, bottom-right,
     * bottom-left; any negative component means automatic mirroring.
     */
    private final CoercedStyleableProperty<Insets, CornerRadii> rippleCornerRadiusCss =
            new CoercedStyleableProperty<>(rippleCornerRadius, StyleableProperties.RIPPLE_CORNER_RADIUS,
                    CornerRadiiCoercion::fromInsets, CornerRadiiCoercion::toInsets);

    /**
     * Explicit corner radii for the ripple clip. When set, the ripple and hover
     * overlay are clipped to a single rounded rectangle with these radii (and
     * the {@link #rippleInsetsProperty() rippleInsets} box), ignoring the pane
     * background layers entirely — the escape hatch for stateful multi-layer
     * backgrounds such as focus rings. The default {@code null} mirrors the
     * pane's painted background geometry. From CSS,
     * {@code -rx-ripple-corner-radius} accepts 1 to 4 sizes in
     * {@code border-radius} order (top-left, top-right, bottom-right,
     * bottom-left); a negative value selects automatic mirroring. Ignored when
     * the pane uses a {@code shape}.
     *
     * @return the ripple corner radius property
     */
    public final ObjectProperty<CornerRadii> rippleCornerRadiusProperty() {
        return rippleCornerRadius;
    }

    /**
     * Returns the ripple corner radius.
     *
     * @return the ripple corner radius, or {@code null} for automatic mirroring
     */
    public final CornerRadii getRippleCornerRadius() {
        return rippleCornerRadius.get();
    }

    /**
     * Sets the ripple corner radius.
     *
     * @param value the ripple corner radius, or {@code null} for automatic
     *              mirroring
     */
    public final void setRippleCornerRadius(CornerRadii value) {
        rippleCornerRadius.set(value);
    }

    // ==================== Layout ====================

    @Override
    public Orientation getContentBias() {
        Node node = getContent();
        return node == null ? null : node.getContentBias();
    }

    @Override
    protected double computeMinWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        Node node = getContent();
        return snappedLeftInset() + (node == null ? 0.0 : node.minWidth(contentHeight)) + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        Node node = getContent();
        return snappedTopInset() + (node == null ? 0.0 : node.minHeight(contentWidth)) + snappedBottomInset();
    }

    @Override
    protected double computePrefWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        Node node = getContent();
        return snappedLeftInset() + (node == null ? 0.0 : node.prefWidth(contentHeight)) + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        Node node = getContent();
        return snappedTopInset() + (node == null ? 0.0 : node.prefHeight(contentWidth)) + snappedBottomInset();
    }

    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        boolean valid = width > 0.0 && height > 0.0
                && Double.isFinite(width) && Double.isFinite(height);

        Node node = getContent();
        if (node != null) {
            double contentW = valid ? Math.max(0.0, width - left - right) : 0.0;
            double contentH = valid ? Math.max(0.0, height - top - bottom) : 0.0;
            layoutInArea(node, left, top, contentW, contentH, 0.0, HPos.LEFT, VPos.TOP);
        }
        // The decoration reads its insets/corner-radius and collapses and
        // clears itself on an invalid size.
        ripple.layout(width, height);
    }

    // ==================== Event Handling ====================

    private void handleMousePressed(MouseEvent event) {
        if (!isRippleEnabled() || isDisabled() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        Point2D local = sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), isRippleCentered());
    }

    // ==================== Helpers ====================

    private void updateContent() {
        Node next = getContent();
        if (currentContent == next) {
            return;
        }
        ripple.clear();
        currentContent = next;
        if (next == null) {
            getChildren().setAll(ripple.getLayer());
        } else {
            getChildren().setAll(next, ripple.getLayer());
        }
        requestLayout();
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXRipplePane, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXRipplePane pane) {
                        return !pane.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXRipplePane pane) {
                        return (StyleableProperty<Paint>) pane.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXRipplePane, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXRipplePane pane) {
                        return !pane.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRipplePane pane) {
                        return (StyleableProperty<Number>) pane.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXRipplePane, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXRipplePane pane) {
                        return !pane.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRipplePane pane) {
                        return (StyleableProperty<Boolean>) pane.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXRipplePane, Boolean> RIPPLE_CENTERED =
                new CssMetaData<>("-rx-ripple-centered",
                        BooleanConverter.getInstance(), DEFAULT_RIPPLE_CENTERED) {
                    @Override
                    public boolean isSettable(RXRipplePane pane) {
                        return !pane.rippleCentered.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRipplePane pane) {
                        return (StyleableProperty<Boolean>) pane.rippleCenteredProperty();
                    }
                };

        private static final CssMetaData<RXRipplePane, Insets> RIPPLE_INSETS =
                new CssMetaData<>("-rx-ripple-insets",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXRipplePane pane) {
                        return !pane.rippleInsets.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXRipplePane pane) {
                        return (StyleableProperty<Insets>) pane.rippleInsetsProperty();
                    }
                };

        private static final CssMetaData<RXRipplePane, Insets> RIPPLE_CORNER_RADIUS =
                new CssMetaData<>("-rx-ripple-corner-radius",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXRipplePane pane) {
                        return !pane.rippleCornerRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Insets> getStyleableProperty(RXRipplePane pane) {
                        return pane.rippleCornerRadiusCss;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(RIPPLE_FILL);
            styleables.add(RIPPLE_OPACITY);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_CENTERED);
            styleables.add(RIPPLE_INSETS);
            styleables.add(RIPPLE_CORNER_RADIUS);
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
     * Returns the CSS metadata associated with this instance.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}
