package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
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

    private SampleCard(Size size, String title, String text, Node... content) {
        this.size = size;
        getStyleClass().add("example-card");

        RXFlowPane flow = new RXFlowPane(20.0, 20.0, content);
        flow.setAlignment(Pos.CENTER);
        StackPane stage = new StackPane(flow);
        stage.getStyleClass().add("stage");
        VBox.setVgrow(stage, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("title");
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
}
