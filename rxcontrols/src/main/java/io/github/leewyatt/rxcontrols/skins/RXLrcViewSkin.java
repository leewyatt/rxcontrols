package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.pojo.LrcDoc;
import io.github.leewyatt.rxcontrols.pojo.LrcLine;
import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <p>
 * 歌词组件的皮肤
 */
public class RXLrcViewSkin extends SkinBase<RXLrcView> {

    private RXLrcView control;
    private Pane lrcPane;
    private final Pane root;
    private final Label tipLabel;
    private int currentIndex;
    private Timeline lrcPaneMoveAnim;
    private ParallelTransition moveAndScaleAnim;
    private Timeline reboundUp;
    private Timeline reboundDown;

    /**
     * 回弹用时
     */
    private final static Duration REBOUND_DURATION = Duration.millis(150);
    /**
     * 开始拖动的Y坐标
     */
    private double startDragY;
    /**
     * 上一次移动距离
     */
    private double lastMoveDis;
    /**
     * 歌词行移动 (尝试过整体移动,性能上并没有比单行移动的效果强...)
     */
    private TranslateTransition[] lineMove;
    /**
     * 变小动画
     */
    private ScaleTransition smallST;
    /**
     * 变大动画
     */
    private ScaleTransition bigST;

    private final ChangeListener<LrcDoc> lrcDocChangeListener = (observable, oldValue, newValue) -> paintLrcLines();

    private final InvalidationListener invalidationListener = observable -> paintLrcLines();

    private final ChangeListener<Duration> durationChangeListener = (observable, oldValue, newValue) -> {
        if (emptyLrcDoc()) {
            return;
        }
        int lastIndex = computeLrcLinesIndex();
        if (currentIndex == lastIndex) {
            return;
        }
        moveLrcLines(lastIndex);
    };

    public RXLrcViewSkin(RXLrcView control) {
        super(control);
        this.control = control;
        lrcPaneMoveAnim = new Timeline();
        moveAndScaleAnim = new ParallelTransition();
        reboundUp = new Timeline();
        reboundDown = new Timeline();
        lineMove = new TranslateTransition[0];
        smallST = new ScaleTransition();
        bigST = new ScaleTransition();

        root = new Pane();
        //lrc-view-root
        root.getStyleClass().add("pane");
        tipLabel = new Label();
        //lrc-view-tip
        tipLabel.getStyleClass().add("tip-label");
        tipLabel.textProperty().bind(control.tipStringProperty());
        lrcPane = new Pane();
        //lrc-view-pane
        lrcPane.getStyleClass().add("lrc-pane");
        root.getChildren().add(lrcPane);
        clipRoot(control, root);
        getChildren().setAll(root);
        paintLrcLines();

        // 歌词文件被替换成其他歌词文件的时候, 重绘歌词
        control.lrcDocProperty().addListener(lrcDocChangeListener);

        // 当组件的宽发生改变时,重绘歌词
        control.widthProperty().addListener(invalidationListener);

        //当组件的高发生改变时,重绘歌词
        control.heightProperty().addListener(invalidationListener);

        control.borderProperty().addListener(invalidationListener);

        control.paddingProperty().addListener(invalidationListener);

        //歌词文件的播放进度放生改变时,移动歌词/
        control.currentTimeProperty().addListener(durationChangeListener);

        //-------让LrcPane 可以手动移动,去查看歌词其他部分的内容---------
        // 鼠标按下
        control.addEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        //鼠标拖动
        control.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        //鼠标释放
        control.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        //行高改变时
        control.lineHeightProperty().addListener(invalidationListener);
    }

    private final EventHandler<MouseEvent> mousePressedHandler = event -> startDragY = event.getY() - lrcPane.getLayoutY();
    private final EventHandler<MouseEvent> mouseDraggedHandler = event -> {
        double offsetY = 0;
        if (lrcPane.getChildren().size() != 0) {
            offsetY = lrcPane.getChildren().get(0).getTranslateY();
        }

        lastMoveDis = event.getY() - startDragY;
        double bottomBound = computeInnerH(control) / 2 + control.getLineHeight() / 2 - control.getLineHeight() - offsetY;

        if (lastMoveDis > bottomBound) {
            lastMoveDis = bottomBound;
        }
        double topBound = -lrcPane.getHeight() - offsetY;
        if (lastMoveDis < topBound) {
            lastMoveDis = topBound;
        }
        lrcPane.setLayoutY(lastMoveDis);
    };

    private EventHandler<MouseEvent> mouseReleasedHandler = event -> {
        double offsetY = 0;
        if (lrcPane.getChildren().size() != 0) {
            offsetY = lrcPane.getChildren().get(0).getTranslateY();
        }
        //低于底部后 ,自动往上的动画
        double x = computeBorderSize(control, false, false, true, false) +
                computePaddingSize(control, false, false, true, false);

        if (lrcPane.getLayoutY() >= computeInnerH(control) / 2 - control.getLineHeight() / 2 - control.getLineHeight() - offsetY - x) {
            if (reboundUp.getStatus() == Animation.Status.RUNNING) {
                reboundUp.stop();
            }
            reboundUp.getKeyFrames().setAll(new KeyFrame(REBOUND_DURATION, new KeyValue(
                    lrcPane.layoutYProperty(),
                    computeInnerH(control) / 2 - control.getLineHeight() / 2 - control.getLineHeight() - offsetY,
                    Interpolator.EASE_OUT)));
            reboundUp.play();
        }
        //高于顶部后,自动往下的动画
        if (lrcPane.getLayoutY() <= -lrcPane.getHeight() + control.getLineHeight() - offsetY) {
            if (reboundDown.getStatus() == Animation.Status.RUNNING) {
                reboundDown.stop();
            }
            reboundDown.getKeyFrames().setAll(new KeyFrame(
                    REBOUND_DURATION,
                    new KeyValue(
                            lrcPane.layoutYProperty(),
                            -lrcPane.getHeight() + control.getLineHeight() - offsetY,
                            Interpolator.EASE_OUT)
            ));

            reboundDown.play();
        }
    };

    private void moveLrcLines(int newIndex) {
        if (newIndex < 0) {
            return;
        }
        animeStopAtEnd(moveAndScaleAnim);
        lrcPaneMoveAnim.getKeyFrames().clear();
        Duration duration = control.getAnimationTime();
        // 歌词面板的整体移动
        if (Double.compare(lrcPane.getLayoutY(), 0) != 0) {
            lrcPaneMoveAnim.getKeyFrames().setAll(new KeyFrame(duration,
                    new KeyValue(lrcPane.layoutYProperty(), 0)
            ));
        }
        moveAndScaleAnim.getChildren().clear();
        ArrayList<LrcLine> lines = control.getLrcDoc().getLrcLines();
        double moveDistance = -(newIndex - currentIndex) * control.getLineHeight();
        // 歌词并行移动
        for (int i = 0; i < lines.size(); i++) {
            LrcLineLabel node = (LrcLineLabel) lrcPane.getChildren().get(i);
            lineMove[i].setDuration(duration);
            lineMove[i].setNode(node);
            lineMove[i].setByY(moveDistance);
            if (newIndex == i) {
//                StyleUtil.addClass(node, "lrc-current-line");
                node.setPlaying(true);
            } else {
//                StyleUtil.removeClass(node, "lrc-current-line");
                node.setPlaying(false);
                if (i != currentIndex) {
                    node.setScaleX(1.0);
                    node.setScaleY(1.0);
                }
            }
        }
        moveAndScaleAnim.getChildren().addAll(lineMove);
        // 当前歌词缩小
        if (currentIndex != -1) {
            Label node = (Label) lrcPane.getChildren().get(currentIndex);
            smallST.setNode(node);
            smallST.setDuration(duration);
            smallST.setToX(1);
            smallST.setToY(1);
            moveAndScaleAnim.getChildren().add(smallST);
        }
        // 下一句歌词放大
        Label node = (Label) lrcPane.getChildren().get(newIndex);
        bigST.setNode(node);
        bigST.setDuration(duration);
        bigST.setToX(control.getCurrentLineScaling());
        bigST.setToY(control.getCurrentLineScaling());
        moveAndScaleAnim.getChildren().addAll(bigST, lrcPaneMoveAnim);
        moveAndScaleAnim.play();
        currentIndex = newIndex;
    }

    private void paintLrcLines() {
        lrcPane.getChildren().clear();
        if (emptyLrcDoc()) {
            lrcPane.getChildren().add(tipLabel);
            tipLabel.layoutXProperty().bind(root.widthProperty().subtract(tipLabel.widthProperty()).divide(2.0));
            tipLabel.layoutYProperty().bind(root.heightProperty().subtract(tipLabel.heightProperty()).divide(2.0));
            return;
        }
        //如果歌词不为空, 那么准备绘制歌词
        int index = computeLrcLinesIndex();
        ArrayList<LrcLine> lines = control.getLrcDoc().getLrcLines();
        for (int i = 0; i < lines.size(); i++) {
            LrcLineLabel label = new LrcLineLabel(lines.get(i).getWords());
//            label.getStyleClass().add("lrc-line");
            if (index == i) {
//                StyleUtil.addClass(label, "lrc-current-line");
                label.setPlaying(true);
            } else {
//                StyleUtil.removeClass(label, "lrc-current-line");
                label.setPlaying(false);
            }
            label.setLayoutY(
                    (i + 1) * control.getLineHeight()
                            + (computeInnerH(control)
                            - control.getLineHeight()) / 2);
            label.setPrefHeight(control.getLineHeight());
            label.prefWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> computeInnerW(control),
                    control.layoutBoundsProperty(),
                    control.paddingProperty(),
                    control.borderProperty()));
            lrcPane.getChildren().add(label);
        }

        currentIndex = -1;

        // 改变行数的时候, 根据需要增减动画
        int newSize = lines.size();
        int oldSize = lineMove.length;
        lineMove = Arrays.copyOf(lineMove, newSize);
        for (int i = oldSize; i < newSize; i++) {
            lineMove[i] = new TranslateTransition();
        }
        moveLrcLines(index);

    }

    /**
     * @return 当前应该播放的是第几行的歌词
     */
    private int computeLrcLinesIndex() {
        LrcDoc lrcDoc = control.getLrcDoc();
        if (emptyLrcDoc()) {
            return -1;
        }
        long now = (long) control.getCurrentTime().add(Duration.millis(lrcDoc.getOffset())).add(control.getUserOffset()).toMillis();
        ArrayList<LrcLine> lrcLines = lrcDoc.getLrcLines();
        int size = lrcLines.size();

        if (now < lrcLines.get(0).getTime() - 1) {
            return -1;
        }
        for (int i = 0; i < size - 1; i++) {
            if (now >= lrcLines.get(i).getTime() && now < lrcLines.get(i + 1).getTime()) {
                return i;
            }
        }
        return size - 1;
    }

    private boolean emptyLrcDoc() {
        return control.getLrcDoc() == null ||
                control.getLrcDoc().getLrcLines() == null ||
                control.getLrcDoc().getLrcLines().size() == 0;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        layoutInArea(root, x, y, w, h, -1, HPos.CENTER, VPos.CENTER);
    }

    @Override
    public void dispose() {
        control.lrcDocProperty().removeListener(lrcDocChangeListener);
        control.widthProperty().removeListener(invalidationListener);
        control.heightProperty().removeListener(invalidationListener);
        control.borderProperty().removeListener(invalidationListener);
        control.paddingProperty().removeListener(invalidationListener);
        control.currentTimeProperty().removeListener(durationChangeListener);
        control.removeEventHandler(MouseEvent.MOUSE_PRESSED, mousePressedHandler);
        control.removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        control.removeEventHandler(MouseEvent.MOUSE_RELEASED, mouseReleasedHandler);
        animeStop(
                reboundUp, reboundDown, moveAndScaleAnim, lrcPaneMoveAnim, smallST, bigST);
        animeStop(lineMove);
        getChildren().clear();
        super.dispose();
    }

    // ==================== Migrated UIUtil helpers (legacy, pending refactor) ====================
    //
    // The helpers below were lifted from io.github.leewyatt.rxcontrols.utils.UIUtil during the
    // 2026-05 review. Behaviour is intentionally preserved as-is — known issues are called out
    // with TODO markers and will be addressed when RXLrcViewSkin itself is refactored. These
    // helpers (along with their TODO markers and the inherited Chinese Javadoc) are temporary;
    // remove them entirely during the rewrite rather than keeping them as long-term residents.

    /**
     * 获得组件的真实内部宽, 去掉了边框和内边距
     *
     * @param control 指定的组件
     */
    // TODO(refactor): redundant, not a correctness bug. For resizable nodes (Region/Control)
    //   layoutBounds is always `(0, 0, width, height)` — effect/clip/transforms do NOT extend
    //   it (see Node#layoutBoundsProperty javadoc) — so `maxX - minX` is just a verbose way
    //   to write `getWidth()`. Padding and border are also already merged by Region.getInsets(),
    //   and snapped*Inset() adds pixel snapping for free.
    //   Suggested simplification:
    //     return control.getWidth() - control.snappedLeftInset() - control.snappedRightInset();
    //   Skin-idiomatic preference: drop this helper entirely and inline `snappedLeftInset()` /
    //   `snappedRightInset()` (protected on SkinBase) at the call sites. The current call sites
    //   are outside layoutChildren (mouse handlers, paintLrcLines, clipRoot binding), so the
    //   `layoutChildren(x, y, w, h)` parameters are not available there.
    //   Better still: reshape the source of layout state — cache the content-area size in Skin
    //   fields updated from layoutChildren — instead of mechanically recomputing it from
    //   `control` at every call site.
    private static double computeInnerW(Control control) {
        if (control == null) {
            return 0;
        }
        double width = 0;
        Bounds bounds = control.getLayoutBounds();
        if (bounds != null) {
            width = bounds.getMaxX() - bounds.getMinX();
        }
        double paddingWidth = 0;
        Insets padding = control.getPadding();
        if (padding != null) {
            paddingWidth = padding.getLeft() + padding.getRight();
        }
        double borderWidth = 0;
        if (control.getBorder() != null && control.getBorder().getInsets() != null) {
            Insets insets = control.getBorder().getInsets();
            borderWidth = insets.getLeft() + insets.getRight();
        }
        return width - borderWidth - paddingWidth;
    }

    /**
     * 获得组件的真实内部高, 去掉了边框和内边距
     *
     * @param control 指定的组件
     */
    // TODO(refactor): same redundancy as computeInnerW (layoutBounds for Region/Control is
    //   just `(0, 0, width, height)`), plus a style inconsistency — this variant delegates to
    //   computeBorderSize/computePaddingSize while computeInnerW inlines the math.
    //   Suggested simplification:
    //     return control.getHeight() - control.snappedTopInset() - control.snappedBottomInset();
    //   Skin-idiomatic preference: same as computeInnerW — drop the helper and inline
    //   `snappedTopInset()` / `snappedBottomInset()` at call sites.
    private static double computeInnerH(Control control) {
        if (control == null) {
            return 0;
        }
        double height = 0;
        Bounds bounds = control.getLayoutBounds();
        if (bounds != null) {
            height = bounds.getMaxY() - bounds.getMinY();
        }
        double borderHeight = computeBorderSize(control, true, false, true, false);
        double paddingHeight = computePaddingSize(control, true, false, true, false);
        return height - borderHeight - paddingHeight;
    }

    /**
     * 获得组件的内边距长度
     *
     * @param control
     * @param top     上边距
     * @param right   右边距
     * @param bottom  下边距
     * @param left    左边距
     * @return
     */
    // TODO(bug): NPE — `control.getPadding()` is dereferenced before the null-check on `control`
    //   (lines below), so a null `control` triggers NPE instead of returning 0.
    // TODO(api): four-boolean edge mask is hard to read at call sites (see the
    //   `(false, false, true, false)` invocation in mouseReleasedHandler). Prefer
    //   `control.snappedTopInset()` / `snappedBottomInset()` etc, which already merge
    //   padding + border and apply pixel snapping.
    private static double computePaddingSize(Control control, boolean top, boolean right, boolean bottom, boolean left) {
        double paddingSize = 0;
        Insets insets = control.getPadding();
        if (control == null || insets == null) {
            return paddingSize;
        }
        return getSize(top, right, bottom, left, insets);
    }

    /**
     * 获取组件的边框大小
     * @param control
     * @param top 上边框
     * @param right 有边框
     * @param bottom 下边框
     * @param left 左边框
     * @return
     */
    // TODO(doc): Javadoc typo — "有边框" should be "右边框".
    // TODO(api): same four-boolean redundancy as computePaddingSize; replace call sites with
    //   `snappedXxxInset()` (which already includes border insets) when refactoring.
    private static double computeBorderSize(Control control, boolean top, boolean right, boolean bottom, boolean left) {
        double borderSize = 0;
        if (control == null || control.getBorder() == null || control.getBorder().getInsets() == null) {
            return borderSize;
        }
        Insets insets = control.getBorder().getInsets();
        return getSize(top, right, bottom, left, insets);
    }

    private static double getSize(boolean top, boolean right, boolean bottom, boolean left, Insets insets) {
        double size = 0;
        if (top) {
            size += insets.getTop();
        }
        if (right) {
            size += insets.getRight();
        }
        if (bottom) {
            size += insets.getBottom();
        }
        if (left) {
            size += insets.getLeft();
        }
        return size;
    }

    /**
     * 根据组件的 位置, 边框, 内边距来计算根节点内容面积的大小
     * @param control 组件
     * @param root 根节点
     */
    // Note: keep the clip at (0, 0). `root` is already laid out at (snappedLeftInset,
    //   snappedTopInset) by RXLrcViewSkin#layoutChildren — see Control#layoutChildren in
    //   OpenJFX which feeds inset-offset coordinates into SkinBase#layoutChildren(x,y,w,h).
    //   Clip coordinates are in the clipped node's local space (AGENTS §3.2), so root's
    //   local origin already equals the content area's top-left. Binding rect.x/rect.y to
    //   snapped insets here would double-offset and crop the clip incorrectly.
    // TODO(perf): `boundsInParentProperty` is in the dependency list but never read inside
    //   the binding lambda; it triggers redundant recomputes on transform / parent changes.
    //   Drop it from both bindings.
    // TODO(lifecycle): Rectangle is created and bound but never returned, so dispose() cannot
    //   `unbind` / `setClip(null)` and the binding keeps a strong ref to `control`. Either
    //   inline this into the skin and store the Rectangle as a field, or return it for the
    //   caller to manage. Required by AGENTS §3.1 (skin dispose must be exhaustive).
    private static void clipRoot(Control control, Pane root) {
        Rectangle rect = new Rectangle();
        rect.widthProperty().bind(Bindings.createDoubleBinding(
                () -> computeInnerW(control),
                control.boundsInParentProperty(),
                control.borderProperty(),
                control.paddingProperty(),
                control.widthProperty()));

        rect.heightProperty().bind(Bindings.createDoubleBinding(
                () -> computeInnerH(control),
                control.boundsInParentProperty(),
                control.borderProperty(),
                control.paddingProperty(),
                control.heightProperty()));
        root.setClip(rect);
    }

    /**
     * 动画跳转到最后面然后停止.
     * @param animations 动画
     */
    // TODO(semantics): for animations with cycleCount = INDEFINITE, `getTotalDuration()`
    //   returns Duration.INDEFINITE. Verified against JFX 17.0.13 Animation#jumpTo:
    //   isUnknown() throws IAE, but isIndefinite() does NOT — the impl silently falls back to
    //   `getCycleDuration().toMillis()`. So this call effectively jumps to ONE cycle's end, not
    //   "the end of the animation". That may or may not be the desired pose; the call site
    //   should decide explicitly (e.g. pass `getCycleDuration()` directly, or just `stop()`
    //   without jumping for infinite animations). Note: javafx.animation.Animation has no
    //   public `jumpToEnd()` API — do not suggest one.
    // TODO(semantics): the `status != STOPPED` guard means an already-stopped animation will
    //   NOT be moved to its end frame, contradicting the method name. Decide whether the
    //   contract is "make sure the animation ends at the end-frame regardless of status".
    // TODO(rename): "anime" is an informal abbreviation; rename to `finishAndStop` when
    //   refactoring.
    private static void animeStopAtEnd(Animation... animations) {
        if (animations == null || animations.length == 0) {
            return;
        }
        for (Animation animation : animations) {
            if (animation == null) {
                continue;
            }
            if (animation.getStatus() != Animation.Status.STOPPED) {
                animation.jumpTo(animation.getTotalDuration());
                animation.stop();
            }
        }
    }

    // TODO(rename): align with `animeStopAtEnd` — rename to `stopAnimations` for clarity.
    private static void animeStop(Animation... animations) {
        if (animations == null || animations.length == 0) {
            return;
        }
        for (Animation animation : animations) {
            if (animation == null) {
                continue;
            }
            animation.stop();
        }
    }

}

class LrcLineLabel extends Label {
    private static final PseudoClass PLAYING_PSEUDO_CLASS = PseudoClass.getPseudoClass("playing");
    private BooleanProperty playing;

    public LrcLineLabel() {
        getStyleClass().setAll("lrc-line");
        playingProperty().addListener(
                (observable, oldValue, newValue) -> pseudoClassStateChanged(PLAYING_PSEUDO_CLASS, newValue));
    }

    public LrcLineLabel(String text) {
        this();
        setText(text);
    }

    public LrcLineLabel(String text, Node graphic) {
        this(text);
        setGraphic(graphic);
    }

    public final void setPlaying(boolean playing) {
        playingProperty().set(playing);
    }

    public final boolean getPlaying() {
        return playing == null ? false : playing.get();
    }

    public final BooleanProperty playingProperty() {
        if (playing == null) {
            playing = new SimpleBooleanProperty(false);
        }
        return playing;
    }
}