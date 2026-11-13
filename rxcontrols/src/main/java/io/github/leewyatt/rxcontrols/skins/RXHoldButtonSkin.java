package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXHoldButton;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Skin for {@link RXHoldButton}: a progress fill layer below three captions,
 * one per face. The idle and holding captions share the first page and swap
 * by visibility; the completed caption sits on the second page. All three
 * stay in the scene, so the button is sized to the largest face and swapping
 * faces never changes its size.
 */
public class RXHoldButtonSkin extends RXSkinBase<RXHoldButton> {

    private static final PseudoClass HOLDING = PseudoClass.getPseudoClass("holding");
    private static final PseudoClass COMPLETED = PseudoClass.getPseudoClass("completed");

    // ==================== Nodes ====================

    private final Pane fillLayer = new Pane();
    private final Region fillRegion = new Region();
    private final TransitionPages pages = new TransitionPages("page", true);
    private final Label idleCaption = new Label();
    private final Label holdingCaption = new Label();
    private final Label completedCaption = new Label();

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given button.
     *
     * @param button the button this skin is attached to
     */
    public RXHoldButtonSkin(RXHoldButton button) {
        super(button);

        fillLayer.getStyleClass().add("fill-layer");
        fillLayer.setManaged(false);
        fillLayer.setMouseTransparent(true);
        fillLayer.setVisible(false);
        fillRegion.getStyleClass().add("fill-region");
        fillRegion.setManaged(false);
        fillLayer.getChildren().add(fillRegion);

        initCaption(idleCaption, "caption");
        initCaption(holdingCaption, "holding-caption");
        initCaption(completedCaption, "completed-caption");
        // Both captions stay managed, so the page is sized to the larger one.
        holdingCaption.setVisible(false);
        pages.getPageA().getChildren().addAll(idleCaption, holdingCaption);
        pages.getPageB().getChildren().add(completedCaption);
        getChildren().addAll(fillLayer, pages.getContentPane());

        // Registered first, so it runs last: by then every listener is gone
        // and nothing can swap the face again.
        disposer.registerDisposeTask(() -> {
            // A direct cut: disposing must not start a transition.
            pages.interrupt();
            pages.directCutTo(pages.getPageA());
            showHoldingCaption(false);
            button.pseudoClassStateChanged(HOLDING, false);
            button.pseudoClassStateChanged(COMPLETED, false);
            pages.dispose(button.getAnimation());
            // Only this skin's nodes: a replacement skin may already have
            // added its own children to the control.
            getChildren().removeAll(fillLayer, pages.getContentPane());
        });

        bindCaptions(button);
        wireFeedback(button);
    }

    private static void initCaption(Label caption, String styleClass) {
        caption.setMnemonicParsing(false);
        // Fill the page, so the button alignment places the content and a
        // narrowed button truncates the text.
        caption.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // setAll drops the built-in .label class: the caption's text fill and
        // font are bound to the button, so .label rules cannot style it
        // anyway, and .label:disabled would dim it a second time inside the
        // disabled button.
        caption.getStyleClass().setAll(styleClass);
    }

    private void bindCaptions(RXHoldButton button) {
        disposer.registerBinding(idleCaption.textProperty(), button.textProperty());
        disposer.registerBinding(idleCaption.graphicProperty(), button.graphicProperty());
        disposer.registerBinding(holdingCaption.textProperty(), button.holdingTextProperty());
        disposer.registerBinding(holdingCaption.graphicProperty(), button.holdingGraphicProperty());
        disposer.registerBinding(completedCaption.textProperty(), button.completedTextProperty());
        disposer.registerBinding(completedCaption.graphicProperty(), button.completedGraphicProperty());
        bindAppearance(idleCaption, button);
        bindAppearance(holdingCaption, button);
        bindAppearance(completedCaption, button);
    }

    private void bindAppearance(Label caption, RXHoldButton button) {
        disposer.registerBinding(caption.fontProperty(), button.fontProperty());
        disposer.registerBinding(caption.textFillProperty(), button.textFillProperty());
        disposer.registerBinding(caption.ellipsisStringProperty(), button.ellipsisStringProperty());
        disposer.registerBinding(caption.contentDisplayProperty(), button.contentDisplayProperty());
        disposer.registerBinding(caption.graphicTextGapProperty(), button.graphicTextGapProperty());
        disposer.registerBinding(caption.alignmentProperty(), button.alignmentProperty());
        disposer.registerBinding(caption.textAlignmentProperty(), button.textAlignmentProperty());
        disposer.registerBinding(caption.textOverrunProperty(), button.textOverrunProperty());
        disposer.registerBinding(caption.wrapTextProperty(), button.wrapTextProperty());
        disposer.registerBinding(caption.underlineProperty(), button.underlineProperty());
        disposer.registerBinding(caption.lineSpacingProperty(), button.lineSpacingProperty());
        // labelPadding pads the caption, as it pads the text and graphic of a
        // standard button.
        disposer.registerBinding(caption.paddingProperty(), button.labelPaddingProperty());
    }

    private void wireFeedback(RXHoldButton button) {
        // A reset posted before this skin existed is stale.
        button.getProperties().remove(FeedbackSignals.HOLD_RESET);
        disposer.registerListener(button.getProperties(), this::consumeResetSignal);
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXHoldButton button = getSkinnable();
        fillLayer.resizeRelocate(0.0, 0.0, button.getWidth(), button.getHeight());
        pages.layout(x, y, w, h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxPageWidth(false, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxPageHeight(false, w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxPageWidth(true, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxPageHeight(true, w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Buttons do not stretch beyond their preferred size, matching
        // LabeledSkinBase: layout panes clamp to this unless the user
        // raises maxWidth explicitly.
        return getSkinnable().prefWidth(height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // Every face counts, visible or not, so the size never changes on a swap.
    private double maxPageWidth(boolean min, double height) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minWidth(height), pageB.minWidth(height))
                : Math.max(pageA.prefWidth(height), pageB.prefWidth(height));
    }

    private double maxPageHeight(boolean min, double width) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minHeight(width), pageB.minHeight(width))
                : Math.max(pageA.prefHeight(width), pageB.prefHeight(width));
    }

    // ==================== Faces ====================

    private void showHoldingCaption(boolean holding) {
        idleCaption.setVisible(!holding);
        holdingCaption.setVisible(holding);
        getSkinnable().notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
    }

    // ==================== Feedback ====================

    private void consumeResetSignal() {
        getSkinnable().getProperties().remove(FeedbackSignals.HOLD_RESET);
    }

    // ==================== Accessibility ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.TEXT) {
            RXHoldButton button = getSkinnable();
            String accessibleText = button.getAccessibleText();
            if (accessibleText != null && !accessibleText.isEmpty()) {
                return accessibleText;
            }
            // A face without text is a visual choice; the button still needs
            // a name, so it falls back to the idle text.
            String faceText = currentCaption().getText();
            if (faceText != null && !faceText.isEmpty()) {
                return faceText;
            }
            String text = button.getText();
            if (text != null && !text.isEmpty()) {
                return text;
            }
            // Like a standard button, an icon-only button falls back to the
            // accessible text of its graphic.
            Node graphic = button.getGraphic();
            return graphic == null ? text : graphic.queryAccessibleAttribute(AccessibleAttribute.TEXT);
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    private Label currentCaption() {
        if (pages.getCurrentPage() == pages.getPageB()) {
            return completedCaption;
        }
        return holdingCaption.isVisible() ? holdingCaption : idleCaption;
    }
}
