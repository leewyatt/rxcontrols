package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.avatar;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.colorOf;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.initials;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.paint;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.sized;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXListView page: one tall contacts list under a toolbar that picks single
 * selection with filled rows or multiple selection with check boxes, with an Options
 * menu for the letter groups, collapsible and sticky headers, and folding every group
 * at once.
 */
public final class RXListViewSamples {

    private static final double LIST_WIDTH = 600.0;
    private static final double LIST_HEIGHT = 520.0;

    private static final List<String[]> PEOPLE = List.of(
            new String[]{"Lee Wyatt", "Product Designer"}, new String[]{"Ada Brooks", "Frontend Engineer"},
            new String[]{"Alma Reyes", "Data Analyst"}, new String[]{"Ben Carter", "Support Lead"},
            new String[]{"Bianca Moss", "QA Engineer"}, new String[]{"Caleb Stone", "Platform Engineer"},
            new String[]{"Clara Dunn", "Recruiter"}, new String[]{"Dario Field", "Sales Engineer"},
            new String[]{"Dana Hill", "Finance Partner"}, new String[]{"Elena Frost", "Technical Writer"},
            new String[]{"Ethan Price", "Mobile Engineer"}, new String[]{"Fiona Grant", "Research Lead"},
            new String[]{"Felix Hart", "Security Engineer"}, new String[]{"Grace Holt", "Program Manager"},
            new String[]{"Gavin Lake", "Backend Engineer"}, new String[]{"Hana Sato", "UX Researcher"},
            new String[]{"Hugo Lane", "Release Manager"}, new String[]{"Iris Vale", "Brand Designer"},
            new String[]{"Ivan Cole", "Site Reliability"}, new String[]{"Jade North", "People Partner"},
            new String[]{"Jonas Reed", "Data Engineer"}, new String[]{"Kara West", "Customer Success"},
            new String[]{"Liam Ford", "Solutions Architect"}, new String[]{"Lena Park", "Content Designer"},
            new String[]{"Mia Chen", "Engineering Manager"}, new String[]{"Marco Rossi", "Growth Analyst"},
            new String[]{"Nora Quinn", "Office Manager"}, new String[]{"Omar Haddad", "DevOps Engineer"},
            new String[]{"Paula Diaz", "Legal Counsel"}, new String[]{"Ravi Patel", "ML Engineer"},
            new String[]{"Rosa Lind", "Illustrator"}, new String[]{"Sam Ortega", "Developer Advocate"},
            new String[]{"Sofia Berg", "Product Manager"}, new String[]{"Theo Grey", "Game Designer"},
            new String[]{"Uma Shah", "Copywriter"}, new String[]{"Victor Wolfe", "Hardware Engineer"},
            new String[]{"Wendy Nash", "Event Planner"}, new String[]{"Yara Nolan", "Data Scientist"},
            new String[]{"Zoe Mills", "Motion Designer"});

    private record Person(String name, String role) {
        String letter() {
            return name.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        String email() {
            return name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com";
        }
    }

    private RXListViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A virtualized list with custom cells, section headers, collapsible groups and multiple selection.",
                List.of("Long or grouped lists: contacts, settings, messages, logs; only the visible rows hold cells.",
                        "For a grid of pictures use RXTileView; for a short fixed menu a VBox of buttons is simpler."),
                List.of(
                        SampleCard.wide("Contacts",
                                "sectionKeyFactory groups people by first letter; Multiple turns on check boxes, "
                                        + "and the Options menu changes the groups.",
                                contacts())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Contacts ====================

    private static Node contacts() {
        ObservableList<Person> people = FXCollections.observableArrayList();
        for (String[] person : PEOPLE) {
            people.add(new Person(person[0], person[1]));
        }
        SortedList<Person> sorted = new SortedList<>(people, Comparator.comparing(Person::name));
        RXListView<Person> list = sized(new RXListView<>(sorted), LIST_WIDTH, LIST_HEIGHT);
        list.getStyleClass().add("contacts");
        list.setFixedCellSize(56.0);
        list.setSectionKeyFactory(Person::letter);
        list.setCellFactory(view -> new PersonCell());
        list.setSectionHeaderFactory(view -> new LetterHeader());
        list.getSelectionModel().select(1);

        Label selected = text("", "hint");
        selected.textProperty().bind(Bindings.createStringBinding(() -> {
            List<Person> picked = list.getSelectionModel().getSelectedItems();
            if (picked.isEmpty()) {
                return "Nobody selected";
            }
            return picked.size() == 1 ? "Selected: " + picked.get(0).name() : picked.size() + " people selected";
        }, list.getSelectionModel().getSelectedItems()));

        VBox box = new VBox(12.0, toolbar(list), list, selected);
        box.setPrefWidth(LIST_WIDTH);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // The selection mode stays in sight; the groups wait in the Options menu.
    private static Node toolbar(RXListView<Person> list) {
        list.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
        RXSegmentedControl<SelectionMode> mode = new RXSegmentedControl<>(
                RXSegmentedItem.of(SelectionMode.SINGLE, "Single"),
                RXSegmentedItem.of(SelectionMode.MULTIPLE, "Multiple"));
        mode.setValue(SelectionMode.SINGLE);
        mode.valueProperty().addListener((obs, old, picked) -> {
            list.setSelectionMode(picked);
            // A filled row for one choice, a check box on every row for several.
            list.setSelectionVisualMode(picked == SelectionMode.MULTIPLE
                    ? RXListSelectionVisualMode.CHECKBOX : RXListSelectionVisualMode.ROW);
        });

        RXMenuButton options = options(list);
        for (Region region : List.of(mode, options)) {
            region.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox bar = new HBox(10.0, mode, spacer(), options);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private static RXMenuButton options(RXListView<Person> list) {
        BooleanProperty grouped = new SimpleBooleanProperty(true);
        grouped.addListener((obs, was, on) -> list.setSectionKeyFactory(on ? Person::letter : null));

        RXMenuItem collapsible = RXMenuItem.checkbox("Collapsible groups", list.collapsibleSectionsProperty());
        RXMenuItem sticky = RXMenuItem.checkbox("Sticky headers", list.stickySectionHeaderProperty());
        RXMenuItem expand = RXMenuItem.of("Expand all");
        expand.setOnAction(event -> list.expandAllSections());
        RXMenuItem collapse = RXMenuItem.of("Collapse all");
        collapse.setOnAction(event -> list.collapseAllSections());
        // Folding needs groups that fold; the headers need groups at all.
        BooleanBinding foldable = grouped.and(list.collapsibleSectionsProperty());
        expand.disableProperty().bind(foldable.not());
        collapse.disableProperty().bind(foldable.not());
        collapsible.disableProperty().bind(grouped.not());
        sticky.disableProperty().bind(grouped.not());

        RXMenuButton options = new RXMenuButton("Options");
        options.getItems().addAll(RXMenuItem.header("Groups"),
                RXMenuItem.checkbox("Group by first letter", grouped), collapsible, sticky,
                RXMenuItem.separator(), expand, collapse);
        return options;
    }

    private static final class PersonCell extends RXListCell<Person> {

        private final Label avatar = avatar(36.0);
        private final Label name = text("", "name");
        private final Label role = text("", "note");
        private final HBox row;

        private PersonCell() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            VBox lines = new VBox(2.0, name, role);
            lines.setAlignment(Pos.CENTER_LEFT);
            row = new HBox(12.0, avatar, lines);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Person person, boolean empty) {
            super.updateItem(person, empty);
            if (empty || person == null) {
                setText(null);
                setGraphic(null);
            } else {
                avatar.setText(initials(person.name()));
                paint(avatar, colorOf(person.name()));
                name.setText(person.name());
                role.setText(person.role());
                setGraphic(row);
            }
        }
    }

    private static final class LetterHeader extends RXListSectionCell {

        private final Label letter = text("", "letter");
        private final Label count = text("", "note");
        private final HBox row = new HBox(8.0, letter, spacer(), count);

        private LetterHeader() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(RXListSection section, boolean empty) {
            super.updateItem(section, empty);
            if (empty || section == null) {
                setGraphic(null);
            } else {
                letter.setText(String.valueOf(section.key()));
                count.setText(section.itemCount() == 1 ? "1 person" : section.itemCount() + " people");
                setGraphic(row);
            }
        }
    }
}
