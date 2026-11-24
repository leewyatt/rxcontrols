package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXAccordion.ExpandMode;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.RXTitledPane.IndicatorPosition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.area;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.pill;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wrapped;

/**
 * The RXAccordion page: a FAQ that keeps one answer open, shop filters that stay open
 * together with expand-all and collapse-all buttons, checkout steps as separate cards
 * that can stay open together, and notifications grouped by app with counts in the
 * headers.
 */
public final class RXAccordionSamples {

    private static final double AREA_WIDTH = 420.0;
    private static final double AREA_HEIGHT = 290.0;

    private static final String[][] FAQ = {
            {"How do I reset my password?",
                    "Open Settings, choose Security and click Reset password. We send a link to your email "
                            + "that works for one hour."},
            {"Can I change my plan later?",
                    "Yes. Upgrades apply right away; a downgrade starts at the end of the current billing period."},
            {"Where is my data stored?",
                    "In two data centers in the EU. Every file is encrypted at rest and copied every night."},
            {"Do you offer a student discount?",
                    "Students and teachers get half off any plan. Sign up with your school email address."},
            {"How do I delete my account?",
                    "Go to Settings, Account, Delete account. Your files are removed within 30 days."}};

    private RXAccordionSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A vertical stack of titled panes that open and close together.",
                List.of("Long content split into sections the reader opens one at a time: FAQs, filters, steps.",
                        "For a single section that opens on its own use RXTitledPane; for views of equal weight use RXTabPane."),
                List.of(
                        SampleCard.large("FAQ",
                                "expandMode SINGLE (the default): opening a question closes the one that was open.",
                                faq()),
                        SampleCard.large("Shop filters",
                                "expandMode MULTIPLE keeps several panes open, here inside a ScrollPane; expandAll and "
                                        + "collapseAll open or close them all.",
                                filters()),
                        SampleCard.large("Checkout steps",
                                "expandMode MULTIPLE lets several steps stay open; expandedSpacing 12 splits an "
                                        + "open pane from its neighbours into a card of its own; headerTrailing "
                                        + "shows each step's status.",
                                checkout()),
                        SampleCard.large("Notifications by app",
                                "indicatorPosition END moves the arrow after the count; every group can be closed.",
                                notifications())),
                NavKit.STYLESHEET);
    }

    // ==================== FAQ ====================

    private static Node faq() {
        RXAccordion accordion = new RXAccordion();
        for (String[] entry : FAQ) {
            Label answer = wrapped(entry[1], "copy");
            accordion.getPanes().add(new RXTitledPane(entry[0], answer));
        }
        accordion.getPanes().get(0).setExpanded(true);
        accordion.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, accordion);
    }

    // ==================== Shop filters ====================

    // One choice per pane keeps two or three open panes inside the card at once.
    private static Node filters() {
        RXTitledPane price = new RXTitledPane("Price", new RXCheckBox("Under $50"));
        RXTitledPane brand = new RXTitledPane("Brand", new RXCheckBox("Northwind only"));
        RXTitledPane rating = new RXTitledPane("Rating", new RXCheckBox("4 stars and up"));
        RXAccordion accordion = new RXAccordion(price, brand, rating);
        accordion.setExpandMode(ExpandMode.MULTIPLE);
        price.setExpanded(true);
        rating.setExpanded(true);

        RXButton open = plain("Expand all");
        open.setOnAction(event -> accordion.expandAll());
        RXButton close = plain("Collapse all");
        close.setOnAction(event -> accordion.collapseAll());
        HBox buttons = new HBox(8.0, text("Filters", "headline"), spacer(), open, close);
        buttons.setAlignment(Pos.CENTER_LEFT);
        ScrollPane scroll = new ScrollPane(accordion);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox box = new VBox(10.0, buttons, scroll);
        box.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, box);
    }

    // ==================== Checkout steps ====================

    private static Node checkout() {
        RXTitledPane address = step("Shipping address", "truck-icon", "sky",
                wrapped("Lee Wyatt, 12 Harbor Road, Bristol BS1 4RW", "copy"), pill("Done", "lime"));
        ToggleGroup methods = new ToggleGroup();
        RXRadioButton card = new RXRadioButton("Card ending 4242");
        RXRadioButton later = new RXRadioButton("Pay later");
        card.setToggleGroup(methods);
        later.setToggleGroup(methods);
        card.setSelected(true);
        RXTitledPane payment = step("Payment", "card-icon", "violet", new VBox(8.0, card, later),
                pill("To do", "amber"));
        RXTitledPane review = step("Review order", "cart-icon", "orange", new VBox(6.0,
                line("Desk lamp", "$39.00"), line("Shipping", "$4.90"), line("Total", "$43.90")), null);
        payment.setExpanded(true);
        RXAccordion accordion = new RXAccordion(address, payment, review);
        accordion.setExpandMode(ExpandMode.MULTIPLE);
        accordion.setExpandedSpacing(12.0);
        // All three steps open are taller than the card.
        ScrollPane scroll = new ScrollPane(accordion);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("plain");
        scroll.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, scroll);
    }

    private static RXTitledPane step(String title, String iconClass, String color, Node content, Node status) {
        RXTitledPane pane = new RXTitledPane(title, content);
        pane.setGraphic(icon(iconClass, color));
        pane.setHeaderTrailing(status);
        return pane;
    }

    private static Node line(String name, String amount) {
        HBox row = new HBox(text(name, "copy"), spacer(), text(amount, "copy"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Notifications ====================

    private static Node notifications() {
        RXTitledPane mail = group("Mail", "mail-icon", "sky", "3",
                "Invoice for November", "Lunch on Friday?", "Your order has shipped");
        RXTitledPane calendar = group("Calendar", "calendar-icon", "rose", "2",
                "Design review at 10:00", "Dentist tomorrow at 15:30");
        RXTitledPane chat = group("Chat", "chat-icon", "teal", "5",
                "Lee Wyatt: the build is green", "Ana: shared a file", "3 more messages");
        mail.setExpanded(true);
        RXAccordion accordion = new RXAccordion(mail, calendar, chat);
        accordion.setPrefWidth(AREA_WIDTH);
        return area(AREA_WIDTH, AREA_HEIGHT, accordion);
    }

    private static RXTitledPane group(String app, String iconClass, String color, String count, String... items) {
        VBox list = new VBox(8.0);
        for (String item : items) {
            HBox row = new HBox(8.0, icon("dot-icon", color, "tiny"), text(item, "copy"));
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        RXTitledPane pane = new RXTitledPane(app, list);
        pane.setGraphic(icon(iconClass, color));
        pane.setHeaderTrailing(pill(count, color));
        pane.setIndicatorPosition(IndicatorPosition.END);
        return pane;
    }
}
