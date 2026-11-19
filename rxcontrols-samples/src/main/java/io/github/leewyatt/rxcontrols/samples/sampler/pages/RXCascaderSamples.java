package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.CascaderData.branch;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.CascaderData.find;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.CascaderData.last;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.CascaderData.leaves;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXCascader page: a shipping address, a product category that shows only its last
 * level, regions checked in multiple selection, people loaded on demand, courses with
 * full and not yet published options, and a saved city written in a custom format.
 */
public final class RXCascaderSamples {

    private static final double FIELD_WIDTH = 250.0;

    private RXCascaderSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A field that picks a value from a tree by opening one column per level in a popup.",
                List.of("Nested choices with a few levels: country, state and city; category and subcategory.",
                        "For a flat list use RXSelectionBox; to keep the columns on the page use RXCascaderView."),
                List.of(
                        SampleCard.medium("Shipping address",
                                "Country, state and city in one field; clearable adds a button that empties it.",
                                address()),
                        SampleCard.medium("Last level only",
                                "With showAllLevels off the field shows just the chosen category, not the whole path.",
                                category()),
                        SampleCard.medium("Multiple selection",
                                "MULTIPLE puts a check box on every row; checking a region checks all its countries, and the field lists the last level of each.",
                                regions()),
                        SampleCard.medium("Lazy loading",
                                "childrenLoader fetches teams and people only when a row is opened; a spinner shows while it waits.",
                                lazy()),
                        SampleCard.medium("Disabled and empty",
                                "Full courses are disabled; a faculty with no courses yet opens an empty column with emptyText.",
                                courses()),
                        SampleCard.medium("Saved value, custom text",
                                "select restores a saved city; pathTextFactory writes it from the city outward.",
                                savedCity())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Shipping address ====================

    private static Node address() {
        RXCascader<String> field = field("Country / State / City");
        field.getRootItems().setAll(CascaderData.places());
        field.setClearable(true);
        field.select(find(field.getRootItems(), "Toronto"));
        return form("Ship to", field, icon("pin-icon"));
    }

    // ==================== Last level only ====================

    private static Node category() {
        RXCascader<String> field = field("Choose a category");
        field.getRootItems().setAll(CascaderData.categories());
        field.setShowAllLevels(false);
        field.select(find(field.getRootItems(), "Headphones"));
        return form("Category", field, icon("tag-icon"));
    }

    // ==================== Multiple selection ====================

    private static Node regions() {
        RXCascader<String> field = field("Where do you ship?");
        field.setSelectionMode(SelectionMode.MULTIPLE);
        field.getRootItems().setAll(CascaderData.regions());
        field.setClearable(true);
        field.setPathTextFactory(CascaderData::last);
        field.seedChecked(List.of(find(field.getRootItems(), "France"), find(field.getRootItems(), "Germany"),
                find(field.getRootItems(), "Japan")));
        Label count = text("", "hint");
        count.textProperty().bind(Bindings.createStringBinding(() -> {
            int size = field.getCheckedPaths().size();
            return size == 0 ? "No countries yet" : size + (size == 1 ? " country" : " countries") + " selected";
        }, field.getCheckedPaths()));
        return form("Ship to", field, icon("globe-icon"), count);
    }

    // ==================== Lazy loading ====================

    private static Node lazy() {
        RXCascader<String> field = field("Department / Team / Person");
        field.setChildrenLoader(CascaderData::loadChildren);
        field.getRootItems().setAll(CascaderData.departments());
        Label picked = text("", "hint");
        picked.textProperty().bind(Bindings.createStringBinding(() -> {
            RXCascaderPath<String> path = field.getSelectedPath();
            return path == null ? "Each level loads in about 0.7 s" : "Assigned to " + last(path);
        }, field.selectedPathProperty()));
        return form("Assign to", field, icon("person-icon"), picked);
    }

    // ==================== Disabled and empty ====================

    private static Node courses() {
        RXCascaderItem<String> design = branch("Design 101 (full)");
        design.setDisable(true);
        RXCascaderItem<String> art = branch("Arts", leaves("Drawing basics", "Color theory"));
        art.getChildren().add(1, design);
        RXCascaderItem<String> ai = branch("Machine learning (full)");
        ai.setDisable(true);
        RXCascaderItem<String> science = branch("Computer science", leaves("Algorithms", "Databases"));
        science.getChildren().add(ai);
        RXCascaderItem<String> music = new RXCascaderItem<>("Music");
        music.setLeafHint(false);

        RXCascader<String> field = field("Pick a course");
        field.getRootItems().setAll(art, science, music);
        field.setEmptyText("No courses yet");
        return form("Course", field, icon("book-icon"));
    }

    // ==================== Saved value, custom text ====================

    private static Node savedCity() {
        RXCascader<String> field = field("Choose a city");
        field.getRootItems().setAll(CascaderData.places());
        field.setPathTextFactory(path -> {
            List<String> values = new ArrayList<>(path.getValues());
            Collections.reverse(values);
            return String.join(", ", values);
        });
        field.select(find(field.getRootItems(), "Kyoto"));
        return form("Home city", field, icon("home-icon"));
    }

    // ==================== Helpers ====================

    private static RXCascader<String> field(String prompt) {
        RXCascader<String> field = new RXCascader<>();
        field.setPromptText(prompt);
        field.setPrefWidth(FIELD_WIDTH);
        return field;
    }

    private static Node form(String caption, RXCascader<String> field, Region glyph, Node... extra) {
        HBox title = new HBox(6.0, glyph, text(caption, "body"));
        title.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8.0, title, field);
        box.getChildren().addAll(extra);
        box.setPrefWidth(FIELD_WIDTH);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }
}
