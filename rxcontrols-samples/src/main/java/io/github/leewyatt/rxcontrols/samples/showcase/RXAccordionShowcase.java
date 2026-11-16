package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXAccordion.ExpandMode;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Showcase application for {@link RXAccordion}.
 *
 * <p>The preview is a settings accordion. The panel drives the group features:
 * single or multiple expansion, the gap opened around expanded panes,
 * {@code expandAll()} / {@code collapseAll()}, adding and removing panes at run time,
 * and a live read-out of the expanded panes. Pane-level features are covered by the
 * RXTitledPane showcase.</p>
 */
public class RXAccordionShowcase extends RXShowcaseApplication {

    private static final String[][] SECTIONS = {
            {"Account", "Name, email and the password used to sign in."},
            {"Notifications", "Which events send an email, a push message or nothing at all."},
            {"Privacy", "Who can see your profile and whether your activity is shared."},
            {"Billing", "The current plan, invoices and the payment method on file."}
    };

    private RXAccordion accordion;
    private final CheckBox animated = new CheckBox("Animated");
    private int added;

    @Override
    protected String title() {
        return "RXAccordion";
    }

    @Override
    protected String subtitle() {
        return "Single or multiple expansion over a group of RXTitledPanes.";
    }

    @Override
    protected Node createPreview() {
        accordion = new RXAccordion();
        for (String[] section : SECTIONS) {
            accordion.getPanes().add(pane(section[0], section[1]));
        }
        accordion.setExpandedSpacing(16.0);
        accordion.getPanes().get(0).setExpanded(true);

        VBox column = new VBox(accordion);
        column.setMaxWidth(480);
        StackPane holder = new StackPane(column);
        holder.setAlignment(Pos.TOP_CENTER);
        return holder;
    }

    private static RXTitledPane pane(String title, String text) {
        Label body = new Label(text);
        body.setWrapText(true);
        return new RXTitledPane(title, body);
    }

    // ==================== Control panel ====================

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Expansion", expansionGrid()),
                section("Panes", panesGrid()));
    }

    private Node expansionGrid() {
        ComboBox<ExpandMode> mode = new ComboBox<>(FXCollections.observableArrayList(ExpandMode.values()));
        mode.setValue(accordion.getExpandMode());
        mode.valueProperty().addListener((obs, old, value) -> accordion.setExpandMode(value));
        mode.setMaxWidth(Double.MAX_VALUE);

        Slider gap = createSlider(0.0, 32.0, accordion.getExpandedSpacing());
        gap.valueProperty().addListener((obs, old, value) -> accordion.setExpandedSpacing(value.doubleValue()));

        Button expandAll = new Button("Expand all");
        expandAll.setOnAction(e -> accordion.expandAll());
        expandAll.disableProperty().bind(accordion.expandModeProperty().isNotEqualTo(ExpandMode.MULTIPLE));
        Button collapseAll = new Button("Collapse all");
        collapseAll.setOnAction(e -> accordion.collapseAll());

        animated.setSelected(true);
        animated.selectedProperty().addListener((obs, old, value) ->
                accordion.getPanes().forEach(p -> p.setAnimated(value)));

        Label expanded = new Label();
        expanded.setWrapText(true);
        expanded.textProperty().bind(Bindings.createStringBinding(() -> {
            String names = accordion.getExpandedPanes().stream()
                    .map(RXTitledPane::getText)
                    .collect(Collectors.joining(", "));
            return "Expanded: " + (names.isEmpty() ? "none" : names);
        }, accordion.getExpandedPanes()));

        return createGrid(
                row("Mode", mode),
                row("Expanded gap", gap, createValueLabel(gap, "%.0f")),
                row(new HBox(8.0, expandAll, collapseAll)),
                row(animated),
                row(expanded));
    }

    private Node panesGrid() {
        Button add = new Button("Add pane");
        add.setOnAction(e -> {
            added++;
            RXTitledPane extra = pane("Extra " + added, "A pane added at run time.");
            extra.setAnimated(animated.isSelected());
            accordion.getPanes().add(extra);
        });
        Button remove = new Button("Remove last");
        remove.setOnAction(e -> {
            if (!accordion.getPanes().isEmpty()) {
                accordion.getPanes().remove(accordion.getPanes().size() - 1);
            }
        });
        remove.disableProperty().bind(Bindings.isEmpty(accordion.getPanes()));
        return createGrid(row(new HBox(8.0, add, remove)));
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
