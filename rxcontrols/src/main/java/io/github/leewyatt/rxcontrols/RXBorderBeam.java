package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.CoercedStyleableProperty;
import io.github.leewyatt.rxcontrols.internal.CornerRadiiCoercion;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXBorderBeamSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A decoration that sends one or more fading highlights, the beams, round the
 * border of a content node in an endless loop.
 *
 * <p>The beam carries no meaning of its own: it draws the eye to a card, a
 * call to action or a featured panel. It is not a progress, focus or
 * validation indicator.</p>
 *
 * <p><b>Geometry.</b> The beam runs in a band, {@link #lineWidthProperty()
 * lineWidth} wide, whose outer edge lies on the edge of the content and which
 * extends inward from there, over the content's own border.
 * {@link #outsetProperty() outset} moves that edge outward, or inward when
 * negative, with the corners staying concentric. The corners are those of
 * {@link #cornerRadiusProperty() cornerRadius}; while that is {@code null}
 * they follow a content that is a {@code Region}:</p>
 * <ul>
 *   <li>with a border, the radii and insets of its first stroke;</li>
 *   <li>otherwise, with a background, the radii and insets of its first fill
 *       that has no negative inset, or of its first fill when all have;</li>
 *   <li>otherwise, and for content that is not a {@code Region}, square
 *       corners on the content's layout bounds.</li>
 * </ul>
 * <p>A region's {@code shape} is not followed, nor is a stroke drawn outside
 * or centered on the edge: set the radii, and an outset, explicitly in those
 * cases.</p>
 *
 * <p><b>The beam.</b> Each beam is a square of side
 * {@link #beamSizeProperty() beamSize}, painted with a gradient that fades
 * out toward its trailing edge and seen only where it crosses the band. The
 * square travels clockwise (counterclockwise in a right-to-left node
 * orientation) on a rounded track whose corner radius is the beam size, so it
 * swings round a corner gradually and shows on both edges while it does. The
 * length of the visible streak is therefore close to, but not exactly, the
 * beam size, and a beam size approaching twice the shorter side of the content
 * reaches the opposite edge as well. Several beams are spread evenly round
 * the border, all of them visible from the first frame.</p>
 *
 * <p><b>Running.</b> The beams show and move while {@link #activeProperty()
 * active} is on. They pause, keeping their place, while the control is not
 * part of a visible scene graph in a showing window, and continue from there.
 * There is no detection of an operating system's reduced-motion setting: turn
 * {@code active} off to honor one.</p>
 *
 * <p><b>Layout.</b> The control is a transparent wrapper: its minimum,
 * preferred and maximum sizes, content bias and baseline are those of the
 * content plus the control's own padding. The beam takes no layout space; with
 * a positive outset it is drawn outside the control's bounds, where a clipping
 * ancestor such as a scroll pane cuts it off. The beam never receives mouse
 * events and the control takes no focus; the content keeps its own clip,
 * effect and transforms.</p>
 *
 * <pre>{@code
 * RXBorderBeam beam = new RXBorderBeam(card);
 *
 * // Three beams in a gradient, shown while the pointer is over the card
 * beam.setBeamCount(3);
 * beam.setBeamFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
 *         new Stop(0, Color.ORANGERED), new Stop(1, Color.GOLD)));
 * beam.activeProperty().bind(beam.hoverProperty());
 * }</pre>
 *
 * <p><b>CSS.</b> The root style class is {@code rx-border-beam}; the layer
 * holding the beams is {@code .rx-border-beam > .beams}, each beam
 * {@code .rx-border-beam > .beams > .beam} and the pane holding the content
 * {@code .rx-border-beam > .content-slot}. The properties are
 * {@code -rx-beam-fill}, {@code -rx-beam-size}, {@code -rx-beam-count},
 * {@code -rx-line-width}, {@code -rx-outset}, {@code -rx-cycle-duration} and
 * {@code -rx-corner-radius}.</p>
 */
@DefaultProperty("content")
public class RXBorderBeam extends Control {

    // ==================== Constants ====================

    /**
     * Color of the beam when the fill is {@code null} or a paint without
     * colors of its own, such as an image pattern.
     */
    public static final Color DEFAULT_BEAM_COLOR = Color.web("#0096c9");

    /**
     * Default side of a beam, also used while the size is not a positive,
     * finite number.
     */
    public static final double DEFAULT_BEAM_SIZE = 100.0;

    /**
     * Default width of the band, also used while the width is not a positive,
     * finite number.
     */
    public static final double DEFAULT_LINE_WIDTH = 1.0;

    /**
     * Default duration of one lap, also used while the duration is
     * {@code null}, not positive, indefinite or unknown.
     */
    public static final Duration DEFAULT_CYCLE_DURATION = Duration.seconds(6.0);

    private static final String DEFAULT_STYLE_CLASS = "rx-border-beam";
    private static final int DEFAULT_BEAM_COUNT = 1;

    // ==================== Constructors ====================

    /**
     * Creates a border beam without content.
     */
    public RXBorderBeam() {
        this(null);
    }

    /**
     * Creates a border beam round the given content.
     *
     * @param content the content node, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXBorderBeam(@NamedArg("content") Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // A null style origin leaves -fx-focus-traversable settable from CSS.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        // Only the content is picked: the empty box around it stays transparent
        // to the mouse.
        setPickOnBounds(false);
        setContent(content);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXBorderBeamSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Orientation getContentBias() {
        Node node = getContent();
        return node == null || !node.isManaged() ? null : node.getContentBias();
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

    /**
     * The node the beam runs round. With {@code null} the control is empty and
     * shows no beam.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Active ====================

    private final BooleanProperty active = new SimpleBooleanProperty(this, "active", true);

    /**
     * Whether the beams are shown and moving. Turned off, they disappear at
     * once; turned on again, they continue from where they were. Initial value
     * is {@code true}.
     *
     * @return the active property
     */
    public final BooleanProperty activeProperty() {
        return active;
    }

    /**
     * Returns whether the beams are shown and moving.
     *
     * @return {@code true} if the beams are shown
     */
    public final boolean isActive() {
        return active.get();
    }

    /**
     * Sets whether the beams are shown and moving.
     *
     * @param value {@code true} to show the beams
     */
    public final void setActive(boolean value) {
        active.set(value);
    }

    // ==================== Beam Fill ====================

    private final ObjectProperty<Paint> beamFill = new StyleableObjectProperty<>(DEFAULT_BEAM_COLOR) {
        @Override
        public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
            return StyleableProperties.BEAM_FILL;
        }

        @Override
        public Object getBean() {
            return RXBorderBeam.this;
        }

        @Override
        public String getName() {
            return "beamFill";
        }
    };

    /**
     * The colors of a beam. A {@code Color} gives a beam of that color. Of a
     * linear or radial gradient only the stops are used, read from the leading
     * edge of the beam at offset {@code 0} toward its tail: they are fitted
     * into the leading 70% of the beam, and the rest fades from the last color
     * to transparent, so every fill ends in the same tail. {@code null} and
     * any other paint give {@link #DEFAULT_BEAM_COLOR}. The style sheet sets a
     * gradient of the theme's primary colors.
     *
     * @return the beam fill property
     */
    public final ObjectProperty<Paint> beamFillProperty() {
        return beamFill;
    }

    /**
     * Returns the beam fill.
     *
     * @return the beam fill, or {@code null}
     */
    public final Paint getBeamFill() {
        return beamFill.get();
    }

    /**
     * Sets the beam fill.
     *
     * @param value the beam fill, or {@code null} for the default color
     */
    public final void setBeamFill(Paint value) {
        beamFill.set(value);
    }

    // ==================== Beam Size ====================

    private final DoubleProperty beamSize = new StyleableDoubleProperty(DEFAULT_BEAM_SIZE) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.BEAM_SIZE;
        }

        @Override
        public Object getBean() {
            return RXBorderBeam.this;
        }

        @Override
        public String getName() {
            return "beamSize";
        }
    };

    /**
     * The side of the square each beam is cut from, and the corner radius of
     * the track it travels on. A streak on a straight edge is about this long.
     * Keep it clearly below twice the shorter side of the content, or the beam
     * shows on the opposite edge too. Initial value is
     * {@link #DEFAULT_BEAM_SIZE}, which also stands in for a value that is not
     * positive and finite.
     *
     * @return the beam size property
     */
    public final DoubleProperty beamSizeProperty() {
        return beamSize;
    }

    /**
     * Returns the beam size.
     *
     * @return the beam size
     */
    public final double getBeamSize() {
        return beamSize.get();
    }

    /**
     * Sets the beam size.
     *
     * @param value the beam size
     */
    public final void setBeamSize(double value) {
        beamSize.set(value);
    }

    // ==================== Beam Count ====================

    private final IntegerProperty beamCount = new StyleableIntegerProperty(DEFAULT_BEAM_COUNT) {
        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.BEAM_COUNT;
        }

        @Override
        public Object getBean() {
            return RXBorderBeam.this;
        }

        @Override
        public String getName() {
            return "beamCount";
        }
    };

    /**
     * The number of beams, spread evenly round the border. A count of zero or
     * less shows none; every beam is a node, so a very large count costs
     * accordingly. Initial value is {@code 1}.
     *
     * @return the beam count property
     */
    public final IntegerProperty beamCountProperty() {
        return beamCount;
    }

    /**
     * Returns the number of beams.
     *
     * @return the number of beams
     */
    public final int getBeamCount() {
        return beamCount.get();
    }

    /**
     * Sets the number of beams.
     *
     * @param value the number of beams
     */
    public final void setBeamCount(int value) {
        beamCount.set(value);
    }

    // ==================== Line Width ====================

    private final DoubleProperty lineWidth = new StyleableDoubleProperty(DEFAULT_LINE_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.LINE_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXBorderBeam.this;
        }

        @Override
        public String getName() {
            return "lineWidth";
        }
    };

    /**
     * The width of the band the beam is seen in, measured inward from its
     * outer edge. A width of half the shorter side or more fills the whole
     * box. Initial value is {@link #DEFAULT_LINE_WIDTH}, which also stands in
     * for a value that is not positive and finite.
     *
     * @return the line width property
     */
    public final DoubleProperty lineWidthProperty() {
        return lineWidth;
    }

    /**
     * Returns the line width.
     *
     * @return the line width
     */
    public final double getLineWidth() {
        return lineWidth.get();
    }

    /**
     * Sets the line width.
     *
     * @param value the line width
     */
    public final void setLineWidth(double value) {
        lineWidth.set(value);
    }

    // ==================== Outset ====================

    private final DoubleProperty outset = new StyleableDoubleProperty() {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.OUTSET;
        }

        @Override
        public Object getBean() {
            return RXBorderBeam.this;
        }

        @Override
        public String getName() {
            return "outset";
        }
    };

    /**
     * How far the outer edge of the band lies outside the edge of the content;
     * a negative value moves it inside. Rounded corners grow or shrink with
     * it, square ones stay square. The outset takes no layout space. Initial
     * value is {@code 0}; a non-finite value counts as zero.
     *
     * @return the outset property
     */
    public final DoubleProperty outsetProperty() {
        return outset;
    }

    /**
     * Returns the outset.
     *
     * @return the outset
     */
    public final double getOutset() {
        return outset.get();
    }

    /**
     * Sets the outset.
     *
     * @param value the outset
     */
    public final void setOutset(double value) {
        outset.set(value);
    }

    // ==================== Cycle Duration ====================

    private final ObjectProperty<Duration> cycleDuration =
            new StyleableObjectProperty<>(DEFAULT_CYCLE_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.CYCLE_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXBorderBeam.this;
                }

                @Override
                public String getName() {
                    return "cycleDuration";
                }
            };

    /**
     * The time a beam takes for one lap, at a constant speed along its track.
     * Changing it while the beams move keeps them where they are. Initial
     * value is {@link #DEFAULT_CYCLE_DURATION}, which also stands in for a
     * duration that is {@code null}, not positive, indefinite or unknown.
     *
     * @return the cycle duration property
     */
    public final ObjectProperty<Duration> cycleDurationProperty() {
        return cycleDuration;
    }

    /**
     * Returns the cycle duration.
     *
     * @return the cycle duration, or {@code null}
     */
    public final Duration getCycleDuration() {
        return cycleDuration.get();
    }

    /**
     * Sets the cycle duration.
     *
     * @param value the cycle duration
     */
    public final void setCycleDuration(Duration value) {
        cycleDuration.set(value);
    }

    // ==================== Corner Radius ====================

    private final ObjectProperty<CornerRadii> cornerRadius =
            new SimpleObjectProperty<>(this, "cornerRadius") {
                @Override
                protected void invalidated() {
                    requestLayout();
                }
            };

    /**
     * CSS facade for {@link #cornerRadius}: the engine delivers a multi-value
     * custom property only through {@code InsetsConverter}, so the CSS type is
     * {@link Insets} and is coerced into {@link CornerRadii} here.
     */
    private final CoercedStyleableProperty<Insets, CornerRadii> cornerRadiusCss =
            new CoercedStyleableProperty<>(cornerRadius, StyleableProperties.CORNER_RADIUS,
                    CornerRadiiCoercion::fromInsets, CornerRadiiCoercion::toInsets);

    /**
     * The corner radii of the band's outer edge, before the outset is applied.
     * Every corner may differ, be elliptical or be a percentage of the box;
     * radii too large for the box shrink together, as they do on a region.
     * With these set, the band lies on the content's layout bounds whatever
     * the content paints. The default {@code null} follows the content, as the
     * class description lays out. From CSS, {@code -rx-corner-radius} accepts
     * 1 to 4 sizes in top-left, top-right, bottom-right, bottom-left order; a
     * negative size selects following the content.
     *
     * @return the corner radius property
     */
    public final ObjectProperty<CornerRadii> cornerRadiusProperty() {
        return cornerRadius;
    }

    /**
     * Returns the corner radii.
     *
     * @return the corner radii, or {@code null} when they follow the content
     */
    public final CornerRadii getCornerRadius() {
        return cornerRadius.get();
    }

    /**
     * Sets the corner radii.
     *
     * @param value the corner radii, or {@code null} to follow the content
     */
    public final void setCornerRadius(CornerRadii value) {
        cornerRadius.set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXBorderBeam, Paint> BEAM_FILL =
                new CssMetaData<>("-rx-beam-fill", PaintConverter.getInstance(), DEFAULT_BEAM_COLOR) {
                    @Override
                    public boolean isSettable(RXBorderBeam control) {
                        return !control.beamFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXBorderBeam control) {
                        return (StyleableProperty<Paint>) control.beamFillProperty();
                    }
                };

        private static final CssMetaData<RXBorderBeam, Number> BEAM_SIZE =
                new CssMetaData<>("-rx-beam-size", SizeConverter.getInstance(), DEFAULT_BEAM_SIZE) {
                    @Override
                    public boolean isSettable(RXBorderBeam control) {
                        return !control.beamSize.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBorderBeam control) {
                        return (StyleableProperty<Number>) control.beamSizeProperty();
                    }
                };

        private static final CssMetaData<RXBorderBeam, Number> BEAM_COUNT =
                new CssMetaData<>("-rx-beam-count", SizeConverter.getInstance(), DEFAULT_BEAM_COUNT) {
                    @Override
                    public boolean isSettable(RXBorderBeam control) {
                        return !control.beamCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBorderBeam control) {
                        return (StyleableProperty<Number>) control.beamCountProperty();
                    }
                };

        private static final CssMetaData<RXBorderBeam, Number> LINE_WIDTH =
                new CssMetaData<>("-rx-line-width", SizeConverter.getInstance(), DEFAULT_LINE_WIDTH) {
                    @Override
                    public boolean isSettable(RXBorderBeam control) {
                        return !control.lineWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBorderBeam control) {
                        return (StyleableProperty<Number>) control.lineWidthProperty();
                    }
                };

        private static final CssMetaData<RXBorderBeam, Number> OUTSET =
                new CssMetaData<>("-rx-outset", SizeConverter.getInstance(), 0.0) {
                    @Override
                    public boolean isSettable(RXBorderBeam control) {
                        return !control.outset.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBorderBeam control) {
                        return (StyleableProperty<Number>) control.outsetProperty();
                    }
                };

        private static final CssMetaData<RXBorderBeam, Duration> CYCLE_DURATION =
                new CssMetaData<>("-rx-cycle-duration", DurationConverter.getInstance(),
                        DEFAULT_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXBorderBeam control) {
                        return !control.cycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXBorderBeam control) {
                        return (StyleableProperty<Duration>) control.cycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXBorderBeam, Insets> CORNER_RADIUS =
                new CssMetaData<>("-rx-corner-radius", InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXBorderBeam control) {
                        return !control.cornerRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Insets> getStyleableProperty(RXBorderBeam control) {
                        return control.cornerRadiusCss;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(BEAM_FILL);
            styleables.add(BEAM_SIZE);
            styleables.add(BEAM_COUNT);
            styleables.add(LINE_WIDTH);
            styleables.add(OUTSET);
            styleables.add(CYCLE_DURATION);
            styleables.add(CORNER_RADIUS);
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

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
