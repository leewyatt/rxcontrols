package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar.BranchMode;
import io.github.leewyatt.rxcontrols.event.RXBreadcrumbEvent;
import io.github.leewyatt.rxcontrols.skins.RXBreadcrumbBarSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXBreadcrumbBar}: the crumb structure, the navigation request
 * contract (controlled by default, auto navigation vetoed by consumption or by a
 * handler's own path change), branch menus in both modes including the category
 * switch that drops the old suffix, positional identity of repeated values, width
 * fitting, stale-menu protection, refresh and skin replacement.
 */
public class RXBreadcrumbBarTest {

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final Map<String, List<String>> CATALOG = Map.of(
            "Components", List.of("Buttons", "Data Display", "Inputs"),
            "Buttons", List.of("RXButton", "RXFillButton"),
            "Data Display", List.of("Badge", "Chip", "Avatar"));

    private Stage stage;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        Platform.setImplicitExit(false);
    }

    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    // ==================== Structure ====================

    @Test
    public void emptySingleAndThreeLevelPaths() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>(), 800);
            assertTrue(visibleItems(bar).isEmpty());
            assertEquals(0, visibleCount(bar, "separator"));

            bar.getItems().setAll("Home");
            layout(bar, 800);
            assertEquals(List.of("Home"), texts(visibleItems(bar)));
            assertEquals(0, visibleCount(bar, "separator"));
            assertInstanceOf(Label.class, visibleItems(bar).get(0), "current is plain text");

            bar.getItems().setAll("Home", "Library", "Data");
            layout(bar, 800);
            List<Labeled> items = visibleItems(bar);
            assertEquals(List.of("Home", "Library", "Data"), texts(items));
            assertEquals(2, visibleCount(bar, "separator"));
            assertFalse(items.get(0).getPseudoClassStates().contains(CURRENT));
            assertTrue(items.get(2).getPseudoClassStates().contains(CURRENT));
            assertInstanceOf(Button.class, items.get(0), "ancestors are focusable links");
            assertFalse(items.get(2).isFocusTraversable(), "current text is not a Tab stop");
            assertFalse(overflow(bar).isVisible());
        });
    }

    @Test
    public void nullItemsListIsAnEmptyPath() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B"), 800);
            bar.setItems(null);
            layout(bar, 800);
            assertTrue(visibleItems(bar).isEmpty());
        });
    }

    // ==================== Navigation requests ====================

    @Test
    public void ancestorRequestIsReportedButNotCommittedByDefault() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Home", "Library", "Data"), 800);
            List<RXBreadcrumbEvent<String>> events = new ArrayList<>();
            bar.setOnNavigate(events::add);

            link(bar, 1).fire();

            assertEquals(1, events.size());
            assertEquals(List.of("Home", "Library"), events.get(0).getPath());
            assertEquals("Library", events.get(0).getItem());
            assertEquals(1, events.get(0).getIndex());
            assertEquals(List.of("Home", "Library", "Data"), bar.getItems(), "controlled: path unchanged");
        });
    }

    @Test
    public void autoNavigateCommitsUnlessConsumedOrAlreadyAnswered() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Home", "Library", "Data"), 800);
            bar.setAutoNavigate(true);

            link(bar, 1).fire();
            assertEquals(List.of("Home", "Library"), bar.getItems());

            bar.getItems().setAll("Home", "Library", "Data");
            layout(bar, 800);
            bar.setOnNavigate(RXBreadcrumbEvent::consume);
            link(bar, 0).fire();
            assertEquals(List.of("Home", "Library", "Data"), bar.getItems(), "consumed request vetoes");

            // A handler that answers by writing its own path is not overwritten, even
            // when it writes back equal content.
            bar.setOnNavigate(e -> bar.getItems().setAll(List.copyOf(bar.getItems())));
            link(bar, 0).fire();
            assertEquals(List.of("Home", "Library", "Data"), bar.getItems());

            bar.setOnNavigate(e -> bar.getItems().setAll("Elsewhere"));
            layout(bar, 800);
            link(bar, 0).fire();
            assertEquals(List.of("Elsewhere"), bar.getItems());
        });
    }

    @Test
    public void programmaticChangesNeverFireRequests() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B"), 800);
            List<RXBreadcrumbEvent<String>> events = new ArrayList<>();
            bar.setOnNavigate(events::add);
            bar.getItems().setAll("X", "Y", "Z");
            bar.getItems().remove(2);
            bar.setItems(null);
            assertTrue(events.isEmpty());
        });
    }

    @Test
    public void currentItemFiresOnlyWhenNavigableAndOnEveryActivation() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Home", "Data"), 800);
            List<RXBreadcrumbEvent<String>> events = new ArrayList<>();
            bar.setOnNavigate(events::add);
            assertInstanceOf(Label.class, visibleItems(bar).get(1));

            bar.setCurrentNavigable(true);
            layout(bar, 800);
            Button current = link(bar, 1);
            assertTrue(current.getPseudoClassStates().contains(CURRENT));
            current.fire();
            current.fire();
            assertEquals(2, events.size());
            assertEquals(List.of("Home", "Data"), events.get(1).getPath());
        });
    }

    @Test
    public void repeatedValuesAreIdentifiedByPosition() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B", "A", "C"), 800);
            List<List<String>> paths = new ArrayList<>();
            bar.setOnNavigate(e -> paths.add(e.getPath()));
            link(bar, 0).fire();
            link(bar, 2).fire();
            assertEquals(List.of(List.of("A"), List.of("A", "B", "A")), paths);
        });
    }

    // ==================== Branch menus ====================

    @Test
    public void siblingSwitchDropsTheOldSuffixAndDoesNotPickAChild() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setBranchFactory(RXBreadcrumbBarTest::siblings);
            bar.setAutoNavigate(true);
            layout(bar, 800);
            List<List<String>> requests = new ArrayList<>();
            bar.setOnNavigate(e -> requests.add(e.getPath()));

            Button arrow = branchButton(bar, 1);
            arrow.fire();
            RXMenuList menu = openMenu();
            assertEquals(List.of("Buttons", "Data Display", "Inputs"), menuTexts(menu));
            assertTrue(entry(menu, "Data Display").getStyleClass().contains("breadcrumb-current"));
            menu.activate(entry(menu, "Buttons"));

            assertEquals(List.of(List.of("Components", "Buttons")), requests);
            assertEquals(List.of("Components", "Buttons"), bar.getItems());
        });
    }

    @Test
    public void currentItemKeepsItsBranchMenuWhileItsTextIsNotNavigable() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setBranchFactory(RXBreadcrumbBarTest::siblings);
            layout(bar, 800);
            List<List<String>> requests = new ArrayList<>();
            bar.setOnNavigate(e -> requests.add(e.getPath()));

            assertInstanceOf(Label.class, visibleItems(bar).get(2));
            Button arrow = branchButton(bar, 2);
            assertTrue(arrow.getPseudoClassStates().contains(CURRENT));
            arrow.fire();
            assertTrue(requests.isEmpty(), "opening a menu is not navigation");
            RXMenuList menu = openMenu();
            menu.activate(entry(menu, "Chip"));
            assertEquals(List.of(List.of("Components", "Data Display", "Chip")), requests);
        });
    }

    @Test
    public void childrenModeAppendsBelowThePosition() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Components", "Buttons"), 800);
            bar.setBranchMode(BranchMode.CHILDREN);
            bar.setBranchFactory(prefix -> CATALOG.get(prefix.get(prefix.size() - 1)));
            layout(bar, 800);
            List<List<String>> requests = new ArrayList<>();
            bar.setOnNavigate(e -> requests.add(e.getPath()));

            branchButton(bar, 1).fire();
            RXMenuList menu = openMenu();
            menu.activate(entry(menu, "RXFillButton"));
            assertEquals(List.of(List.of("Components", "Buttons", "RXFillButton")), requests);
        });
    }

    @Test
    public void pathChangeWhileAMenuIsOpenClosesIt() throws InterruptedException {
        AtomicReference<RXBreadcrumbBar<String>> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setBranchFactory(RXBreadcrumbBarTest::siblings);
            layout(bar, 800);
            branchButton(bar, 2).fire();
            assertNotNull(openMenu());
            bar.getItems().setAll("Components");
            assertTrue(openMenus().isEmpty(), "a stale menu cannot be activated");
            ref.set(bar);
        });
        assertNotNull(ref.get());
    }

    // ==================== Width fitting ====================

    @Test
    public void narrowWidthCollapsesTheMiddleAndWideWidthRestoresIt() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>(
                    "Root", "Alpha Alpha", "Beta Beta", "Gamma Gamma", "Delta Delta", "Current"), 1200);
            double full = bar.prefWidth(-1);
            assertEquals(6, visibleItems(bar).size());

            layout(bar, full * 0.6);
            List<String> visible = texts(visibleItems(bar));
            assertTrue(overflow(bar).isVisible());
            assertEquals("Root", visible.get(0), "root kept first");
            assertEquals("Current", visible.get(visible.size() - 1));
            assertTrue(visible.size() < 6);
            assertEquals(visible.size(), visibleCount(bar, "separator"),
                    "one separator between each pair of visible units, none at the ends");

            layout(bar, bar.minWidth(-1));
            assertEquals(List.of("Current"), texts(visibleItems(bar)));
            assertTrue(overflow(bar).isVisible());

            layout(bar, full);
            assertEquals(6, visibleItems(bar).size(), "widening restores every item");
            assertFalse(overflow(bar).isVisible());
        });
    }

    @Test
    public void overflowMenuListsTheHiddenRunInPathOrder() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B", "C", "D", "E"), 1200);
            bar.setMaxVisibleItems(2);
            layout(bar, 1200);
            assertEquals(List.of("A", "E"), texts(visibleItems(bar)));
            List<List<String>> requests = new ArrayList<>();
            bar.setOnNavigate(e -> requests.add(e.getPath()));

            overflow(bar).fire();
            RXMenuList menu = openMenu();
            assertEquals(List.of("B", "C", "D"), menuTexts(menu));
            menu.activate(entry(menu, "C"));
            assertEquals(List.of(List.of("A", "B", "C")), requests);
        });
    }

    // ==================== Refresh / lifecycle ====================

    @Test
    public void refreshRereadsMutableItemsAndConverterChangesRebuild() throws InterruptedException {
        runOnFx(() -> {
            StringBuilder name = new StringBuilder("Old");
            RXBreadcrumbBar<StringBuilder> bar = show(
                    new RXBreadcrumbBar<>(new StringBuilder("Root"), name), 800);
            name.replace(0, name.length(), "New");
            assertEquals(List.of("Root", "Old"), texts(visibleItems(bar)), "in-place mutation is not observed");
            bar.refresh();
            layout(bar, 800);
            assertEquals(List.of("Root", "New"), texts(visibleItems(bar)));

            bar.setConverter(new StringConverter<>() {
                @Override
                public String toString(StringBuilder value) {
                    return value.toString().toUpperCase();
                }

                @Override
                public StringBuilder fromString(String string) {
                    return null;
                }
            });
            layout(bar, 800);
            assertEquals(List.of("ROOT", "NEW"), texts(visibleItems(bar)));
        });
    }

    @Test
    public void replacedSkinStopsTrackingThePath() throws InterruptedException {
        runOnFx(() -> {
            List<Throwable> errors = new ArrayList<>();
            Thread.UncaughtExceptionHandler previous = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
            try {
                RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B"), 800);
                bar.setSkin(new RXBreadcrumbBarSkin<>(bar));
                bar.getItems().setAll("X", "Y", "Z");
                layout(bar, 800);
                assertEquals(List.of("X", "Y", "Z"), texts(visibleItems(bar)));
                assertEquals(3, bar.getChildrenUnmodifiable().stream()
                        .filter(n -> n.getStyleClass().contains("item")).count(), "only one skin's crumbs");
                assertTrue(errors.isEmpty(), "the disposed skin must not react: " + errors);
            } finally {
                Thread.currentThread().setUncaughtExceptionHandler(previous);
            }
        });
    }

    // ==================== Helpers ====================

    private static List<String> siblings(List<String> prefix) {
        if (prefix.size() < 2) {
            return List.of();
        }
        return CATALOG.getOrDefault(prefix.get(prefix.size() - 2), List.of());
    }

    private <T> RXBreadcrumbBar<T> show(RXBreadcrumbBar<T> bar, double width) {
        Pane root = new Pane(bar);
        stage = new Stage();
        stage.setScene(new Scene(root, 1300, 120));
        stage.show();
        layout(bar, width);
        return bar;
    }

    private static void layout(RXBreadcrumbBar<?> bar, double width) {
        Parent root = bar.getParent();
        root.applyCss();
        root.layout();
        bar.resize(width, bar.prefHeight(-1));
        bar.layout();
    }

    private static List<Labeled> visibleItems(RXBreadcrumbBar<?> bar) {
        return bar.getChildrenUnmodifiable().stream()
                .filter(n -> n.getStyleClass().contains("item") && n.isVisible())
                .map(n -> (Labeled) n)
                .collect(Collectors.toList());
    }

    private static long visibleCount(RXBreadcrumbBar<?> bar, String styleClass) {
        return bar.getChildrenUnmodifiable().stream()
                .filter(n -> n.getStyleClass().contains(styleClass) && n.isVisible())
                .count();
    }

    private static List<String> texts(List<Labeled> items) {
        return items.stream().map(Labeled::getText).collect(Collectors.toList());
    }

    // The item node at a path position (all positions, visible or not).
    private static Button link(RXBreadcrumbBar<?> bar, int index) {
        List<Node> items = bar.getChildrenUnmodifiable().stream()
                .filter(n -> n.getStyleClass().contains("item"))
                .collect(Collectors.toList());
        return (Button) items.get(index);
    }

    // The branch button that follows the item at a path position.
    private static Button branchButton(RXBreadcrumbBar<?> bar, int index) {
        List<Node> children = bar.getChildrenUnmodifiable();
        int seen = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).getStyleClass().contains("item")) {
                seen++;
            } else if (seen == index && children.get(i).getStyleClass().contains("branch-button")) {
                return (Button) children.get(i);
            }
        }
        throw new AssertionError("no branch button at " + index);
    }

    private static Button overflow(RXBreadcrumbBar<?> bar) {
        return (Button) bar.getChildrenUnmodifiable().stream()
                .filter(n -> n.getStyleClass().contains("overflow-button"))
                .findFirst().orElseThrow();
    }

    private static List<RXMenuList> openMenus() {
        List<RXMenuList> menus = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window.getScene() != null) {
                Node list = window.getScene().getRoot().lookup(".rx-menu-list");
                if (list instanceof RXMenuList menuList) {
                    menus.add(menuList);
                }
            }
        }
        return menus;
    }

    private static RXMenuList openMenu() {
        List<RXMenuList> menus = openMenus();
        assertEquals(1, menus.size(), "exactly one open menu");
        return menus.get(0);
    }

    private static List<String> menuTexts(RXMenuList menu) {
        return menu.getItems().stream().map(RXMenuItem::getText).collect(Collectors.toList());
    }

    private static RXMenuItem entry(RXMenuList menu, String text) {
        return menu.getItems().stream().filter(i -> text.equals(i.getText())).findFirst().orElseThrow();
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}
