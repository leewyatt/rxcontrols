package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Showcase for {@link RXKanbanView}: a three-column board (plus a backlog column) of
 * draggable cards, with a live control panel for spacing, sizing, drag / reorder
 * toggles and animation. Cards reorder within a column and move across columns by
 * pointer drag; column headers reorder the columns; each footer adds a card so the
 * settle animation and WIP pill are visible.
 */
public class RXKanbanViewShowcase extends RXShowcaseApplication {

    private final AtomicInteger cardSeq = new AtomicInteger(100);
    private RXKanbanView<String> kanban;
    // Initialized eagerly so the createPreview() event handlers never touch a null label
    // if an event arrives before the control panel is built.
    private final Label lastEvent = new Label("(no events yet)");
    private final Label selected = new Label("(none)");

    @Override
    protected String title() {
        return "RXKanbanView";
    }

    @Override
    protected String subtitle() {
        return "Drag cards within and across columns; drag headers to reorder columns";
    }

    @Override
    protected Node createPreview() {
        kanban = new RXKanbanView<>();
        kanban.setColumns(sampleColumns());
        kanban.setColumnReorderEnabled(true);
        kanban.setPrefColumnWidth(240.0);
        kanban.setStyle("-fx-background-color: #f0ffff;");

        // Each column footer adds a card, so the settle glide and the WIP pill react.
        kanban.setColumnFooterFactory(column -> {
            Button add = new Button("+ Add card");
            add.setMaxWidth(Double.MAX_VALUE);
            add.setOnAction(e -> column.getCards().add("Card " + cardSeq.incrementAndGet()));
            return add;
        });

        kanban.setOnCardMoved(e -> lastEvent.setText(
                "moved \"" + e.getCard() + "\" " + e.getFromColumn().getTitle()
                        + "[" + e.getFromIndex() + "] → " + e.getToColumn().getTitle()
                        + "[" + e.getToIndex() + "]"));
        kanban.setOnColumnMoved(e -> lastEvent.setText(
                "reordered column \"" + e.getColumn().getTitle() + "\" "
                        + e.getFromIndex() + " → " + e.getToIndex()));
        kanban.setOnCardAction(e -> lastEvent.setText("activated \"" + e.getCard() + "\""));

        return kanban;
    }

    private ObservableList<RXKanbanColumn<String>> sampleColumns() {
        RXKanbanColumn<String> todo = new RXKanbanColumn<>("TODO");
        todo.getCards().addAll("Wire up API", "Draft docs", "Design icon set", "Review PR #42");
        RXKanbanColumn<String> doing = new RXKanbanColumn<>("DOING");
        doing.getCards().addAll("Kanban DnD", "Settle animation");
        doing.setWipLimit(3);
        RXKanbanColumn<String> done = new RXKanbanColumn<>("DONE");
        done.getCards().addAll("Column model", "Virtualized viewport", "Board scroll");
        RXKanbanColumn<String> backlog = new RXKanbanColumn<>("BACKLOG");
        backlog.getCards().addAll("Swimlanes", "Keyboard DnD", "Multi-select", "Live regions", "Variable height");
        return FXCollections.observableArrayList(todo, doing, done, backlog);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Layout", layoutGrid()),
                section("Behavior", behaviorGrid()),
                section("Animation", animationGrid()),
                section("Activity", activityGrid()));
    }

    private Node layoutGrid() {
        Slider columnWidth = createSlider(160.0, 360.0, kanban.getPrefColumnWidth());
        columnWidth.valueProperty().addListener((o, ov, v) -> kanban.setPrefColumnWidth(v.doubleValue()));
        Slider columnSpacing = createSlider(0.0, 40.0, kanban.getColumnSpacing());
        columnSpacing.valueProperty().addListener((o, ov, v) -> kanban.setColumnSpacing(v.doubleValue()));
        // Column justify governs the spare width; STRETCH grows columns to fill it (capped
        // by max column width), the SPACE_* modes spread the gaps.
        ChoiceBox<ItemsJustify> justify = new ChoiceBox<>(FXCollections.observableArrayList(ItemsJustify.values()));
        justify.setValue(kanban.getColumnsJustify());
        justify.valueProperty().addListener((o, ov, v) -> kanban.setColumnsJustify(v));
        // Min column width sets the shrink floor for a narrow board: the far-left end is
        // negative (USE_COMPUTED_SIZE = "auto", no shrink → scroll), 0 lets columns shrink
        // to nothing (never scroll), a positive floor shrinks that far then scrolls. Max
        // column width caps STRETCH growth; negative = "auto" (uncapped).
        Slider minWidth = createSlider(-1.0, 280.0, kanban.getMinColumnWidth());
        minWidth.valueProperty().addListener((o, ov, v) -> kanban.setMinColumnWidth(v.doubleValue()));
        Slider maxWidth = createSlider(-1.0, 480.0, kanban.getMaxColumnWidth());
        maxWidth.valueProperty().addListener((o, ov, v) -> kanban.setMaxColumnWidth(v.doubleValue()));
        Slider cardHeight = createSlider(60.0, 160.0, kanban.getPrefCardHeight());
        cardHeight.valueProperty().addListener((o, ov, v) -> kanban.setPrefCardHeight(v.doubleValue()));
        Slider cardSpacing = createSlider(0.0, 24.0, kanban.getCardSpacing());
        cardSpacing.valueProperty().addListener((o, ov, v) -> kanban.setCardSpacing(v.doubleValue()));
        return createGrid(
                row("Column justify", justify),
                row("Column width", columnWidth, createValueLabel(columnWidth, "%.0f px")),
                row("Min column width", minWidth, sizeOrAutoLabel(minWidth)),
                row("Max column width", maxWidth, sizeOrAutoLabel(maxWidth)),
                row("Column spacing", columnSpacing, createValueLabel(columnSpacing, "%.0f px")),
                row("Card height", cardHeight, createValueLabel(cardHeight, "%.0f px")),
                row("Card spacing", cardSpacing, createValueLabel(cardSpacing, "%.0f px")));
    }

    // A value label that renders a negative slider value (USE_COMPUTED_SIZE) as "auto"
    // and any non-negative value as pixels, so the sentinel reads clearly.
    private Label sizeOrAutoLabel(Slider slider) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.textProperty().bind(Bindings.createStringBinding(
                () -> slider.getValue() < 0.0 ? "auto" : String.format("%.0f px", slider.getValue()),
                slider.valueProperty()));
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private Node behaviorGrid() {
        CheckBox editable = new CheckBox("Editable");
        editable.setSelected(kanban.isEditable());
        editable.selectedProperty().addListener((o, ov, on) -> kanban.setEditable(on));
        CheckBox cardDrag = new CheckBox("Card drag & drop");
        cardDrag.setSelected(kanban.isCardDragEnabled());
        cardDrag.selectedProperty().addListener((o, ov, on) -> kanban.setCardDragEnabled(on));
        CheckBox columnReorder = new CheckBox("Column reorder");
        columnReorder.setSelected(kanban.isColumnReorderEnabled());
        columnReorder.selectedProperty().addListener((o, ov, on) -> kanban.setColumnReorderEnabled(on));
        // Toggle the DONE column by IDENTITY, not by index: a column reorder changes
        // indices, so getColumns().get(2) would later resolve to a different column and
        // leave the real one stuck hidden. Bind bidirectionally to the column's own
        // visible property (checked = shown).
        RXKanbanColumn<String> doneColumn = kanban.getColumns().get(2);
        CheckBox showDone = new CheckBox("Show DONE column");
        showDone.selectedProperty().bindBidirectional(doneColumn.visibleProperty());
        return createGrid(
                row(editable),
                row(cardDrag),
                row(columnReorder),
                row(showDone));
    }

    private Node animationGrid() {
        CheckBox animated = new CheckBox("Animated");
        animated.setSelected(kanban.isAnimated());
        animated.selectedProperty().addListener((o, ov, on) -> kanban.setAnimated(on));
        Slider duration = createSlider(0.0, 600.0, kanban.getAnimationDuration().toMillis());
        duration.valueProperty().addListener((o, ov, v) -> kanban.setAnimationDuration(Duration.millis(v.doubleValue())));
        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node activityGrid() {
        kanban.selectedCardProperty().addListener((o, ov, card) -> selected.setText(card == null ? "(none)" : card));
        lastEvent.setWrapText(true);
        lastEvent.setMaxWidth(Double.MAX_VALUE);
        lastEvent.setAlignment(Pos.CENTER_LEFT);
        return createGrid(
                row("Selected", selected),
                row("Last event", lastEvent));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-kanban-view-showcase.css").toExternalForm();
    }

    /**
     * Launches the showcase.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
