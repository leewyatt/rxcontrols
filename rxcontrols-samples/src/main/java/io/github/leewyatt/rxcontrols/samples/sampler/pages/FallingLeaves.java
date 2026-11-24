package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.Random;

/**
 * Autumn leaves for the photo samples. As a layer, the leaves keep falling while it plays
 * and stop in the air when it pauses, like a paused video. The carousel slideshow uses
 * the leaf and the fall on their own.
 */
final class FallingLeaves extends Pane {

    static final String STYLESHEET =
            FallingLeaves.class.getResource("rx-falling-leaves.css").toExternalForm();

    private static final String[] COLORS = {"red", "orange", "amber"};

    private final Region[] leaves;
    private final Timeline[] falls;
    private final Random random = new Random(7L);
    private boolean playing;

    /**
     * Creates a layer of leaves that fall across it while it plays.
     *
     * @param count        how many leaves are in the air at most
     * @param cornerRadius the corner radius of the picture under the layer
     */
    FallingLeaves(int count, double cornerRadius) {
        leaves = new Region[count];
        falls = new Timeline[count];
        getStylesheets().add(STYLESHEET);
        setMouseTransparent(true);
        // Sized by its parent: the leaves waiting above the top edge must not grow it.
        setMinSize(0.0, 0.0);
        setPrefSize(0.0, 0.0);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(cornerRadius * 2.0);
        clip.setArcHeight(cornerRadius * 2.0);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
    }

    /**
     * Starts the leaves falling, or lets them go on from where they stopped.
     */
    void play() {
        if (playing) {
            return;
        }
        playing = true;
        for (int i = 0; i < falls.length; i++) {
            if (falls[i] == null) {
                drop(i, i * 0.6);
            } else {
                falls[i].play();
            }
        }
    }

    /**
     * Stops the leaves where they are.
     */
    void pause() {
        playing = false;
        for (Timeline fall : falls) {
            if (fall != null) {
                fall.pause();
            }
        }
    }

    // Sends a leaf down again from above the top edge, with a new size, place and path.
    private void drop(int index, double delaySeconds) {
        double size = 14.0 + random.nextDouble() * 10.0;
        Region leaf = leaves[index];
        if (leaf == null) {
            leaf = leaf(size, index);
            leaves[index] = leaf;
            getChildren().add(leaf);
        } else {
            leaf.setPrefSize(size, size * 1.2);
        }
        leaf.setTranslateX(0.0);
        leaf.setTranslateY(0.0);
        leaf.setRotate(0.0);
        leaf.relocate(getWidth() * (0.15 + random.nextDouble() * 0.85), -size * 2.0);
        Timeline fall = fall(leaf, -60.0 - random.nextDouble() * 80.0, getHeight() + size * 3.0, 22.0,
                180.0 + random.nextDouble() * 360.0, Duration.seconds(5.0 + random.nextDouble() * 3.0));
        fall.setDelay(Duration.seconds(delaySeconds));
        fall.setOnFinished(event -> drop(index, random.nextDouble() * 1.5));
        falls[index] = fall;
        fall.play();
    }

    /**
     * Creates a leaf; its shape and color come from {@link #STYLESHEET}, which a parent
     * of the leaf must load.
     *
     * @param size  the leaf width; the leaf is a fifth taller
     * @param index picks one of the autumn colors in turn
     * @return the leaf
     */
    static Region leaf(double size, int index) {
        Region leaf = new Region();
        leaf.getStyleClass().addAll("leaf", COLORS[index % COLORS.length]);
        leaf.setPrefSize(size, size * 1.2);
        return leaf;
    }

    /**
     * Creates a fall: the node drops by dy while drifting by dx, swaying sideways and
     * turning on the way.
     *
     * @param node  the falling node
     * @param dx    the sideways drift
     * @param dy    the drop
     * @param sway  how far it swings to each side of its path
     * @param turn  how many degrees it turns
     * @param time  how long the fall takes
     * @return the fall, not started
     */
    static Timeline fall(Node node, double dx, double dy, double sway, double turn, Duration time) {
        return new Timeline(
                new KeyFrame(time.multiply(0.33),
                        new KeyValue(node.translateXProperty(), dx * 0.33 + sway, Interpolator.EASE_BOTH)),
                new KeyFrame(time.multiply(0.66),
                        new KeyValue(node.translateXProperty(), dx * 0.66 - sway, Interpolator.EASE_BOTH)),
                new KeyFrame(time,
                        new KeyValue(node.translateXProperty(), dx, Interpolator.EASE_BOTH),
                        new KeyValue(node.translateYProperty(), dy, Interpolator.LINEAR),
                        new KeyValue(node.rotateProperty(), turn, Interpolator.LINEAR)));
    }
}
