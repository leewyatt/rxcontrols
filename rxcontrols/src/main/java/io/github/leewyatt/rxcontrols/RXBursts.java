package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.internal.burst.BurstSettings;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Plays particle bursts on any node: a plain button, a label, an image, a
 * list cell or a point in a pane. Nothing about the node changes; the burst
 * is drawn in the nearest {@link RXOverlayPane} above the node, on top of
 * everything else in that pane.
 *
 * <pre>{@code
 * // Every click of an ordinary button bursts from its center.
 * button.addEventHandler(ActionEvent.ACTION, e -> RXBursts.play(button));
 *
 * // A burst wherever the user clicks in a pane.
 * pane.setOnMouseClicked(e -> RXBursts.playAt(pane, e.getX(), e.getY()));
 *
 * // Another preset, or full settings.
 * RXBursts.play(button, BurstAnimation.CONFETTI);
 * RXBursts.play(button, RXBurstOptions.builder().animation(BurstAnimation.FIREWORKS).scale(2).build());
 * }</pre>
 *
 * <p>The node needs an {@code RXOverlayPane} above it, usually the one
 * wrapping the scene root. Without one nothing is played, and a warning is
 * logged the first time. A burst stays in the window of its node: to play
 * one in a popup window, wrap the popup's content in an overlay pane.</p>
 *
 * <p>The burst overlay is never picked by the mouse. A burst follows its
 * node if the node moves while it plays, and keeps its last position if the
 * node leaves the scene. Several bursts can play at once; beyond 16 in one
 * overlay pane, the oldest is ended.</p>
 *
 * <p>Because the overlay is above everything in its pane, a burst from a
 * node behind a dialog's scrim draws over the scrim, and nothing clips it
 * but the pane.</p>
 *
 * <p>Without a color in the options, the colors derive from the theme's
 * accent color: Modena's {@code -fx-accent}, or AtlantaFX's accent with the
 * AtlantaFX bridge installed. A stylesheet changes that default through the
 * overlay's style class, e.g.
 * {@code .root .rx-burst-layer { -rx-burst-color: #ff5d73; }} (the
 * {@code .root} prefix outranks the AtlantaFX bridge's own mapping).</p>
 *
 * <p>Call these methods on the JavaFX application thread. A node that is
 * not in a scene plays nothing.</p>
 */
public final class RXBursts {

    private static final Logger LOGGER = Logger.getLogger(RXBursts.class.getName());

    // A missing overlay pane is a setup mistake: one warning says it, not one per click.
    private static boolean missingPaneWarned;

    private RXBursts() {
    }

    /**
     * Plays the default burst from the center of a node.
     *
     * @param anchor the node to burst from
     * @throws NullPointerException if {@code anchor} is {@code null}
     */
    public static void play(Node anchor) {
        play(anchor, RXBurstOptions.defaults());
    }

    /**
     * Plays a burst from the center of a node with the default settings
     * otherwise.
     *
     * @param anchor    the node to burst from
     * @param animation the burst to play, or {@code null} to play nothing
     * @throws NullPointerException if {@code anchor} is {@code null}
     */
    public static void play(Node anchor, BurstAnimation animation) {
        play(anchor, RXBurstOptions.builder().animation(animation).build());
    }

    /**
     * Plays a burst from the center of a node.
     *
     * @param anchor  the node to burst from
     * @param options the burst settings
     * @throws NullPointerException if {@code anchor} or {@code options} is
     *                              {@code null}
     */
    public static void play(Node anchor, RXBurstOptions options) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(options, "options");
        playIn(anchor, Double.NaN, Double.NaN, options);
    }

    /**
     * Plays the default burst at a point of a node.
     *
     * @param anchor the node the point belongs to
     * @param x      the burst center x, in the node's coordinates
     * @param y      the burst center y, in the node's coordinates
     * @throws NullPointerException if {@code anchor} is {@code null}
     * @throws IllegalArgumentException if {@code x} or {@code y} is not finite
     */
    public static void playAt(Node anchor, double x, double y) {
        playAt(anchor, x, y, RXBurstOptions.defaults());
    }

    /**
     * Plays a burst at a point of a node.
     *
     * @param anchor  the node the point belongs to
     * @param x       the burst center x, in the node's coordinates
     * @param y       the burst center y, in the node's coordinates
     * @param options the burst settings
     * @throws NullPointerException if {@code anchor} or {@code options} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code x} or {@code y} is not finite
     */
    public static void playAt(Node anchor, double x, double y, RXBurstOptions options) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(options, "options");
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("the burst point must be finite: " + x + ", " + y);
        }
        playIn(anchor, x, y, options);
    }

    private static void playIn(Node anchor, double x, double y, RXBurstOptions options) {
        BurstAnimation animation = options.getAnimation();
        Duration duration = BurstSettings.durationOrDefault(options.getDuration(), RXBurstOptions.DEFAULT_DURATION);
        if (anchor.getScene() == null || animation == null || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        // Not through popups: a popup is a window of its own on top of its
        // owner, so a burst drawn in the owner's window would be behind it.
        RXOverlayPane pane = RXOverlayPane.nearest(anchor, false);
        if (pane == null) {
            if (!missingPaneWarned) {
                missingPaneWarned = true;
                LOGGER.warning(RXOverlayPane.missingPaneMessage("RXBursts", "anchor")
                        + " Nothing is played. A burst does not leave a popup window: wrap the"
                        + " popup's content to play one there. This is logged once.");
            }
            return;
        }
        pane.ensureBurstLayer().play(anchor, x, y, options, animation, duration);
    }
}
