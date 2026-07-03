package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.RXPlaceholder.Status;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXPlaceholder}.
 *
 * <p>Drives every configurable slot — the status preset (and its pseudo-class
 * driven default icon), the graphic escape hatch, title, wrapping description,
 * and the actions footer — to verify slot collapse and the {@code :filled}
 * states interactively.</p>
 */
public class RXPlaceholderShowcase extends RXShowcaseApplication {

    private RXPlaceholder placeholder;
    private final Button primaryAction = new Button("Primary");
    private final Button secondaryAction = new Button("Secondary");

    @Override
    protected String title() {
        return "RXPlaceholder";
    }

    @Override
    protected String subtitle() {
        return "A centered icon + title + description + actions display view for empty, error, and result states.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-placeholder-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        placeholder = new RXPlaceholder(Status.EMPTY, "No data");
        placeholder.setDescription("Everything you load will show up here. "
                + "Use the panel on the right to explore every slot.");
        StackPane host = new StackPane(placeholder);
        host.getStyleClass().add("preview-host");
        return host;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Status", statusGrid()),
                section("Slots", slotsGrid()));
    }

    private Node statusGrid() {
        ComboBox<Status> status = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
        status.setValue(placeholder.getStatus());
        status.valueProperty().addListener((obs, old, value) -> placeholder.setStatus(value));
        status.setMaxWidth(Double.MAX_VALUE);
        return createGrid(row("Status", status));
    }

    private Node slotsGrid() {
        TextField title = new TextField(placeholder.getTitle());
        title.setPromptText("title (empty collapses)");
        title.textProperty().addListener((obs, old, value) -> placeholder.setTitle(value));

        TextField description = new TextField(placeholder.getDescription());
        description.setPromptText("description (wraps, empty collapses)");
        description.textProperty().addListener((obs, old, value) -> placeholder.setDescription(value));

        CheckBox customGraphic = checkBox("Custom graphic (escape hatch)", false, selected ->
                placeholder.setGraphic(selected ? customGraphic() : null));

        CheckBox primary = checkBox("Primary action", false, selected -> {
            if (selected) {
                placeholder.getActions().add(0, primaryAction);
            } else {
                placeholder.getActions().remove(primaryAction);
            }
        });
        CheckBox secondary = checkBox("Secondary action", false, selected -> {
            if (selected) {
                placeholder.getActions().add(secondaryAction);
            } else {
                placeholder.getActions().remove(secondaryAction);
            }
        });

        return createGrid(
                row("Title", title),
                row("Description", description),
                row(customGraphic),
                row(primary),
                row(secondary));
    }

    private Node customGraphic() {
        Circle circle = new Circle(28.0, Color.web("#616dff"));
        StackPane badge = new StackPane(circle);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return badge;
    }

    private static CheckBox checkBox(String text, boolean selected, Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox(text);
        box.setSelected(selected);
        box.selectedProperty().addListener((obs, old, value) -> onChange.accept(value));
        return box;
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
