package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderCell;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Callback;

import java.util.List;
import java.util.StringJoiner;

/**
 * Shared sample data and presentation helpers for the cascader showcases.
 */
final class CascaderShowcaseSupport {

    static final String SEPARATOR = " / ";

    private CascaderShowcaseSupport() {
    }

    // ==================== Readout ====================

    static String describeSelection(RXCascader<CascaderOption> cascader) {
        return describeSelection(
                cascader.getSelectionMode(),
                cascader.getCheckedPaths(),
                cascader.getSelectedPath(),
                cascader.getItemTextFactory());
    }

    static String describeSelection(RXCascaderView<CascaderOption> view) {
        return describeSelection(
                view.getSelectionMode(),
                view.getCheckedPaths(),
                view.getSelectedPath(),
                view.getItemTextFactory());
    }

    private static String describeSelection(
            SelectionMode mode,
            List<RXCascaderPath<CascaderOption>> checked,
            RXCascaderPath<CascaderOption> selected,
            Callback<CascaderOption, String> itemTextFactory) {
        if (mode == SelectionMode.MULTIPLE) {
            if (checked.isEmpty()) {
                return "checked: (none)";
            }
            StringJoiner joiner = new StringJoiner("\n");
            for (RXCascaderPath<CascaderOption> path : checked) {
                joiner.add("- " + String.join(SEPARATOR, pathTexts(itemTextFactory, path)));
            }
            return "checked (" + checked.size() + "):\n" + joiner;
        }
        if (selected == null) {
            return "selected: (none)";
        }
        return "selected: " + String.join(SEPARATOR, pathTexts(itemTextFactory, selected));
    }

    static List<String> pathTexts(
            Callback<CascaderOption, String> itemTextFactory,
            RXCascaderPath<CascaderOption> path) {
        return path.getValues().stream()
                .map(value -> displayText(itemTextFactory, value))
                .toList();
    }

    private static String displayText(Callback<CascaderOption, String> itemTextFactory, CascaderOption value) {
        if (value == null) {
            return "";
        }
        String text = itemTextFactory == null ? String.valueOf(value) : itemTextFactory.call(value);
        return text == null ? "" : text;
    }

    // ==================== Sample data ====================

    static List<RXCascaderItem<CascaderOption>> sampleOptions() {
        RXCascaderItem<CascaderOption> disabledCity = item("disabled", "Disabled City");
        disabledCity.setDisable(true);

        RXCascaderItem<CascaderOption> china = item("china", "China");
        china.getChildren().setAll(List.of(
                item("shanghai", "Shanghai"),
                item("hangzhou", "Hangzhou"),
                disabledCity));

        RXCascaderItem<CascaderOption> japan = item("japan", "Japan");
        japan.getChildren().setAll(List.of(
                item("tokyo", "Tokyo"),
                item("osaka", "Osaka")));

        RXCascaderItem<CascaderOption> asia = item("asia", "Asia");
        asia.getChildren().setAll(List.of(china, japan));

        RXCascaderItem<CascaderOption> germany = item("germany", "Germany");
        germany.getChildren().setAll(List.of(
                item("berlin", "Berlin"),
                item("munich", "Munich")));

        RXCascaderItem<CascaderOption> europe = item("europe", "Europe");
        europe.getChildren().setAll(List.of(germany));

        return List.of(asia, europe);
    }

    static List<RXCascaderItem<CascaderOption>> lazyRoots() {
        return List.of(item("source", "Remote Source"));
    }

    static RXCascaderItem<CascaderOption> item(String id, String label) {
        return new RXCascaderItem<>(new CascaderOption(id, label));
    }

    static RXCascaderItem<CascaderOption> leaf(String id, String label) {
        RXCascaderItem<CascaderOption> leaf = item(id, label);
        leaf.setLeafHint(true);
        return leaf;
    }

    // ==================== Custom cell ====================

    /**
     * Cell that overrides only the content area with a colored dot plus item text.
     *
     * @param <T> application value type
     */
    static final class DotCell<T> extends RXCascaderCell<T> {

        DotCell(RXCascaderView<T> view) {
            super(view);
        }

        @Override
        protected Node createContent(RXCascaderItem<T> item) {
            Region dot = new Region();
            dot.getStyleClass().add("demo-cell-dot");
            dot.setMinSize(8.0, 8.0);
            dot.setPrefSize(8.0, 8.0);
            dot.setMaxSize(8.0, 8.0);

            HBox box = new HBox(8.0, dot, new Label(getDisplayText(item.getValue())));
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }
    }
}
