package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.RXPlaceholder.Status;

import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

/**
 * Skin for {@link RXPlaceholder}. Stacks the graphic, title, description, and
 * actions slots in a centered vertical box; empty slots are hidden and
 * unmanaged so they take no space, and each slot mirrors its state through the
 * {@code :filled} pseudo-class.
 *
 * <p>The description renders in a {@link TextFlow}, whose wrapped height JavaFX
 * containers ignore; {@code computeMinHeight} / {@code computePrefHeight}
 * therefore measure the content box at the actual wrap width so multi-line
 * descriptions never truncate the footer actions.</p>
 */
public class RXPlaceholderSkin extends RXSkinBase<RXPlaceholder> {

    private static final PseudoClass FILLED_PSEUDO_CLASS = PseudoClass.getPseudoClass("filled");

    private final VBox contentBox = new VBox();
    private final StackPane graphicSlot = new StackPane();
    private final Region statusIcon = new Region();
    private final Label titleLabel = new Label();
    private final TextFlow descriptionFlow = new TextFlow();
    private final Text descriptionText = new Text();
    private final HBox actionsBox = new HBox();
    private final ListChangeListener<Node> actionsListener = change -> updateActions();

    /**
     * Creates the skin and assembles the slot structure from the control's
     * current property values.
     *
     * @param control the placeholder this skin is attached to
     */
    public RXPlaceholderSkin(RXPlaceholder control) {
        super(control);

        contentBox.getStyleClass().add("content");
        contentBox.setAlignment(Pos.CENTER);
        // fillWidth (the VBox default) must stay on: it is what carries the wrap
        // width into the description TextFlow during both measurement and slot
        // allocation — with fillWidth off, VBox measures children at width -1 and
        // a wrapped description would overlap the slots below it.

        graphicSlot.getStyleClass().add("graphic");
        statusIcon.getStyleClass().add("icon");
        statusIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        titleLabel.getStyleClass().add("title");
        // Slots stretch to the box width; center their content within.
        titleLabel.setAlignment(Pos.CENTER);

        descriptionFlow.getStyleClass().add("description");
        descriptionFlow.setTextAlignment(TextAlignment.CENTER);
        descriptionText.getStyleClass().add("text");
        descriptionFlow.getChildren().add(descriptionText);

        actionsBox.getStyleClass().add("actions");
        actionsBox.setAlignment(Pos.CENTER);

        contentBox.getChildren().setAll(graphicSlot, titleLabel, descriptionFlow, actionsBox);
        getChildren().setAll(contentBox);

        disposer.registerListener(control.graphicProperty(), this::updateGraphic);
        disposer.registerListener(control.statusProperty(), this::updateGraphic);
        disposer.registerListener(control.titleProperty(), this::updateTitle);
        disposer.registerListener(control.descriptionProperty(), this::updateDescription);
        control.getActions().addListener(actionsListener);
        disposer.registerDisposeTask(() -> getSkinnable().getActions().removeListener(actionsListener));

        updateGraphic();
        updateTitle();
        updateDescription();
        updateActions();
    }

    // ==================== Slots ====================

    private void updateGraphic() {
        Node userGraphic = getSkinnable().getGraphic();
        boolean filled;
        if (userGraphic != null) {
            graphicSlot.getChildren().setAll(userGraphic);
            filled = true;
        } else if (statusOrDefault() != Status.NONE) {
            graphicSlot.getChildren().setAll(statusIcon);
            filled = true;
        } else {
            graphicSlot.getChildren().clear();
            filled = false;
        }
        applySlotState(graphicSlot, filled);
    }

    private void updateTitle() {
        String value = getSkinnable().getTitle();
        titleLabel.setText(value);
        applySlotState(titleLabel, value != null && !value.isEmpty());
    }

    private void updateDescription() {
        String value = getSkinnable().getDescription();
        descriptionText.setText(value == null ? "" : value);
        applySlotState(descriptionFlow, value != null && !value.isEmpty());
    }

    private void updateActions() {
        actionsBox.getChildren().setAll(getSkinnable().getActions());
        applySlotState(actionsBox, !actionsBox.getChildren().isEmpty());
    }

    private void applySlotState(Node slot, boolean filled) {
        slot.setVisible(filled);
        slot.setManaged(filled);
        slot.pseudoClassStateChanged(FILLED_PSEUDO_CLASS, filled);
    }

    private Status statusOrDefault() {
        Status current = getSkinnable().getStatus();
        return current == null ? RXPlaceholder.DEFAULT_STATUS : current;
    }

    // ==================== Sizing ====================

    // The description TextFlow is HORIZONTAL content-biased and containers
    // ignore its wrapped height; measure the content box at the actual wrap
    // width so multi-line descriptions never truncate the actions footer.

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double inner = (height == -1) ? -1 : Math.max(0, height - topInset - bottomInset);
        return leftInset + contentBox.prefWidth(inner) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double inner = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        return topInset + contentBox.prefHeight(inner) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double inner = (height == -1) ? -1 : Math.max(0, height - topInset - bottomInset);
        return leftInset + contentBox.minWidth(inner) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }
}
