package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
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
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Skin for {@link RXFillButton}: the {@link RXButtonSkin} plus a fill
 * decoration layer between the button background and the ripple layer.
 *
 * <p>A single timeline drives an internal fill progress; the trigger state
 * (hover or pressed) plays it forward or, from the current progress, in
 * reverse, so interrupted sweeps reverse smoothly with proportional duration.
 * Two layers share the progress geometry: the fill layer (below the ripple
 * and label) reveals the fill color bounded to the button's painted geometry,
 * and a top-most text layer reveals a mirrored caption colored with
 * {@code hoverTextFill} above the regular label, so the fill boundary
 * recolors the text as it sweeps over it.</p>
 */
public class RXFillButtonSkin extends RXButtonSkin {

    private final SkinDisposer fillDisposer = new SkinDisposer();
    private final Pane fillLayer = new Pane();
    private final Pane fillContent = new Pane();
    private final Region fillRegion = new Region();
    private final Pane hoverTextLayer = new Pane();
    private final Label hoverLabel = new Label();
    private final BoundedClipSupport boundedClip = new BoundedClipSupport(fillLayer);
    private final DoubleProperty fillProgress =
            new SimpleDoubleProperty(this, "fillProgress", 0.0);
    private final InvalidationListener graphicBoundsListener =
            observable -> syncGraphicPlaceholder();

    private Timeline fillTimeline;
    private Node observedGraphic;
    private Region graphicPlaceholder;
    private FillAnimation appliedAnimation;
    private Node fillClip;
    private Node textClip;

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
        fillContent.getChildren().add(fillRegion);
        fillLayer.getChildren().add(fillContent);
        // The mirrored caption lives in its own top-most layer: it must paint
        // above the regular label, while the fill color stays below it.
        hoverTextLayer.getStyleClass().add("hover-text-layer");
        hoverTextLayer.setManaged(false);
        hoverTextLayer.setMouseTransparent(true);
        hoverLabel.setManaged(false);
        hoverTextLayer.getChildren().add(hoverLabel);

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
        fillDisposer.registerListener(button.fillAnimationProperty(), this::updateFillGeometry);
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
        // Border width changes relayout via the insets chain on their own.
        fillDisposer.registerListener(button.fillInsetsProperty(), button::requestLayout);

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
            getChildren().add(hoverTextLayer);
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        RXFillButton button = fillButton();
        double width = button.getWidth();
        double height = button.getHeight();
        Insets fillInsets = button.getFillInsets();
        Insets effective = fillInsets != null
                ? fillInsets
                : BoundedClipSupport.borderInsetsOf(button);
        double areaW = Math.max(0.0, width - effective.getLeft() - effective.getRight());
        double areaH = Math.max(0.0, height - effective.getTop() - effective.getBottom());
        fillLayer.resizeRelocate(0.0, 0.0, width, height);
        boundedClip.updateClipFor(button, width, height, fillInsets);
        fillContent.resizeRelocate(effective.getLeft(), effective.getTop(), areaW, areaH);
        fillRegion.resizeRelocate(0.0, 0.0, areaW, areaH);
        hoverTextLayer.resizeRelocate(0.0, 0.0, width, height);
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
        // The fill area is the (possibly inset) fillContent box, sized by
        // layoutChildren from fillInsets or the host border.
        double areaW = fillContent.getWidth();
        double areaH = fillContent.getHeight();
        if (areaW <= 0.0 || areaH <= 0.0
                || !Double.isFinite(areaW) || !Double.isFinite(areaH)) {
            appliedAnimation = null;
            fillClip = null;
            textClip = null;
            fillContent.setClip(null);
            hoverTextLayer.setClip(null);
            return;
        }
        FillAnimation animation = animationOrDefault();
        if (animation != appliedAnimation || fillClip == null || textClip == null) {
            appliedAnimation = animation;
            fillClip = animation.createClip();
            textClip = animation.createClip();
            fillContent.setClip(fillClip);
            hoverTextLayer.setClip(textClip);
        }
        double progress = RXMath.clamp0To1(fillProgress.get());
        animation.update(fillClip, progress, areaW, areaH);
        // The text layer covers the full bounds; shift its clip so the reveal
        // boundary matches the fill area.
        textClip.setTranslateX(fillContent.getLayoutX());
        textClip.setTranslateY(fillContent.getLayoutY());
        animation.update(textClip, progress, areaW, areaH);
    }

    // ==================== Trigger State ====================

    private RXFillButton fillButton() {
        return (RXFillButton) getSkinnable();
    }

    private RXAnimationTrigger triggerOrDefault() {
        RXAnimationTrigger trigger = fillButton().getAnimationTrigger();
        return trigger == null ? RXFillButton.DEFAULT_ANIMATION_TRIGGER : trigger;
    }

    private FillAnimation animationOrDefault() {
        FillAnimation animation = fillButton().getFillAnimation();
        return animation == null ? RXFillButton.DEFAULT_FILL_ANIMATION : animation;
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
        hoverTextLayer.setClip(null);
        appliedAnimation = null;
        fillClip = null;
        textClip = null;
        getChildren().remove(fillLayer);
        getChildren().remove(hoverTextLayer);
    }
}
