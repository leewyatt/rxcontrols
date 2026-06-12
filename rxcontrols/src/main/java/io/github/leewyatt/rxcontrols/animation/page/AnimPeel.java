package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Page peel transition. The current page peels away from a corner like
 * turning a book page, revealing the next page beneath. A fold-back
 * region shows the "underside" of the peeled page.
 *
 * <p>Two fold-line endpoints start at the peel corner and travel along
 * adjacent edges at constant speed:
 * <ul>
 *   <li><b>Endpoint A</b> follows the shorter adjacent edge then
 *       continues along the far edge (total path = height + width).</li>
 *   <li><b>Endpoint B</b> follows the longer adjacent edge
 *       (total path = width).</li>
 * </ul>
 * Both advance proportionally to animation progress, so the fold line
 * transitions smoothly from a corner triangle into a near-vertical
 * spine sweep with no phase discontinuity. The initial fold angle is
 * determined naturally by the page's aspect ratio.</p>
 *
 * <p>The fold-back region is computed by mirroring the entire peeled
 * polygon across the fold line and clipping to the page bounds.</p>
 *
 * <p>FORWARD peels from the bottom-right corner; BACKWARD peels from
 * the bottom-left corner.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane dimensions
 * on every frame to compute the peel geometry.</p>
 */
public class AnimPeel extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private Color foldBackColor = Color.rgb(230, 230, 230);
    private Color foldShadowColor = Color.rgb(0, 0, 0, 0.15);

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private StackPane animContentPane;
    private Polygon foldBack;
    private Polygon foldShadow;
    private boolean forward;

    /**
     * Creates a page peel animation.
     */
    public AnimPeel() {
    }

    /**
     * Returns the color of the fold-back region (page underside).
     *
     * @return the fold-back color
     */
    public Color getFoldBackColor() {
        return foldBackColor;
    }

    /**
     * Sets the fold-back color. Default is light gray.
     *
     * @param foldBackColor the color
     */
    public void setFoldBackColor(Color foldBackColor) {
        this.foldBackColor = foldBackColor;
    }

    /**
     * Returns the fold shadow color drawn along the fold crease.
     *
     * @return the shadow color
     */
    public Color getFoldShadowColor() {
        return foldShadowColor;
    }

    /**
     * Sets the fold shadow color. Default is semi-transparent black.
     *
     * @param foldShadowColor the shadow color
     */
    public void setFoldShadowColor(Color foldShadowColor) {
        this.foldShadowColor = foldShadowColor;
    }

    /**
     * Returns the interpolator used for the animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the animation.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        animContentPane = context.getContentPane();

        forward = direction == TransitionDirection.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        foldBack = new Polygon();
        foldBack.setFill(foldBackColor);
        foldBack.setManaged(false);

        foldShadow = new Polygon();
        foldShadow.setFill(foldShadowColor);
        foldShadow.setManaged(false);

        animContentPane.getChildren().addAll(foldBack, foldShadow);
        foldBack.toFront();
        foldShadow.toFront();

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
            }
            removeFoldShapes();
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updatePeel(p, w, h, currentPage);
        };
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    private void updatePeel(double p, double w, double h, Node currentPage) {
        if (currentPage == null) {
            return;
        }

        if (p >= 0.99) {
            currentPage.setClip(null);
            currentPage.setVisible(false);
            foldBack.getPoints().clear();
            foldShadow.getPoints().clear();
            return;
        }

        if (p <= 0.01) {
            currentPage.setClip(null);
            foldBack.getPoints().clear();
            foldShadow.getPoints().clear();
            return;
        }

        // --- Fold line endpoints via unified edge-path parameterization ---
        //
        // Both endpoints start at the peel corner and travel along page
        // edges at constant speed proportional to progress:
        //
        //   A path (total = h + w):
        //     corner → along short edge (length h) → along far edge (length w)
        //   B path (total = w):
        //     corner → along bottom edge (length w)
        //
        // This produces a smooth fold angle that starts at atan(w / (h+w))
        // (determined by aspect ratio) and decreases continuously to 0.

        double ax, ay, bx, by;
        double cornerX, cornerY;

        if (forward) {
            cornerX = w;
            cornerY = h;

            double distA = (h + w) * p;
            if (distA <= h) {
                ax = w;
                ay = h - distA;
            } else {
                ax = w - (distA - h);
                ay = 0;
            }

            bx = w - w * p;
            by = h;
        } else {
            cornerX = 0;
            cornerY = h;

            double distA = (h + w) * p;
            if (distA <= h) {
                ax = 0;
                ay = h - distA;
            } else {
                ax = distA - h;
                ay = 0;
            }

            bx = w * p;
            by = h;
        }

        // --- Normal of fold line, pointing toward the peel corner ---

        double abx = bx - ax;
        double aby = by - ay;
        double lenSq = abx * abx + aby * aby;

        if (lenSq < 1) {
            currentPage.setClip(null);
            foldBack.getPoints().clear();
            foldShadow.getPoints().clear();
            return;
        }

        double pnx = -aby;
        double pny = abx;
        double midX = (ax + bx) / 2.0;
        double midY = (ay + by) / 2.0;
        if (pnx * (cornerX - midX) + pny * (cornerY - midY) < 0) {
            pnx = -pnx;
            pny = -pny;
        }

        // --- Clip currentPage to the visible (non-peeled) half-plane ---

        List<double[]> pageRect = makePageRect(w, h);
        List<double[]> visiblePoly = clipByHalfPlane(
                copyPoly(pageRect), midX, midY, -pnx, -pny);

        if (visiblePoly.size() >= 3) {
            setPolygonAsClip(currentPage, visiblePoly);
        } else {
            currentPage.setClip(null);
        }

        // --- Peeled polygon (corner side of fold line within page) ---

        List<double[]> peeledPoly = clipByHalfPlane(
                copyPoly(pageRect), midX, midY, pnx, pny);

        // --- Fold-back = mirror entire peeled polygon, clip to page ---

        if (peeledPoly.size() >= 3) {
            List<double[]> mirrored = new ArrayList<>(peeledPoly.size());
            for (double[] v : peeledPoly) {
                mirrored.add(mirrorAcrossLine(v[0], v[1], ax, ay, bx, by));
            }
            List<double[]> foldBackPoly = clipToPage(mirrored, w, h);

            if (foldBackPoly.size() >= 3) {
                setPolygonPoints(foldBack, foldBackPoly);
            } else {
                foldBack.getPoints().clear();
            }
        } else {
            foldBack.getPoints().clear();
        }

        // --- Shadow strip along the fold crease (on the visible side) ---

        double normLen = Math.sqrt(pnx * pnx + pny * pny);
        double shadowWidth = 6;
        double snx = -pnx / normLen * shadowWidth;
        double sny = -pny / normLen * shadowWidth;

        foldShadow.getPoints().setAll(
                ax, ay, bx, by,
                bx + snx, by + sny,
                ax + snx, ay + sny);
    }

    // ---- Polygon geometry helpers ----

    private static void setPolygonAsClip(Node target, List<double[]> poly) {
        Polygon clip = new Polygon();
        for (double[] v : poly) {
            clip.getPoints().addAll(v[0], v[1]);
        }
        target.setClip(clip);
    }

    private static void setPolygonPoints(Polygon polygon, List<double[]> poly) {
        polygon.getPoints().clear();
        for (double[] v : poly) {
            polygon.getPoints().addAll(v[0], v[1]);
        }
    }

    private static List<double[]> makePageRect(double w, double h) {
        List<double[]> rect = new ArrayList<>(4);
        rect.add(new double[]{0, 0});
        rect.add(new double[]{w, 0});
        rect.add(new double[]{w, h});
        rect.add(new double[]{0, h});
        return rect;
    }

    private static List<double[]> copyPoly(List<double[]> poly) {
        List<double[]> copy = new ArrayList<>(poly.size());
        for (double[] v : poly) {
            copy.add(new double[]{v[0], v[1]});
        }
        return copy;
    }

    /**
     * Sutherland-Hodgman single-edge clip: keeps the half-plane where
     * (point - linePoint) dot normal >= 0.
     */
    private static List<double[]> clipByHalfPlane(List<double[]> poly,
                                                  double lx, double ly,
                                                  double nx, double ny) {
        List<double[]> out = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] curr = poly.get(i);
            double[] next = poly.get((i + 1) % n);
            double dCurr = (curr[0] - lx) * nx + (curr[1] - ly) * ny;
            double dNext = (next[0] - lx) * nx + (next[1] - ly) * ny;
            if (dCurr >= 0) {
                out.add(curr);
            }
            if ((dCurr >= 0) != (dNext >= 0)) {
                double t = dCurr / (dCurr - dNext);
                out.add(new double[]{
                        curr[0] + t * (next[0] - curr[0]),
                        curr[1] + t * (next[1] - curr[1])
                });
            }
        }
        return out;
    }

    /**
     * Clips a polygon to the rectangle (0,0)-(w,h).
     */
    private static List<double[]> clipToPage(List<double[]> poly,
                                             double w, double h) {
        poly = clipByHalfPlane(poly, 0, 0, 1, 0);
        poly = clipByHalfPlane(poly, 0, 0, 0, 1);
        poly = clipByHalfPlane(poly, w, 0, -1, 0);
        poly = clipByHalfPlane(poly, 0, h, 0, -1);
        return poly;
    }

    /**
     * Mirrors point (px, py) across the line through (ax, ay)-(bx, by).
     */
    private static double[] mirrorAcrossLine(double px, double py,
                                             double ax, double ay,
                                             double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-10) {
            return new double[]{px, py};
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
        double footX = ax + t * dx;
        double footY = ay + t * dy;
        return new double[]{2 * footX - px, 2 * footY - py};
    }

    private void removeFoldShapes() {
        if (animContentPane != null) {
            if (foldBack != null) {
                animContentPane.getChildren().remove(foldBack);
                foldBack = null;
            }
            if (foldShadow != null) {
                animContentPane.getChildren().remove(foldShadow);
                foldShadow = null;
            }
        }
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.setClip(null);
        }
        removeFoldShapes();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeFoldShapes();
        super.dispose();
    }
}