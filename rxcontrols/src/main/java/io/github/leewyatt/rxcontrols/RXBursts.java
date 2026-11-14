package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.internal.RXBurstLayer;
import javafx.scene.Node;

import java.util.Objects;

/**
 * Plays particle bursts on any node: a plain button, a label, an image, a
 * list cell or a point in a pane. Nothing about the node changes; the burst
 * is drawn on a per-scene overlay above everything else in the scene.
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
 * <p>The overlay mounts on the first burst in a scene and stays: over a
 * {@code Pane} scene root it is an extra unmanaged child; any other root is
 * wrapped in a new root pane that leaves it where it was, as for dialogs and
 * snackbars. It is never
 * picked by the mouse. A burst follows its node if the node moves while it
 * plays, and keeps its last position if the node leaves the scene. Several
 * bursts can play at once; beyond 16 in one scene, the oldest is ended.</p>
 *
 * <p>Because the overlay is above everything, a burst from a node behind a
 * dialog's scrim draws over the scrim, and nothing clips it but the window:
 * a burst in a popup window stops at the popup's edges. Over a wrapped root,
 * closing a dialog that was opened before the overlay mounted restores the
 * root and ends the bursts still playing.</p>
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
        RXBurstLayer.play(anchor, Double.NaN, Double.NaN, options);
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
        RXBurstLayer.play(anchor, x, y, options);
    }
}
