package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipSet;
import io.github.leewyatt.rxcontrols.RXChipSet.SelectionMode;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXChipSet page: a required single choice, an optional single choice, an editable
 * tag list, a display-only set on a profile and a job filter bar that narrows a list.
 */
public final class RXChipSetSamples {

    private record Job(String title, String company, Set<String> tags) {
    }

    private static final List<Job> JOBS = List.of(
            new Job("Frontend engineer", "Northwind", Set.of("Remote", "Full time", "Senior")),
            new Job("Product designer", "Bluebird Labs", Set.of("Hybrid", "Full time")),
            new Job("Support specialist", "Kettle & Co", Set.of("Remote", "Part time", "Junior")),
            new Job("Data analyst", "Orbit", Set.of("On site", "Contract", "Senior")),
            new Job("QA tester", "Northwind", Set.of("Remote", "Contract", "Junior")));

    private static final List<String> FILTERS =
            List.of("Remote", "Hybrid", "On site", "Full time", "Part time", "Contract", "Junior", "Senior");

    private RXChipSetSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A wrapping group of chips with a shared selection: none, single or multiple.",
                List.of("A filter bar, a set of answers to pick from, or a list of tags that wraps to fit.",
                        "To add tags by typing use RXChipInput; for a single chip on its own use RXChip."),
                List.of(
                        SampleCard.large("Required choice",
                                "SINGLE with allowEmptySelection off: one size is always picked once a choice exists.",
                                sizes()),
                        SampleCard.large("Optional choice",
                                "SINGLE with the default allowEmptySelection: clicking the picked chip clears it.",
                                delivery()),
                        SampleCard.large("Editable tags",
                                "Close buttons or Delete remove a removable chip; Enter in the field adds one.",
                                editable()),
                        SampleCard.large("Display only",
                                "Selection mode NONE, the default, turns the set into a plain list of tags.",
                                profile()),
                        SampleCard.wide("Filter bar",
                                "MULTIPLE selection: the list shows only the jobs that carry every selected tag.",
                                jobFilter())),
                SelectionIcons.STYLESHEET);
    }

    private static Node jobFilter() {
        RXChipSet filters = new RXChipSet();
        for (String filter : FILTERS) {
            filters.getChips().add(selectable(filter));
        }
        filters.setSelectionMode(SelectionMode.MULTIPLE);
        filters.getChips().get(0).setSelected(true);

        VBox list = new VBox(0.0);
        list.getStyleClass().add("job-list");
        Label empty = text("No jobs match all the selected tags", "hint");
        Runnable update = () -> {
            Set<String> wanted = filters.getSelectedChips().stream().map(RXChip::getText).collect(Collectors.toSet());
            list.getChildren().clear();
            for (Job job : JOBS) {
                if (job.tags().containsAll(wanted)) {
                    list.getChildren().add(jobRow(job));
                }
            }
            if (list.getChildren().isEmpty()) {
                list.getChildren().add(empty);
            }
        };
        filters.setOnSelectionChange(event -> update.run());
        update.run();
        VBox box = new VBox(14.0, filters, list);
        box.setPrefWidth(560.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node jobRow(Job job) {
        VBox texts = new VBox(1.0, text(job.title(), "body"), text(job.company(), "hint"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        Label tags = text(String.join(" · ", job.tags().stream().sorted().collect(Collectors.toList())), "hint");
        HBox row = new HBox(12.0, texts, tags);
        row.getStyleClass().add("job-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node sizes() {
        RXChipSet set = new RXChipSet(selectable("XS"), selectable("S"), selectable("M"), selectable("L"),
                selectable("XL"));
        set.setSelectionMode(SelectionMode.SINGLE);
        set.setAllowEmptySelection(false);
        set.getChips().get(2).setSelected(true);
        set.getStyleClass().add("teal");
        return withStatus(set, "Size: ");
    }

    private static Node delivery() {
        RXChipSet set = new RXChipSet(selectable("Morning"), selectable("Afternoon"), selectable("Evening"));
        set.setSelectionMode(SelectionMode.SINGLE);
        set.getStyleClass().add("orange");
        return withStatus(set, "Delivery slot: ");
    }

    private static Node withStatus(RXChipSet set, String prefix) {
        Label status = text("", "hint");
        status.textProperty().bind(Bindings.createStringBinding(
                () -> prefix + (set.getSelectedChips().isEmpty() ? "any" : set.getSelectedChips().get(0).getText()),
                set.selectedChipsProperty()));
        VBox box = new VBox(10.0, set, status);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node editable() {
        RXChipSet set = new RXChipSet();
        for (String tag : List.of("travel", "family", "summer 2026", "beach")) {
            set.getChips().add(removable(tag));
        }
        TextField field = new TextField();
        field.setPromptText("Add a tag and press Enter");
        field.setOnAction(event -> {
            String tag = field.getText().trim();
            if (!tag.isEmpty()) {
                set.getChips().add(removable(tag));
                field.clear();
            }
        });
        Label count = text("", "hint");
        count.textProperty().bind(Bindings.size(set.getChips()).asString("%d tags"));
        VBox box = new VBox(10.0, set, field, count);
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node profile() {
        RXAvatar avatar = new RXAvatar();
        avatar.setText("LW");
        avatar.setPrefSize(44.0, 44.0);
        avatar.getStyleClass().add("profile-avatar");
        VBox names = new VBox(1.0, text("Lee Wyatt", "body"), text("@LeeWyatt_7788", "hint"));
        HBox head = new HBox(12.0, avatar, names);
        head.setAlignment(Pos.CENTER_LEFT);
        RXChipSet skills = new RXChipSet(new RXChip("JavaFX"), new RXChip("CSS"), new RXChip("UI design"),
                new RXChip("Open source"), new RXChip("Java 17"));
        skills.setHgap(6.0);
        skills.setVgap(6.0);
        VBox card = new VBox(12.0, head, skills);
        card.getStyleClass().add("form-panel");
        card.setPrefWidth(300.0);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        return card;
    }

    private static RXChip selectable(String text) {
        RXChip chip = new RXChip(text);
        chip.setSelectable(true);
        return chip;
    }

    private static RXChip removable(String text) {
        RXChip chip = new RXChip(text);
        chip.setRemovable(true);
        return chip;
    }
}
