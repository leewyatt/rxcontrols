package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXLineLabel;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;

/**
 * The RXLineLabel page: links inside running text, section headings, a footer menu,
 * a choice of line animations and line colors and thickness.
 */
public final class RXLineLabelSamples {

    private RXLineLabelSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A label that draws an animated line under, around or beside its text on hover.",
                List.of("Links in text, footer and menu entries, and headings that should hint at the pointer quietly.",
                        "For a clickable action with the same lines use RXLineButton."),
                List.of(
                        SampleCard.wide("Links in text",
                                "Line labels sit in a TextFlow beside plain text; hover the highlighted words.",
                                paragraph()),
                        SampleCard.medium("Section headings",
                                "lineThickness 3 and lineGap 6 give a heading a bolder line further from the text.",
                                headings()),
                        SampleCard.medium("Footer menu",
                                "Columns of quiet links; the underline grows from the left with UNDERLINE_LEFT_TO_RIGHT.",
                                footer()),
                        SampleCard.medium("Line animations",
                                "lineAnimation picks the lines; UNDERLINE_CENTER_OUT is the default.",
                                animations()),
                        SampleCard.medium("Colors and icons",
                                "-rx-line-color colors the line and the hovered text; the line spans the icon too.",
                                colors())),
                MediaKit.STYLESHEET);
    }

    // ==================== Links in text ====================

    private static Node paragraph() {
        TextFlow flow = new TextFlow(
                plain("Read the "), link("getting started guide"), plain(" first, then browse the "),
                link("component gallery"), plain(" for ideas. Questions are welcome in the "),
                link("discussion board"), plain(", and every release is listed in the "),
                link("change log"), plain("."));
        flow.getStyleClass().add("paragraph");
        flow.setLineSpacing(6.0);
        flow.setPrefWidth(520.0);
        flow.setMaxWidth(520.0);
        return flow;
    }

    private static Text plain(String value) {
        Text text = new Text(value);
        text.getStyleClass().add("flow-text");
        return text;
    }

    private static Node link(String value) {
        RXLineLabel link = new RXLineLabel(value);
        link.getStyleClass().add("inline-link");
        link.setLineAnimation(LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
        link.setLineGap(1.0);
        return link;
    }

    // ==================== Footer menu ====================

    private static Node footer() {
        HBox columns = new HBox(36.0,
                column("Product", "Features", "Pricing", "Updates"),
                column("Company", "About", "Careers", "Contact"));
        columns.setAlignment(Pos.TOP_CENTER);
        return columns;
    }

    private static Node column(String title, String... items) {
        VBox column = new VBox(8.0, text(title, "copy-strong"));
        for (String item : items) {
            RXLineLabel link = new RXLineLabel(item);
            link.getStyleClass().add("footer-link");
            link.setLineAnimation(LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
            column.getChildren().add(link);
        }
        return column;
    }

    // ==================== Section headings ====================

    private static Node headings() {
        VBox box = new VBox(16.0, heading("01", "Overview", "violet"), heading("02", "Installation", "teal"),
                heading("03", "Examples", "orange"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Node heading(String number, String value, String color) {
        RXLineLabel heading = new RXLineLabel(value, text(number, "heading-number"));
        heading.setGraphicTextGap(10.0);
        heading.getStyleClass().addAll("heading-line", color);
        heading.setLineThickness(3.0);
        heading.setLineGap(6.0);
        heading.setLineAnimation(LineAnimation.UNDERLINE_SLIDE_UP);
        return heading;
    }

    // ==================== Line animations ====================

    private static Node animations() {
        GridPane grid = new GridPane();
        grid.setHgap(28.0);
        grid.setVgap(18.0);
        grid.add(line("Center out", LineAnimation.UNDERLINE_CENTER_OUT), 0, 0);
        grid.add(line("Fade in", LineAnimation.UNDERLINE_FADE), 1, 0);
        grid.add(line("Top and bottom", LineAnimation.TOP_BOTTOM_CENTER_OUT), 0, 1);
        grid.add(line("Brackets", LineAnimation.LEFT_RIGHT_CONVERGE), 1, 1);
        grid.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return grid;
    }

    private static Node line(String value, LineAnimation animation) {
        RXLineLabel label = new RXLineLabel(value);
        label.getStyleClass().add("sample-line");
        label.setLineAnimation(animation);
        return label;
    }

    // ==================== Colors and icons ====================

    private static Node colors() {
        VBox box = new VBox(12.0,
                colored("Download the report", "download-icon", "sky"),
                colored("Share with the team", "share-icon", "pink"),
                colored("Open in the browser", "open-icon", "lime"));
        box.setAlignment(Pos.CENTER_LEFT);
        return fixedWidth(box, 200.0);
    }

    private static Node colored(String value, String iconClass, String color) {
        RXLineLabel label = new RXLineLabel(value, icon(iconClass, "line-icon"));
        label.getStyleClass().addAll("sample-line", color);
        label.setGraphicTextGap(8.0);
        label.setLineAnimation(LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
        return label;
    }
}
