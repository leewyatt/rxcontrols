package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.event.RXLrcLineEvent;
import io.github.leewyatt.rxcontrols.lrc.RXLrcDocument;
import io.github.leewyatt.rxcontrols.lrc.RXLrcLine;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Skin for {@link RXLrcView}.
 */
public class RXLrcViewSkin extends RXSkinBase<RXLrcView> {

    // ==================== Constants ====================

    private static final int NO_LINE_INDEX = -1;
    private static final int SEEK_JUMP_THRESHOLD = 1;
    private static final double DEFAULT_PREF_WIDTH = 280.0;
    private static final double DEFAULT_PREF_HEIGHT = 360.0;
    private static final double BOUNDARY_RESISTANCE = 0.35;
    private static final double CLICK_SUPPRESSION_DISTANCE = 3.0;
    private static final Duration REBOUND_DURATION = Duration.millis(180.0);
    private static final Interpolator INTERPOLATOR =
            Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0);
    private static final PseudoClass CURRENT_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("current");

    // ==================== Nodes ====================

    private final Pane viewport = new ManualPane();
    private final Pane content = new ManualPane();
    private final Rectangle clip = new Rectangle();
    private final List<LineNode> lineNodes = new ArrayList<>();

    // ==================== Geometry ====================

    private double[] lineTops = new double[0];
    private double[] lineHeights = new double[0];
    private double contentTotalHeight;
    private boolean metricsDirty = true;
    private long lastLineTimeMillis = Long.MIN_VALUE;

    // ==================== Animation ====================

    private final DoubleProperty autoTranslateY = new SimpleDoubleProperty();
    private final DoubleProperty manualOffsetY = new SimpleDoubleProperty();
    private final Timeline scrollAnim = new Timeline();
    private final Timeline reboundAnim = new Timeline();
    private final Timeline recoverAnim = new Timeline();
    private final PauseTransition recoverPause = new PauseTransition();

    // ==================== Manual Browse ====================

    private double dragStartY;
    private double dragStartManualOffsetY;
    private boolean dragging;
    private boolean manualBrowsing;
    private boolean suppressNextClick;

    // ==================== Listeners ====================

    private final ChangeListener<Number> currentLineIndexListener =
            (observable, oldValue, newValue) -> onCurrentLineIndexChanged(
                    oldValue.intValue(), newValue.intValue());
    private final ChangeListener<RXLrcDocument> documentListener =
            (observable, oldValue, newValue) -> onDocumentChanged();
    private final ChangeListener<Node> placeholderListener =
            (observable, oldValue, newValue) -> onPlaceholderChanged(oldValue, newValue);
    private final Runnable metricsInvalidationAction = () -> {
        metricsDirty = true;
        getSkinnable().requestLayout();
    };
    private final Runnable scaleInvalidationAction =
            () -> applyCurrentLineScale(getSkinnable().getCurrentLineIndex());
    private final Runnable manualBrowseEnabledInvalidationAction = () -> {
        if (!getSkinnable().isManualBrowseEnabled()) {
            stopDraggingAndRecover();
        }
    };
    private final Runnable translateInvalidationAction = this::applyDisplayTranslate;
    private final EventHandler<MouseEvent> lineClickHandler = this::onLineClicked;
    private final EventHandler<MouseEvent> browseMousePressedHandler = this::onBrowseMousePressed;
    private final EventHandler<MouseEvent> browseMouseDraggedHandler = this::onBrowseMouseDragged;
    private final EventHandler<MouseEvent> browseMouseReleasedHandler = this::onBrowseMouseReleased;
    private final EventHandler<MouseEvent> browseMouseClickedHandler = this::onBrowseMouseClicked;
    private final EventHandler<ScrollEvent> browseScrollHandler = this::onBrowseScroll;

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXLrcViewSkin(RXLrcView control) {
        super(control);

        viewport.getStyleClass().add("viewport");
        content.getStyleClass().add("content");
        viewport.setClip(clip);
        viewport.getChildren().add(content);
        installPlaceholder(control.getPlaceholder());
        getChildren().setAll(viewport);

        scrollAnim.setCycleCount(1);
        reboundAnim.setCycleCount(1);
        reboundAnim.setOnFinished(event -> onReboundFinished());
        recoverAnim.setCycleCount(1);
        recoverAnim.setOnFinished(event -> onRecoverFinished());
        recoverPause.setOnFinished(event -> startRecoverAnimation());

        disposer.registerListener(control.currentLineIndexProperty(), currentLineIndexListener);
        disposer.registerListener(control.documentProperty(), documentListener);
        disposer.registerListener(control.placeholderProperty(), placeholderListener);
        disposer.registerListener(control.lineSpacingProperty(), metricsInvalidationAction);
        disposer.registerListener(control.currentLinePositionProperty(), metricsInvalidationAction);
        disposer.registerListener(control.currentLineScaleProperty(), scaleInvalidationAction);
        disposer.registerListener(control.manualBrowseEnabledProperty(), manualBrowseEnabledInvalidationAction);
        disposer.registerListener(autoTranslateY, translateInvalidationAction);
        disposer.registerListener(manualOffsetY, translateInvalidationAction);
        disposer.registerEventHandler(content, MouseEvent.MOUSE_CLICKED, lineClickHandler);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_PRESSED, browseMousePressedHandler);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_DRAGGED, browseMouseDraggedHandler);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_RELEASED, browseMouseReleasedHandler);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_CLICKED, browseMouseClickedHandler);
        disposer.registerEventHandler(viewport, ScrollEvent.SCROLL, browseScrollHandler);
        disposer.registerDisposeTask(scrollAnim::stop);
        disposer.registerDisposeTask(reboundAnim::stop);
        disposer.registerDisposeTask(recoverAnim::stop);
        disposer.registerDisposeTask(recoverPause::stop);
        disposer.registerDisposeTask(() -> viewport.setClip(null));

        rebuildLineNodes();
        updatePlaceholderState();
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double width = Math.max(0.0, w);
        double height = Math.max(0.0, h);
        viewport.resizeRelocate(x, y, width, height);
        clip.setX(0.0);
        clip.setY(0.0);
        clip.setWidth(width);
        clip.setHeight(height);

        measureLines(width, height);

        content.resizeRelocate(0.0, 0.0, width, contentTotalHeight);
        for (int i = 0; i < lineNodes.size(); i++) {
            lineNodes.get(i).resizeRelocate(0.0, lineTops[i], width, lineHeights[i]);
        }

        updateTranslateAfterLayout();
        layoutPlaceholder(width, height);
        applyDisplayTranslate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + DEFAULT_PREF_HEIGHT + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    // ==================== Document ====================

    private void onDocumentChanged() {
        resetManualBrowseState();
        rebuildLineNodes();
        metricsDirty = true;
        lastLineTimeMillis = Long.MIN_VALUE;
        syncCurrentLineState();
        updatePlaceholderState();
        getSkinnable().requestLayout();
    }

    private void rebuildLineNodes() {
        lineNodes.clear();
        RXLrcDocument document = getSkinnable().getDocument();
        if (document != null) {
            for (RXLrcLine line : document.lines()) {
                lineNodes.add(new LineNode(line));
            }
        }
        content.getChildren().setAll(lineNodes);
        lineTops = new double[lineNodes.size()];
        lineHeights = new double[lineNodes.size()];
        metricsDirty = true;
    }

    // ==================== Placeholder ====================

    private void onPlaceholderChanged(Node oldValue, Node newValue) {
        if (oldValue != null) {
            viewport.getChildren().remove(oldValue);
        }
        installPlaceholder(newValue);
        updatePlaceholderState();
        getSkinnable().requestLayout();
    }

    private void installPlaceholder(Node placeholder) {
        if (placeholder == null) {
            return;
        }
        if (!placeholder.getStyleClass().contains("placeholder")) {
            placeholder.getStyleClass().add("placeholder");
        }
        placeholder.setTranslateY(manualOffsetY.get());
        if (!viewport.getChildren().contains(placeholder)) {
            viewport.getChildren().add(placeholder);
        }
    }

    private void updatePlaceholderState() {
        Node placeholder = getSkinnable().getPlaceholder();
        boolean empty = isDocumentEmpty();
        if (placeholder != null) {
            placeholder.setVisible(empty);
            placeholder.setManaged(empty);
        }
        content.setVisible(!empty);
        content.setManaged(!empty);
    }

    private void layoutPlaceholder(double width, double height) {
        Node placeholder = getSkinnable().getPlaceholder();
        if (placeholder == null || !placeholder.isVisible()) {
            return;
        }
        layoutInArea(placeholder, 0.0, 0.0, width, height, 0.0, HPos.CENTER, VPos.CENTER);
    }

    private boolean isDocumentEmpty() {
        RXLrcDocument document = getSkinnable().getDocument();
        return document == null || document.isEmpty();
    }

    // ==================== Current Line ====================

    private void onCurrentLineIndexChanged(int oldIndex, int newIndex) {
        applyCurrentLineState(oldIndex, false);
        applyCurrentLineState(newIndex, true);

        if (metricsDirty || lineTops.length != lineNodes.size()) {
            getSkinnable().requestLayout();
            updateLastLineTime(newIndex);
            return;
        }

        double target = targetTranslateY(newIndex, viewport.getHeight());
        if (manualBrowsing) {
            double displayedTranslate = displayTranslateY();
            boolean reboundRunning = reboundAnim.getStatus() == Animation.Status.RUNNING;
            boolean recoverRunning = recoverAnim.getStatus() == Animation.Status.RUNNING;
            scrollAnim.stop();
            reboundAnim.stop();
            recoverAnim.stop();
            autoTranslateY.set(target);
            manualOffsetY.set(displayedTranslate - target);
            if (reboundRunning) {
                startReboundThenRecover();
            } else if (recoverRunning) {
                startRecoverAnimation();
            }
            updateLastLineTime(newIndex);
            return;
        }

        applyScroll(oldIndex, newIndex, target);
        updateLastLineTime(newIndex);
    }

    private void syncCurrentLineState() {
        for (LineNode lineNode : lineNodes) {
            lineNode.pseudoClassStateChanged(CURRENT_PSEUDO_CLASS, false);
            lineNode.setScaleX(1.0);
            lineNode.setScaleY(1.0);
        }
        applyCurrentLineState(getSkinnable().getCurrentLineIndex(), true);
        updateLastLineTime(getSkinnable().getCurrentLineIndex());
    }

    private void applyCurrentLineState(int index, boolean current) {
        if (index < 0 || index >= lineNodes.size()) {
            return;
        }
        LineNode node = lineNodes.get(index);
        node.pseudoClassStateChanged(CURRENT_PSEUDO_CLASS, current);
        if (current) {
            double scale = getSkinnable().getCurrentLineScale();
            if (!Double.isFinite(scale) || scale <= 0.0) {
                scale = 1.0;
            }
            node.setScaleX(scale);
            node.setScaleY(scale);
        } else {
            node.setScaleX(1.0);
            node.setScaleY(1.0);
        }
    }

    private void applyCurrentLineScale(int index) {
        applyCurrentLineState(index, true);
    }

    // ==================== Geometry ====================

    private void measureLines(double width, double height) {
        double position = clamp(getSkinnable().getCurrentLinePosition(), 0.0, 1.0);
        double spacing = Math.max(0.0, getSkinnable().getLineSpacing());
        double y = height * position;

        for (int i = 0; i < lineNodes.size(); i++) {
            LineNode line = lineNodes.get(i);
            double lineHeight = Math.max(0.0, line.prefHeight(width));
            lineTops[i] = y;
            lineHeights[i] = lineHeight;
            y += lineHeight + spacing;
        }
        if (!lineNodes.isEmpty()) {
            y -= spacing;
        }
        contentTotalHeight = y + height * (1.0 - position);
        metricsDirty = false;
    }

    private double targetTranslateY(int index, double viewportHeight) {
        if (index < 0 || index >= lineNodes.size()) {
            return 0.0;
        }
        double position = clamp(getSkinnable().getCurrentLinePosition(), 0.0, 1.0);
        double anchorY = viewportHeight * position;
        return anchorY - (lineTops[index] + lineHeights[index] / 2.0);
    }

    private void updateTranslateAfterLayout() {
        scrollAnim.stop();
        if (isDocumentEmpty()) {
            autoTranslateY.set(0.0);
            if (!manualBrowsing) {
                manualOffsetY.set(0.0);
            }
            return;
        }

        double displayedTranslate = displayTranslateY();
        double target = targetTranslateY(getSkinnable().getCurrentLineIndex(), viewport.getHeight());
        autoTranslateY.set(target);
        if (manualBrowsing) {
            double preservedManualOffset = displayedTranslate - target;
            if (dragging) {
                manualOffsetY.set(applyBoundaryResistance(preservedManualOffset));
            } else {
                manualOffsetY.set(clampManualOffset(preservedManualOffset));
            }
        } else {
            manualOffsetY.set(0.0);
        }
    }

    private void applyDisplayTranslate() {
        content.setTranslateY(displayTranslateY());
        Node placeholder = getSkinnable().getPlaceholder();
        if (placeholder != null) {
            placeholder.setTranslateY(manualOffsetY.get());
        }
    }

    private double displayTranslateY() {
        return autoTranslateY.get() + manualOffsetY.get();
    }

    private double applyBoundaryResistance(double rawOffset) {
        if (!Double.isFinite(rawOffset)) {
            return 0.0;
        }
        if (isDocumentEmpty()) {
            return rawOffset * BOUNDARY_RESISTANCE;
        }
        double min = minManualOffset();
        double max = maxManualOffset();
        if (rawOffset < min) {
            return min + (rawOffset - min) * BOUNDARY_RESISTANCE;
        }
        if (rawOffset > max) {
            return max + (rawOffset - max) * BOUNDARY_RESISTANCE;
        }
        return rawOffset;
    }

    private double clampManualOffset(double offset) {
        if (!Double.isFinite(offset) || isDocumentEmpty()) {
            return 0.0;
        }
        return clamp(offset, minManualOffset(), maxManualOffset());
    }

    private double minManualOffset() {
        if (lineNodes.isEmpty()) {
            return 0.0;
        }
        return targetTranslateY(lineNodes.size() - 1, viewport.getHeight()) - autoTranslateY.get();
    }

    private double maxManualOffset() {
        if (lineNodes.isEmpty()) {
            return 0.0;
        }
        return targetTranslateY(0, viewport.getHeight()) - autoTranslateY.get();
    }

    // ==================== Animation ====================

    private void applyScroll(int oldIndex, int newIndex, double target) {
        stopManualAnimations();
        manualBrowsing = false;
        dragging = false;
        manualOffsetY.set(0.0);
        if (shouldSnap(oldIndex, newIndex) || !isPositiveFiniteAnimationDuration()) {
            scrollAnim.stop();
            autoTranslateY.set(target);
            return;
        }

        scrollAnim.stop();
        scrollAnim.getKeyFrames().setAll(new KeyFrame(
                getSkinnable().getAnimationDuration(),
                new KeyValue(autoTranslateY, target, INTERPOLATOR)));
        scrollAnim.playFromStart();
    }

    private boolean shouldSnap(int oldIndex, int newIndex) {
        if (oldIndex == NO_LINE_INDEX || newIndex == NO_LINE_INDEX) {
            return true;
        }
        if (Math.abs(newIndex - oldIndex) > SEEK_JUMP_THRESHOLD) {
            return true;
        }
        long newTimeMillis = lineTimeMillis(newIndex);
        return newTimeMillis < lastLineTimeMillis;
    }

    private boolean isPositiveFiniteAnimationDuration() {
        if (!getSkinnable().isAnimated()) {
            return false;
        }
        Duration duration = getSkinnable().getAnimationDuration();
        return duration != null
                && !duration.isUnknown()
                && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO)
                && Double.isFinite(duration.toMillis());
    }

    private void startReboundThenRecover() {
        scrollAnim.stop();
        recoverAnim.stop();
        recoverPause.stop();

        double target = clampManualOffset(manualOffsetY.get());
        if (Math.abs(manualOffsetY.get() - target) < 0.5) {
            manualOffsetY.set(target);
            onReboundFinished();
            return;
        }
        if (!getSkinnable().isAnimated()) {
            manualOffsetY.set(target);
            onReboundFinished();
            return;
        }

        reboundAnim.stop();
        reboundAnim.getKeyFrames().setAll(new KeyFrame(
                REBOUND_DURATION,
                new KeyValue(manualOffsetY, target, Interpolator.EASE_OUT)));
        reboundAnim.playFromStart();
    }

    private void onReboundFinished() {
        if (isDocumentEmpty()) {
            manualOffsetY.set(0.0);
            manualBrowsing = false;
            return;
        }
        scheduleRecover();
    }

    private void scheduleRecover() {
        scrollAnim.stop();
        reboundAnim.stop();
        recoverAnim.stop();
        recoverPause.stop();

        Duration delay = browseRecoverDelayOrDefault();
        if (delay.lessThanOrEqualTo(Duration.ZERO)) {
            startRecoverAnimation();
            return;
        }
        recoverPause.setDuration(delay);
        recoverPause.playFromStart();
    }

    private void startRecoverAnimation() {
        scrollAnim.stop();
        reboundAnim.stop();
        recoverAnim.stop();
        recoverPause.stop();

        if (isDocumentEmpty()) {
            manualOffsetY.set(0.0);
            manualBrowsing = false;
            return;
        }

        double displayedTranslate = displayTranslateY();
        double target = targetTranslateY(getSkinnable().getCurrentLineIndex(), viewport.getHeight());
        autoTranslateY.set(target);
        manualOffsetY.set(displayedTranslate - target);
        if (Math.abs(manualOffsetY.get()) < 0.5 || !isPositiveFiniteAnimationDuration()) {
            manualOffsetY.set(0.0);
            manualBrowsing = false;
            return;
        }

        recoverAnim.getKeyFrames().setAll(new KeyFrame(
                getSkinnable().getAnimationDuration(),
                new KeyValue(manualOffsetY, 0.0, INTERPOLATOR)));
        recoverAnim.playFromStart();
    }

    private void onRecoverFinished() {
        manualOffsetY.set(0.0);
        manualBrowsing = false;
    }

    private Duration browseRecoverDelayOrDefault() {
        Duration delay = getSkinnable().getBrowseRecoverDelay();
        if (delay == null
                || delay.isUnknown()
                || delay.isIndefinite()
                || !Double.isFinite(delay.toMillis())) {
            return RXLrcView.DEFAULT_BROWSE_RECOVER_DELAY;
        }
        return delay;
    }

    private void stopDraggingAndRecover() {
        dragging = false;
        if (manualBrowsing) {
            startRecoverAnimation();
        }
    }

    private void resetManualBrowseState() {
        stopAllAnimations();
        dragging = false;
        manualBrowsing = false;
        suppressNextClick = false;
        autoTranslateY.set(0.0);
        manualOffsetY.set(0.0);
    }

    private void stopAllAnimations() {
        scrollAnim.stop();
        stopManualAnimations();
    }

    private void stopManualAnimations() {
        reboundAnim.stop();
        recoverAnim.stop();
        recoverPause.stop();
    }

    private long lineTimeMillis(int index) {
        if (index < 0 || index >= lineNodes.size()) {
            return Long.MIN_VALUE;
        }
        return Math.round(lineNodes.get(index).getLine().time().toMillis());
    }

    private void updateLastLineTime(int index) {
        if (index >= 0 && index < lineNodes.size()) {
            lastLineTimeMillis = lineTimeMillis(index);
        }
    }

    // ==================== Events ====================

    private void onBrowseMousePressed(MouseEvent event) {
        if (!getSkinnable().isManualBrowseEnabled()) {
            return;
        }
        stopManualAnimations();
        dragging = true;
        dragStartY = event.getY();
        dragStartManualOffsetY = manualOffsetY.get();
        suppressNextClick = false;
    }

    private void onBrowseMouseDragged(MouseEvent event) {
        if (!dragging || !getSkinnable().isManualBrowseEnabled()) {
            return;
        }
        scrollAnim.stop();
        double deltaY = event.getY() - dragStartY;
        if (Math.abs(deltaY) > CLICK_SUPPRESSION_DISTANCE) {
            suppressNextClick = true;
        }
        manualBrowsing = true;
        manualOffsetY.set(applyBoundaryResistance(dragStartManualOffsetY + deltaY));
        event.consume();
    }

    private void onBrowseMouseReleased(MouseEvent event) {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (!manualBrowsing) {
            return;
        }
        startReboundThenRecover();
        if (suppressNextClick) {
            event.consume();
        }
    }

    private void onBrowseMouseClicked(MouseEvent event) {
        if (suppressNextClick) {
            suppressNextClick = false;
            event.consume();
        }
    }

    private void onBrowseScroll(ScrollEvent event) {
        if (!getSkinnable().isMouseWheelBrowseEnabled() || isDocumentEmpty()) {
            return;
        }
        stopAllAnimations();
        dragging = false;
        manualBrowsing = true;
        manualOffsetY.set(applyBoundaryResistance(manualOffsetY.get() + event.getDeltaY()));
        startReboundThenRecover();
        event.consume();
    }

    private void onLineClicked(MouseEvent event) {
        if (suppressNextClick) {
            suppressNextClick = false;
            event.consume();
            return;
        }
        LineNode lineNode = findLineNode(event.getTarget());
        if (lineNode == null) {
            return;
        }
        RXLrcLine line = lineNode.getLine();
        getSkinnable().fireEvent(new RXLrcLineEvent(
                getSkinnable(),
                RXLrcLineEvent.LINE_CLICKED,
                line,
                line.index(),
                line.time()));
    }

    private LineNode findLineNode(Object target) {
        if (!(target instanceof Node node)) {
            return null;
        }
        while (node != null && node != content) {
            if (node instanceof LineNode lineNode) {
                return lineNode;
            }
            Parent parent = node.getParent();
            node = parent;
        }
        return null;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        stopAllAnimations();
        lineNodes.clear();
        content.getChildren().clear();
        viewport.getChildren().clear();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    // ==================== Manual Pane ====================

    private static final class ManualPane extends Pane {

        @Override
        protected void layoutChildren() {
        }
    }

    // ==================== Line Node ====================

    private static final class LineNode extends Region {

        private final RXLrcLine line;
        private final Label text = new Label();

        private LineNode(RXLrcLine line) {
            this.line = line;
            getStyleClass().add("line");
            text.getStyleClass().add("text");
            text.setText(line.text());
            text.setWrapText(true);
            getChildren().add(text);
        }

        private RXLrcLine getLine() {
            return line;
        }

        @Override
        protected void layoutChildren() {
            text.resizeRelocate(0.0, 0.0, getWidth(), getHeight());
        }

        @Override
        protected double computeMinWidth(double height) {
            return 0.0;
        }

        @Override
        protected double computeMinHeight(double width) {
            return 0.0;
        }

        @Override
        protected double computePrefWidth(double height) {
            return text.prefWidth(height);
        }

        @Override
        protected double computePrefHeight(double width) {
            return text.prefHeight(width);
        }
    }
}
