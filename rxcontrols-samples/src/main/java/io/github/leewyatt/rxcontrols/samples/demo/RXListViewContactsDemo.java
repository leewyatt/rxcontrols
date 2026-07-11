package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Comparator;
import java.util.Locale;

/**
 * Compact contacts app for {@link RXListView}: a sorted directory grouped into
 * sticky alphabet sections with custom rows and custom section headers.
 */
public class RXListViewContactsDemo extends Application {

    private record Contact(String name, String role, String location, String phone, String color) {

        String section() {
            return name.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        String initials() {
            String[] parts = name.split(" ");
            if (parts.length == 1) {
                return name.substring(0, 1).toUpperCase(Locale.ROOT);
            }
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                    .toUpperCase(Locale.ROOT);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        ObservableList<Contact> rawContacts = createContacts();
        SortedList<Contact> contacts = new SortedList<>(rawContacts,
                Comparator.comparing(Contact::section).thenComparing(Contact::name));

        RXListView<Contact> list = new RXListView<>(contacts);
        list.setFixedCellSize(72.0);
        list.setSectionHeaderHeight(34.0);
        list.setSectionSpacing(6.0);
        list.setStickySectionHeader(true);
        list.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
        list.setSectionKeyFactory(Contact::section);
        list.setCellFactory(view -> new ContactCell());
        list.setSectionHeaderFactory(view -> new ContactSectionHeader());
        list.setStyle("-fx-background-color: white;"
                + " -fx-background-radius: 14;"
                + " -fx-border-color: #dbe3ee;"
                + " -fx-border-radius: 14;"
                + " -fx-padding: 6;");
        list.getSelectionModel().selectFirst();

        VBox content = new VBox(14.0, createHeader(rawContacts.size()), list);
        content.setMaxWidth(520.0);
        VBox.setVgrow(list, Priority.ALWAYS);

        StackPane root = new StackPane(content);
        root.setPadding(new Insets(24.0));
        root.setStyle("-fx-background-color: #eef3f8;");

        primaryStage.setScene(new Scene(root, 580.0, 720.0));
        primaryStage.setTitle("RXListView Contacts Demo");
        primaryStage.show();
    }

    private static Node createHeader(int contactCount) {
        Label title = new Label("Team Directory");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #162033;");

        Label count = new Label(contactCount + " contacts");
        count.setStyle("-fx-background-color: #dbeafe;"
                + " -fx-background-radius: 999;"
                + " -fx-padding: 5 10;"
                + " -fx-text-fill: #1d4ed8;"
                + " -fx-font-size: 12px;"
                + " -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10.0, title, spacer, count);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label subtitle = new Label("Design, engineering, operations, support");
        subtitle.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        VBox header = new VBox(3.0, titleRow, subtitle);
        header.setPadding(new Insets(2.0, 4.0, 0.0, 4.0));
        return header;
    }

    private static ObservableList<Contact> createContacts() {
        return FXCollections.observableArrayList(
                new Contact("Logan Field", "Program Manager", "Remote", "000-000-0119", "#15803d"),
                new Contact("Arden Vale", "Product Designer", "Remote", "000-000-0101", "#4f46e5"),
                new Contact("Rowan Brooks", "Growth Analyst", "Remote", "000-000-0123", "#0369a1"),
                new Contact("Cameron Reed", "Platform Engineer", "Remote", "000-000-0105", "#7c3aed"),
                new Contact("Harper Quinn", "People Partner", "Remote", "000-000-0114", "#be123c"),
                new Contact("Blair Stone", "Frontend Engineer", "Remote", "000-000-0103", "#059669"),
                new Contact("Wren Calder", "Release Manager", "Remote", "000-000-0126", "#ca8a04"),
                new Contact("Ellis Gray", "QA Engineer", "Remote", "000-000-0109", "#0d9488"),
                new Contact("Avery North", "Research Lead", "Remote", "000-000-0102", "#0891b2"),
                new Contact("Morgan Ash", "Solutions Architect", "Remote", "000-000-0120", "#b45309"),
                new Contact("Devon Pierce", "Data Analyst", "Remote", "000-000-0107", "#2563eb"),
                new Contact("Skyler West", "Design Ops", "Remote", "000-000-0124", "#e11d48"),
                new Contact("Finley Hart", "Security Engineer", "Remote", "000-000-0111", "#c2410c"),
                new Contact("Casey Holt", "Customer Success", "Remote", "000-000-0106", "#ea580c"),
                new Contact("Parker Hale", "Finance Partner", "Remote", "000-000-0121", "#047857"),
                new Contact("Briar Lane", "Brand Strategist", "Remote", "000-000-0104", "#db2777"),
                new Contact("Jordan Lake", "Infrastructure Lead", "Remote", "000-000-0117", "#1d4ed8"),
                new Contact("Emery Frost", "Technical Writer", "Remote", "000-000-0110", "#9333ea"),
                new Contact("Taylor Grove", "Developer Advocate", "Remote", "000-000-0125", "#0e7490"),
                new Contact("Drew Mercer", "Operations Manager", "Remote", "000-000-0108", "#16a34a"),
                new Contact("Greer Sloan", "Product Manager", "Remote", "000-000-0113", "#65a30d"),
                new Contact("Indigo Ridge", "Support Specialist", "Remote", "000-000-0116", "#0f766e"),
                new Contact("Reese Winter", "Mobile Engineer", "Remote", "000-000-0122", "#7e22ce"),
                new Contact("Hayden Wells", "Sales Engineer", "Remote", "000-000-0115", "#4f46e5"),
                new Contact("Kendall Moss", "UX Engineer", "Remote", "000-000-0118", "#c026d3"),
                new Contact("Flynn Carter", "Backend Engineer", "Remote", "000-000-0112", "#0284c7"));
    }

    private static final class ContactCell extends RXListCell<Contact> {

        private final Label avatar = new Label();
        private final Label name = new Label();
        private final Label role = new Label();
        private final Label phone = new Label();
        private final Region spacer = new Region();
        private final HBox row;

        private ContactCell() {
            // Full-row rich content: GRAPHIC_ONLY + a resizable graphic is stretched
            // by the skin to fill the content area (trailing phone right-aligns).
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            avatar.setMinSize(42.0, 42.0);
            avatar.setPrefSize(42.0, 42.0);
            avatar.setMaxSize(42.0, 42.0);
            avatar.setAlignment(Pos.CENTER);

            name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #162033;");
            role.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
            phone.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

            VBox text = new VBox(2.0, name, role);
            text.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row = new HBox(12.0, avatar, text, spacer, phone);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8.0, 10.0, 8.0, 0.0));
        }

        @Override
        protected void updateItem(Contact contact, boolean empty) {
            super.updateItem(contact, empty);
            if (empty || contact == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            avatar.setText(contact.initials());
            avatar.setStyle("-fx-background-color: " + contact.color() + ";"
                    + " -fx-background-radius: 999;"
                    + " -fx-text-fill: white;"
                    + " -fx-font-weight: bold;"
                    + " -fx-font-size: 12px;");
            name.setText(contact.name());
            role.setText(contact.role() + " / " + contact.location());
            phone.setText(contact.phone());
            setGraphic(row);
        }
    }

    private static final class ContactSectionHeader extends RXListSectionCell {

        private final Label letter = new Label();
        private final Label count = new Label();
        private final Region divider = new Region();
        private final HBox row = new HBox(8.0, letter, count, divider);

        private ContactSectionHeader() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setStyle("-fx-background-color: white;");

            letter.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155;");
            count.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            divider.setMaxHeight(1.0);
            divider.setStyle("-fx-background-color: #e2e8f0;");
            HBox.setHgrow(divider, Priority.ALWAYS);

            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(7.0, 14.0, 4.0, 14.0));
        }

        @Override
        protected void updateItem(RXListSection section, boolean empty) {
            super.updateItem(section, empty);
            if (empty || section == null) {
                setGraphic(null);
                return;
            }
            letter.setText(String.valueOf(section.key()));
            count.setText(section.itemCount() + " people");
            setGraphic(row);
        }
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
