package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBBCodeView;
import io.github.leewyatt.rxcontrols.bbcode.RXBBBlockNode;
import io.github.leewyatt.rxcontrols.bbcode.RXBBDocument;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Skin for {@link RXBBCodeView}. Renders the parsed document into a permanent
 * {@code .content} {@link VBox} — one child node per top-level block — and shows the
 * control's {@link RXBBCodeView#placeholderProperty() placeholder} when the document is
 * empty. The view does not scroll itself: it reports its natural wrapped height (it is
 * {@code HORIZONTAL} content-biased), and a caller who needs scrolling wraps it in a
 * {@code ScrollPane}.
 *
 * <p>Inline images are dynamic, background-loaded resources: every {@link Image} put on
 * screen is tracked in {@link #liveImages} so a rebuild (or dispose) can
 * {@link Image#cancel() cancel} in-flight loads. Because the image fit is applied
 * imperatively (not via long-lived bindings), the per-image listeners live between the
 * discarded {@code Image} / {@code ImageView} pair and are reclaimed with them — only the
 * pending network load needs an explicit cancel.
 */
public class RXBBCodeViewSkin extends RXSkinBase<RXBBCodeView> {

    // ==================== Nodes ====================

    private final VBox content = new VBox();

    /**
     * Background-loading images currently on screen, cancelled on every rebuild / dispose.
     */
    private final List<Image> liveImages = new ArrayList<>();

    private final RenderContext renderContext;

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control this skin is attached to
     */
    public RXBBCodeViewSkin(RXBBCodeView control) {
        super(control);
        renderContext = new RenderContext(control, liveImages);
        content.getStyleClass().add("content");
        content.setFillWidth(true);
        getChildren().add(content);
        installPlaceholder(control.getPlaceholder());

        disposer.registerBinding(content.spacingProperty(), control.paragraphSpacingProperty());
        disposer.registerListener(control.documentProperty(), this::rebuild);
        disposer.registerListener(control.imageMaxWidthProperty(), this::rebuild);
        disposer.registerListener(control.imageMaxHeightProperty(), this::rebuild);
        disposer.registerListener(control.maxFontSizeProperty(), this::rebuild);
        disposer.registerListener(control.placeholderProperty(),
                (observable, oldPlaceholder, newPlaceholder) -> onPlaceholderChanged(oldPlaceholder, newPlaceholder));

        rebuild();
    }

    // ==================== Rebuild ====================

    private void rebuild() {
        cancelLiveImages();
        content.getChildren().clear();
        RXBBDocument document = getSkinnable().getDocument();
        if (document != null) {
            BBCodeBlockRenderer blockRenderer = new BBCodeBlockRenderer(renderContext);
            for (RXBBBlockNode block : document.children()) {
                content.getChildren().add(block.accept(blockRenderer));
            }
        }
        updatePlaceholderState();
        getSkinnable().requestLayout();
    }

    private void cancelLiveImages() {
        for (Image image : liveImages) {
            image.cancel();
        }
        liveImages.clear();
    }

    // ==================== Placeholder ====================

    private void onPlaceholderChanged(Node oldPlaceholder, Node newPlaceholder) {
        if (oldPlaceholder != null) {
            getChildren().remove(oldPlaceholder);
        }
        installPlaceholder(newPlaceholder);
        // The placeholder is orthogonal to the document, so only re-toggle the empty-state
        // slot; a full rebuild() would needlessly cancel in-flight image loads (PR-12).
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
        if (!getChildren().contains(placeholder)) {
            getChildren().add(placeholder);
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

    private boolean isDocumentEmpty() {
        RXBBDocument document = getSkinnable().getDocument();
        return document == null || document.isEmpty();
    }

    /**
     * The node currently occupying the layout: the placeholder while empty, else the
     * content column. May be {@code null} when the document is empty and no placeholder
     * is set.
     */
    private Node shownNode() {
        return isDocumentEmpty() ? getSkinnable().getPlaceholder() : content;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        if (content.isManaged()) {
            layoutInArea(content, contentX, contentY, contentWidth, contentHeight, -1, HPos.LEFT, VPos.TOP);
        }
        Node placeholder = getSkinnable().getPlaceholder();
        if (placeholder != null && placeholder.isManaged()) {
            layoutInArea(placeholder, contentX, contentY, contentWidth, contentHeight, -1, HPos.CENTER, VPos.CENTER);
        }
    }

    // ==================== Sizing ====================

    // The content wraps, so height depends on width: delegate to the shown node at the
    // actual wrap width rather than letting SkinBase ask for prefHeight(-1).

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double innerWidth = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        Node target = shownNode();
        double inner = (target == null) ? 0 : target.prefHeight(innerWidth);
        return topInset + inner + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        cancelLiveImages();
        content.getChildren().clear();
    }
}
