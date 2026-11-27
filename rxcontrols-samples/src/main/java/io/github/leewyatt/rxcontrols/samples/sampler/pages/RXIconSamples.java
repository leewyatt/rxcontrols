package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXIcon;
import io.github.leewyatt.rxcontrols.RXIconData;
import io.github.leewyatt.rxcontrols.RXIcons;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXSvgIcons;
import io.github.leewyatt.rxcontrols.icons.bootstrap.RXBootstrapIcons;
import io.github.leewyatt.rxcontrols.icons.material.RXMaterialIcons;
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

/**
 * The RXIcon page: icons from the two bundled packs, sizes that follow the font,
 * icons in buttons and status colors, a list whose reused cells swap icons, the
 * fixed box next to text that does not fit, and an icon read from SVG markup.
 */
public final class RXIconSamples {

    private static final String STYLESHEET =
            RXIconSamples.class.getResource("rx-icon-samples.css").toExternalForm();

    /** A custom icon, as a design tool exports it: a filled path and a bounding box with no fill. */
    private static final String LOGO_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 32 32\""
            + " fill-rule=\"evenodd\"><rect width=\"32\" height=\"32\" fill=\"none\"/>"
            + "<path d=\"M16 2 L29 9.5 V22.5 L16 30 L3 22.5 V9.5 Z M16 9 L23 13 V19 L16 23 L9 19 V13 Z\"/>"
            + "<circle cx=\"16\" cy=\"16\" r=\"3\"/></svg>";

    private RXIconSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A single-color icon in a fixed-size box, drawn from icon data or from a CSS shape.",
                List.of("Icons in toolbars, buttons, lists, fields and status messages: pick one from a bundled "
                                + "pack by name, or read your own from an SVG file.",
                        "For pictures and multi-color artwork use RXImageView; the icons inside the library's "
                                + "controls are RXIcons styled with -fx-shape."),
                List.of(
                        SampleCard.large("Icons from a pack",
                                "new RXIcon(RXMaterialIcons.HOME) or -rx-icon: \"mi-home\" in CSS. Material Icons "
                                        + "(mi) and Bootstrap Icons (bi) register themselves when their jar is present.",
                                packs()),
                        SampleCard.large("A list that swaps icons",
                                "Each reused cell sets its icon in code, so the right icon shows at once while "
                                        + "scrolling; nothing waits for a CSS pass.",
                                files()),
                        SampleCard.medium("Sizes that follow the font",
                                "-rx-icon-size: 1.25em sizes the box from the font of the label it sits in.",
                                sizes()),
                        SampleCard.medium("Buttons and status colors",
                                "The icon is a region filled with its background color, so CSS colors it like "
                                        + "any other node.",
                                buttons(), statuses()),
                        SampleCard.medium("A box that keeps its size",
                                "The text gives way when the row is short of space; the icon keeps its box.",
                                fixedBox()),
                        SampleCard.medium("Your own icon from SVG",
                                "RXSvgIcons reads an exported file, RXIcons.register makes its name usable "
                                        + "anywhere, here in CSS.",
                                custom())),
                STYLESHEET);
    }

    private static Node packs() {
        HBox material = row(RXMaterialIcons.HOME, RXMaterialIcons.SEARCH, RXMaterialIcons.SETTINGS,
                RXMaterialIcons.NOTIFICATIONS, RXMaterialIcons.FAVORITE, RXMaterialIcons.SHARE);
        HBox bootstrap = row(RXBootstrapIcons.HOUSE, RXBootstrapIcons.SEARCH, RXBootstrapIcons.GEAR,
                RXBootstrapIcons.BELL, RXBootstrapIcons.HEART, RXBootstrapIcons.SHARE);
        VBox box = new VBox(14.0, caption("Material Icons"), material, caption("Bootstrap Icons"), bootstrap);
        box.getStyleClass().add("pack-rows");
        return box;
    }

    private static HBox row(String... names) {
        HBox row = new HBox(18.0);
        for (String name : names) {
            RXIcon icon = new RXIcon(name);
            icon.setIconSize(28.0);
            row.getChildren().add(icon);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Node files() {
        RXListView<String> list = new RXListView<>(FXCollections.observableArrayList(
                "Quarterly report.pdf", "Team photo.jpg", "Budget.xlsx", "Intro.mp4", "Notes.txt",
                "Archive.zip", "Slides.pptx", "Theme song.mp3", "Logo.svg", "Contract.pdf",
                "Holiday.png", "Data.csv", "Readme.md", "Interview.mov", "Backup.zip"));
        list.setCellFactory(view -> new FileCell());
        list.setPrefSize(320.0, 220.0);
        list.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return list;
    }

    private static Node sizes() {
        VBox box = new VBox(10.0);
        for (String size : new String[]{"small", "medium", "large"}) {
            RXIcon icon = new RXIcon(RXMaterialIcons.CLOUD_DONE);
            Label label = new Label("Synced", icon);
            label.getStyleClass().addAll("sized-label", size);
            box.getChildren().add(label);
        }
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Node buttons() {
        RXButton edit = new RXButton("Edit", new RXIcon(RXMaterialIcons.EDIT));
        RXButton upload = new RXButton("Upload", new RXIcon(RXMaterialIcons.CLOUD_UPLOAD));
        RXButton delete = new RXButton(null, new RXIcon(RXMaterialIcons.DELETE));
        delete.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        delete.setAccessibleText("Delete");
        HBox row = new HBox(10.0, edit, upload, delete);
        row.getStyleClass().add("icon-buttons");
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node statuses() {
        HBox row = new HBox(16.0,
                status(RXBootstrapIcons.CHECK_CIRCLE_FILL, "Saved", "success"),
                status(RXBootstrapIcons.EXCLAMATION_TRIANGLE_FILL, "Low space", "warning"),
                status(RXBootstrapIcons.X_CIRCLE_FILL, "Failed", "danger"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Label status(String iconName, String text, String styleClass) {
        Label label = new Label(text, new RXIcon(iconName));
        label.getStyleClass().addAll("status", styleClass);
        return label;
    }

    private static Node fixedBox() {
        Label path = new Label("Documents / Projects / 2026 / Annual report / Final drafts / Appendix.pdf");
        path.setMinWidth(0.0);
        HBox.setHgrow(path, Priority.ALWAYS);
        RXIcon lock = new RXIcon(RXMaterialIcons.LOCK);
        lock.setIconSize(18.0);
        RXIcon folder = new RXIcon(RXMaterialIcons.FOLDER);
        folder.setIconSize(18.0);
        HBox row = new HBox(8.0, folder, path, lock);
        row.getStyleClass().add("path-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWidth(240.0);
        row.setMaxWidth(Region.USE_PREF_SIZE);
        return row;
    }

    private static Node custom() {
        if (RXIcons.find("sample-logo") == null) {
            RXIcons.register(RXSvgIcons.parse("sample-logo", LOGO_SVG));
        }
        RXIcon logo = new RXIcon();
        logo.getStyleClass().add("brand-logo");
        Label name = new Label("Hexa Studio");
        name.getStyleClass().add("brand-name");
        HBox brand = new HBox(12.0, logo, name);
        brand.setAlignment(Pos.CENTER);
        return brand;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("caption");
        return label;
    }

    /** A file row whose icon follows the file type; the nodes are created once per cell. */
    private static final class FileCell extends RXListCell<String> {

        private final RXIcon icon = new RXIcon();
        private final Label name = new Label();
        private final HBox row = new HBox(10.0, icon, name);

        private FileCell() {
            icon.setIconSize(20.0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("file-row");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                icon.setIcon(null);
            } else {
                name.setText(item);
                icon.setIcon(iconFor(item));
                setGraphic(row);
            }
        }

        private static RXIconData iconFor(String file) {
            return RXIcons.find(switch (kindOf(file)) {
                case "pdf" -> RXBootstrapIcons.FILETYPE_PDF;
                case "image" -> RXMaterialIcons.IMAGE;
                case "sheet" -> RXMaterialIcons.TABLE_CHART;
                case "video" -> RXMaterialIcons.MOVIE;
                case "audio" -> RXMaterialIcons.MUSIC_NOTE;
                case "archive" -> RXBootstrapIcons.FILE_EARMARK_ZIP;
                case "slides" -> RXMaterialIcons.SLIDESHOW;
                default -> RXMaterialIcons.DESCRIPTION;
            });
        }

        private static String kindOf(String file) {
            String extension = file.substring(file.lastIndexOf('.') + 1);
            return switch (extension) {
                case "pdf" -> "pdf";
                case "jpg", "png", "svg" -> "image";
                case "xlsx", "csv" -> "sheet";
                case "mp4", "mov" -> "video";
                case "mp3" -> "audio";
                case "zip" -> "archive";
                case "pptx" -> "slides";
                default -> "text";
            };
        }
    }
}
