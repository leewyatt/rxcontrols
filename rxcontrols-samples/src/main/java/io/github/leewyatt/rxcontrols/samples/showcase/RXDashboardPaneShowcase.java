package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.event.RXDashboardEvent;
import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase for {@link RXDashboardPane}. Six plain cards, each a numbered title over one line, inside a
 * {@code ScrollPane}. A card that can move says so at its bottom left and shows an open hand, a locked card says it is
 * locked, and a card that can be resized shows a grip in its bottom-right resize handle; all three follow the card's
 * pseudo-classes and handles, so they change with the editing switches. Controls cover the grid (columns, row height,
 * gaps, card padding, minimum column width), editing (collision policy, keeping the cards packed upward, editable, live
 * resize, a locked card, a card that cannot move, one that cannot be resized, a minimum span, dragging by the title
 * only), animation, and the layout itself: save, restore, defaults, compact (only useful with packing off), adding and
 * removing cards, the encoded snapshot and the last user edit.
 */
public class RXDashboardPaneShowcase extends RXShowcaseApplication {

    private static final PseudoClass DRAG_BY_TITLE = PseudoClass.getPseudoClass("drag-by-title");

    private static final RXDashboardLayout DEFAULTS = RXDashboardLayout.builder(4)
            .card("weather", 0, 0, 2, 1)
            .card("sales", 2, 0, 1, 2)
            .card("tasks", 3, 0, 1, 1)
            .card("storage", 0, 1, 1, 1)
            .card("team", 1, 1, 1, 1)
            .card("release", 3, 1, 1, 1)
            .build();

    private RXDashboardPane dashboard;
    private RXDashboardLayout saved;
    private final DoubleProperty cardPadding = new SimpleDoubleProperty(8.0);
    private final BooleanProperty dragByTitle = new SimpleBooleanProperty();
    private int cardCount;

    @Override
    protected String title() {
        return "RXDashboardPane";
    }

    @Override
    protected String subtitle() {
        return "An editable dashboard: drag and resize cards on a fixed grid, save and restore the layout";
    }

    @Override
    protected Node createPreview() {
        dashboard = new RXDashboardPane();
        dashboard.setColumnCount(4);
        dashboard.setRowHeight(120.0);
        dashboard.getCards().addAll(
                card("weather", "Weather", "Temperature 26°"),
                card("sales", "Sales", "Revenue $48.2k"),
                card("tasks", "Tasks", "18 of 25 done"),
                card("storage", "Storage", "640 GB of 1 TB used"),
                card("team", "Team", "3 online, 1 away"),
                card("release", "Release", "Version 3.2 in testing"));
        dashboard.applyLayout(DEFAULTS);
        saved = dashboard.getLayout();
        dragByTitle.addListener((obs, old, value) -> dashboard.pseudoClassStateChanged(DRAG_BY_TITLE, value));
        ScrollPane scroller = new ScrollPane(dashboard);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("dashboard-scroller");
        return scroller;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Grid", gridSection()),
                section("Editing", editingSection()),
                section("Animation", animationSection()),
                section("Layout", layoutSection()));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-dashboard-pane-showcase.css").toExternalForm();
    }

    // ==================== Sections ====================

    private Node gridSection() {
        Slider columns = intSlider(1, 12, dashboard.getColumnCount());
        columns.valueProperty().addListener((obs, old, value) -> dashboard.setColumnCount(value.intValue()));
        Slider rowHeight = createSlider(60, 260, dashboard.getRowHeight());
        rowHeight.valueProperty().addListener((obs, old, value) -> dashboard.setRowHeight(value.doubleValue()));
        Slider hgap = createSlider(0, 24, dashboard.getHgap());
        hgap.valueProperty().addListener((obs, old, value) -> dashboard.setHgap(value.doubleValue()));
        Slider vgap = createSlider(0, 24, dashboard.getVgap());
        vgap.valueProperty().addListener((obs, old, value) -> dashboard.setVgap(value.doubleValue()));
        Slider padding = createSlider(0, 16, cardPadding.get());
        cardPadding.bind(padding.valueProperty());
        Slider minColumn = createSlider(0, 200, dashboard.getMinColumnWidth());
        minColumn.valueProperty().addListener(
                (obs, old, value) -> dashboard.setMinColumnWidth(value.doubleValue()));
        Label note = new Label("Changing the column count converts the layout; changing it back with no edit in "
                + "between restores it. A minimum column width makes the scroll pane scroll sideways instead of "
                + "narrowing the columns.");
        note.getStyleClass().add("note-label");
        note.setWrapText(true);
        return createGrid(
                row("Columns", columns, createValueLabel(columns, "%.0f")),
                row("Row height", rowHeight, createValueLabel(rowHeight, "%.0f px")),
                row("Hgap", hgap, createValueLabel(hgap, "%.0f px")),
                row("Vgap", vgap, createValueLabel(vgap, "%.0f px")),
                row("Card padding", padding, createValueLabel(padding, "%.0f px")),
                row("Min column width", minColumn, createValueLabel(minColumn, "%.0f px")),
                row(note));
    }

    private Node editingSection() {
        ChoiceBox<CollisionPolicy> policy = new ChoiceBox<>(
                FXCollections.observableArrayList(CollisionPolicy.values()));
        policy.setValue(dashboard.getCollisionPolicy());
        policy.valueProperty().addListener((obs, old, value) -> dashboard.setCollisionPolicy(value));

        CheckBox autoCompact = new CheckBox("Keep the cards packed upward");
        autoCompact.selectedProperty().bindBidirectional(dashboard.autoCompactProperty());
        CheckBox editable = new CheckBox("Editable");
        editable.selectedProperty().bindBidirectional(dashboard.editableProperty());
        CheckBox liveResize = new CheckBox("Live resize");
        liveResize.selectedProperty().bindBidirectional(dashboard.liveResizeProperty());
        CheckBox locked = new CheckBox("Lock #2 Sales");
        locked.selectedProperty().addListener((obs, old, value) -> withCard("sales", card -> card.setLocked(value)));
        CheckBox fixed = new CheckBox("#1 Weather cannot move");
        fixed.selectedProperty().addListener(
                (obs, old, value) -> withCard("weather", card -> card.setMoveEnabled(!value)));
        CheckBox sized = new CheckBox("#3 Tasks cannot be resized");
        sized.selectedProperty().addListener(
                (obs, old, value) -> withCard("tasks", card -> card.setResizeEnabled(!value)));
        CheckBox minSpan = new CheckBox("#5 Team at least 2 columns");
        minSpan.selectedProperty().addListener(
                (obs, old, value) -> withCard("team", card -> card.setMinColumnSpan(value ? 2 : 1)));
        CheckBox handles = new CheckBox("Drag by the title only");
        handles.selectedProperty().bindBidirectional(dragByTitle);
        return createGrid(
                row("Collision", policy),
                row(autoCompact),
                row(editable),
                row(liveResize),
                row(locked),
                row(fixed),
                row(sized),
                row(minSpan),
                row(handles));
    }

    private Node animationSection() {
        CheckBox animated = new CheckBox("Animate layout changes");
        animated.selectedProperty().bindBidirectional(dashboard.animatedProperty());
        Slider duration = createSlider(0, 800, dashboard.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> dashboard.setAnimationDuration(Duration.millis(value.doubleValue())));
        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node layoutSection() {
        Button save = new Button("Save");
        save.setOnAction(e -> saved = dashboard.getLayout());
        Button restore = new Button("Restore");
        restore.setOnAction(e -> dashboard.applyLayout(saved));
        Button defaults = new Button("Defaults");
        defaults.setOnAction(e -> dashboard.applyLayout(DEFAULTS));
        Button compact = new Button("Compact");
        compact.setOnAction(e -> dashboard.compact());
        compact.disableProperty().bind(dashboard.autoCompactProperty());
        HBox layoutButtons = new HBox(8.0, save, restore, defaults, compact);
        layoutButtons.setAlignment(Pos.CENTER_LEFT);

        Button add = new Button("Add card");
        add.setOnAction(e -> dashboard.getCards().add(card("note-" + (cardCount + 1), "Note", "Added by Add card")));
        Button remove = new Button("Remove last");
        remove.setOnAction(e -> {
            if (!dashboard.getCards().isEmpty()) {
                dashboard.getCards().remove(dashboard.getCards().size() - 1);
            }
        });
        HBox cardButtons = new HBox(8.0, add, remove);
        cardButtons.setAlignment(Pos.CENTER_LEFT);

        TextArea encoded = new TextArea();
        encoded.setEditable(false);
        encoded.setWrapText(true);
        encoded.setPrefRowCount(4);
        encoded.textProperty().bind(dashboard.layoutProperty().asString());

        Label lastEdit = new Label("No user edit yet");
        lastEdit.setWrapText(true);
        dashboard.addEventHandler(RXDashboardEvent.LAYOUT_CHANGED, event -> lastEdit.setText(
                "Last user edit: " + event.getCard().getCardId() + " (" + event.getLayout().getPlacements().size()
                        + " cards in the snapshot)"));
        return createGrid(
                row(layoutButtons),
                row(cardButtons),
                row(encoded),
                row(lastEdit));
    }

    // ==================== Cards ====================

    // A plain card: a numbered title over one line, and at the bottom left what the user can do with it: move it (an
    // open hand and "Drag to move", or "Drag the title"), or nothing while it is locked. The style sheet shows each
    // hint by the card's pseudo-classes, and draws the resize grip in the card's own corner handle.
    private RXDashboardCard card(String id, String name, String description) {
        cardCount++;
        Label title = new Label("#" + cardCount + "  " + name);
        title.getStyleClass().add("card-title");
        title.setMaxWidth(Double.MAX_VALUE);
        Label line = new Label(description);
        line.getStyleClass().add("card-description");

        Label move = new Label();
        move.textProperty().bind(Bindings.when(dragByTitle).then("Drag the title").otherwise("Drag to move"));
        move.setGraphic(symbol("move-symbol"));
        move.getStyleClass().add("move-hint");
        Label lock = new Label("Locked", symbol("lock-symbol"));
        lock.getStyleClass().add("lock-hint");
        StackPane hints = new StackPane(move, lock);
        hints.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setVgrow(hints, Priority.ALWAYS);

        VBox box = new VBox(4.0, title, line, hints);
        RXDashboardCard card = new RXDashboardCard(id, box);
        bindPadding(card);
        card.dragHandleProperty().bind(Bindings.when(dragByTitle).then((Node) title).otherwise((Node) null));
        return card;
    }

    private static Region symbol(String styleClass) {
        Region symbol = new Region();
        symbol.getStyleClass().addAll("hint-symbol", styleClass);
        symbol.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return symbol;
    }

    private RXDashboardCard bindPadding(RXDashboardCard card) {
        card.paddingProperty().bind(Bindings.createObjectBinding(() -> new Insets(cardPadding.get()), cardPadding));
        return card;
    }

    private void withCard(String id, Consumer<RXDashboardCard> action) {
        for (RXDashboardCard card : dashboard.getCards()) {
            if (id.equals(card.getCardId())) {
                action.accept(card);
                return;
            }
        }
    }

    // ==================== Helpers ====================

    private Slider intSlider(int min, int max, int value) {
        Slider slider = createSlider(min, max, value);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(1);
        return slider;
    }

    @Override
    protected double sceneWidth() {
        return 1200;
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
