package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.bbcode.RXBackgroundNode;
import io.github.leewyatt.rxcontrols.bbcode.RXBBBlockNode;
import io.github.leewyatt.rxcontrols.bbcode.RXBBBlockNodeVisitor;
import io.github.leewyatt.rxcontrols.bbcode.RXCodeBlockNode;
import io.github.leewyatt.rxcontrols.bbcode.RXHeadingNode;
import io.github.leewyatt.rxcontrols.bbcode.RXHorizontalRuleNode;
import io.github.leewyatt.rxcontrols.bbcode.RXListItemNode;
import io.github.leewyatt.rxcontrols.bbcode.RXListKind;
import io.github.leewyatt.rxcontrols.bbcode.RXListNode;
import io.github.leewyatt.rxcontrols.bbcode.RXParagraphNode;
import io.github.leewyatt.rxcontrols.bbcode.RXQuoteNode;
import io.github.leewyatt.rxcontrols.bbcode.RXSpoilerNode;
import io.github.leewyatt.rxcontrols.bbcode.RXTableCellNode;
import io.github.leewyatt.rxcontrols.bbcode.RXTableNode;
import io.github.leewyatt.rxcontrols.bbcode.RXTableRowNode;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/**
 * Renders {@link RXBBBlockNode}s into JavaFX {@link Node}s, one node per block, for the
 * content column. Inline content is delegated to {@link BBCodeInlineRenderer}; block
 * nesting (quote / list-item / cell / spoiler bodies) recurses through this same visitor.
 */
final class BBCodeBlockRenderer implements RXBBBlockNodeVisitor<Node> {

    /**
     * Seed font size per heading level 1..6, applied as the inline base for a heading.
     */
    private static final double[] HEADING_SIZES = {30, 24, 20, 18, 16, 14};

    private static final String TEXT_STYLE_CLASS = "text";
    private static final String MARKER_STYLE_CLASS = "marker";
    private static final String DEFAULT_SPOILER_LABEL = "Spoiler";

    private final RenderContext ctx;

    BBCodeBlockRenderer(RenderContext ctx) {
        this.ctx = ctx;
    }

    // ==================== Text blocks ====================

    @Override
    public Node visitParagraph(RXParagraphNode node) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("paragraph");
        new BBCodeInlineRenderer(flow, ctx).render(node.children());
        return flow;
    }

    @Override
    public Node visitHeading(RXHeadingNode node) {
        int level = Math.max(1, Math.min(HEADING_SIZES.length, node.level()));
        TextFlow flow = new TextFlow();
        flow.getStyleClass().addAll("heading", "heading-" + level);
        BBCodeInlineRenderer.heading(flow, ctx, HEADING_SIZES[level - 1]).render(node.children());
        return flow;
    }

    @Override
    public Node visitHorizontalRule(RXHorizontalRuleNode node) {
        Region rule = new Region();
        rule.getStyleClass().add("hr");
        return rule;
    }

    @Override
    public Node visitCodeBlock(RXCodeBlockNode node) {
        // A Region cannot host children directly (getChildren() is protected), so use a
        // StackPane. The content is rendered verbatim in one monospace Text — never
        // re-parsed and never a TextArea.
        StackPane pane = new StackPane();
        pane.getStyleClass().add("code-block");
        pane.setAlignment(Pos.TOP_LEFT);
        Text code = new Text(node.content());
        code.getStyleClass().add(TEXT_STYLE_CLASS);
        pane.getChildren().add(code);
        return pane;
    }

    // ==================== Container blocks ====================

    @Override
    public Node visitQuote(RXQuoteNode node) {
        VBox quote = new VBox();
        quote.getStyleClass().add("quote");
        if (node.author() != null) {
            Text authorText = new Text(node.author());
            authorText.getStyleClass().add(TEXT_STYLE_CLASS);
            TextFlow author = new TextFlow(authorText);
            author.getStyleClass().add("author");
            quote.getChildren().add(author);
        }
        for (RXBBBlockNode child : node.children()) {
            quote.getChildren().add(child.accept(this));
        }
        return quote;
    }

    @Override
    public Node visitBackground(RXBackgroundNode node) {
        VBox box = new VBox();
        box.getStyleClass().add("background");
        if (node.color() != null) {
            // The colour is a pre-validated token; apply it with a typed Background rather
            // than inline CSS. setBackground replaces the whole fill, so the corner radius
            // is baked in here (CSS -fx-background-radius would be overridden).
            box.setBackground(new Background(new BackgroundFill(
                    Color.web(node.color()), new CornerRadii(4), Insets.EMPTY)));
        }
        for (RXBBBlockNode child : node.children()) {
            box.getChildren().add(child.accept(this));
        }
        return box;
    }

    @Override
    public Node visitList(RXListNode node) {
        return node.kind() == RXListKind.ORDERED ? orderedList(node) : unorderedList(node);
    }

    private Node unorderedList(RXListNode node) {
        VBox list = new VBox();
        list.getStyleClass().add("list");
        for (RXListItemNode item : node.items()) {
            HBox row = new HBox();
            row.getStyleClass().add("item");
            Text marker = new Text("•");
            marker.getStyleClass().add(MARKER_STYLE_CLASS);
            Node body = fillMaxWidth(renderBlocks(item.children()));
            HBox.setHgrow(body, Priority.ALWAYS);
            row.getChildren().addAll(marker, body);
            list.getChildren().add(row);
        }
        return list;
    }

    private Node orderedList(RXListNode node) {
        GridPane list = new GridPane();
        list.getStyleClass().add("list");
        int rowIndex = 0;
        for (RXListItemNode item : node.items()) {
            Text marker = new Text((rowIndex + 1) + ".");
            marker.getStyleClass().add(MARKER_STYLE_CLASS);
            Node body = fillMaxWidth(renderBlocks(item.children()));
            GridPane.setHgrow(body, Priority.ALWAYS);
            list.add(marker, 0, rowIndex);
            list.add(body, 1, rowIndex);
            rowIndex++;
        }
        return list;
    }

    /**
     * Removes a node's preferred-width ceiling so its host (list row / table column) can
     * compress it to the available width, letting an inner paragraph wrap instead of
     * overflowing at its unwrapped preferred width.
     */
    private static Node fillMaxWidth(Node node) {
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return node;
    }

    @Override
    public Node visitTable(RXTableNode node) {
        GridPane table = new GridPane();
        table.getStyleClass().add("table");
        int columns = maxColumns(node);
        for (int column = 0; column < columns; column++) {
            // Equal percent columns so that, at a finite layout width, every column is a
            // fixed share and each cell's TextFlow wraps within its share (§13.3).
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            table.getColumnConstraints().add(constraint);
        }
        int rowIndex = 0;
        for (RXTableRowNode row : node.rows()) {
            int columnIndex = 0;
            for (RXTableCellNode cell : row.cells()) {
                table.add(buildCell(cell), columnIndex, rowIndex);
                columnIndex++;
            }
            rowIndex++;
        }
        return table;
    }

    private static int maxColumns(RXTableNode node) {
        int columns = 0;
        for (RXTableRowNode row : node.rows()) {
            columns = Math.max(columns, row.cells().size());
        }
        return columns;
    }

    private Node buildCell(RXTableCellNode cell) {
        List<RXBBBlockNode> children = cell.children();
        Node shell;
        if (children.size() == 1 && children.get(0) instanceof RXParagraphNode) {
            // Pure-inline cell: the paragraph TextFlow is itself the cell shell.
            shell = children.get(0).accept(this);
        } else {
            VBox box = new VBox();
            for (RXBBBlockNode child : children) {
                box.getChildren().add(fillMaxWidth(child.accept(this)));
            }
            shell = box;
        }
        shell.getStyleClass().add("cell");
        if (cell.header()) {
            shell.getStyleClass().add("header");
        }
        fillMaxWidth(shell);
        GridPane.setHgrow(shell, Priority.SOMETIMES);
        return shell;
    }

    @Override
    public Node visitSpoiler(RXSpoilerNode node) {
        SpoilerBox spoiler = new SpoilerBox();
        spoiler.getStyleClass().add("spoiler");

        Text headerText = new Text(node.label() != null ? node.label() : DEFAULT_SPOILER_LABEL);
        headerText.getStyleClass().add(TEXT_STYLE_CLASS);
        HBox header = new HBox(headerText);
        header.getStyleClass().add("header");

        VBox body = new VBox();
        body.getStyleClass().add("content");
        for (RXBBBlockNode child : node.children()) {
            body.getChildren().add(child.accept(this));
        }
        // Start collapsed: the Skin toggles managed (not CSS visibility) so the layout
        // actually collapses (§13.3).
        body.setVisible(false);
        body.setManaged(false);

        header.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            boolean revealed = !body.isVisible();
            body.setVisible(revealed);
            body.setManaged(revealed);
            spoiler.setRevealed(revealed);
        });

        spoiler.getChildren().addAll(header, body);
        return spoiler;
    }

    // ==================== Helpers ====================

    private Node renderBlocks(List<RXBBBlockNode> blocks) {
        if (blocks.size() == 1) {
            return blocks.get(0).accept(this);
        }
        VBox box = new VBox();
        for (RXBBBlockNode block : blocks) {
            box.getChildren().add(block.accept(this));
        }
        return box;
    }

    /**
     * A {@link VBox}-based spoiler shell that exposes a {@code :revealed} pseudo-class so
     * the header can restyle (e.g. rotate its arrow) when the body is shown.
     */
    private static final class SpoilerBox extends VBox {

        private static final PseudoClass REVEALED = PseudoClass.getPseudoClass("revealed");

        void setRevealed(boolean revealed) {
            pseudoClassStateChanged(REVEALED, revealed);
        }
    }
}
