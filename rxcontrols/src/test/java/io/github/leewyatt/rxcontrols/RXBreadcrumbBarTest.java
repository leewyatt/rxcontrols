package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar.BranchMode;
import io.github.leewyatt.rxcontrols.event.RXBreadcrumbEvent;
import io.github.leewyatt.rxcontrols.skins.RXBreadcrumbBarSkin;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link RXBreadcrumbBar}: the crumb structure, the navigation request
 * contract (controlled by default, auto navigation vetoed by consumption or by a
 * handler's own path change), branch menus in both modes from one children factory
 * (entries read when a menu opens, arrows decided by the path or the branch filter)
 * including the category switch that drops the old suffix, positional identity of repeated values, width
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

            bar.setCurrentItemNavigable(true);
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

    // ==================== Callbacks that change the path ====================

    @Test
    public void pathChangedFromACallbackDuringARebuildLeavesOneConsistentBar() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B", "C"), 800);
            boolean[] changed = new boolean[1];
            bar.setChildrenFactory(path -> {
                if (!changed[0]) {
                    changed[0] = true;
                    bar.getItems().setAll("X");
                }
                return List.of();
            });
            layout(bar, 800);
            assertEquals(List.of("X"), bar.getItems());
            assertEquals(List.of("X"), texts(visibleItems(bar)));
            assertEquals(1, visibleCount(bar, "item"));
            assertEquals(0, visibleCount(bar, "separator"));
        });
    }

    @Test
    public void callbackThatAlwaysChangesTheBarIsStoppedWithAnError() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B"), 800);
            List<Throwable> errors = new ArrayList<>();
            Thread.UncaughtExceptionHandler previous = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
            int[] calls = new int[1];
            try {
                bar.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(String item) {
                        calls[0]++;
                        bar.refresh();
                        return item;
                    }

                    @Override
                    public String fromString(String text) {
                        return text;
                    }
                });
            } catch (IllegalStateException direct) {
                errors.add(direct);
            } finally {
                Thread.currentThread().setUncaughtExceptionHandler(previous);
            }
            assertTrue(calls[0] < 100, "the rebuild stops: " + calls[0] + " calls");
            assertEquals(1, errors.size());
            assertInstanceOf(IllegalStateException.class, errors.get(0));

            bar.setConverter(null);
            bar.getItems().setAll("A", "C");
            layout(bar, 800);
            assertEquals(List.of("A", "C"), texts(visibleItems(bar)), "and the bar recovers");
        });
    }

    @Test
    public void failingCallbackKeepsThePreviousCrumbs() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B"), 800);
            bar.setConverter(new StringConverter<>() {
                @Override
                public String toString(String item) {
                    if ("bad".equals(item)) {
                        throw new IllegalStateException("converter failure");
                    }
                    return item;
                }

                @Override
                public String fromString(String text) {
                    return text;
                }
            });
            layout(bar, 800);
            try {
                bar.getItems().setAll("A", "bad", "C");
            } catch (IllegalStateException expected) {
                // Listener failures reach the caller or the thread's handler, by platform.
            }
            layout(bar, 800);
            assertEquals(List.of("A", "B"), texts(visibleItems(bar)), "the last good build stays");

            bar.getItems().setAll("A", "C");
            layout(bar, 800);
            assertEquals(List.of("A", "C"), texts(visibleItems(bar)), "and the bar recovers");
        });
    }

    // ==================== Empty path ====================

    @Test
    public void emptyPathKeepsTheHeightOfARowAndShowsThePlaceholder() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>(), 800);
            double emptyHeight = bar.prefHeight(-1);
            assertFalse(overflow(bar).isVisible());

            bar.getItems().setAll("Home");
            layout(bar, 800);
            double rowHeight = bar.prefHeight(-1);
            assertTrue(rowHeight > 0);
            assertEquals(rowHeight, emptyHeight, 0.01, "no jump when a path appears");

            bar.getItems().clear();
            layout(bar, 800);
            assertEquals(rowHeight, bar.prefHeight(-1), 0.01, "no jump when the path empties");
            assertTrue(visibleItems(bar).isEmpty());

            Label hint = new Label("No location");
            bar.setPlaceholder(hint);
            layout(bar, 800);
            assertEquals(bar, hint.getParent());
            assertEquals(rowHeight, bar.prefHeight(-1), 0.01);
            assertTrue(bar.prefWidth(-1) >= hint.prefWidth(-1));
            assertTrue(visibleItems(bar).isEmpty(), "the placeholder is not a path item");

            bar.getItems().setAll("Home", "Library");
            layout(bar, 800);
            assertFalse(bar.getChildrenUnmodifiable().contains(hint));
            assertEquals(List.of("Home", "Library"), texts(visibleItems(bar)));

            bar.getItems().clear();
            bar.setPlaceholder(null);
            layout(bar, 800);
            assertFalse(bar.getChildrenUnmodifiable().contains(hint));
        });
    }

    // ==================== Separators ====================

    @Test
    public void separatorFactorySuppliesOneNodePerSeparator() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("A", "B", "C"), 800);
            assertEquals(List.of(Region.class, Region.class), separatorContentTypes(bar));

            List<Node> made = new ArrayList<>();
            bar.setSeparatorFactory(() -> {
                Label slash = new Label("/");
                made.add(slash);
                return slash;
            });
            layout(bar, 800);
            List<Node> shown = separatorContents(bar);
            assertEquals(2, shown.size());
            assertTrue(made.containsAll(shown) && shown.get(0) != shown.get(1), "one node each");
            assertTrue(shown.get(0).getParent().isMouseTransparent());

            bar.getItems().add("D");
            layout(bar, 800);
            assertEquals(3, separatorContents(bar).size());

            bar.setSeparatorFactory(() -> null);
            layout(bar, 800);
            assertEquals(List.of(Region.class, Region.class, Region.class), separatorContentTypes(bar));

            bar.setSeparatorFactory(null);
            layout(bar, 800);
            assertEquals(List.of(Region.class, Region.class, Region.class), separatorContentTypes(bar));
        });
    }

    // ==================== Branch menus ====================

    @Test
    public void siblingSwitchDropsTheOldSuffixAndDoesNotPickAChild() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setChildrenFactory(RXBreadcrumbBarTest::children);
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
            bar.setChildrenFactory(RXBreadcrumbBarTest::children);
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
            bar.setChildrenFactory(RXBreadcrumbBarTest::children);
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
    public void oneFactoryServesBothModes() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setChildrenFactory(RXBreadcrumbBarTest::children);
            layout(bar, 800);
            List<List<String>> requests = new ArrayList<>();
            bar.setOnNavigate(e -> requests.add(e.getPath()));

            // SIBLINGS: no alternative roots, the other two positions list their level.
            assertEquals(List.of(false, true, true), branchButtons(bar));
            branchButton(bar, 1).fire();
            RXMenuList siblings = openMenu();
            assertEquals(List.of("Buttons", "Data Display", "Inputs"), menuTexts(siblings));
            siblings.activate(entry(siblings, "Inputs"));

            bar.setBranchMode(BranchMode.CHILDREN);
            layout(bar, 800);
            // CHILDREN: the leaf has nothing below it.
            assertEquals(List.of(true, true, false), branchButtons(bar));
            branchButton(bar, 1).fire();
            RXMenuList children = openMenu();
            assertEquals(List.of("Badge", "Chip", "Avatar"), menuTexts(children));
            assertTrue(entry(children, "Badge").getStyleClass().contains("breadcrumb-current"));
            children.activate(entry(children, "Chip"));

            assertEquals(List.of(
                    List.of("Components", "Inputs"),
                    List.of("Components", "Data Display", "Chip")), requests);
        });
    }

    @Test
    public void rootAsksAboutTheEmptyPathInSiblingsMode() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("C:", "Users"), 800);
            List<List<String>> asked = new ArrayList<>();
            bar.setChildrenFactory(path -> {
                asked.add(List.copyOf(path));
                return path.isEmpty() ? List.of("C:", "D:") : List.of("Users", "Windows");
            });
            layout(bar, 800);
            assertEquals(List.of(List.of()), asked, "a rebuild asks only about the root's parent");
            List<List<String>> requests = new ArrayList<>();
            bar.setOnNavigate(e -> requests.add(e.getPath()));

            branchButton(bar, 0).fire();
            RXMenuList menu = openMenu();
            assertEquals(List.of("C:", "D:"), menuTexts(menu));
            menu.activate(entry(menu, "D:"));
            assertEquals(List.of(List.of("D:")), requests);
        });
    }

    @Test
    public void menuEntriesAreReadWhenTheMenuOpens() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Components", "Buttons"), 800);
            List<String> level = new ArrayList<>(List.of("Buttons", "Inputs"));
            List<List<String>> asked = new ArrayList<>();
            bar.setChildrenFactory(path -> {
                asked.add(List.copyOf(path));
                return path.isEmpty() ? List.of() : level;
            });
            layout(bar, 800);
            asked.clear();

            level.add("Navigation");
            branchButton(bar, 1).fire();
            assertEquals(List.of(List.of("Components")), asked);
            assertEquals(List.of("Buttons", "Inputs", "Navigation"), menuTexts(openMenu()));
        });
    }

    @Test
    public void emptyResultAtOpenShowsNoMenu() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Components", "Buttons"), 800);
            bar.setChildrenFactory(path -> List.of());
            layout(bar, 800);
            branchButton(bar, 1).fire();
            assertTrue(openMenus().isEmpty());
        });
    }

    @Test
    public void branchFilterDecidesArrowsByPathInBothModes() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Home", "Orders", "#12345"), 800);
            List<List<String>> asked = new ArrayList<>();
            // The sections under Home are worth a menu; the orders themselves are not.
            bar.setBranchFilter(path -> path.equals(List.of("Home")));
            bar.setChildrenFactory(path -> {
                asked.add(List.copyOf(path));
                return List.of("Orders", "Customers");
            });
            layout(bar, 800);
            assertEquals(List.of(false, true, false), branchButtons(bar));

            bar.setBranchMode(BranchMode.CHILDREN);
            layout(bar, 800);
            assertEquals(List.of(true, false, false), branchButtons(bar));
            assertTrue(asked.isEmpty(), "with a filter a rebuild never calls the factory");

            bar.setBranchFilter(null);
            layout(bar, 800);
            assertEquals(List.of(true, true, true), branchButtons(bar));
        });
    }

    @Test
    public void pathChangeWhileAMenuIsOpenClosesIt() throws InterruptedException {
        AtomicReference<RXBreadcrumbBar<String>> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setChildrenFactory(RXBreadcrumbBarTest::children);
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
            bar.setPrefWidth(Region.USE_COMPUTED_SIZE);
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
    public void buttonsLeftBehindByADisposedSkinAreInert() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Inputs", "Buttons", "Badge"), 1200);
            bar.setChildrenFactory(prefix -> List.of("Badge", "Chip"));
            bar.setMaxVisibleItems(2);
            layout(bar, 1200);
            List<RXBreadcrumbEvent<String>> events = new ArrayList<>();
            bar.setOnNavigate(events::add);
            Button oldLink = link(bar, 0);
            Button oldArrow = branchButton(bar, 4);
            Button oldOverflow = overflow(bar);

            // SkinBase does not clear the children, so the old buttons stay in the scene.
            bar.setSkin(new SkinBase<RXBreadcrumbBar<String>>(bar) {
            });
            assertTrue(bar.getChildrenUnmodifiable().contains(oldArrow));

            oldLink.fire();
            oldArrow.fire();
            oldArrow.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false));
            oldOverflow.fire();
            assertTrue(events.isEmpty(), "no request from a disposed skin");
            assertTrue(openMenus().isEmpty(), "no menu from a disposed skin");
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
                RXBreadcrumbBarSkin<String> oldSkin = (RXBreadcrumbBarSkin<String>) bar.getSkin();
                // A distinct class: JavaFX ignores a new skin of the same class.
                bar.setSkin(new RXBreadcrumbBarSkin<>(bar) {
                });
                assertTrue(bar.getSkin() != oldSkin && oldSkin.getSkinnable() == null, "the old skin is disposed");
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

    @Test
    public void skinSwapKeepsFocusInsideAndClosesTheOldMenu() throws InterruptedException {
        AtomicReference<RXBreadcrumbBar<String>> ref = new AtomicReference<>();
        List<Throwable> errors = new ArrayList<>();
        AtomicReference<Thread.UncaughtExceptionHandler> previous = new AtomicReference<>();
        runOnFx(() -> {
            previous.set(Thread.currentThread().getUncaughtExceptionHandler());
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setChildrenFactory(RXBreadcrumbBarTest::children);
            layout(bar, 800);
            link(bar, 1).requestFocus();
            branchButton(bar, 2).fire();
            assertNotNull(openMenu());
            bar.setSkin(new RXBreadcrumbBarSkin<>(bar) {
            });
            assertTrue(openMenus().isEmpty(), "the old skin's menu closes with it");
            ref.set(bar);
        });
        Thread.sleep(300);
        runOnFx(() -> {
            try {
                RXBreadcrumbBar<String> bar = ref.get();
                Node owner = bar.getScene().getFocusOwner();
                assertTrue(errors.isEmpty(), "no listener of the old skin may fail: " + errors);
                assertTrue(owner != null && owner.getParent() == bar, "focus stays in the bar: " + owner);
                assertEquals("Data Display", ((Labeled) owner).getText());
            } finally {
                Thread.currentThread().setUncaughtExceptionHandler(previous.get());
            }
        });
    }

    @Test
    public void downKeyOpensABranchMenu() throws InterruptedException {
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            bar.setChildrenFactory(RXBreadcrumbBarTest::children);
            layout(bar, 800);
            Button arrow = branchButton(bar, 2);
            arrow.requestFocus();
            arrow.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false));
            assertNotNull(openMenu());
            assertTrue(arrow.getPseudoClassStates().contains(PseudoClass.getPseudoClass("showing")));
        });
    }

    @Test
    public void focusedItemThatCollapsesHandsFocusToTheOverflowButton() throws InterruptedException {
        AtomicReference<RXBreadcrumbBar<String>> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>(
                    "Root", "Alpha Alpha", "Beta Beta", "Gamma Gamma", "Delta Delta", "Current"), 1200);
            link(bar, 2).requestFocus();
            layout(bar, bar.minWidth(-1));
            assertFalse(link(bar, 2).isVisible());
            ref.set(bar);
        });
        Thread.sleep(200);
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = ref.get();
            assertEquals(overflow(bar), bar.getScene().getFocusOwner());
            // A width that keeps the root, so the overflow hides [1, k).
            bar.setPrefWidth(Region.USE_COMPUTED_SIZE);
            layout(bar, bar.prefWidth(-1) * 0.6);
            assertTrue(link(bar, 0).isVisible() && !link(bar, 1).isVisible());
            overflow(bar).requestFocus();
            layout(bar, 1200);
        });
        Thread.sleep(200);
        runOnFx(() -> assertEquals("Alpha Alpha",
                ((Labeled) ref.get().getScene().getFocusOwner()).getText(),
                "a vanished overflow button hands focus to the first item it hid"));
    }

    @Test
    public void focusRingIsHeldBackAfterAPointerPressUntilAKeyPress() throws InterruptedException {
        runOnFx(() -> {
            PseudoClass shown = PseudoClass.getPseudoClass("focus-shown");
            RXBreadcrumbBar<String> bar = show(new RXBreadcrumbBar<>("Home", "Library", "Data"), 800);
            Button home = link(bar, 0);
            home.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, true, false, false, null));
            stage.requestFocus();
            home.requestFocus();
            // Node.focused needs a focused window, which a test run cannot always get.
            assumeTrue(home.isFocused(), "the test window did not receive focus");
            assertFalse(home.getPseudoClassStates().contains(shown), "pointer focus: no ring");

            // Not an arrow key: the scene would traverse focus away on one.
            home.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, false, false, false));
            assertTrue(home.getPseudoClassStates().contains(shown), "a key press brings the ring back");

            Button library = link(bar, 1);
            library.requestFocus();
            assertTrue(library.getPseudoClassStates().contains(shown), "keyboard focus shows the ring");
        });
    }

    @Test
    public void focusMovedByAPathCommitKeepsThePointerFocusRingHidden() throws InterruptedException {
        PseudoClass shown = PseudoClass.getPseudoClass("focus-shown");
        AtomicReference<RXBreadcrumbBar<String>> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            Button middle = link(bar, 1);
            middle.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, true, false, false, null));
            stage.requestFocus();
            middle.requestFocus();
            assumeTrue(middle.isFocused(), "the test window did not receive focus");
            // Committed from an animation callback, as a delayed application commit is:
            // the scene then drops the removed button's focus before the bar restores it.
            new AnimationTimer() {
                @Override
                public void handle(long now) {
                    stop();
                    bar.getItems().setAll("Components", "Data Display");
                }
            }.start();
            ref.set(bar);
        });
        Thread.sleep(300);
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = ref.get();
            Node owner = bar.getScene().getFocusOwner();
            assertEquals(link(bar, 0), owner, "focus falls back to the nearest ancestor");
            assertFalse(owner.getPseudoClassStates().contains(shown), "still pointer focus: no ring");
            owner.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, false, false, false));
            assertTrue(owner.getPseudoClassStates().contains(shown), "a key press brings the ring back");
        });
    }

    @Test
    public void delayedCommitHandsFocusStraightToTheFallbackItem() throws InterruptedException {
        AtomicReference<RXBreadcrumbBar<String>> ref = new AtomicReference<>();
        List<Node> owners = new ArrayList<>();
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = show(
                    new RXBreadcrumbBar<>("Components", "Data Display", "Badge"), 800);
            // First in the scene's traversal order: where a dropped focus would land.
            Button outside = new Button("Outside");
            outside.setLayoutY(60);
            ((Pane) bar.getParent()).getChildren().add(0, outside);
            link(bar, 1).requestFocus();
            assertEquals(link(bar, 1), bar.getScene().getFocusOwner());
            bar.getScene().focusOwnerProperty().addListener((obs, old, owner) -> owners.add(owner));
            new AnimationTimer() {
                @Override
                public void handle(long now) {
                    stop();
                    bar.getItems().setAll("Components", "Data Display");
                }
            }.start();
            ref.set(bar);
        });
        Thread.sleep(300);
        runOnFx(() -> {
            RXBreadcrumbBar<String> bar = ref.get();
            assertEquals(List.of(link(bar, 0)), owners, "focus goes to the ancestor and nowhere else");
        });
    }

    // ==================== Helpers ====================

    private static List<String> children(List<String> path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return CATALOG.getOrDefault(path.get(path.size() - 1), List.of());
    }

    // Whether each path position has a branch button, in path order.
    private static List<Boolean> branchButtons(RXBreadcrumbBar<?> bar) {
        List<Boolean> result = new ArrayList<>();
        for (int i = 0; i < bar.getItems().size(); i++) {
            result.add(hasBranchButton(bar, i));
        }
        return result;
    }

    private static boolean hasBranchButton(RXBreadcrumbBar<?> bar, int index) {
        List<Node> children = bar.getChildrenUnmodifiable();
        int seen = -1;
        for (Node child : children) {
            if (child.getStyleClass().contains("item")) {
                seen++;
            } else if (seen == index && child.getStyleClass().contains("branch-button")) {
                return true;
            }
        }
        return false;
    }

    private <T> RXBreadcrumbBar<T> show(RXBreadcrumbBar<T> bar, double width) {
        Pane root = new Pane(bar);
        stage = new Stage();
        stage.setScene(new Scene(root, 1300, 120));
        stage.show();
        layout(bar, width);
        return bar;
    }

    // The width is also the preferred width, so the Pane root keeps it on later pulses.
    private static void layout(RXBreadcrumbBar<?> bar, double width) {
        bar.setPrefWidth(width);
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

    // The node inside each visible separator, in path order.
    private static List<Node> separatorContents(RXBreadcrumbBar<?> bar) {
        return bar.getChildrenUnmodifiable().stream()
                .filter(n -> n.getStyleClass().contains("separator") && n.isVisible())
                .map(n -> ((Parent) n).getChildrenUnmodifiable().get(0))
                .collect(Collectors.toList());
    }

    private static List<Class<?>> separatorContentTypes(RXBreadcrumbBar<?> bar) {
        return separatorContents(bar).stream().map(Node::getClass).collect(Collectors.toList());
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
        if (t instanceof TestAbortedException) {
            throw (TestAbortedException) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}
