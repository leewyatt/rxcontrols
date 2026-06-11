package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXFillButton.FillMode;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.BoundedClipSupport;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Skin for {@link RXFillButton}: the {@link RXButtonSkin} plus a fill
 * decoration layer between the button background and the ripple layer.
 *
 * <p>A single timeline drives an internal fill progress; the trigger state
 * (hover or pressed) plays it forward or, from the current progress, in
 * reverse, so interrupted sweeps reverse smoothly with proportional duration.
 * The fill content (an opaque region plus a mirrored caption colored with
 * {@code hoverTextFill}) is revealed by a progress clip, so the fill boundary
 * recolors the text as it sweeps over it; an outer clip bounds everything to
 * the button's painted geometry.</p>
 */
public class RXFillButtonSkin extends RXButtonSkin {

    private final SkinDisposer fillDisposer = new SkinDisposer();
    private final Pane fillLayer = new Pane();
    private final Pane fillContent = new Pane();
    private final Region fillRegion = new Region();
    private final Label hoverLabel = new Label();
    private final BoundedClipSupport boundedClip = new BoundedClipSupport(fillLayer);
    private final Rectangle progressRect = new Rectangle();
    private final Circle progressCircle = new Circle();
    private final DoubleProperty fillProgress =
            new SimpleDoubleProperty(this, "fillProgress", 0.0);
    private final InvalidationListener graphicBoundsListener =
            observable -> syncGraphicPlaceholder();

    private Timeline fillTimeline;
    private Node observedGraphic;
    private Region graphicPlaceholder;

    /**
     * Creates the skin and wires the fill decoration layer.
     *
     * @param button the button this skin is attached to
     */
    public RXFillButtonSkin(RXFillButton button) {
        super(button);

        fillLayer.getStyleClass().add("fill-layer");
        fillLayer.setManaged(false);
        fillLayer.setMouseTransparent(true);
        fillContent.getStyleClass().add("fill-content");
        fillContent.setManaged(false);
        fillRegion.getStyleClass().add("fill-region");
        fillRegion.setManaged(false);
        hoverLabel.setManaged(false);
        fillContent.getChildren().addAll(fillRegion, hoverLabel);
        fillLayer.getChildren().add(fillContent);

        // ==================== Mirrored caption ====================
        fillDisposer.registerBinding(hoverLabel.textProperty(), button.textProperty());
        fillDisposer.registerBinding(hoverLabel.fontProperty(), button.fontProperty());
        fillDisposer.registerBinding(hoverLabel.alignmentProperty(), button.alignmentProperty());
        fillDisposer.registerBinding(hoverLabel.contentDisplayProperty(), button.contentDisplayProperty());
        fillDisposer.registerBinding(hoverLabel.graphicTextGapProperty(), button.graphicTextGapProperty());
        fillDisposer.registerBinding(hoverLabel.textAlignmentProperty(), button.textAlignmentProperty());
        fillDisposer.registerBinding(hoverLabel.textOverrunProperty(), button.textOverrunProperty());
        fillDisposer.registerBinding(hoverLabel.wrapTextProperty(), button.wrapTextProperty());
        fillDisposer.registerBinding(hoverLabel.underlineProperty(), button.underlineProperty());
        fillDisposer.registerBinding(hoverLabel.lineSpacingProperty(), button.lineSpacingProperty());
        fillDisposer.registerBinding(hoverLabel.ellipsisStringProperty(), button.ellipsisStringProperty());
        fillDisposer.registerBinding(hoverLabel.mnemonicParsingProperty(), button.mnemonicParsingProperty());
        fillDisposer.registerBinding(hoverLabel.textFillProperty(), button.hoverTextFillProperty());

        // A graphic node cannot live in two scene-graph locations; the mirror
        // uses a size-following placeholder so the caption layout matches.
        fillDisposer.registerListener(button.graphicProperty(), this::updateGraphicPlaceholder);
        fillDisposer.registerDisposeTask(() -> observeGraphic(null));
        updateGraphicPlaceholder();

        // ==================== Progress model ====================
        fillDisposer.registerListener(fillProgress, this::updateFillGeometry);
        fillDisposer.registerListener(button.fillModeProperty(), this::updateFillGeometry);
        fillDisposer.registerListener(button.animationDurationProperty(), this::rebuildTimeline);

        // ==================== Triggers ====================
        fillDisposer.registerEventHandler(button, MouseEvent.MOUSE_ENTERED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.HOVER) {
                animateTo(true);
            }
        });
        fillDisposer.registerEventHandler(button, MouseEvent.MOUSE_EXITED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.HOVER) {
                animateTo(false);
            }
        });
        fillDisposer.registerEventHandler(button, MouseEvent.MOUSE_PRESSED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(true);
            }
        });
        fillDisposer.registerEventHandler(button, MouseEvent.MOUSE_RELEASED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(false);
            }
        });
        fillDisposer.registerListener(button.animationTriggerProperty(),
                () -> animateTo(isTriggerActive()));
        fillDisposer.registerListener(button.sceneProperty(), () -> {
            if (button.getScene() == null) {
                snapTo(isTriggerActive());
            }
        });

        rebuildTimeline();
        if (isTriggerActive()) {
            snapTo(true);
        }

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first calls come from superclass constructors, before this
        // skin's fields are initialized.
        if (fillLayer != null) {
            getChildren().add(0, fillLayer);
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        double width = getSkinnable().getWidth();
        double height = getSkinnable().getHeight();
        fillLayer.resizeRelocate(0.0, 0.0, width, height);
        boundedClip.updateClipFor(getSkinnable(), width, height);
        fillContent.resizeRelocate(0.0, 0.0, width, height);
        fillRegion.resizeRelocate(0.0, 0.0, width, height);
        hoverLabel.resizeRelocate(x, y, w, h);
        updateFillGeometry();
    }

    /**
     * Stops the fill animation, removes the fill layer and unregisters all
     * fill listeners before the {@link RXButtonSkin} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeFill, fillDisposer::dispose, super::dispose);
    }

    // ==================== Progress Model ====================

    private void animateTo(boolean active) {
        Duration duration = fillButton().getAnimationDuration();
        if (duration != null && duration.equals(Duration.ZERO)) {
            snapTo(active);
            return;
        }
        fillTimeline.setRate(active ? 1.0 : -1.0);
        fillTimeline.play();
    }

    private void snapTo(boolean active) {
        fillTimeline.stop();
        fillTimeline.jumpTo(active ? fillTimeline.getTotalDuration() : Duration.ZERO);
        fillProgress.set(active ? 1.0 : 0.0);
    }

    private void rebuildTimeline() {
        double progress = RXMath.clamp0To1(fillProgress.get());
        boolean running = false;
        double rate = 1.0;
        if (fillTimeline != null) {
            running = fillTimeline.getStatus() == Animation.Status.RUNNING;
            rate = fillTimeline.getRate();
            fillTimeline.stop();
        }
        Duration duration = positiveDurationOrDefault();
        fillTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(fillProgress, 0.0, Interpolator.EASE_BOTH)),
                new KeyFrame(duration,
                        new KeyValue(fillProgress, 1.0, Interpolator.EASE_BOTH)));
        fillTimeline.jumpTo(duration.multiply(progress));
        if (running) {
            fillTimeline.setRate(rate);
            fillTimeline.play();
        }
    }

    private void updateFillGeometry() {
        double width = fillLayer.getWidth();
        double height = fillLayer.getHeight();
        if (width <= 0.0 || height <= 0.0
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            fillContent.setClip(null);
            return;
        }
        double p = RXMath.clamp0To1(fillProgress.get());
        FillMode mode = modeOrDefault();
        if (mode == FillMode.CIRCLE) {
            progressCircle.setCenterX(width / 2.0);
            progressCircle.setCenterY(height / 2.0);
            progressCircle.setRadius(p * Math.hypot(width, height) / 2.0);
            fillContent.setClip(progressCircle);
            return;
        }
        double rectX = 0.0;
        double rectY = 0.0;
        double rectW = width;
        double rectH = height;
        switch (mode) {
            case RIGHT_TO_LEFT:
                rectX = width - p * width;
                rectW = p * width;
                break;
            case TOP_TO_BOTTOM:
                rectH = p * height;
                break;
            case BOTTOM_TO_TOP:
                rectY = height - p * height;
                rectH = p * height;
                break;
            case CENTER_OUT:
                rectX = (width - p * width) / 2.0;
                rectW = p * width;
                break;
            case LEFT_TO_RIGHT:
            default:
                rectW = p * width;
                break;
        }
        progressRect.setX(rectX);
        progressRect.setY(rectY);
        progressRect.setWidth(rectW);
        progressRect.setHeight(rectH);
        fillContent.setClip(progressRect);
    }

    // ==================== Trigger State ====================

    private RXFillButton fillButton() {
        return (RXFillButton) getSkinnable();
    }

    private RXAnimationTrigger triggerOrDefault() {
        RXAnimationTrigger trigger = fillButton().getAnimationTrigger();
        return trigger == null ? RXFillButton.DEFAULT_ANIMATION_TRIGGER : trigger;
    }

    private FillMode modeOrDefault() {
        FillMode mode = fillButton().getFillMode();
        return mode == null ? RXFillButton.DEFAULT_FILL_MODE : mode;
    }

    private Duration positiveDurationOrDefault() {
        Duration duration = fillButton().getAnimationDuration();
        if (duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return RXFillButton.DEFAULT_ANIMATION_DURATION;
        }
        return duration;
    }

    private boolean isTriggerActive() {
        return triggerOrDefault() == RXAnimationTrigger.HOVER
                ? getSkinnable().isHover()
                : getSkinnable().isPressed();
    }

    // ==================== Graphic Placeholder ====================

    private void updateGraphicPlaceholder() {
        Node graphic = fillButton().getGraphic();
        observeGraphic(graphic);
        if (graphic == null) {
            graphicPlaceholder = null;
            hoverLabel.setGraphic(null);
        } else {
            graphicPlaceholder = new Region();
            hoverLabel.setGraphic(graphicPlaceholder);
            syncGraphicPlaceholder();
        }
    }

    private void observeGraphic(Node graphic) {
        if (observedGraphic != null) {
            observedGraphic.layoutBoundsProperty().removeListener(graphicBoundsListener);
        }
        observedGraphic = graphic;
        if (graphic != null) {
            graphic.layoutBoundsProperty().addListener(graphicBoundsListener);
        }
    }

    private void syncGraphicPlaceholder() {
        if (graphicPlaceholder == null || observedGraphic == null) {
            return;
        }
        Bounds bounds = observedGraphic.getLayoutBounds();
        graphicPlaceholder.setMinSize(bounds.getWidth(), bounds.getHeight());
        graphicPlaceholder.setPrefSize(bounds.getWidth(), bounds.getHeight());
        graphicPlaceholder.setMaxSize(bounds.getWidth(), bounds.getHeight());
    }

    // ==================== Cleanup ====================

    private void disposeFill() {
        if (fillTimeline != null) {
            fillTimeline.stop();
        }
        boundedClip.clearClip();
        fillContent.setClip(null);
        getChildren().remove(fillLayer);
    }
}
