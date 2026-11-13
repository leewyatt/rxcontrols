package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXBurstOptions;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.internal.burst.BurstPlayback;
import io.github.leewyatt.rxcontrols.internal.burst.BurstSettings;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Per-scene overlay layer that plays bursts for {@code RXBursts}: one small
 * canvas per burst, drawn above everything else in the scene, dialogs
 * included, and never picked by the mouse.
 *
 * <p>The layer is long-lived like the snackbar layer: it mounts on first use
 * and stays installed, because unmounting after every burst would re-wrap a
 * non-{@code Pane} scene root each time, taking along any dialog opened in
 * the meantime. Canvases leave the layer when their burst ends and are
 * reused. The layer clips to its own bounds so that a burst never grows the
 * window of a popup it plays in. Each frame, a burst re-reads where its
 * anchor is, so it follows a node that scrolls or moves.</p>
 *
 * <p>The layer carries the style class {@code rx-burst-layer} and resolves
 * its default color from the theme through {@code -rx-burst-color}. It is
 * not a control, so the {@code -rx-*} tokens are not declared on it; the
 * stylesheets map the color straight to each theme's accent.</p>
 */
public final class RXBurstLayer extends RXSceneOverlayLayer {

    private static final Object LAYER_KEY = new Object();
    private static final String STYLE_CLASS = "rx-burst-layer";
    // In front of the dialog layer (-20): a burst fired from a dialog shows.
    private static final double VIEW_ORDER = -30.0;
    // Bursts beyond this many at once cancel the oldest.
    private static final int MAX_BURSTS = 16;
    private static final Paint FALLBACK_COLOR = Color.web("#0096c9");
    // Idle canvases kept for reuse, and the largest one worth keeping: a
    // pooled canvas holds its texture.
    private static final int MAX_IDLE = 4;
    private static final double MAX_IDLE_SIDE = 512.0;
    // Automatic particle size: a burst centered on a node sizes with it
    // within these bounds; a burst at a point uses the fixed size.
    private static final double MIN_AUTO_SIZE = 16.0;
    private static final double MAX_AUTO_SIZE = 48.0;
    private static final double POINT_SIZE = 24.0;

    private final Deque<Burst> active = new ArrayDeque<>();
    private final Deque<Burst> idle = new ArrayDeque<>();

    private RXBurstLayer() {
        super(LAYER_KEY, VIEW_ORDER);
        getStyleClass().add(STYLE_CLASS);
        setMouseTransparent(true);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                cancelAll();
            }
        });
    }

    /**
     * Plays one burst for an anchor node.
     *
     * @param anchor  the node the burst follows; must be in a scene
     * @param x       the burst center x in the anchor's coordinates, or
     *                {@code NaN} for the center of the anchor
     * @param y       the burst center y in the anchor's coordinates, or
     *                {@code NaN} for the center of the anchor
     * @param options the burst settings
     */
    public static void play(Node anchor, double x, double y, RXBurstOptions options) {
        Scene scene = anchor.getScene();
        BurstAnimation animation = options.getAnimation();
        Duration duration = BurstSettings.durationOrDefault(options.getDuration(), RXBurstOptions.DEFAULT_DURATION);
        if (scene == null || animation == null || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        layerFor(scene).start(anchor, x, y, options, animation, duration);
    }

    private static RXBurstLayer layerFor(Scene scene) {
        Object cached = scene.getProperties().get(LAYER_KEY);
        if (cached instanceof RXBurstLayer layer && layer.getScene() == scene) {
            return layer;
        }
        if (cached instanceof RXBurstLayer stale) {
            // Orphaned off-scene (its mount point went away): unhook it and
            // rebuild.
            stale.uninstall();
        }
        RXBurstLayer fresh = new RXBurstLayer();
        fresh.installInto(scene, null);
        // Resolves -rx-burst-color now, so the very first burst has it.
        fresh.applyCss();
        return fresh;
    }

    private void start(Node anchor, double x, double y, RXBurstOptions options,
                       BurstAnimation animation, Duration duration) {
        while (active.size() >= MAX_BURSTS) {
            finish(active.peekFirst());
        }
        Burst burst = idle.isEmpty() ? new Burst() : idle.pop();
        burst.anchor = anchor;
        burst.centered = Double.isNaN(x) || Double.isNaN(y);
        burst.anchorX = x;
        burst.anchorY = y;
        burst.size = options.getSize() > 0.0 && Double.isFinite(options.getSize())
                ? options.getSize()
                : autoSize(anchor, burst.centered);
        burst.reach = BurstSettings.reach(burst.size,
                BurstSettings.scaleOrDefault(options.getScale(), RXBurstOptions.DEFAULT_SCALE));
        List<Paint> colors = BurstSettings.palette(options.getPalette(),
                options.getColor() != null ? options.getColor() : getBurstColor(), FALLBACK_COLOR);
        active.addLast(burst);
        getChildren().add(burst.playback.getCanvas());
        burst.playback.play(animation, duration, colors, BurstSettings.nextSeed());
    }

    private double autoSize(Node anchor, boolean centered) {
        if (!centered) {
            return POINT_SIZE;
        }
        Bounds bounds = sceneToLocal(anchor.localToScene(anchor.getLayoutBounds(), true), true);
        double size = bounds == null ? POINT_SIZE : Math.min(bounds.getWidth(), bounds.getHeight());
        return Math.max(MIN_AUTO_SIZE, Math.min(MAX_AUTO_SIZE, size));
    }

    // Ends a burst early or on time: its canvas leaves the layer and waits
    // for reuse.
    private void finish(Burst burst) {
        burst.playback.cancel();
        active.remove(burst);
        getChildren().remove(burst.playback.getCanvas());
        burst.anchor = null;
        if (idle.size() < MAX_IDLE && burst.canvas.getWidth() <= MAX_IDLE_SIDE) {
            idle.push(burst);
        }
    }

    private void cancelAll() {
        for (Burst burst : new ArrayList<>(active)) {
            finish(burst);
        }
    }

    // ==================== Burst ====================

    private final class Burst {

        private final Canvas canvas = new Canvas();
        private final BurstPlayback playback;
        private Node anchor;
        private boolean centered;
        private double anchorX;
        private double anchorY;
        private double size;
        private double reach;
        // The last center in layer coordinates, kept once the anchor leaves.
        private Point2D center = Point2D.ZERO;

        private Burst() {
            canvas.setManaged(false);
            canvas.setMouseTransparent(true);
            playback = new BurstPlayback(canvas, () -> reach, () -> size, this::place, () -> finish(this));
        }

        private void place() {
            Node node = anchor;
            if (node != null && node.getScene() == getScene()) {
                Bounds bounds = node.getLayoutBounds();
                double x = centered ? bounds.getMinX() + bounds.getWidth() / 2.0 : anchorX;
                double y = centered ? bounds.getMinY() + bounds.getHeight() / 2.0 : anchorY;
                Point2D local = sceneToLocal(node.localToScene(x, y, true), true);
                if (local != null) {
                    center = local;
                }
            }
            double side = canvas.getWidth();
            // Snapped to device pixels, so particles stay sharp at any
            // render scale.
            canvas.relocate(snapPositionX(center.getX() - side / 2.0), snapPositionY(center.getY() - side / 2.0));
        }
    }

    // ==================== Theme color ====================

    private final ObjectProperty<Paint> burstColor = new StyleableObjectProperty<>() {
        @Override
        public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
            return StyleableProperties.BURST_COLOR;
        }

        @Override
        public Object getBean() {
            return RXBurstLayer.this;
        }

        @Override
        public String getName() {
            return "burstColor";
        }
    };

    private Paint getBurstColor() {
        return burstColor.get();
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    private static final class StyleableProperties {

        private static final CssMetaData<RXBurstLayer, Paint> BURST_COLOR =
                new CssMetaData<>("-rx-burst-color", PaintConverter.getInstance()) {
                    @Override
                    public boolean isSettable(RXBurstLayer layer) {
                        return !layer.burstColor.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXBurstLayer layer) {
                        return (StyleableProperty<Paint>) layer.burstColor;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(StackPane.getClassCssMetaData());
            styleables.add(BURST_COLOR);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }
}
