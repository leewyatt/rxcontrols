package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.samples.demo.RXSpeedDialDemo;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSpeedDialShowcase;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the sampler: the catalog, the navigation, and every page and variant.
 */
public class SamplerSmokeTest {

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
    }

    /** The home entry comes first and no two entries share a name. */
    @Test
    public void catalogStartsWithHomeAndHasUniqueNames() {
        List<SamplerEntry> entries = SamplerCatalog.entries();
        assertTrue(entries.get(0).isHome());
        Set<String> names = new HashSet<>();
        for (SamplerEntry entry : entries) {
            assertTrue(names.add(entry.name()), "duplicate entry " + entry.name());
            assertTrue(entry.isHome() || !entry.variants().isEmpty());
        }
    }

    /** A search finds a merged page by a member's type name and any page by its category. */
    @Test
    public void searchMatchesVariantTypeNamesAndCategory() {
        SamplerEntry week = SamplerCatalog.entries().stream()
                .filter(entry -> entry.name().equals("RXWeekRange")).findFirst().orElseThrow();
        assertTrue(week.matches("weekrangeview"));
        assertTrue(week.matches("date"));
        assertFalse(week.matches("monthpicker"));
    }

    /** The standalone Speed Dial demo and playground both ship their stylesheets. */
    @Test
    public void speedDialSampleStylesheetsArePackaged() {
        assertNotNull(RXSpeedDialDemo.class.getResource("rx-speed-dial-demo.css"));
        assertNotNull(RXSpeedDialShowcase.class.getResource("rx-speed-dial-showcase.css"));
    }

    /** The current entry selects its page and opens its group; picking a page sets the current entry. */
    @Test
    public void navSelectionAndCurrentEntryFollowEachOther() throws Exception {
        runOnFx(() -> {
            List<SamplerEntry> entries = SamplerCatalog.entries();
            SamplerNav nav = new SamplerNav(entries);
            SamplerEntry last = entries.get(entries.size() - 1);
            nav.currentProperty().set(last);
            RXSidebarItem selected = nav.getSelectedItem();
            assertSame(last, selected.getUserData());
            assertTrue(selected.getParent().isExpanded());

            nav.selectItem((RXSidebarItem) nav.getItems().get(0));
            assertSame(entries.get(0), nav.currentProperty().get());
        });
    }

    /** Collapsing to the rail clears the search, and with it the filter. */
    @Test
    public void collapsingNavClearsSearch() throws Exception {
        runOnFx(() -> {
            SamplerNav nav = new SamplerNav(SamplerCatalog.entries());
            mount(nav);
            TextField search = (TextField) nav.lookup(".rx-text-field");
            search.setText("week");
            assertNotNull(nav.getFilter());

            nav.setMode(RXSidebar.Mode.MINI);
            assertEquals("", search.getText());
            assertNull(nav.getFilter());
        });
    }

    /** Every page and every variant's playground build without error. */
    @Test
    public void everyPageAndVariantBuilds() throws Exception {
        List<String> failures = new ArrayList<>();
        List<String> cssWarnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    cssWarnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger cssLogger = Logger.getLogger("javafx.css");
        cssLogger.addHandler(handler);
        try {
            for (SamplerEntry entry : SamplerCatalog.entries()) {
                if (entry.isHome()) {
                    continue;
                }
                runOnFx(() -> {
                    try {
                        mount(new SamplerPage(entry));
                        for (SamplerVariant variant : entry.variants()) {
                            Node playground = variant.showcase() == null
                                    ? SamplerInstances.create(variant.type())
                                    : variant.showcase().get().createEmbeddedContent();
                            if (playground != null) {
                                mount(playground);
                            }
                        }
                    } catch (RuntimeException e) {
                        failures.add(entry.name() + ": " + e);
                    }
                });
            }
        } finally {
            cssLogger.removeHandler(handler);
        }
        assertEquals(List.of(), failures);
        cssWarnings.forEach(message -> System.out.println("CSS warning: " + message));
    }

    private static void mount(Node node) {
        StackPane root = new StackPane(node);
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().addAll(RXShowcaseApplication.shellStylesheet(),
                RXSampler.class.getResource("rx-sampler.css").toExternalForm());
        root.applyCss();
        root.layout();
        root.getChildren().clear();
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}
