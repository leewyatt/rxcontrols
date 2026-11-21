package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.avatar;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.colorOf;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.initials;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.paint;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXKanbanView page: one sprint board whose cards move within and between columns
 * and whose columns move by their headers, each with a button that adds a task; a
 * toolbar reports every move, and its Options menu switches the dragging, the columns
 * shown, the add buttons and the animation.
 */
public final class RXKanbanViewSamples {

    private static final double BOARD_HEIGHT = 560.0;

    private static final String[] NEW_TAGS = {"Feature", "Bug", "Design", "Docs"};
    private static final String[] NEW_OWNERS = {"Lee Wyatt", "Mia Chen", "Sam Ortega", "Ada Brooks"};

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
                                "Drag a card within or across columns and a column by its header; the Options "
                                        + "menu turns dragging on and off and shows the hidden Review column.",
                                sprint())),
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
                        new Task("Export to CSV", "Feature", "Mia Chen", 3),
                        new Task("Keyboard shortcuts page", "Docs", "Sam Ortega", 2),
                        new Task("Bulk edit tags", "Feature", "Ada Brooks", 5),
                        new Task("Slow search on large boards", "Bug", "Omar Haddad", 3)),
                column("In progress",
                        new Task("Dark theme colors", "Design", "Ada Brooks", 5),
                        new Task("Login rate limit", "Security", "Omar Haddad", 3),
                        new Task("Drag cards between boards", "Feature", "Lee Wyatt", 8),
                        new Task("Avatar upload crops", "Bug", "Hana Sato", 2)),
                column("Review",
                        new Task("Cache user avatars", "Feature", "Sam Ortega", 2),
                        new Task("Onboarding copy", "Docs", "Mia Chen", 1),
                        new Task("Session timeout dialog", "Design", "Ravi Patel", 3)),
                column("Done",
                        new Task("Fix date parsing", "Bug", "Lee Wyatt", 1),
                        new Task("Onboarding tour", "Feature", "Mia Chen", 8),
                        new Task("Two-factor sign in", "Security", "Omar Haddad", 5),
                        new Task("Faster board loading", "Feature", "Ada Brooks", 3),
                        new Task("Typo in settings", "Bug", "Sam Ortega", 1))));
        board.setPrefColumnWidth(190.0);
        board.setColumnsJustify(ItemsJustify.STRETCH);
        board.setPrefCardHeight(92.0);
        board.setCardCellFactory(view -> new TaskCell());
        board.setColumnReorderEnabled(true);
        // Review starts hidden, so the other three columns show whole in the card.
        board.getColumns().get(2).setVisible(false);
        int[] added = {0};
        Callback<RXKanbanColumn<Task>, Node> addButtons = column -> {
            RXButton add = new RXButton("Add task");
            add.getStyleClass().add("add-button");
            add.setGraphic(icon("plus-icon"));
            add.setMaxWidth(Double.MAX_VALUE);
            add.setOnAction(event -> {
                int n = added[0]++;
                column.getCards().add(new Task("New task " + (n + 1), NEW_TAGS[n % NEW_TAGS.length],
                        NEW_OWNERS[n % NEW_OWNERS.length], 1 + n % 5));
            });
            return add;
        };
        board.setColumnFooterFactory(addButtons);
        board.setPrefHeight(BOARD_HEIGHT);
        board.setMinHeight(BOARD_HEIGHT);

        Label log = text("Drag a card to another column, or a column by its header", "hint");
        board.setOnCardMoved(event -> log.setText(event.isReorder()
                ? "Reordered “" + event.getCard().title() + "” in " + event.getToColumn().getTitle()
                : "Moved “" + event.getCard().title() + "” from " + event.getFromColumn().getTitle()
                + " to " + event.getToColumn().getTitle()));
        board.setOnColumnMoved(event -> log.setText("Moved the " + event.getColumn().getTitle() + " column"));
        RXMenuButton options = options(board, addButtons);
        options.setMinWidth(Region.USE_PREF_SIZE);
        HBox toolbar = new HBox(8.0, log, spacer(), options);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(board, Priority.ALWAYS);
        return new VBox(12.0, toolbar, board);
    }

    private static RXMenuButton options(RXKanbanView<Task> board, Callback<RXKanbanColumn<Task>, Node> addButtons) {
        RXMenuButton options = new RXMenuButton("Options");
        options.getItems().addAll(RXMenuItem.header("Dragging"),
                RXMenuItem.checkbox("Drag cards", board.cardDragEnabledProperty()),
                RXMenuItem.checkbox("Drag columns by the header", board.columnReorderEnabledProperty()),
                RXMenuItem.separator(), RXMenuItem.header("Columns"));
        for (RXKanbanColumn<Task> column : board.getColumns()) {
            options.getItems().add(RXMenuItem.checkbox(column.getTitle(), column.visibleProperty()));
        }
        BooleanProperty adding = new SimpleBooleanProperty(true);
        adding.addListener((obs, was, on) -> board.setColumnFooterFactory(on ? addButtons : null));
        options.getItems().addAll(RXMenuItem.separator(),
                RXMenuItem.checkbox("Add task buttons", adding),
                RXMenuItem.checkbox("Animate moves", board.animatedProperty()));
        return options;
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
                updateColor(tag, task.tag());
                title.setText(task.title());
                owner.setText(initials(task.owner()));
                updateColor(owner, task.owner());
                points.setText(task.points() + " pt");
                setGraphic(card);
            }
        }

        private static void updateColor(Label label, String key) {
            String color = colorOf(key);
            if (!label.getStyleClass().contains(color)) {
                paint(label, color);
            }
        }
    }

    // ==================== Helpers ====================

    @SafeVarargs
    private static <T> RXKanbanColumn<T> column(String title, T... cards) {
        RXKanbanColumn<T> column = new RXKanbanColumn<>(title);
        column.getCards().addAll(cards);
        return column;
    }
}
