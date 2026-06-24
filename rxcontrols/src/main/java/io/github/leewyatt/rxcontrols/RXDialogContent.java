package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A rich content template for an {@link RXDialog} card: an optional graphic +
 * header row (with an optional in-header close button), a body, and an optional
 * collapsible "details" region. Modelled on the native {@code DialogPane} content
 * model, but with <strong>no</strong> action bar — that belongs to {@link RXDialog}
 * ({@link RXDialog#getButtonTypes() buttonTypes}). It is an {@link RXDialogContentBase}
 * (a {@link Region} that carries a {@link #dialogProperty() dialog} back-reference),
 * usable standalone or as an {@code RXDialog}'s {@link RXDialog#contentProperty()
 * content}.
 *
 * <p>The header shows {@link #headerProperty() header} (a node) or, as a
 * convenience, {@link #headerTextProperty() headerText} (a string); likewise the
 * body shows {@link #contentProperty() content} or {@link #contentTextProperty()
 * contentText}. Heading and body padding are styled with standard {@code -fx-padding}
 * on the {@code .heading} / {@code .body} sub-structures.</p>
 *
 * <p>When {@link #showCloseProperty() showClose} is set, a close (X) button is laid
 * out in the header's trailing edge (in flow, so it never overlaps the title) and,
 * when this layout is hosted by an {@link RXDialog}, closes it through that dialog's
 * vetoable gate. Standalone (no hosting dialog), clicking it does nothing.</p>
 *
 * <pre>{@code
 * RXDialogContent layout = new RXDialogContent("Delete file?", "This cannot be undone.");
 * layout.setExpandableContent(new TextArea(stackTrace));
 * dialog.setContent(layout);
 * }</pre>
 */
public class RXDialogContent extends RXDialogContentBase {

    private static final String DEFAULT_STYLE_CLASS = "rx-dialog-content";

    private final VBox container = new VBox();
    private final BorderPane heading = new BorderPane();
    private final Label titleLabel = new Label();
    private final StackPane body = new StackPane();
    private final Label contentLabel = new Label();
    private final Hyperlink detailsToggle = new Hyperlink();
    private final StackPane expandableWrapper = new StackPane();
    private final StackPane graphicWrapper = new StackPane();
    private final StackPane closeButton = createCloseButton();

    // ==================== Constructors ====================

    /**
     * Creates an empty layout.
     */
    public RXDialogContent() {
        this(null, null);
    }

    /**
     * Creates a layout with header and body text.
     *
     * @param headerText  the header text, or {@code null}
     * @param contentText the body text, or {@code null}
     */
    public RXDialogContent(@NamedArg("headerText") String headerText,
                          @NamedArg("contentText") String contentText) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        container.getStyleClass().add("container");
        titleLabel.getStyleClass().add("title");
        contentLabel.getStyleClass().add("content-text");
        contentLabel.setWrapText(true);
        heading.getStyleClass().add("heading");
        graphicWrapper.getStyleClass().add("graphic-wrapper");
        body.getStyleClass().add("body");
        detailsToggle.getStyleClass().add("details-toggle");
        expandableWrapper.getStyleClass().add("expandable-wrapper");

        VBox.setVgrow(body, Priority.ALWAYS);
        container.getChildren().setAll(heading, body, detailsToggle, expandableWrapper);
        getChildren().setAll(container);

        detailsToggle.setOnAction(event -> setExpanded(!isExpanded()));

        setHeaderText(headerText);
        setContentText(contentText);
        updateHeading();
        updateBody();
        updateExpandable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic") {
        @Override
        protected void invalidated() {
            updateHeading();
        }
    };

    /**
     * An optional graphic shown to the left of the header. May be {@code null}.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the header graphic.
     *
     * @return the graphic node, or {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the header graphic.
     *
     * @param value the graphic node, or {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Header ====================

    private final ObjectProperty<Node> header = new SimpleObjectProperty<>(this, "header") {
        @Override
        protected void invalidated() {
            updateHeading();
        }
    };

    /**
     * A custom header node. When set, it replaces the {@link #headerTextProperty()
     * headerText} label in the header. May be {@code null}.
     *
     * @return the header property
     */
    public final ObjectProperty<Node> headerProperty() {
        return header;
    }

    /**
     * Returns the header node.
     *
     * @return the header node, or {@code null}
     */
    public final Node getHeader() {
        return header.get();
    }

    /**
     * Sets the header node.
     *
     * @param value the header node, or {@code null}
     */
    public final void setHeader(Node value) {
        header.set(value);
    }

    // ==================== Header Text ====================

    private final StringProperty headerText = new SimpleStringProperty(this, "headerText") {
        @Override
        protected void invalidated() {
            updateHeading();
        }
    };

    /**
     * Convenience header text, shown as a title when no {@link #headerProperty()
     * header} node is set. May be {@code null} or empty for no header.
     *
     * @return the header text property
     */
    public final StringProperty headerTextProperty() {
        return headerText;
    }

    /**
     * Returns the header text.
     *
     * @return the header text, or {@code null}
     */
    public final String getHeaderText() {
        return headerText.get();
    }

    /**
     * Sets the header text.
     *
     * @param value the header text, or {@code null}
     */
    public final void setHeaderText(String value) {
        headerText.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content") {
        @Override
        protected void invalidated() {
            updateBody();
        }
    };

    /**
     * The body content node. When set, it replaces the {@link #contentTextProperty()
     * contentText} label. May be {@code null}.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the body content node.
     *
     * @return the content node, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the body content node.
     *
     * @param value the content node, or {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Content Text ====================

    private final StringProperty contentText = new SimpleStringProperty(this, "contentText") {
        @Override
        protected void invalidated() {
            updateBody();
        }
    };

    /**
     * Convenience body text, shown (wrapped) when no {@link #contentProperty()
     * content} node is set. May be {@code null} or empty for no body.
     *
     * @return the content text property
     */
    public final StringProperty contentTextProperty() {
        return contentText;
    }

    /**
     * Returns the body text.
     *
     * @return the body text, or {@code null}
     */
    public final String getContentText() {
        return contentText.get();
    }

    /**
     * Sets the body text.
     *
     * @param value the body text, or {@code null}
     */
    public final void setContentText(String value) {
        contentText.set(value);
    }

    // ==================== Expandable Content ====================

    private final ObjectProperty<Node> expandableContent = new SimpleObjectProperty<>(this, "expandableContent") {
        @Override
        protected void invalidated() {
            updateExpandable();
        }
    };

    /**
     * Optional content revealed by a built-in "Show Details" toggle. When
     * {@code null}, no toggle is shown.
     *
     * @return the expandable content property
     */
    public final ObjectProperty<Node> expandableContentProperty() {
        return expandableContent;
    }

    /**
     * Returns the expandable content node.
     *
     * @return the expandable content node, or {@code null}
     */
    public final Node getExpandableContent() {
        return expandableContent.get();
    }

    /**
     * Sets the expandable content node.
     *
     * @param value the expandable content node, or {@code null}
     */
    public final void setExpandableContent(Node value) {
        expandableContent.set(value);
    }

    // ==================== Expanded ====================

    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false) {
        @Override
        protected void invalidated() {
            updateExpandable();
        }
    };

    /**
     * Whether the {@link #expandableContentProperty() expandableContent} is
     * currently revealed.
     *
     * @return the expanded property
     */
    public final BooleanProperty expandedProperty() {
        return expanded;
    }

    /**
     * Returns whether the expandable content is revealed.
     *
     * @return whether expanded
     */
    public final boolean isExpanded() {
        return expanded.get();
    }

    /**
     * Sets whether the expandable content is revealed.
     *
     * @param value whether expanded
     */
    public final void setExpanded(boolean value) {
        expanded.set(value);
    }

    // ==================== Show Close ====================

    private final BooleanProperty showClose = new SimpleBooleanProperty(this, "showClose", false) {
        @Override
        protected void invalidated() {
            updateHeading();
        }
    };

    /**
     * Whether a close (X) button is shown at the header's trailing edge. Default
     * {@code false}. The button lays out in flow (it never overlaps the title) and,
     * when this layout is the {@link #dialogProperty() content of an RXDialog}, closes
     * that dialog through its vetoable gate (reason {@link CloseReason#CLOSE_BUTTON});
     * standalone it does nothing.
     *
     * @return the show-close property
     */
    public final BooleanProperty showCloseProperty() {
        return showClose;
    }

    /**
     * Returns whether the close button is shown.
     *
     * @return whether the close button is shown
     */
    public final boolean isShowClose() {
        return showClose.get();
    }

    /**
     * Sets whether the close button is shown.
     *
     * @param value whether the close button is shown
     */
    public final void setShowClose(boolean value) {
        showClose.set(value);
    }

    // ==================== Slots ====================

    private void updateHeading() {
        Node graphicNode = getGraphic();
        Node headerNode = getHeader();
        String text = getHeaderText();
        boolean hasText = text != null && !text.isEmpty();
        titleLabel.setText(text == null ? "" : text);

        Node center = headerNode != null ? headerNode : (hasText ? titleLabel : null);
        // The graphic sits in a wrapper whose CSS padding (.heading > .graphic-wrapper) is
        // the graphic-to-title gap — author-tunable, unlike a hardcoded BorderPane margin.
        if (graphicNode != null) {
            graphicWrapper.getChildren().setAll(graphicNode);
        } else {
            graphicWrapper.getChildren().clear();
        }
        heading.setLeft(graphicNode != null ? graphicWrapper : null);
        heading.setCenter(center);
        heading.setRight(isShowClose() ? closeButton : null);

        boolean visible = graphicNode != null || center != null || isShowClose();
        heading.setVisible(visible);
        heading.setManaged(visible);
    }

    // The close (X) button: a shape-backed icon in a transparent, pickable wrapper
    // (the wrapper is pinned to its preferred size so the BorderPane right slot does
    // not stretch it). It closes the hosting dialog, if any, through the vetoable gate.
    private StackPane createCloseButton() {
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMouseTransparent(true);
        StackPane button = new StackPane(icon);
        button.getStyleClass().add("close-button");
        button.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        BorderPane.setAlignment(button, Pos.TOP_RIGHT);
        button.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleCloseClicked);
        return button;
    }

    private void handleCloseClicked(MouseEvent event) {
        RXDialog<?> dialog = getDialog();
        if (dialog != null) {
            dialog.requestClose(null, CloseReason.CLOSE_BUTTON);
            event.consume();
        }
    }

    private void updateBody() {
        Node contentNode = getContent();
        String text = getContentText();
        boolean hasText = text != null && !text.isEmpty();
        contentLabel.setText(text == null ? "" : text);

        Node bodyChild = contentNode != null ? contentNode : (hasText ? contentLabel : null);
        if (bodyChild == null) {
            body.getChildren().clear();
        } else {
            body.getChildren().setAll(bodyChild);
        }
        boolean visible = bodyChild != null;
        body.setVisible(visible);
        body.setManaged(visible);
    }

    private void updateExpandable() {
        Node expandable = getExpandableContent();
        boolean hasExpandable = expandable != null;
        detailsToggle.setVisible(hasExpandable);
        detailsToggle.setManaged(hasExpandable);
        detailsToggle.setText(isExpanded() ? "Hide Details" : "Show Details");

        boolean reveal = hasExpandable && isExpanded();
        if (reveal) {
            expandableWrapper.getChildren().setAll(expandable);
        } else {
            expandableWrapper.getChildren().clear();
        }
        expandableWrapper.setVisible(reveal);
        expandableWrapper.setManaged(reveal);
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the inner column so a wrapped {@code contentText} (a
     * HORIZONTAL-biased label) gets its height computed at the laid-out width
     * instead of at an unconstrained width (which would reserve too little
     * vertical space and clip the body).</p>
     */
    @Override
    public Orientation getContentBias() {
        return container.getContentBias();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren() {
        Insets insets = getInsets();
        double x = insets.getLeft();
        double y = insets.getTop();
        double w = getWidth() - insets.getLeft() - insets.getRight();
        double h = getHeight() - insets.getTop() - insets.getBottom();
        layoutInArea(container, x, y, w, h, 0, HPos.LEFT, VPos.TOP);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height) {
        Insets insets = getInsets();
        return insets.getLeft() + container.minWidth(innerHeight(height, insets)) + insets.getRight();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width) {
        Insets insets = getInsets();
        return insets.getTop() + container.minHeight(innerWidth(width, insets)) + insets.getBottom();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height) {
        Insets insets = getInsets();
        return insets.getLeft() + container.prefWidth(innerHeight(height, insets)) + insets.getRight();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width) {
        Insets insets = getInsets();
        return insets.getTop() + container.prefHeight(innerWidth(width, insets)) + insets.getBottom();
    }

    private static double innerHeight(double height, Insets insets) {
        return height == -1 ? -1 : Math.max(0, height - insets.getTop() - insets.getBottom());
    }

    private static double innerWidth(double width, Insets insets) {
        return width == -1 ? -1 : Math.max(0, width - insets.getLeft() - insets.getRight());
    }
}
