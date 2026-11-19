package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.CascaderData.find;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.CascaderData.last;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.DataViewKit.text;

/**
 * The RXCascaderView page: a category browser on the page, regions checked with a live
 * summary, people loaded level by level, and dense rows with a disabled branch.
 */
public final class RXCascaderViewSamples {

    private RXCascaderViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "The columns of a cascader placed straight on the page, with no field and no popup.",
                List.of("Browsing a tree that should stay visible: a category menu, a filter panel, a settings tree.",
                        "When space is short and the choice ends up in a form, use RXCascader."),
                List.of(
                        SampleCard.wide("Category browser",
                                "A column opens for each level you visit; select and revealSelectedPath open the columns of a saved choice.",
                                browser()),
                        SampleCard.wide("Check regions",
                                "In MULTIPLE mode a region shows a partial check while only some countries are checked; getCheckedPaths drives the count.",
                                regions()),
                        SampleCard.large("Lazy loading",
                                "childrenLoader fills each column when you open its parent, after a short wait.",
                                lazy()),
                        SampleCard.large("Dense rows",
                                "rowHeight, columnWidth and visibleRowCount make the columns compact; a disabled country disables its states.",
                                dense())),
                DataViewKit.STYLESHEET);
    }

    // ==================== Category browser ====================

    private static Node browser() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.getRootItems().setAll(CascaderData.categories());
        view.select(find(view.getRootItems(), "Laptops"));
        view.revealSelectedPath();

        Label result = text("", "body");
        result.textProperty().bind(Bindings.createStringBinding(() -> {
            RXCascaderPath<String> path = view.getSelectedPath();
            return path == null ? "Pick a category" : "Showing " + String.join(" › ", path.getValues());
        }, view.selectedPathProperty()));
        HBox summary = new HBox(8.0, icon("tag-icon"), result);
        summary.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(12.0, view, summary);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Check regions ====================

    private static Node regions() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.setSelectionMode(SelectionMode.MULTIPLE);
        view.setColumnWidth(160.0);
        view.getRootItems().setAll(CascaderData.regions());
        RXCascaderItem<String> europe = find(view.getRootItems(), "Europe");
        view.seedChecked(List.of(find(view.getRootItems(), "France"), find(view.getRootItems(), "Italy"),
                find(view.getRootItems(), "Oceania")));
        view.expand(europe);

        Label count = text("", "name");
        Runnable refresh = () -> {
            int size = view.getCheckedPaths().size();
            count.setText(size == 0 ? "No countries checked" : size + (size == 1 ? " country checked" : " countries checked"));
        };
        view.getCheckedPaths().addListener((ListChangeListener<RXCascaderPath<String>>) change -> refresh.run());
        refresh.run();
        VBox box = new VBox(12.0, view, count);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    // ==================== Lazy loading ====================

    private static Node lazy() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.setColumnWidth(136.0);
        view.setChildrenLoader(CascaderData::loadChildren);
        view.getRootItems().setAll(CascaderData.departments());
        Label picked = text("", "hint");
        picked.textProperty().bind(Bindings.createStringBinding(() -> {
            RXCascaderPath<String> path = view.getSelectedPath();
            return path == null ? "Open a department to load its teams" : "Picked " + last(path);
        }, view.selectedPathProperty()));
        view.setMaxWidth(Region.USE_PREF_SIZE);
        VBox box = new VBox(12.0, view, picked);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Dense rows ====================

    private static Node dense() {
        RXCascaderView<String> view = new RXCascaderView<>();
        view.getStyleClass().add("dense");
        view.setRowHeight(26.0);
        view.setColumnWidth(130.0);
        view.setVisibleRowCount(9);
        view.getRootItems().setAll(CascaderData.places());
        find(view.getRootItems(), "Canada").setDisable(true);
        view.expand(find(view.getRootItems(), "United States"));
        return view;
    }
}
