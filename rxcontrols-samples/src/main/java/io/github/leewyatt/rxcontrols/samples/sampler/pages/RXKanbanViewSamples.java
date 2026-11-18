package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.PALETTE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.avatar;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.colorOf;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.initials;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.paint;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.sized;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXKanbanView page: a sprint board with rich cards and a move log, work-in-progress
 * limits with a rule that rejects some moves, columns reordered by their headers, and a
 * footer button that adds cards.
 */
public final class RXKanbanViewSamples {

    private static final double BOARD_WIDTH = 420.0;

    private record Task(String title, String tag, String owner, int points) {
    }

    private RXKanbanViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A board of columns holding cards that can be dragged within and between columns.",
                List.of("Work that moves through stages: tasks in a sprint, deals in a pipeline, tickets in support.",
                        "For one ordered list use RXListView; for a fixed sequence of steps use RXSegmentedStepIndicator."),
                List.of(
                        SampleCard.canvas("Sprint board",
                                "Drag a card within or across columns; onCardMoved reports every move and a custom card cell shows tag, owner and points.",
                                sprint()),
                        SampleCard.canvas("Limits and rules",
                                "wipLimit marks a column that holds too many cards without blocking the drop; dropValidator refuses moves from Backlog straight to Shipped.",
                                limits()),
                        SampleCard.large("Reorder columns",
                                "columnReorderEnabled lets you drag a column by its header to a new place.",
                                reorder()),
                        SampleCard.large("Add cards",
                                "columnFooterFactory puts an add button under each column; an empty column shows its placeholder.",
                                addCards())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Sprint board ====================

    private static Node sprint() {
        RXKanbanView<Task> board = new RXKanbanView<>();
        board.setColumns(FXCollections.observableArrayList(
                column("To do",
                        new Task("Write release notes", "Docs", "Lee Wyatt", 2),
                        new Task("Empty state for search", "Design", "Hana Sato", 3),
                        new Task("Retry failed uploads", "Bug", "Ravi Patel", 5),
                        new Task("Export to CSV", "Feature", "Mia Chen", 3)),
                column("In progress",
                        new Task("Dark theme colors", "Design", "Ada Brooks", 5),
                        new Task("Login rate limit", "Security", "Omar Haddad", 3)),
                column("Review",
                        new Task("Cache user avatars", "Feature", "Sam Ortega", 2)),
                column("Done",
                        new Task("Fix date parsing", "Bug", "Lee Wyatt", 1),
                        new Task("Onboarding tour", "Feature", "Mia Chen", 8))));
        board.setPrefColumnWidth(220.0);
        board.setColumnsJustify(ItemsJustify.STRETCH);
        board.setPrefCardHeight(92.0);
        board.setCardCellFactory(view -> new TaskCell());
        board.setPrefSize(BOARD_WIDTH, 360.0);
        board.setMinHeight(360.0);

        Label log = text("Drag a card to another column", "hint");
        board.setOnCardMoved(event -> log.setText(event.isReorder()
                ? "Reordered “" + event.getCard().title() + "” in " + event.getToColumn().getTitle()
                : "Moved “" + event.getCard().title() + "” from " + event.getFromColumn().getTitle()
                + " to " + event.getToColumn().getTitle()));
        HBox header = new HBox(8.0, text("Sprint 14", "heading"), spacer(), log);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(board, Priority.ALWAYS);
        return new VBox(12.0, header, board);
    }

    private static final class TaskCell extends RXKanbanCardCell<Task> {

        private final Label tag = text("", "tag");
        private final Label title = text("", "name");
        private final Label owner = avatar(24.0);
        private final Label points = text("", "note");
        private final VBox card;

        private TaskCell() {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            title.setWrapText(true);
            owner.getStyleClass().add("small");
            HBox footer = new HBox(6.0, owner, spacer(), icon("points-icon"), points);
            footer.setAlignment(Pos.CENTER_LEFT);
            Region gap = new Region();
            VBox.setVgrow(gap, Priority.ALWAYS);
            card = new VBox(6.0, tag, title, gap, footer);
        }

        @Override
        protected void updateItem(Task task, boolean empty) {
            super.updateItem(task, empty);
            if (empty || task == null) {
                setText(null);
                setGraphic(null);
            } else {
                tag.setText(task.tag());
                tag.getStyleClass().removeAll(PALETTE);
                tag.getStyleClass().add(colorOf(task.tag()));
                title.setText(task.title());
                owner.setText(initials(task.owner()));
                paint(owner, colorOf(task.owner()));
                points.setText(task.points() + " pt");
                setGraphic(card);
            }
        }
    }

    // ==================== Limits and rules ====================

    private static Node limits() {
        RXKanbanColumn<String> backlog = stringColumn("Backlog",
                "Price alerts", "Saved searches", "Gift cards", "Wish list", "Bulk edit");
        RXKanbanColumn<String> building = stringColumn("Building", "Checkout redesign", "Order tracking",
                "Store pickup");
        building.setWipLimit(2);
        RXKanbanColumn<String> testing = stringColumn("Testing", "Coupon codes");
        testing.setWipLimit(2);
        RXKanbanColumn<String> shipped = stringColumn("Shipped", "Reviews", "Newsletter");

        RXKanbanView<String> board = new RXKanbanView<>();
        board.setColumns(FXCollections.observableArrayList(backlog, building, testing, shipped));
        board.setPrefColumnWidth(200.0);
        board.setColumnsJustify(ItemsJustify.STRETCH);
        board.setPrefCardHeight(44.0);
        board.setPrefSize(BOARD_WIDTH, 300.0);
        board.setMinHeight(300.0);
        board.getStyleClass().add("compact");
        board.setDropValidator(context ->
                !(context.getSourceColumn() == backlog && context.getTargetColumn() == shipped));
        return board;
    }

    // ==================== Reorder columns ====================

    private static Node reorder() {
        RXKanbanView<String> board = new RXKanbanView<>();
        board.setColumns(FXCollections.observableArrayList(
                stringColumn("Ideas", "Podcast", "Workshop", "Mugs"),
                stringColumn("Planned", "Webinar", "Blog series"),
                stringColumn("Live", "Newsletter")));
        board.setColumnReorderEnabled(true);
        board.setPrefColumnWidth(124.0);
        board.setPrefCardHeight(40.0);
        board.getStyleClass().add("compact");
        return sized(board, BOARD_WIDTH, 280.0);
    }

    // ==================== Add cards ====================

    private static Node addCards() {
        RXKanbanView<String> board = new RXKanbanView<>();
        board.setColumns(FXCollections.observableArrayList(
                stringColumn("Groceries", "Apples", "Rice", "Coffee"),
                stringColumn("In the cart"),
                stringColumn("Paid", "Bread")));
        board.setPrefColumnWidth(124.0);
        board.setPrefCardHeight(40.0);
        board.getStyleClass().add("compact");
        board.setEmptyColumnPlaceholderFactory(column -> new Label("Drop items here"));
        int[] counter = {1};
        board.setColumnFooterFactory(column -> {
            RXButton add = new RXButton("Add item");
            add.getStyleClass().add("add-button");
            add.setGraphic(icon("plus-icon"));
            add.setMaxWidth(Double.MAX_VALUE);
            add.setOnAction(event -> column.getCards().add("Item " + counter[0]++));
            return add;
        });
        return sized(board, BOARD_WIDTH, 280.0);
    }

    // ==================== Helpers ====================

    @SafeVarargs
    private static <T> RXKanbanColumn<T> column(String title, T... cards) {
        RXKanbanColumn<T> column = new RXKanbanColumn<>(title);
        column.getCards().addAll(cards);
        return column;
    }

    private static RXKanbanColumn<String> stringColumn(String title, String... cards) {
        RXKanbanColumn<String> column = new RXKanbanColumn<>(title);
        column.getCards().addAll(cards);
        return column;
    }
}
