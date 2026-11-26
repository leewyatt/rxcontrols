package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXBurstLayer;
import io.github.leewyatt.rxcontrols.internal.RXDialogLayer;
import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.beans.DefaultProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.layout.Region;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A pane that shows its content with the overlays of {@link RXDialog},
 * {@link RXSnackbars} and {@link RXBursts} on top of it. Those three show
 * inside the nearest {@code RXOverlayPane} above the node they are given, so
 * an application wraps its scene root once:
 *
 * <pre>{@code
 * stage.setScene(new Scene(new RXOverlayPane(root)));
 * }</pre>
 *
 * <p>The content is laid out the way a scene lays out its root: a resizable
 * node fills the pane, whatever its maximum size, and a node that is not
 * resizable, such as a {@code Group}, keeps its size and position. The pane
 * measures like its content, and reaches the far edge of a node that is not
 * resizable, so wrapping a scene root does not change the size of its window.
 * The overlays cover the whole pane, above the content and in a fixed order:
 * snackbars, then dialogs, then bursts. Each is created
 * the first time it is used. They are not public children of the pane; a page
 * is replaced with {@link #setContent(Node)}, which leaves open dialogs and
 * snackbars as they are.</p>
 *
 * <p>A pane can sit anywhere in a scene, and panes can be nested: a pane
 * around a part of the window keeps what is requested from inside it within
 * that part. A request goes to the nearest pane above its node; to show
 * further out, pass a node outside the inner pane, or the pane returned by
 * {@link #findOutermost(Node)}. The search starts at the node itself and
 * follows its parents. It continues from the root of a sub-scene at the
 * {@code SubScene} node, and from a popup window (a context menu, a combo box
 * list) at the node the popup was shown for; a burst is the exception and
 * stays in the window of its node.</p>
 *
 * <p>The pane does nothing when its content is replaced or when it leaves its
 * scene. A dialog that is open stays open and is there again when the pane is
 * back in a scene; close it first if it should not be. Snackbars and bursts
 * end, as they do whenever they leave a scene.</p>
 */
@DefaultProperty("content")
public class RXOverlayPane extends Region {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-overlay-pane";

    // ==================== Internal State ====================

    private Node currentContent;
    private RXSnackbarHost snackbarHost;
    private RXDialogLayer dialogLayer;
    private RXBurstLayer burstLayer;

    // ==================== Constructors ====================

    /**
     * Creates an empty overlay pane.
     */
    public RXOverlayPane() {
        this(null);
    }

    /**
     * Creates an overlay pane with the given content.
     *
     * @param content the content node, or {@code null}
     */
    public RXOverlayPane(Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setContent(content);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content =
            new SimpleObjectProperty<>(this, "content") {
                @Override
                protected void invalidated() {
                    updateContent();
                }
            };

    /**
     * The node shown below the overlays, usually the application's root
     * layout. May be {@code null}. Replacing it does not touch the overlays.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Lookup ====================

    /**
     * Finds the overlay pane a request made with the given node goes to: the
     * node itself if it is an overlay pane, or else the nearest one above it.
     *
     * <p>From inside a {@code SubScene}, a pane outside of it is found only
     * while the {@code SubScene} is in a scene.</p>
     *
     * @param node the node to search from
     * @return the nearest overlay pane, or empty when there is none
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static Optional<RXOverlayPane> find(Node node) {
        Objects.requireNonNull(node, "node");
        return Optional.ofNullable(search(node, true, false));
    }

    /**
     * Finds the outermost overlay pane at or above the given node, usually the
     * one at the scene root. Pass it as the owner to show over the whole window
     * from inside a nested pane.
     *
     * @param node the node to search from
     * @return the outermost overlay pane, or empty when there is none
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static Optional<RXOverlayPane> findOutermost(Node node) {
        Objects.requireNonNull(node, "node");
        return Optional.ofNullable(search(node, true, true));
    }

    static RXOverlayPane nearest(Node node, boolean throughPopups) {
        return search(node, throughPopups, false);
    }

    static String missingPaneMessage(String request, String role) {
        return request + ": no RXOverlayPane above the " + role + " node. Wrap the scene root once, "
                + "for example new Scene(new RXOverlayPane(root)), or pass a node that is inside an "
                + "RXOverlayPane.";
    }

    private static RXOverlayPane search(Node start, boolean throughPopups, boolean outermost) {
        RXOverlayPane found = null;
        // The roots already left for the node above them: an owner chain that
        // loops back into its own popup ends the search.
        Set<Node> leftRoots = null;
        Node node = start;
        while (node != null) {
            if (node instanceof RXOverlayPane pane) {
                if (!outermost) {
                    return pane;
                }
                found = pane;
            }
            Node above = node.getParent();
            if (above == null) {
                above = aboveRoot(node, throughPopups);
                if (above != null) {
                    if (leftRoots == null) {
                        leftRoots = Collections.newSetFromMap(new IdentityHashMap<>());
                    }
                    if (!leftRoots.add(node)) {
                        break;
                    }
                }
            }
            node = above;
        }
        return found;
    }

    // Where the search goes on from a node without a parent: the SubScene
    // node of a sub-scene root, or what a popup window was shown for.
    private static Node aboveRoot(Node root, boolean throughPopups) {
        Scene scene = root.getScene();
        if (scene == null) {
            return null;
        }
        if (scene.getRoot() != root) {
            return subSceneOf(root, scene.getRoot());
        }
        if (!throughPopups || !(scene.getWindow() instanceof PopupWindow popup)) {
            return null;
        }
        Node ownerNode = popup.getOwnerNode();
        if (ownerNode != null) {
            return ownerNode;
        }
        Window ownerWindow = popup.getOwnerWindow();
        Scene ownerScene = ownerWindow == null ? null : ownerWindow.getScene();
        return ownerScene == null ? null : ownerScene.getRoot();
    }

    // JavaFX has no public link from the root of a sub-scene to its SubScene
    // node, so the scene is searched for it.
    private static SubScene subSceneOf(Node subSceneRoot, Parent from) {
        for (Node child : from.getChildrenUnmodifiable()) {
            SubScene found = null;
            if (child instanceof SubScene subScene) {
                found = subScene.getRoot() == subSceneRoot ? subScene : subSceneOf(subSceneRoot, subScene.getRoot());
            } else if (child instanceof Parent parent) {
                found = subSceneOf(subSceneRoot, parent);
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ==================== Overlays ====================

    /**
     * Returns the snackbar host of this pane, creating it on the first call.
     * Use it to set where snackbars appear, their margin or their queue; the
     * {@link RXSnackbars} methods show in it. It belongs to this pane and
     * must not be moved to another parent. {@link RXSnackbars#hostFor(Node)}
     * looks for it without creating it.
     *
     * @return the snackbar host of this pane
     */
    public final RXSnackbarHost getOrCreateSnackbarHost() {
        if (snackbarHost == null) {
            // The field is set before the node is added, so a listener that
            // asks again while it is being added gets this same host.
            snackbarHost = new RXSnackbarHost();
            // Placed by the overlays above it: the content may be anywhere in
            // the child list once the application has moved it to the front.
            Node above = dialogLayer != null ? dialogLayer : burstLayer;
            ObservableList<Node> children = getChildren();
            addOverlay(snackbarHost, above == null ? children.size() : children.indexOf(above));
        }
        return snackbarHost;
    }

    RXSnackbarHost createdSnackbarHost() {
        return snackbarHost;
    }

    RXDialogLayer ensureDialogLayer() {
        if (dialogLayer == null) {
            dialogLayer = new RXDialogLayer();
            ObservableList<Node> children = getChildren();
            addOverlay(dialogLayer, burstLayer == null ? children.size() : children.indexOf(burstLayer));
        }
        return dialogLayer;
    }

    RXBurstLayer ensureBurstLayer() {
        if (burstLayer == null) {
            burstLayer = new RXBurstLayer();
            addOverlay(burstLayer, getChildren().size());
            // Resolves -rx-burst-color now, so the very first burst has it.
            burstLayer.applyCss();
        }
        return burstLayer;
    }

    // An overlay is unmanaged: it lays out on its own, and what happens in it
    // never asks the pane or the content for a new layout. Its view order
    // keeps it in front of the content whatever view order the content has;
    // among themselves the overlays keep the order of the child list.
    private void addOverlay(Node overlay, int index) {
        overlay.setManaged(false);
        overlay.setViewOrder(Double.NEGATIVE_INFINITY);
        getChildren().add(index, overlay);
        overlay.resizeRelocate(0.0, 0.0, getWidth(), getHeight());
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Orientation getContentBias() {
        Node node = managedContent();
        return node == null ? null : node.getContentBias();
    }

    @Override
    protected double computeMinWidth(double height) {
        Node node = managedContent();
        return node == null
                ? horizontalInsets()
                : horizontalInsets() + originX(node) + node.minWidth(within(height, verticalInsets()));
    }

    @Override
    protected double computeMinHeight(double width) {
        Node node = managedContent();
        return node == null
                ? verticalInsets()
                : verticalInsets() + originY(node) + node.minHeight(within(width, horizontalInsets()));
    }

    @Override
    protected double computePrefWidth(double height) {
        Node node = managedContent();
        return node == null
                ? horizontalInsets()
                : horizontalInsets() + originX(node) + node.prefWidth(within(height, verticalInsets()));
    }

    @Override
    protected double computePrefHeight(double width) {
        Node node = managedContent();
        return node == null
                ? verticalInsets()
                : verticalInsets() + originY(node) + node.prefHeight(within(width, horizontalInsets()));
    }

    @Override
    protected double computeMaxWidth(double height) {
        Node node = managedContent();
        return node == null
                ? Double.MAX_VALUE
                : horizontalInsets() + originX(node) + node.maxWidth(within(height, verticalInsets()));
    }

    @Override
    protected double computeMaxHeight(double width) {
        Node node = managedContent();
        return node == null
                ? Double.MAX_VALUE
                : verticalInsets() + originY(node) + node.maxHeight(within(width, horizontalInsets()));
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        Node node = managedContent();
        // Like a scene root: a resizable node takes the whole area, whatever
        // its maximum size, and a node that is not resizable is left alone.
        if (node != null && node.isResizable()) {
            double left = snappedLeftInset();
            double top = snappedTopInset();
            node.resizeRelocate(left, top,
                    Math.max(0.0, width - left - snappedRightInset()),
                    Math.max(0.0, height - top - snappedBottomInset()));
        }
        fill(snackbarHost, width, height);
        fill(dialogLayer, width, height);
        fill(burstLayer, width, height);
    }

    // ==================== Helpers ====================

    private void updateContent() {
        Node next = getContent();
        if (currentContent == next) {
            return;
        }
        ObservableList<Node> children = getChildren();
        int index = currentContent == null ? -1 : children.indexOf(currentContent);
        if (next == null) {
            if (index >= 0) {
                children.remove(index);
            }
        } else if (index >= 0) {
            children.set(index, next);
        } else {
            children.add(0, next);
        }
        currentContent = next;
    }

    private Node managedContent() {
        Node node = currentContent;
        return node == null || node.getParent() != this || !node.isManaged() ? null : node;
    }

    // A node that is not resizable keeps its own position, so the pane has to
    // reach its far edge; a scene measures such a root the same way.
    private static double originX(Node node) {
        return node.isResizable() ? 0.0 : node.getLayoutX() + node.getLayoutBounds().getMinX();
    }

    private static double originY(Node node) {
        return node.isResizable() ? 0.0 : node.getLayoutY() + node.getLayoutBounds().getMinY();
    }

    private static void fill(Node overlay, double width, double height) {
        if (overlay != null) {
            overlay.resizeRelocate(0.0, 0.0, width, height);
        }
    }

    private double horizontalInsets() {
        return snappedLeftInset() + snappedRightInset();
    }

    private double verticalInsets() {
        return snappedTopInset() + snappedBottomInset();
    }

    // The size left for the content inside the insets; -1 stays "not given".
    private static double within(double size, double insets) {
        return size == -1.0 ? -1.0 : Math.max(0.0, size - insets);
    }
}
