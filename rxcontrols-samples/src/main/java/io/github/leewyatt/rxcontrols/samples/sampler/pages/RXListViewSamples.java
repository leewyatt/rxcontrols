package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.avatar;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.colorOf;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.controls;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.initials;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.paint;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.section;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.setting;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.sized;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXListView page: one tall contacts list with a panel beside it that switches the
 * letter groups, collapsible and sticky headers, single or multiple selection and the
 * look of the selection.
 */
public final class RXListViewSamples {

    private static final double LIST_WIDTH = 400.0;
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
                                "sectionKeyFactory groups people by first letter; the panel switches the groups, "
                                        + "their headers and how a selection is made and shown.",
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

        HBox box = new HBox(28.0, list, panel(list));
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private static Node panel(RXListView<Person> list) {
        RXSwitchButton grouped = setting("Group by first letter");
        grouped.setSelected(true);
        grouped.selectedProperty().addListener((obs, was, on) ->
                list.setSectionKeyFactory(on ? Person::letter : null));

        RXSwitchButton collapsible = setting("Collapsible groups");
        collapsible.disableProperty().bind(grouped.selectedProperty().not());
        list.collapsibleSectionsProperty().bind(collapsible.selectedProperty());

        // Shown only while the groups can fold; the picked segment follows the groups, so
        // folding one by hand leaves neither picked.
        RXSegmentedControl<Boolean> fold = new RXSegmentedControl<>(
                RXSegmentedItem.of(true, "Expand all"), RXSegmentedItem.of(false, "Collapse all"));
        fold.setBlock(true);
        fold.setAllowEmptySelection(true);
        fold.visibleProperty().bind(collapsible.selectedProperty().and(grouped.selectedProperty()));
        fold.managedProperty().bind(fold.visibleProperty());
        boolean[] syncing = {false};
        fold.valueProperty().addListener((obs, old, expand) -> {
            if (syncing[0] || expand == null) {
                return;
            }
            if (expand) {
                list.expandAllSections();
            } else {
                list.collapseAllSections();
            }
        });
        Runnable syncFold = () -> {
            int collapsed = list.getCollapsedSectionKeys().size();
            syncing[0] = true;
            fold.setValue(collapsed == 0 ? Boolean.TRUE
                    : collapsed == list.getSections().size() ? Boolean.FALSE : null);
            syncing[0] = false;
        };
        list.getCollapsedSectionKeys().addListener((SetChangeListener<Object>) change -> syncFold.run());
        list.getSections().addListener((ListChangeListener<RXListSection>) change -> syncFold.run());
        syncFold.run();

        RXSwitchButton sticky = setting("Sticky headers");
        sticky.setSelected(list.isStickySectionHeader());
        sticky.disableProperty().bind(grouped.selectedProperty().not());
        list.stickySectionHeaderProperty().bind(sticky.selectedProperty());

        RXSegmentedControl<RXListSelectionVisualMode> look = new RXSegmentedControl<>(
                RXSegmentedItem.of(RXListSelectionVisualMode.ROW, "Row"),
                RXSegmentedItem.of(RXListSelectionVisualMode.CHECKMARK, "Check mark"),
                RXSegmentedItem.of(RXListSelectionVisualMode.CHECKBOX, "Check box"));
        look.setValue(RXListSelectionVisualMode.ROW);
        look.setBlock(true);
        list.selectionVisualModeProperty().bind(look.valueProperty());

        RXSegmentedControl<SelectionMode> mode = new RXSegmentedControl<>(
                RXSegmentedItem.of(SelectionMode.SINGLE, "Single"),
                RXSegmentedItem.of(SelectionMode.MULTIPLE, "Multiple"));
        mode.setValue(SelectionMode.SINGLE);
        mode.setBlock(true);
        mode.valueProperty().addListener((obs, old, picked) -> {
            list.setSelectionMode(picked);
            // A multiple choice reads best with a light tick on each picked row.
            look.setValue(picked == SelectionMode.MULTIPLE
                    ? RXListSelectionVisualMode.CHECKMARK : RXListSelectionVisualMode.ROW);
        });

        Label selected = text("", "body");
        selected.setWrapText(true);
        selected.textProperty().bind(Bindings.createStringBinding(() -> {
            List<Person> picked = list.getSelectionModel().getSelectedItems();
            if (picked.isEmpty()) {
                return "Nobody selected";
            }
            return picked.size() == 1 ? "Selected: " + picked.get(0).name() : picked.size() + " people selected";
        }, list.getSelectionModel().getSelectedItems()));

        VBox panel = controls(
                section("Groups", true), grouped, collapsible, fold, sticky,
                section("Selection", false), mode, look,
                selected);
        VBox.setMargin(selected, new Insets(14.0, 0.0, 0.0, 0.0));
        return panel;
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
