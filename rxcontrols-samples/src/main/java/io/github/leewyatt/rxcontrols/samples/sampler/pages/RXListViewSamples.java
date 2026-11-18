package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.avatar;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.colorOf;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.converter;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.initials;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.paint;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.sized;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXListView page: a contacts list with sticky letter headers, collapsible settings
 * groups, multiple selection with check boxes, an inbox with rows of different heights,
 * a log of one hundred thousand lines and a single choice marked with a check mark.
 */
public final class RXListViewSamples {

    private static final double LIST_WIDTH = 420.0;
    private static final double LIST_HEIGHT = 300.0;

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

    private static final String[][] SETTINGS = {
            {"General", "About", "Software update", "Storage", "Date & time"},
            {"Appearance", "Theme", "Accent color", "Font size"},
            {"Network", "Wi-Fi", "Ethernet", "VPN", "Firewall"},
            {"Privacy", "Location", "Camera", "Microphone", "Notifications"},
            {"Advanced", "Developer mode", "Logs", "Reset"}};

    private static final String[][] MAIL = {
            {"Lee Wyatt", "Release notes for 2.4", "The draft is ready. Could you check the section on the new list view before Friday?"},
            {"Mia Chen", "Lunch?", "Noodles at noon."},
            {"Build server", "Nightly build passed", "All 2,930 tests passed in 34 seconds. Artifacts are on the download page, and the size of the bundle went down by 4 percent compared to last night."},
            {"Sam Ortega", "Slides", "Here are the slides from today."},
            {"Calendar", "Design review moved", "The design review moved from Tuesday 10:00 to Wednesday 14:30. Room 4B is booked, and the video link stays the same."},
            {"Ada Brooks", "Re: icons", "Looks great, ship it."},
            {"Hana Sato", "Interview notes", "I talked to six customers this week. Most of them sort their lists by date and almost nobody uses the filter panel, so we might move it behind a button."},
            {"Ravi Patel", "Model results", "Accuracy is up two points."}};

    private static final List<String> SOUNDS = List.of(
            "Chime", "Bell", "Pop", "Glass", "Marimba", "Breeze", "Pulse", "Ripple", "Echo", "Silent");

    private record Person(String name, String role) {
        String letter() {
            return name.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        String email() {
            return name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com";
        }
    }

    private record Setting(String group, String name) {
    }

    private record Mail(String from, String subject, String preview) {
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
                        SampleCard.large("Contacts",
                                "sectionKeyFactory groups people by first letter; the headers stick to the top while you scroll, which is the default.",
                                contacts()),
                        SampleCard.large("Collapsible groups",
                                "collapsibleSections turns every header into a toggle; Advanced starts collapsed.",
                                settings()),
                        SampleCard.large("Check boxes",
                                "MULTIPLE selection with the CHECKBOX visual mode; a click toggles a row.",
                                share()),
                        SampleCard.large("Rows of any height",
                                "A non-positive fixedCellSize sizes each row to its content, so long previews wrap.",
                                inbox()),
                        SampleCard.large("One hundred thousand rows",
                                "Only the visible rows hold cells; scrollTo jumps anywhere and Enter or a double-click fires onAction.",
                                log()),
                        SampleCard.large("Check mark",
                                "The CHECKMARK visual mode marks the single choice with a light tick instead of a filled row.",
                                sounds())),
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
        return list;
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

    // ==================== Collapsible groups ====================

    private static Node settings() {
        ObservableList<Setting> items = FXCollections.observableArrayList();
        for (String[] group : SETTINGS) {
            for (int i = 1; i < group.length; i++) {
                items.add(new Setting(group[0], group[i]));
            }
        }
        RXListView<Setting> list = sized(new RXListView<>(items), LIST_WIDTH, LIST_HEIGHT);
        list.setFixedCellSize(34.0);
        list.setSectionKeyFactory(Setting::group);
        list.setCollapsibleSections(true);
        list.setSectionExpanded("Advanced", false);
        list.setConverter(converter(Setting::name));
        list.getSelectionModel().select(1);

        Label page = text("", "hint");
        page.textProperty().bind(Bindings.createStringBinding(() -> {
            Setting setting = list.getSelectionModel().getSelectedItem();
            return setting == null ? "Pick a page" : "Open page: " + setting.group() + " › " + setting.name();
        }, list.getSelectionModel().selectedItemProperty()));

        HBox actions = new HBox(8.0, page, spacer(), button("Expand all", list::expandAllSections),
                button("Collapse all", list::collapseAllSections));
        actions.setAlignment(Pos.CENTER_LEFT);
        return column(list, actions);
    }

    // ==================== Check boxes ====================

    private static Node share() {
        ObservableList<Person> people = FXCollections.observableArrayList();
        for (int i = 0; i < 14; i++) {
            people.add(new Person(PEOPLE.get(i * 2)[0], PEOPLE.get(i * 2)[1]));
        }
        RXListView<Person> list = sized(new RXListView<>(people), LIST_WIDTH, LIST_HEIGHT);
        list.setSelectionMode(SelectionMode.MULTIPLE);
        list.setSelectionVisualMode(RXListSelectionVisualMode.CHECKBOX);
        list.setFixedCellSize(50.0);
        list.setCellFactory(view -> new EmailCell());
        list.getSelectionModel().selectIndices(0, 3, 4);

        Label count = text("", "hint");
        count.textProperty().bind(Bindings.size(list.getSelectionModel().getSelectedItems())
                .asString("%d people will get an invitation"));
        HBox actions = new HBox(8.0, count, spacer(),
                button("Select all", () -> list.getSelectionModel().selectAll()),
                button("Clear", () -> list.getSelectionModel().clearSelection()));
        actions.setAlignment(Pos.CENTER_LEFT);
        return column(list, actions);
    }

    private static final class EmailCell extends RXListCell<Person> {

        private final Label name = text("", "name");
        private final Label email = text("", "note");
        private final VBox lines = new VBox(1.0, name, email);

        private EmailCell() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            lines.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Person person, boolean empty) {
            super.updateItem(person, empty);
            if (empty || person == null) {
                setText(null);
                setGraphic(null);
            } else {
                name.setText(person.name());
                email.setText(person.email());
                setGraphic(lines);
            }
        }
    }

    // ==================== Rows of any height ====================

    private static Node inbox() {
        ObservableList<Mail> mails = FXCollections.observableArrayList();
        for (String[] mail : MAIL) {
            mails.add(new Mail(mail[0], mail[1], mail[2]));
        }
        RXListView<Mail> list = sized(new RXListView<>(mails), LIST_WIDTH, LIST_HEIGHT);
        list.setFixedCellSize(Region.USE_COMPUTED_SIZE);
        list.setEstimatedCellSize(76.0);
        list.setCellFactory(view -> new MailCell());
        list.getSelectionModel().select(0);
        return list;
    }

    private static final class MailCell extends RXListCell<Mail> {

        private final Label avatar = avatar(32.0);
        private final Label from = text("", "name");
        private final Label subject = text("", "subject");
        private final Label preview = text("", "note");
        private final HBox row;

        private MailCell() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            preview.setWrapText(true);
            VBox lines = new VBox(2.0, from, subject, preview);
            lines.setMinWidth(0.0);
            row = new HBox(12.0, avatar, lines);
            row.setAlignment(Pos.TOP_LEFT);
            row.getStyleClass().add("mail");
            HBox.setHgrow(lines, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(Mail mail, boolean empty) {
            super.updateItem(mail, empty);
            if (empty || mail == null) {
                setText(null);
                setGraphic(null);
            } else {
                avatar.setText(initials(mail.from()));
                paint(avatar, colorOf(mail.from()));
                from.setText(mail.from());
                subject.setText(mail.subject());
                preview.setText(mail.preview());
                setGraphic(row);
            }
        }
    }

    // ==================== One hundred thousand rows ====================

    private static Node log() {
        final int count = 100_000;
        ObservableList<Integer> lines = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            lines.add(i);
        }
        RXListView<Integer> list = sized(new RXListView<>(lines), LIST_WIDTH, LIST_HEIGHT);
        list.getStyleClass().add("log");
        list.setConverter(converter(RXListViewSamples::logLine));

        Label status = text("Double-click a line to open it", "hint");
        list.setOnAction(event -> status.setText("Opened line " + (event.getIndex() + 1)));

        HBox actions = new HBox(8.0, status, spacer(),
                button("Top", () -> jump(list, 0)),
                button("Middle", () -> jump(list, count / 2)),
                button("End", () -> jump(list, count - 1)));
        actions.setAlignment(Pos.CENTER_LEFT);
        return column(list, actions);
    }

    private static void jump(RXListView<Integer> list, int index) {
        list.getSelectionModel().clearAndSelect(index);
        list.scrollTo(index, ScrollAlignment.CENTER);
        list.requestFocus();
    }

    private static String logLine(Integer index) {
        int seconds = index / 3;
        String level = index % 41 == 0 ? "WARN " : "INFO ";
        return String.format("%02d:%02d:%02d  %s GET /api/orders/%d  %d ms",
                (seconds / 3600) % 24, (seconds / 60) % 60, seconds % 60, level, 10_000 + index,
                12 + (index * 37) % 180);
    }

    // ==================== Check mark ====================

    private static Node sounds() {
        RXListView<String> list = sized(new RXListView<>(FXCollections.observableArrayList(SOUNDS)),
                LIST_WIDTH, LIST_HEIGHT);
        list.setSelectionVisualMode(RXListSelectionVisualMode.CHECKMARK);
        list.setFixedCellSize(36.0);
        list.getSelectionModel().select("Marimba");

        Region bell = icon("bell-icon");
        Label sound = text("", "body");
        sound.textProperty().bind(Bindings.createStringBinding(() -> {
            String value = list.getSelectionModel().getSelectedItem();
            return value == null ? "No sound picked" : "New messages play " + value;
        }, list.getSelectionModel().selectedItemProperty()));
        HBox summary = new HBox(8.0, bell, sound);
        summary.setAlignment(Pos.CENTER_LEFT);
        return column(list, summary);
    }

    // ==================== Helpers ====================

    private static Node button(String value, Runnable action) {
        Button button = plain(value);
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Node column(Node... nodes) {
        VBox column = new VBox(12.0, nodes);
        column.setPrefWidth(LIST_WIDTH);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        return column;
    }
}
