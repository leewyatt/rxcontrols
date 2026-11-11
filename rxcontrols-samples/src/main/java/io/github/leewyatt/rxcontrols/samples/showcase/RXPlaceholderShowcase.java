package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.SampleIcons;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXPlaceholder}.
 *
 * <p>Drives every slot — the graphic (an application-supplied icon), title,
 * wrapping description, and the actions footer — to verify slot collapse and
 * the {@code :filled} states interactively.</p>
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
        return "A centered graphic + title + description + actions display view for empty and error states.";
    }

    @Override
    protected Node createPreview() {
        placeholder = new RXPlaceholder("Placeholder Title", inboxIcon());
        placeholder.setDescription("Placeholder Description: Everything you load will show up here. "
                + "Use the panel on the right to explore every slot.");
        return placeholder;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(section("Slots", slotsGrid()));
    }

    private Node slotsGrid() {
        TextField title = new TextField(placeholder.getTitle());
        title.setPromptText("title (empty collapses)");
        title.textProperty().addListener((obs, old, value) -> placeholder.setTitle(value));

        TextField description = new TextField(placeholder.getDescription());
        description.setPromptText("description (wraps, empty collapses)");
        description.textProperty().addListener((obs, old, value) -> placeholder.setDescription(value));

        CheckBox graphic = checkBox("Graphic (empty collapses)", true, selected ->
                placeholder.setGraphic(selected ? inboxIcon() : null));

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
                row(graphic),
                row(primary),
                row(secondary));
    }

    private static Node inboxIcon() {
        return SampleIcons.icon(SampleIcons.INBOX, 48.0, "-rx-outline-variant");
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
