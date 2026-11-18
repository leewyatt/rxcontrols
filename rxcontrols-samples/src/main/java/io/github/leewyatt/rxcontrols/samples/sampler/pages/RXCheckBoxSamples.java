package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXCheckBox page: a permissions grid, a tri-state "select all", a sign-up form gated
 * by a terms box, a colored to-do list and boxes placed after their labels.
 */
public final class RXCheckBoxSamples {

    private static final List<String> PERMISSIONS = List.of("View", "Comment", "Edit", "Delete", "Invite");

    private RXCheckBoxSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A check box with a Material box, an animated check mark and an indeterminate state.",
                List.of("Options that can be turned on one by one and take effect when a form is saved.",
                        "For a setting that applies at once use RXSwitchButton; for one choice of several use RXRadioButton."),
                List.of(
                        SampleCard.wide("Permissions",
                                "A role-by-permission grid of check boxes without labels; the owner row is disabled because owners can do everything.",
                                permissions()),
                        SampleCard.large("Select all",
                                "The parent box turns indeterminate while only some files are checked.",
                                selectAll()),
                        SampleCard.large("Sign-up form",
                                "The button stays disabled until the terms box is checked.",
                                signUp()),
                        SampleCard.large("To-do list",
                                "-rx-primary on each box gives every project its own color; done tasks are struck through.",
                                todo()),
                        SampleCard.large("Box on the right",
                                "boxSide RIGHT puts the box after the label, so a list of options lines up on the edge.",
                                rightSide())),
                SelectionIcons.STYLESHEET);
    }

    private static Node permissions() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("permission-grid");
        grid.setHgap(18.0);
        grid.setVgap(2.0);
        for (int c = 0; c < PERMISSIONS.size(); c++) {
            Label header = text(PERMISSIONS.get(c), "hint");
            GridPane.setHalignment(header, HPos.CENTER);
            grid.add(header, c + 1, 0);
        }
        String[] roles = {"Viewer", "Commenter", "Editor", "Owner"};
        for (int r = 0; r < roles.length; r++) {
            Label role = text(roles[r], "body");
            role.setMinWidth(Region.USE_PREF_SIZE);
            grid.add(role, 0, r + 1);
            for (int c = 0; c < PERMISSIONS.size(); c++) {
                RXCheckBox box = new RXCheckBox();
                box.setSelected(c <= r || r == roles.length - 1);
                if (r == roles.length - 1) {
                    box.setDisable(true);
                }
                GridPane.setHalignment(box, HPos.CENTER);
                grid.add(box, c + 1, r + 1);
            }
        }
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return grid;
    }

    private static Node selectAll() {
        RXCheckBox all = new RXCheckBox("All files");
        all.getStyleClass().add("parent");
        List<RXCheckBox> files = new ArrayList<>();
        VBox children = new VBox(0.0);
        children.getStyleClass().add("children");
        String[][] items = {{"Invoice-May.pdf", "214 KB"}, {"Team photo.png", "3.1 MB"}, {"Budget 2026.xlsx", "88 KB"}};
        for (int i = 0; i < items.length; i++) {
            RXCheckBox file = new RXCheckBox(items[i][0]);
            file.setSelected(i != 1);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(file, spacer, text(items[i][1], "hint"));
            row.setAlignment(Pos.CENTER_LEFT);
            files.add(file);
            children.getChildren().add(row);
        }
        boolean[] adjusting = {false};
        Runnable syncParent = () -> {
            long count = files.stream().filter(RXCheckBox::isSelected).count();
            adjusting[0] = true;
            all.setIndeterminate(count > 0 && count < files.size());
            all.setSelected(count == files.size());
            adjusting[0] = false;
        };
        files.forEach(file -> file.selectedProperty().addListener((obs, was, now) -> {
            if (!adjusting[0]) {
                syncParent.run();
            }
        }));
        all.selectedProperty().addListener((obs, was, now) -> {
            if (!adjusting[0]) {
                adjusting[0] = true;
                files.forEach(file -> file.setSelected(now));
                adjusting[0] = false;
            }
        });
        syncParent.run();
        VBox box = new VBox(2.0, all, children);
        box.setPrefWidth(280.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node signUp() {
        RXCheckBox terms = new RXCheckBox("I agree to the terms of service");
        RXCheckBox news = new RXCheckBox("Send me the monthly newsletter");
        news.setSelected(true);
        RXButton create = new RXButton("Create account");
        create.getStyleClass().add("solid");
        create.disableProperty().bind(terms.selectedProperty().not());
        VBox form = new VBox(2.0, terms, news);
        VBox box = new VBox(12.0, form, create);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node todo() {
        VBox list = new VBox(0.0,
                task("Sketch the onboarding screens", "Design", "rose", true),
                task("Fix the login timeout", "Backend", "teal", false),
                task("Write the release notes", "Docs", "amber", false),
                task("Review the pricing page", "Marketing", "violet", true));
        list.setPrefWidth(320.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    private static Node task(String title, String project, String color, boolean done) {
        RXCheckBox box = new RXCheckBox(title);
        box.getStyleClass().addAll("task", color);
        box.setSelected(done);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tag = text(project, "project-tag");
        tag.getStyleClass().add(color);
        HBox row = new HBox(8.0, box, spacer, tag);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node rightSide() {
        VBox list = new VBox(0.0,
                trailing("Email me when someone replies", "mail-icon", true),
                trailing("Weekly summary", "calendar-icon", false),
                trailing("Product updates", "bell-icon", true));
        list.getStyleClass().add("option-list");
        list.setPrefWidth(300.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    private static Node trailing(String title, String iconClass, boolean selected) {
        RXCheckBox box = new RXCheckBox(title, icon(iconClass));
        box.setBoxSide(HorizontalDirection.RIGHT);
        box.setSelected(selected);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("trailing");
        return box;
    }
}
