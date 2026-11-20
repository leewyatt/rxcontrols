package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXCol;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXRow;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Small builders shared by the parts of the home page: text, shape glyphs, responsive
 * rows and columns, icon buttons and the sample images.
 */
final class HomeKit {

    /**
     * The breakpoints of every row on the home page, measured on the row's own width:
     * one column below SM, two from SM and the full layout from MD, which the page
     * reaches next to the expanded navigation at the sampler's default window size.
     */
    static final RXBreakpointProfile PROFILE = RXBreakpointProfile.builder()
            .columns(24)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.SM, 520.0)
            .breakpoint(RXBreakpoint.MD, 860.0)
            .build();

    // Pages are built on the FX application thread, so a plain map is enough.
    private static final Map<String, Image> IMAGES = new HashMap<>();

    private HomeKit() {
    }

    static Label text(String value, String... styleClasses) {
        Label label = new Label(value);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    static Label wrapped(String value, String... styleClasses) {
        Label label = text(value, styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    static Region glyph(String... styleClasses) {
        Region glyph = new Region();
        glyph.getStyleClass().add("glyph");
        glyph.getStyleClass().addAll(styleClasses);
        glyph.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        glyph.setMouseTransparent(true);
        return glyph;
    }

    static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    static RXRow row(double gutter, double rowGap) {
        RXRow row = new RXRow();
        row.setBreakpointProfile(PROFILE);
        row.setGutter(gutter);
        row.setRowGap(rowGap);
        return row;
    }

    /**
     * Pulls a row out by half its gutter on each side. Every column keeps half the gutter
     * on both sides, so without this the outer columns sit half a gutter in from the
     * container's edge, and a row nested in a column sits a whole gutter in.
     */
    static <T extends RXRow> T bleed(T row) {
        double half = row.getGutter() / 2.0;
        Insets margin = new Insets(0.0, -half, 0.0, -half);
        VBox.setMargin(row, margin);
        StackPane.setMargin(row, margin);
        return row;
    }

    /** A column of the given span from XS, SM and MD up; a later breakpoint inherits the earlier one. */
    static RXCol col(Node content, int xs, int sm, int md) {
        RXCol col = new RXCol(content);
        col.setXs(RXColSpec.of(xs));
        col.setSm(RXColSpec.of(sm));
        col.setMd(RXColSpec.of(md));
        return col;
    }

    static RXButton iconButton(String glyphClass, String tooltip, Runnable action) {
        RXButton button = new RXButton(null, glyph(glyphClass));
        button.getStyleClass().add("icon-button");
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        button.setOnAction(event -> action.run());
        return button;
    }

    /** A classpath image, loaded in the background the first time it is asked for. */
    static Image image(String path) {
        return IMAGES.computeIfAbsent(path,
                key -> new Image(HomeKit.class.getResource(key).toExternalForm(), true));
    }
}
