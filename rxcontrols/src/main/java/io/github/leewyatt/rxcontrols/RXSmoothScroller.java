package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.smooth.RXSmoothScrollEngine;
import io.github.leewyatt.rxcontrols.internal.smooth.ScrollPaneSmoothScrollable;
import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

/**
 * Lifecycle handle for smooth wheel scrolling installed on a {@link ScrollPane}.
 */
public final class RXSmoothScroller implements AutoCloseable {

    // ==================== State ====================

    private final ScrollPane scrollPane;
    private final ScrollPaneSmoothScrollable adapter;
    private final RXSmoothScrollEngine engine;

    private final EventHandler<ScrollEvent> scrollHandler = this::onScroll;
    private final ChangeListener<Node> contentListener = (obs, oldContent, newContent) -> {
        detachContent(oldContent);
        attachContent(newContent);
        reattachEventNode();
    };
    private final ChangeListener<Parent> contentParentListener = (obs, oldParent, newParent) -> reattachEventNode();
    private final ChangeListener<Skin<?>> skinListener = (obs, oldSkin, newSkin) -> reattachEventNode();
    private final ChangeListener<Scene> sceneListener;
    private final ChangeListener<Number> scrollValueListener =
            (obs, oldValue, newValue) -> onExternalScrollValueChanged();

    private Node observedContent;
    private Node eventNode;
    private boolean disposed;

    // ==================== Constructors ====================

    RXSmoothScroller(ScrollPane scrollPane, RXSmoothScrollOptions options) {
        this.scrollPane = scrollPane;
        adapter = new ScrollPaneSmoothScrollable(scrollPane);
        engine = new RXSmoothScrollEngine(adapter);
        sceneListener = (obs, oldScene, newScene) -> {
            if (newScene == null) {
                engine.stop();
            }
        };

        setEnabled(options.isEnabled());
        setAxis(options.getAxis());
        setDuration(options.getDuration());
        setInterpolator(options.getInterpolator());
        setWheelMultiplier(options.getWheelMultiplier());
        setMode(options.getMode());
        setBoundaryPolicy(options.getBoundaryPolicy());
        setShiftWheelHorizontal(options.isShiftWheelHorizontal());
        setReducedMotion(options.isReducedMotion());

        scrollPane.contentProperty().addListener(contentListener);
        scrollPane.skinProperty().addListener(skinListener);
        scrollPane.sceneProperty().addListener(sceneListener);
        scrollPane.hvalueProperty().addListener(scrollValueListener);
        scrollPane.vvalueProperty().addListener(scrollValueListener);
        attachContent(scrollPane.getContent());
        reattachEventNode();
    }

    // ==================== Enabled ====================

    private final BooleanProperty enabled = new SimpleBooleanProperty(this, "enabled", true) {
        @Override
        protected void invalidated() {
            if (!get()) {
                engine.stop();
            }
        }
    };

    /**
     * Whether wheel input should be animated. When disabled, supported wheel input
     * is applied immediately through the same boundary policy.
     *
     * @return the enabled property
     */
    public BooleanProperty enabledProperty() {
        return enabled;
    }

    /**
     * Returns whether smooth animation is enabled.
     *
     * @return {@code true} when enabled
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Sets whether smooth animation is enabled.
     *
     * @param value {@code true} to animate wheel input
     */
    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    // ==================== Axis ====================

    private final ObjectProperty<ScrollAxis> axis =
            new SimpleObjectProperty<>(this, "axis", ScrollAxis.BOTH) {
                @Override
                protected void invalidated() {
                    engine.stop();
                    engine.snapToCurrentOffsets();
                }
            };

    /**
     * Axes that this scroller may drive. A {@code null} value is treated as
     * {@link ScrollAxis#BOTH}.
     *
     * @return the axis property
     */
    public ObjectProperty<ScrollAxis> axisProperty() {
        return axis;
    }

    /**
     * Returns the enabled axes.
     *
     * @return the enabled axes, possibly {@code null}
     */
    public ScrollAxis getAxis() {
        return axis.get();
    }

    /**
     * Sets the enabled axes.
     *
     * @param value the axes, or {@code null} for the default
     */
    public void setAxis(ScrollAxis value) {
        axis.set(value);
    }

    // ==================== Duration ====================

    private final ObjectProperty<Duration> duration =
            new SimpleObjectProperty<>(this, "duration", RXSmoothScrollOptions.DEFAULT_DURATION);

    /**
     * Duration of wheel retarget animations. A {@code null}, non-positive,
     * unknown or indefinite value applies wheel input immediately.
     *
     * @return the duration property
     */
    public ObjectProperty<Duration> durationProperty() {
        return duration;
    }

    /**
     * Returns the duration.
     *
     * @return the duration, possibly {@code null}
     */
    public Duration getDuration() {
        return duration.get();
    }

    /**
     * Sets the duration.
     *
     * @param value the duration, or {@code null} for immediate application
     */
    public void setDuration(Duration value) {
        duration.set(value);
    }

    // ==================== Interpolator ====================

    private final ObjectProperty<Interpolator> interpolator =
            new SimpleObjectProperty<>(this, "interpolator", RXSmoothScrollOptions.DEFAULT_INTERPOLATOR);

    /**
     * Interpolator used by wheel retarget animations. A {@code null} value uses
     * the default interpolator.
     *
     * @return the interpolator property
     */
    public ObjectProperty<Interpolator> interpolatorProperty() {
        return interpolator;
    }

    /**
     * Returns the interpolator.
     *
     * @return the interpolator, possibly {@code null}
     */
    public Interpolator getInterpolator() {
        return interpolator.get();
    }

    /**
     * Sets the interpolator.
     *
     * @param value the interpolator, or {@code null} for the default
     */
    public void setInterpolator(Interpolator value) {
        interpolator.set(value);
    }

    // ==================== Wheel Multiplier ====================

    private final DoubleProperty wheelMultiplier =
            new SimpleDoubleProperty(this, "wheelMultiplier", RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER);

    /**
     * Multiplier applied to pixel-normalized wheel deltas.
     *
     * @return the wheel multiplier property
     */
    public DoubleProperty wheelMultiplierProperty() {
        return wheelMultiplier;
    }

    /**
     * Returns the wheel multiplier.
     *
     * @return the wheel multiplier
     */
    public double getWheelMultiplier() {
        return wheelMultiplier.get();
    }

    /**
     * Sets the wheel multiplier.
     *
     * @param value the wheel multiplier
     */
    public void setWheelMultiplier(double value) {
        wheelMultiplier.set(value);
    }

    // ==================== Mode ====================

    private final ObjectProperty<SmoothScrollMode> mode =
            new SimpleObjectProperty<>(this, "mode", RXSmoothScrollOptions.DEFAULT_MODE) {
                @Override
                protected void invalidated() {
                    engine.setMode(get());
                }
            };

    /**
     * Smooth animation mode used while smooth scrolling is enabled. A
     * {@code null} value is treated as {@link RXSmoothScrollOptions#DEFAULT_MODE}.
     *
     * @return the smooth scroll mode property
     */
    public ObjectProperty<SmoothScrollMode> modeProperty() {
        return mode;
    }

    /**
     * Returns the smooth scroll mode.
     *
     * @return the mode, possibly {@code null}
     */
    public SmoothScrollMode getMode() {
        return mode.get();
    }

    /**
     * Sets the smooth scroll mode.
     *
     * @param value the mode, or {@code null} for the default
     */
    public void setMode(SmoothScrollMode value) {
        mode.set(value);
    }

    // ==================== Boundary Policy ====================

    private final ObjectProperty<ScrollBoundaryPolicy> boundaryPolicy =
            new SimpleObjectProperty<>(this, "boundaryPolicy", ScrollBoundaryPolicy.CHAIN);

    /**
     * Boundary policy used when wheel input reaches the scroll limits. A
     * {@code null} value is treated as {@link ScrollBoundaryPolicy#CHAIN}.
     *
     * @return the boundary policy property
     */
    public ObjectProperty<ScrollBoundaryPolicy> boundaryPolicyProperty() {
        return boundaryPolicy;
    }

    /**
     * Returns the boundary policy.
     *
     * @return the boundary policy, possibly {@code null}
     */
    public ScrollBoundaryPolicy getBoundaryPolicy() {
        return boundaryPolicy.get();
    }

    /**
     * Sets the boundary policy.
     *
     * @param value the boundary policy, or {@code null} for the default
     */
    public void setBoundaryPolicy(ScrollBoundaryPolicy value) {
        boundaryPolicy.set(value);
    }

    // ==================== Shift Wheel Horizontal ====================

    private final BooleanProperty shiftWheelHorizontal =
            new SimpleBooleanProperty(this, "shiftWheelHorizontal", true);

    /**
     * Whether Shift+wheel maps vertical wheel input to horizontal scrolling when
     * horizontal scrolling is writable.
     *
     * @return the Shift+wheel horizontal property
     */
    public BooleanProperty shiftWheelHorizontalProperty() {
        return shiftWheelHorizontal;
    }

    /**
     * Returns whether Shift+wheel horizontal mapping is enabled.
     *
     * @return {@code true} when enabled
     */
    public boolean isShiftWheelHorizontal() {
        return shiftWheelHorizontal.get();
    }

    /**
     * Sets whether Shift+wheel horizontal mapping is enabled.
     *
     * @param value {@code true} to enable mapping
     */
    public void setShiftWheelHorizontal(boolean value) {
        shiftWheelHorizontal.set(value);
    }

    // ==================== Reduced Motion ====================

    private final BooleanProperty reducedMotion = new SimpleBooleanProperty(this, "reducedMotion", false) {
        @Override
        protected void invalidated() {
            if (get()) {
                engine.stop();
            }
        }
    };

    /**
     * Whether reduced-motion mode is active. When active, supported wheel input
     * is applied immediately through the same boundary policy.
     *
     * @return the reduced-motion property
     */
    public BooleanProperty reducedMotionProperty() {
        return reducedMotion;
    }

    /**
     * Returns whether reduced-motion mode is active.
     *
     * @return {@code true} when reduced-motion mode is active
     */
    public boolean isReducedMotion() {
        return reducedMotion.get();
    }

    /**
     * Sets whether reduced-motion mode is active.
     *
     * @param value {@code true} to apply input immediately
     */
    public void setReducedMotion(boolean value) {
        reducedMotion.set(value);
    }

    // ==================== Lifecycle ====================

    /**
     * Stops active animations without uninstalling the scroller.
     */
    public void stop() {
        engine.stop();
    }

    /**
     * Disposes this scroller. The method is idempotent.
     *
     * @throws IllegalStateException if called off the JavaFX Application Thread
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        RXSmoothScrollSupport.checkFxThread();
        disposed = true;
        detachEventNode();
        detachContent(observedContent);
        scrollPane.contentProperty().removeListener(contentListener);
        scrollPane.skinProperty().removeListener(skinListener);
        scrollPane.sceneProperty().removeListener(sceneListener);
        scrollPane.hvalueProperty().removeListener(scrollValueListener);
        scrollPane.vvalueProperty().removeListener(scrollValueListener);
        engine.dispose();
        if (scrollPane.getProperties().get(RXSmoothScrollSupport.SCROLLER_KEY) == this) {
            scrollPane.getProperties().remove(RXSmoothScrollSupport.SCROLLER_KEY);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        dispose();
    }

    /**
     * Returns whether this scroller has been disposed.
     *
     * @return {@code true} after disposal
     */
    public boolean isDisposed() {
        return disposed;
    }

    // ==================== Internals ====================

    private void onScroll(ScrollEvent event) {
        if (disposed || event.isConsumed() || event.isDirect()) {
            return;
        }
        boolean consume = engine.handleScroll(event, getAxis(), getDuration(), getInterpolator(),
                getWheelMultiplier(), getMode(), getBoundaryPolicy(), isShiftWheelHorizontal(), isReducedMotion(),
                isEnabled(), false);
        if (consume) {
            event.consume();
        }
    }

    private void onExternalScrollValueChanged() {
        if (disposed || adapter.isWritingScrollValue() || !engine.isRunning()) {
            return;
        }
        engine.stop();
        engine.snapToCurrentOffsets();
    }

    private void attachContent(Node content) {
        observedContent = content;
        if (content != null) {
            content.parentProperty().addListener(contentParentListener);
        }
    }

    private void detachContent(Node content) {
        if (content != null) {
            content.parentProperty().removeListener(contentParentListener);
        }
        if (observedContent == content) {
            observedContent = null;
        }
    }

    private void reattachEventNode() {
        if (disposed) {
            return;
        }
        Node next = adapter.eventNode();
        if (eventNode == next) {
            return;
        }
        detachEventNode();
        eventNode = next;
        if (eventNode != null) {
            eventNode.addEventHandler(ScrollEvent.SCROLL, scrollHandler);
        }
    }

    private void detachEventNode() {
        if (eventNode != null) {
            eventNode.removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
            eventNode = null;
        }
    }
}
