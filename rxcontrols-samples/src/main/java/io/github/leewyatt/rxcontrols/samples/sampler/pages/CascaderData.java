package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Option trees shared by the RXCascader and RXCascaderView pages, and a loader that
 * answers after a short delay as a server would.
 */
final class CascaderData {

    private static final Duration LOAD_DELAY = Duration.millis(700.0);

    private static final Map<String, List<String>> TEAMS = Map.of(
            "Engineering", List.of("Platform", "Mobile", "Web"),
            "Design", List.of("Brand", "Product", "Research"),
            "Sales", List.of("Europe", "Americas", "Asia"),
            "Support", List.of("Tier 1", "Tier 2", "Billing"));

    private static final List<String> PEOPLE = List.of(
            "Lee Wyatt", "Mia Chen", "Sam Ortega", "Ada Brooks", "Hana Sato", "Ravi Patel", "Omar Haddad");

    private CascaderData() {
    }

    static List<RXCascaderItem<String>> places() {
        return List.of(
                branch("United States",
                        branch("California", leaves("San Francisco", "Los Angeles", "San Diego")),
                        branch("New York", leaves("New York City", "Buffalo", "Albany")),
                        branch("Washington", leaves("Seattle", "Spokane", "Tacoma"))),
                branch("Canada",
                        branch("Ontario", leaves("Toronto", "Ottawa")),
                        branch("British Columbia", leaves("Vancouver", "Victoria"))),
                branch("Germany",
                        branch("Bavaria", leaves("Munich", "Nuremberg")),
                        branch("Hamburg", leaves("Hamburg"))),
                branch("Japan",
                        branch("Kanto", leaves("Tokyo", "Yokohama")),
                        branch("Kansai", leaves("Osaka", "Kyoto", "Kobe"))));
    }

    static List<RXCascaderItem<String>> categories() {
        return List.of(
                branch("Electronics",
                        branch("Computers", leaves("Laptops", "Desktops", "Monitors", "Keyboards")),
                        branch("Phones", leaves("Smartphones", "Cases", "Chargers")),
                        branch("Audio", leaves("Headphones", "Speakers"))),
                branch("Home",
                        branch("Kitchen", leaves("Cookware", "Knives", "Coffee makers")),
                        branch("Furniture", leaves("Chairs", "Desks", "Shelves"))),
                branch("Sports",
                        branch("Cycling", leaves("Bikes", "Helmets", "Lights")),
                        branch("Running", leaves("Shoes", "Watches"))));
    }

    static List<RXCascaderItem<String>> regions() {
        return List.of(
                branch("Europe", leaves("France", "Germany", "Italy", "Spain", "Netherlands")),
                branch("Asia", leaves("Japan", "South Korea", "Singapore", "India")),
                branch("Americas", leaves("United States", "Canada", "Brazil", "Mexico")),
                branch("Oceania", leaves("Australia", "New Zealand")));
    }

    /**
     * Departments whose teams and people load on demand.
     */
    static List<RXCascaderItem<String>> departments() {
        List<RXCascaderItem<String>> items = new ArrayList<>();
        for (String name : List.of("Engineering", "Design", "Sales", "Support")) {
            items.add(new RXCascaderItem<>(name));
        }
        return items;
    }

    static CompletionStage<List<RXCascaderItem<String>>> loadChildren(RXCascaderItem<String> item) {
        List<RXCascaderItem<String>> children = new ArrayList<>();
        if (item.getParent() == null) {
            for (String team : TEAMS.get(item.getValue())) {
                children.add(new RXCascaderItem<>(team));
            }
        } else {
            int offset = Math.floorMod(item.getValue().hashCode(), PEOPLE.size());
            for (int i = 0; i < 3; i++) {
                RXCascaderItem<String> leaf = new RXCascaderItem<>(PEOPLE.get((offset + i) % PEOPLE.size()));
                leaf.setLeafHint(true);
                children.add(leaf);
            }
        }
        CompletableFuture<List<RXCascaderItem<String>>> future = new CompletableFuture<>();
        PauseTransition delay = new PauseTransition(LOAD_DELAY);
        delay.setOnFinished(event -> future.complete(children));
        delay.play();
        return future;
    }

    static String last(RXCascaderPath<String> path) {
        List<String> values = path.getValues();
        return values.isEmpty() ? "" : values.get(values.size() - 1);
    }

    @SafeVarargs
    static RXCascaderItem<String> branch(String value, RXCascaderItem<String>... children) {
        RXCascaderItem<String> item = new RXCascaderItem<>(value);
        item.getChildren().setAll(children);
        return item;
    }

    @SuppressWarnings("unchecked")
    static RXCascaderItem<String>[] leaves(String... values) {
        RXCascaderItem<String>[] items = new RXCascaderItem[values.length];
        for (int i = 0; i < values.length; i++) {
            items[i] = new RXCascaderItem<>(values[i]);
        }
        return items;
    }

    static RXCascaderItem<String> find(List<RXCascaderItem<String>> items, String value) {
        for (RXCascaderItem<String> item : items) {
            if (value.equals(item.getValue())) {
                return item;
            }
            RXCascaderItem<String> found = find(item.getChildren(), value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
