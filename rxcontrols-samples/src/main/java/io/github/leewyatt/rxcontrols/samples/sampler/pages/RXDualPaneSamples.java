package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBorderBeam;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDualPane;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.iconButton;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;

/**
 * The RXDualPane page: a contact list that slides to a contact's details and back, a
 * profile that cross-fades between viewing and editing, a payment card that flips to
 * its back, and sign-in and sign-up forms on two faces of a cube.
 */
public final class RXDualPaneSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double SWITCH_LINK_PADDING = 10.0;
    private static final double FRAME_HEIGHT = 300.0;

    private static final String[][] CONTACTS = {
            {"Lee Wyatt", "@LeeWyatt_7788", "Product designer", "teal"},
            {"Ana Kowalski", "@ana.k", "Engineering lead", "orange"},
            {"Marco Rossi", "@marcor", "Sales", "violet"},
            {"Jun Sato", "@jun", "Support", "sky"},
            {"Nora Berg", "@nberg", "Marketing", "pink"}};

    private RXDualPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A container with two faces that plays an animation when it flips from one to the other.",
                List.of("Two states of one place: a list and its detail, view and edit, front and back.",
                        "For more than two contents, or content that changes freely, use RXTransitionPane."),
                List.of(
                        SampleCard.large("Front and back",
                                "A click on the card calls toggle(); AnimFlip with Orientation.VERTICAL turns it over side to side.",
                                paymentCard()),
                        SampleCard.large("List and detail",
                                "Picking a contact sets showingSecond; AnimSlide plays forward to the detail and "
                                        + "backward on the way back.",
                                contacts()),
                        SampleCard.large("View and edit",
                                "showingSecond is bound to the Edit switch; AnimFade cross-fades the two faces.",
                                profile()),
                        SampleCard.large("Sign in or sign up",
                                "The links flip between the two forms with AnimCube; the pane keeps the size of "
                                        + "the larger face.",
                                account())),
                NavKit.STYLESHEET);
    }

    // ==================== List and detail ====================

    private static Node contacts() {
        RXDualPane pane = new RXDualPane();
        pane.setAnimation(new AnimSlide());

        Label avatar = avatar("", "teal", 64.0);
        Label name = text("", "page-title");
        Label handle = text("", "muted");
        Label role = text("", "copy");
        StackPane back = iconButton("back-icon");
        back.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> pane.setShowingSecond(false));
        HBox bar = new HBox(10.0, back, text("Contacts", "copy"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("frame-bar");
        RXButton backButton = solid("Back", "teal");
        backButton.setOnAction(event -> pane.setShowingSecond(false));
        VBox card = new VBox(8.0, avatar, name, handle, role, backButton);
        card.setAlignment(Pos.CENTER);
        VBox.setMargin(backButton, new Insets(8.0, 0.0, 0.0, 0.0));
        BorderPane detail = new BorderPane(card);
        detail.setTop(bar);

        VBox list = new VBox(2.0);
        list.setPadding(new Insets(10.0, 12.0, 10.0, 12.0));
        list.getChildren().add(text("Contacts", "headline", "list-title"));
        for (String[] contact : CONTACTS) {
            HBox row = new HBox(12.0, avatar(contact[0], contact[3], 32.0),
                    new VBox(1.0, text(contact[0], "copy-strong"), text(contact[2], "muted")),
                    spacer(), icon("chevron-icon"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().addAll("list-row", "clickable");
            row.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                avatar.setText(initials(contact[0]));
                avatar.getStyleClass().setAll("label", "avatar", "large-avatar", contact[3]);
                name.setText(contact[0]);
                handle.setText(contact[1]);
                role.setText(contact[2]);
                pane.setShowingSecond(true);
            });
            list.getChildren().add(row);
        }
        pane.setFirstContent(list);
        pane.setSecondContent(detail);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, pane);
    }

    // ==================== View and edit ====================

    private static Node profile() {
        RXTextField nameField = new RXTextField("Lee Wyatt");
        RXTextField roleField = new RXTextField("Product designer");
        RXTextField cityField = new RXTextField("Bristol");

        Label name = text("", "page-title");
        name.textProperty().bind(nameField.textProperty());
        Label role = text("", "copy");
        role.textProperty().bind(roleField.textProperty());
        Label city = text("", "muted");
        city.textProperty().bind(cityField.textProperty());
        HBox place = new HBox(6.0, icon("place-icon", "rose"), city);
        place.setAlignment(Pos.CENTER);
        VBox view = new VBox(8.0, avatar("Lee Wyatt", "teal", 64.0), name, role, place);
        view.setAlignment(Pos.CENTER);
        view.getStyleClass().add("face");

        VBox edit = new VBox(6.0, text("Name", "muted"), nameField, text("Role", "muted"), roleField,
                text("City", "muted"), cityField);
        edit.setPadding(new Insets(16.0, 20.0, 16.0, 20.0));
        edit.setAlignment(Pos.CENTER_LEFT);
        edit.getStyleClass().add("face");

        RXDualPane pane = new RXDualPane(view, edit);
        pane.setAnimation(new AnimFade());
        pane.setAnimationDuration(Duration.millis(300.0));
        RXSwitchButton editing = new RXSwitchButton("Edit");
        pane.showingSecondProperty().bind(editing.selectedProperty());

        HBox bar = new HBox(10.0, text("Profile", "headline"), spacer(), editing);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("frame-bar");
        BorderPane window = new BorderPane(pane);
        window.setTop(bar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, window);
    }

    // ==================== Front and back ====================

    private static Node paymentCard() {
        Label number = text("5858  5858  5858  5858", "card-number");
        HBox top = new HBox(text("Northwind Bank", "card-text"), spacer(), icon("card-icon", "on-color"));
        HBox bottom = new HBox(new VBox(2.0, text("Card holder", "card-caption"), text("LEE WYATT", "card-text")),
                spacer(), new VBox(2.0, text("Expires", "card-caption"), text("09/29", "card-text")));
        VBox front = new VBox(top, spacer(), number, spacer(), bottom);
        front.getStyleClass().addAll("bank-card", "front");

        Region stripe = new Region();
        stripe.getStyleClass().add("stripe");
        stripe.setPrefHeight(36.0);
        HBox cvc = new HBox(10.0, text("Security code", "card-caption"), text("123", "cvc"));
        cvc.setAlignment(Pos.CENTER_RIGHT);
        cvc.setPadding(new Insets(0.0, 18.0, 0.0, 18.0));
        VBox back = new VBox(14.0, stripe, cvc,
                text("Call 0800 123 456 if this card is lost.", "card-caption", "back-note"));
        back.getStyleClass().addAll("bank-card", "back");

        RXDualPane pane = new RXDualPane(front, back);
        pane.setAnimation(new AnimFlip(Orientation.VERTICAL));
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pane.getStyleClass().add("clickable");
        pane.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> pane.toggle());

        VBox box = new VBox(14.0, pane, text("Click the card to turn it over", "muted"));
        box.setAlignment(Pos.CENTER);
        StackPane holder = new StackPane(box);
        holder.setPrefSize(FRAME_WIDTH, FRAME_HEIGHT);
        return holder;
    }

    // ==================== Sign in or sign up ====================

    private static Node account() {
        Hyperlink toSignUp = new Hyperlink("Create an account");
        Hyperlink toSignIn = new Hyperlink("I already have an account");
        VBox signIn = form("Welcome back", "Sign in", "indigo", toSignUp,
                field("Email", "lee@example.com"), password("Password"));
        VBox signUp = form("Create an account", "Sign up", "pink", toSignIn,
                field("Name", "Lee Wyatt"), field("Email", "lee@example.com"), password("Choose a password"));

        RXDualPane pane = new RXDualPane(signIn, signUp);
        pane.setAnimation(new AnimCube());
        toSignUp.setOnAction(event -> pane.setShowingSecond(true));
        toSignIn.setOnAction(event -> pane.setShowingSecond(false));
        return frame(FRAME_WIDTH, FRAME_HEIGHT + 30.0, pane);
    }

    // A beam runs round the link to the other face, to invite the switch.
    private static Node beam(Hyperlink link, String color) {
        link.getStyleClass().add("switch-link");
        RXBorderBeam beam = new RXBorderBeam(link);
        beam.setBeamFill(Color.web("pink".equals(color) ? "#ec4899" : "#6366f1"));
        beam.setBeamSize(35);
        beam.setLineWidth(1.5);
        beam.setCycleDuration(Duration.seconds(2.5));
        // The form would stretch it to the full width otherwise.
        beam.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return beam;
    }

    private static VBox form(String title, String action, String color, Hyperlink link, Node... fields) {
        VBox form = new VBox(8.0);
        form.getChildren().add(text(title, "page-title"));
        form.getChildren().addAll(fields);
        RXButton submit = solid(action, color);
        submit.setMaxWidth(Double.MAX_VALUE);
        Node switchLink = beam(link, color);
        form.getChildren().addAll(submit, switchLink);
        VBox.setMargin(submit, new Insets(6.0, 0.0, 0.0, 0.0));
        // The link's padding gives the beam room; pulling it back keeps its text in line
        // with the fields.
        VBox.setMargin(switchLink, new Insets(0.0, 0.0, 0.0, -SWITCH_LINK_PADDING));
        form.setPadding(new Insets(18.0, 60.0, 18.0, 60.0));
        form.setAlignment(Pos.CENTER_LEFT);
        form.getStyleClass().add("face");
        return form;
    }

    private static RXTextField field(String prompt, String value) {
        RXTextField field = new RXTextField(value);
        field.setPromptText(prompt);
        return field;
    }

    private static RXPasswordField password(String prompt) {
        RXPasswordField field = new RXPasswordField();
        field.setPromptText(prompt);
        return field;
    }

    // ==================== Shared ====================

    private static Label avatar(String name, String color, double size) {
        Label avatar = text(initials(name), "avatar", color);
        if (size > 40.0) {
            avatar.getStyleClass().add("large-avatar");
        }
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(size, size);
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);
        return avatar;
    }

    /** "Lee Wyatt" becomes "LW". */
    private static String initials(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split("\\s+");
        String first = parts[0].substring(0, 1);
        return parts.length == 1 ? first : first + parts[parts.length - 1].charAt(0);
    }
}
