package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * One example on a sampler page: a stage that holds live controls, and below it a title
 * and a one-sentence explanation of what the example shows.
 */
public final class SampleCard extends VBox {

    /**
     * How much of the gallery row a card takes once the page is wide enough.
     */
    public enum Size {
        /** A third of the row: buttons, switches, single indicators. */
        SMALL,
        /** Half of the row: fields, small cards, progress bars. */
        MEDIUM,
        /** Half of the row, but only on a wide page: content that needs more room than MEDIUM. */
        LARGE,
        /** The whole row: lists, calendars, side-by-side comparisons. */
        WIDE
    }

    private final Size size;
    private final HBox links = new HBox(12.0);
    private Class<?> type;
    private String ribbon;

    private SampleCard(Size size, String title, String text, Node... content) {
        this(size, title, text, flow(content));
    }

    private SampleCard(Size size, String title, String text, Node stageContent) {
        this.size = size;
        getStyleClass().add("example-card");

        StackPane stage = new StackPane(stageContent);
        stage.getStyleClass().add("stage");
        VBox.setVgrow(stage, Priority.ALWAYS);

        // Selectable, so a card can be named exactly when talking about it; a click still
        // focuses it for copying, but Tab skips it rather than stopping at every card.
        RXTextView titleLabel = new RXTextView(title);
        titleLabel.getStyleClass().add("title");
        titleLabel.setFocusTraversable(false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        links.setAlignment(Pos.CENTER_RIGHT);
        HBox titleRow = new HBox(8.0, titleLabel, spacer, links);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("text");
        textLabel.setWrapText(true);
        textLabel.setMinHeight(Region.USE_PREF_SIZE);

        VBox meta = new VBox(4.0, titleRow, textLabel);
        meta.getStyleClass().add("meta");

        getChildren().addAll(stage, meta);
    }

    /**
     * Creates a card a third of the row wide.
     *
     * @param title   the example name
     * @param text    one sentence on what the example shows
     * @param content the live nodes on the stage
     * @return the card
     */
    public static SampleCard small(String title, String text, Node... content) {
        return new SampleCard(Size.SMALL, title, text, content);
    }

    /**
     * Creates a card half of the row wide.
     *
     * @param title   the example name
     * @param text    one sentence on what the example shows
     * @param content the live nodes on the stage
     * @return the card
     */
    public static SampleCard medium(String title, String text, Node... content) {
        return new SampleCard(Size.MEDIUM, title, text, content);
    }

    /**
     * Creates a card half of the row wide on a wide page and as wide as the row otherwise.
     *
     * @param title   the example name
     * @param text    one sentence on what the example shows
     * @param content the live nodes on the stage
     * @return the card
     */
    public static SampleCard large(String title, String text, Node... content) {
        return new SampleCard(Size.LARGE, title, text, content);
    }

    /**
     * Creates a card as wide as the row whose content fills the whole stage instead of
     * being centered in it, for examples that use the stage itself as their canvas.
     *
     * @param title   the example name
     * @param text    one sentence on what the example shows
     * @param content the node that fills the stage
     * @return the card
     */
    public static SampleCard canvas(String title, String text, Node content) {
        return new SampleCard(Size.WIDE, title, text, content);
    }

    /**
     * Creates a card as wide as the row.
     *
     * @param title   the example name
     * @param text    one sentence on what the example shows
     * @param content the live nodes on the stage
     * @return the card
     */
    public static SampleCard wide(String title, String text, Node... content) {
        return new SampleCard(Size.WIDE, title, text, content);
    }

    /**
     * Marks the card as the example of one type of a page that shows several, so the page
     * can put that type's demo and showcase links on it.
     *
     * @param value the type the card shows
     * @return this card
     */
    public SampleCard forType(Class<?> value) {
        type = value;
        return this;
    }

    /**
     * Returns the type set by {@link #forType(Class)}.
     *
     * @return the type, or {@code null}
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Hangs a ribbon with a short label over the card's top corner, to tell apart cards
     * that show the same thing in different modes.
     *
     * @param value the label
     * @return this card
     */
    public SampleCard ribbon(String value) {
        ribbon = value;
        return this;
    }

    /**
     * Returns the label set by {@link #ribbon(String)}.
     *
     * @return the label, or {@code null}
     */
    public String getRibbon() {
        return ribbon;
    }

    /**
     * Returns the width class of the card.
     *
     * @return the size
     */
    public Size getSize() {
        return size;
    }

    /**
     * Puts launcher links at the end of the title row.
     *
     * @param nodes the links
     */
    public void setLinks(Node... nodes) {
        links.getChildren().setAll(nodes);
    }

    private static Node flow(Node... content) {
        if (content.length == 1) {
            return new Centered(content[0]);
        }
        RXFlowPane flow = new RXFlowPane(20.0, 20.0, content);
        flow.setAlignment(Pos.CENTER);
        return flow;
    }

    /**
     * Centers a lone node at its preferred size and lets it shrink toward its minimum
     * width on a narrow card. A flow pane would hold it at its preferred width, so the
     * card could not get narrower than that and would overlap its neighbor.
     */
    private static final class Centered extends Pane {

        private final Node child;

        Centered(Node child) {
            super(child);
            this.child = child;
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computeMinWidth(double height) {
            return snappedLeftInset() + child.minWidth(-1) + snappedRightInset();
        }

        @Override
        protected double computePrefWidth(double height) {
            return snappedLeftInset() + child.prefWidth(-1) + snappedRightInset();
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computePrefHeight(double width) {
            double inner = width < 0 ? -1 : width - snappedLeftInset() - snappedRightInset();
            return snappedTopInset() + child.prefHeight(childWidth(inner)) + snappedBottomInset();
        }

        @Override
        protected void layoutChildren() {
            double width = getWidth() - snappedLeftInset() - snappedRightInset();
            double height = getHeight() - snappedTopInset() - snappedBottomInset();
            double w = childWidth(width);
            double h = Math.min(Math.max(child.prefHeight(w), child.minHeight(w)), Math.max(height, child.minHeight(w)));
            child.resizeRelocate(snapPositionX(snappedLeftInset() + (width - w) / 2.0),
                    snapPositionY(snappedTopInset() + (height - h) / 2.0), snapSizeX(w), snapSizeY(h));
        }

        private double childWidth(double available) {
            double pref = Math.min(child.prefWidth(-1), child.maxWidth(-1));
            if (available < 0) {
                return pref;
            }
            return Math.max(child.minWidth(-1), Math.min(available, pref));
        }
    }
}
