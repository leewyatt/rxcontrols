package io.github.leewyatt.rxcontrols.internal.ripple;

import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Ripple feedback decoration shared by ripple hosts ({@code RXButton} via its
 * skin, {@code RXRipplePane}, and future controls such as checkbox / radio /
 * list cell): a bounded ripple layer plus a low-opacity hover state overlay,
 * clipped to the host's painted geometry.
 *
 * <p>The decoration owns the layer, the ripple state machine, the hover
 * overlay (enter/exit driven, gated by its own enabled property and the
 * disabled state), the shared lifecycle that releases or clears the ripple
 * when the host is disabled, leaves the scene, or turns the ripple off, and
 * the clip refresh as the host geometry or the optional ripple insets / corner
 * radius change. The host keeps its own ripple <em>trigger</em> — armed-driven
 * for a button, pointer-press for a pane — and calls
 * {@link #press(double, double, boolean)} / {@link #release()} accordingly.</p>
 *
 * <p>The host inserts {@link #getLayer()} into its children at the desired
 * z-order, calls {@link #layout(double, double)} from its layout pass, and
 * (when it is a skin) calls {@link #dispose()} from its dispose chain;
 * children removal stays with the host.</p>
 */
public final class RippleDecoration {

    private final Region host;
    private final ObservableValue<Boolean> rippleEnabled;
    private final ObservableValue<Boolean> hoverOverlayEnabled;
    private final ObservableValue<Insets> rippleInsets;
    private final ObservableValue<CornerRadii> rippleCornerRadius;
    private final RippleLayer layer = new RippleLayer();
    private final RippleBehavior behavior;
    private final SkinDisposer disposer = new SkinDisposer();

    private boolean pointerInside;

    /**
     * Creates the decoration and wires the hover overlay, shared lifecycle and
     * clip-refresh listeners on the host.
     *
     * @param host               the region carrying the ripple
     * @param rippleEnabled      whether press ripple interaction is enabled
     *                           (clears live ripple state when off)
     * @param hoverOverlayEnabled whether the hover state overlay is enabled
     * @param rippleFill         the ripple and overlay fill
     * @param rippleOpacity      the peak ripple opacity
     * @param rippleInsets       extra clip insets, or {@code null} if the host
     *                           has no insets override
     * @param rippleCornerRadius explicit clip corner radii, or {@code null} if
     *                           the host has no corner-radius override
     * @throws NullPointerException if {@code host}, {@code rippleEnabled},
     *                              {@code hoverOverlayEnabled}, {@code rippleFill}
     *                              or {@code rippleOpacity} is {@code null}
     */
    public RippleDecoration(Region host,
                            ObservableValue<Boolean> rippleEnabled,
                            ObservableValue<Boolean> hoverOverlayEnabled,
                            ObservableValue<Paint> rippleFill,
                            DoubleSupplier rippleOpacity,
                            ObservableValue<Insets> rippleInsets,
                            ObservableValue<CornerRadii> rippleCornerRadius) {
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.rippleEnabled = Objects.requireNonNull(rippleEnabled, "rippleEnabled cannot be null");
        this.hoverOverlayEnabled = Objects.requireNonNull(
                hoverOverlayEnabled, "hoverOverlayEnabled cannot be null");
        Objects.requireNonNull(rippleFill, "rippleFill cannot be null");
        Objects.requireNonNull(rippleOpacity, "rippleOpacity cannot be null");
        this.rippleInsets = rippleInsets;
        this.rippleCornerRadius = rippleCornerRadius;
        this.behavior = new RippleBehavior(layer, rippleFill::getValue, rippleOpacity);
        layer.setOverlayFill(rippleFill.getValue());

        // Hover state overlay: a low-opacity tint while the pointer is inside.
        disposer.registerEventHandler(host, MouseEvent.MOUSE_ENTERED, event -> {
            pointerInside = true;
            updateOverlay();
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_EXITED, event -> {
            pointerInside = false;
            updateOverlay();
        });
        disposer.registerListener(rippleFill, () -> layer.setOverlayFill(rippleFill.getValue()));
        disposer.registerListener(hoverOverlayEnabled, this::updateOverlay);

        // Shared lifecycle.
        disposer.registerListener(rippleEnabled, () -> {
            if (!isRippleEnabled()) {
                behavior.clear();
            }
            updateOverlay();
        });
        disposer.registerListener(host.disabledProperty(), () -> {
            if (host.isDisabled()) {
                behavior.release();
            }
            updateOverlay();
        });
        disposer.registerListener(host.sceneProperty(), () -> {
            if (host.getScene() == null) {
                pointerInside = false;
                clear();
            }
        });

        // Keep the bounded clip fresh as the host geometry or clip-shape inputs change.
        disposer.registerListener(host.backgroundProperty(), host::requestLayout);
        disposer.registerListener(host.shapeProperty(), host::requestLayout);
        disposer.registerListener(host.scaleShapeProperty(), host::requestLayout);
        disposer.registerListener(host.centerShapeProperty(), host::requestLayout);
        if (rippleInsets != null) {
            disposer.registerListener(rippleInsets, host::requestLayout);
        }
        if (rippleCornerRadius != null) {
            disposer.registerListener(rippleCornerRadius, host::requestLayout);
        }
    }

    /**
     * Returns the ripple layer for the host to insert into its children.
     *
     * @return the ripple layer
     */
    public RippleLayer getLayer() {
        return layer;
    }

    /**
     * Starts a ripple at the given host-local coordinates.
     *
     * @param x        the host-local x coordinate
     * @param y        the host-local y coordinate
     * @param centered whether to ignore the coordinates and use the center
     */
    public void press(double x, double y, boolean centered) {
        behavior.press(x, y, centered);
    }

    /**
     * Releases the active ripple.
     */
    public void release() {
        behavior.release();
    }

    /**
     * Stops all ripple animations, removes the ripple nodes and clears the
     * bounded clip (the hover overlay is torn down with the clip).
     */
    public void clear() {
        behavior.clear();
        layer.clearClip();
    }

    /**
     * Lays out the ripple layer over the host bounds and refreshes the clip
     * from the host's optional ripple insets and corner radius. An invalid
     * (zero or non-finite) size clears live ripple state and collapses the
     * layer.
     *
     * @param width  the host width
     * @param height the host height
     */
    public void layout(double width, double height) {
        if (width <= 0.0 || height <= 0.0
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            clear();
            layer.resizeRelocate(0.0, 0.0, 0.0, 0.0);
            return;
        }
        Insets insets = rippleInsets == null ? null : rippleInsets.getValue();
        CornerRadii radius = rippleCornerRadius == null ? null : rippleCornerRadius.getValue();
        layer.resizeRelocate(0.0, 0.0, width, height);
        layer.updateClipFor(host, width, height, insets, radius);
        // Re-sync the overlay from current state: a prior zero-size layout tears
        // it down via clear(), and hovering does not re-fire MOUSE_ENTERED, so
        // it must be restored here rather than left off.
        updateOverlay();
    }

    /**
     * Clears live ripple state and unregisters all listeners; the host removes
     * {@link #getLayer()} from its children itself.
     */
    public void dispose() {
        clear();
        disposer.dispose();
    }

    private void updateOverlay() {
        layer.setOverlayState(isHoverOverlayEnabled() && pointerInside && !host.isDisabled());
    }

    private boolean isRippleEnabled() {
        return Boolean.TRUE.equals(rippleEnabled.getValue());
    }

    private boolean isHoverOverlayEnabled() {
        return Boolean.TRUE.equals(hoverOverlayEnabled.getValue());
    }
}
