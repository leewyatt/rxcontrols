
package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXAvatar.DisplayState;
import io.github.leewyatt.rxcontrols.RXAvatar.ShapeType;
import javafx.beans.InvalidationListener;
import javafx.geometry.HPos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Default skin for {@link RXAvatar}.
 *
 * <p>The visible rendering area is always {@code min(width, height)} square,
 * centered within the control bounds. This ensures the avatar remains
 * square/circular regardless of the control's aspect ratio.</p>
 */
public class RXAvatarSkin extends SkinBase<RXAvatar> {

    private final ImageView imageView;
    private final Group imageGroup;
    private final StackPane textPane;
    private final Label textLabel;
    private final StackPane defaultIconPane;
    private final Region defaultIcon;

    // Only one node is visible at a time, so a single pair of clip shapes suffices.
    private final Rectangle clipRect;
    private final Circle clipCircle;

    // ==================== Listeners ====================

    private final InvalidationListener imageListener = obs -> onImageChanged();
    private final InvalidationListener displayStateListener = obs -> onDisplayStateChanged();
    private final InvalidationListener layoutListener = obs -> getSkinnable().requestLayout();

    // ==================== Constructor ====================

    public RXAvatarSkin(RXAvatar control) {
        super(control);

        imageView = new ImageView();
        imageView.setSmooth(true);
        imageView.setPreserveRatio(false);

        // Group wraps the clipped ImageView so that effect (e.g. DropShadow)
        // applied on the Group follows the clipped shape, not the full rectangle.
        imageGroup = new Group(imageView);
        imageGroup.getStyleClass().add("image-group");

        clipRect = new Rectangle();
        clipCircle = new Circle();

        textLabel = new Label();
        textLabel.textProperty().bind(control.textProperty());
        textPane = new StackPane(textLabel);
        textPane.getStyleClass().add("text");

        defaultIcon = new Region();
        defaultIcon.getStyleClass().add("default-icon");
        defaultIconPane = new StackPane(defaultIcon);
        defaultIconPane.getStyleClass().add("default-icon-pane");
        defaultIconPane.setMouseTransparent(true);

        getChildren().addAll(defaultIconPane, textPane, imageGroup);

        control.imageProperty().addListener(imageListener);
        control.displayStateProperty().addListener(displayStateListener);
        control.shapeTypeProperty().addListener(layoutListener);
        control.arcWidthProperty().addListener(layoutListener);
        control.arcHeightProperty().addListener(layoutListener);

        imageView.setImage(control.getImage());
        updateVisibility();
    }

    // ==================== State Changes ====================

    private void onImageChanged() {
        imageView.setImage(getSkinnable().getImage());
        getSkinnable().requestLayout();
    }

    private void onDisplayStateChanged() {
        updateVisibility();
        getSkinnable().requestLayout();
    }

    private void updateVisibility() {
        DisplayState state = getSkinnable().getDisplayState();
        boolean showImage = state == DisplayState.IMAGE;
        boolean showText = state == DisplayState.TEXT;
        boolean showIcon = state == DisplayState.EMPTY;

        imageGroup.setVisible(showImage);
        imageGroup.setManaged(showImage);
        textPane.setVisible(showText);
        textPane.setManaged(showText);
        defaultIconPane.setVisible(showIcon);
        defaultIconPane.setManaged(showIcon);

        // Clear clip from hidden nodes
        if (!showImage) {
            imageView.setClip(null);
        }
        if (!showText) {
            textPane.setClip(null);
        }
        if (!showIcon) {
            defaultIconPane.setClip(null);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double size = Math.min(w, h);
        double cx = x + (w - size) / 2;
        double cy = y + (h - size) / 2;

        ShapeType type = getSkinnable().getShapeType();
        double arcW = getSkinnable().getArcWidth();
        double arcH = getSkinnable().getArcHeight();

        if (imageGroup.isVisible()) {
            layoutImage(cx, cy, size);
            applyClip(imageView, size, type, arcW, arcH);
        } else if (textPane.isVisible()) {
            layoutInArea(textPane, cx, cy, size, size, -1, HPos.CENTER, VPos.CENTER);
            applyClip(textPane, size, type, arcW, arcH);
        } else if (defaultIconPane.isVisible()) {
            layoutInArea(defaultIconPane, cx, cy, size, size, -1, HPos.CENTER, VPos.CENTER);
            applyClip(defaultIconPane, size, type, arcW, arcH);
        }
    }

    private void applyClip(Node node, double size, ShapeType type,
                           double arcW, double arcH) {
        if (type == ShapeType.CIRCLE) {
            double r = size / 2;
            clipCircle.setCenterX(r);
            clipCircle.setCenterY(r);
            clipCircle.setRadius(r);
            node.setClip(clipCircle);
        } else {
            clipRect.setWidth(size);
            clipRect.setHeight(size);
            clipRect.setArcWidth(arcW);
            clipRect.setArcHeight(arcH);
            node.setClip(clipRect);
        }
    }

    private void layoutImage(double x, double y, double size) {
        Image img = getSkinnable().getImage();
        double imgW = img.getWidth();
        double imgH = img.getHeight();

        if (size <= 0 || imgW <= 0 || imgH <= 0) {
            imageView.setViewport(null);
            imageView.setFitWidth(0);
            imageView.setFitHeight(0);
            imageGroup.relocate(x, y);
            return;
        }

        // Cover-fit: scale image to fill size*size, crop overflow via viewport
        double scale = Math.max(size / imgW, size / imgH);
        double viewportW = size / scale;
        double viewportH = size / scale;
        double viewportX = (imgW - viewportW) / 2;
        double viewportY = (imgH - viewportH) / 2;
        imageView.setViewport(new Rectangle2D(viewportX, viewportY, viewportW, viewportH));

        imageView.setFitWidth(size);
        imageView.setFitHeight(size);

        imageGroup.relocate(x, y);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + 100 + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + 100 + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        RXAvatar control = getSkinnable();

        control.imageProperty().removeListener(imageListener);
        control.displayStateProperty().removeListener(displayStateListener);
        control.shapeTypeProperty().removeListener(layoutListener);
        control.arcWidthProperty().removeListener(layoutListener);
        control.arcHeightProperty().removeListener(layoutListener);

        textLabel.textProperty().unbind();

        imageView.setImage(null);
        imageView.setClip(null);
        imageView.setViewport(null);
        textPane.setClip(null);
        defaultIconPane.setClip(null);

        super.dispose();
    }
}
