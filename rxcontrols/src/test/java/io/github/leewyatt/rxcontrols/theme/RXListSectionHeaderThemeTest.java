package io.github.leewyatt.rxcontrols.theme;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Theme test for collapsible {@link RXListView} section headers: under Modena and
 * every AtlantaFX theme a hovered header and the header holding the keyboard cursor
 * (in-flow and sticky) each paint differently from a resting header, and the header
 * rules log no CSS warning.
 */
public class RXListSectionHeaderThemeTest {

    /** Held strongly: the log manager keeps loggers weakly, and a fresh one would drop the handler. */
    private static final Logger CSS_LOGGER = Logger.getLogger("javafx.css");

    private static final List<Theme> THEMES = List.of(new PrimerLight(), new PrimerDark(), new NordLight(),
            new NordDark(), new CupertinoLight(), new CupertinoDark(), new Dracula());

    @BeforeAll
    public static void setup() throws InterruptedException {
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

    @AfterAll
    public static void restore() {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    @Test
    public void hoverAndCursorStandOutUnderEveryTheme() throws Exception {
        List<String> problems = new ArrayList<>();
        List<String> warnings = captureCssWarnings(() -> {
            check("Modena", Application.STYLESHEET_MODENA, false, problems);
            for (Theme theme : THEMES) {
                check(theme.getName(), theme.getUserAgentStylesheet(), true, problems);
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(warnings.isEmpty(), "CSS warnings: " + warnings);
    }

    // A focused, collapsible list with three two-item sections: section 0's header is
    // the sticky one, sections 1 and 2 are in-flow. First section 1 is hovered and the
    // keyboard cursor rests on section 2's in-flow header; then the cursor moves to the
    // sticky header. Rest, hover and both cursor looks must all tell apart.
    private static void check(String name, String stylesheet, boolean atlantafx, List<String> problems) {
        Application.setUserAgentStylesheet(stylesheet);
        ObservableList<Integer> data = FXCollections.observableArrayList();
        for (int i = 0; i < 6; i++) {
            data.add(i);
        }
        FocusableList list = new FocusableList(data);
        list.setSectionKeyFactory(i -> i / 2);
        list.setCollapsibleSections(true);
        list.setSectionHeaderFactory(view -> new HoverableHeader());
        StackPane host = new StackPane(list);
        Scene scene = new Scene(host, 300, 400);
        if (atlantafx) {
            RXAtlantaFXThemeBridge.install(scene);
        }
        list.showFocus();
        pump(host);
        Look rest = look(header(list, 0));
        header(list, 1).hover(true);
        moveCursorToHeaderOf(list, 4);
        pump(host);
        Look hover = look(header(list, 1));
        Look cursor = look(header(list, 2));
        moveCursorToHeaderOf(list, 0);
        pump(host);
        HoverableHeader sticky = header(list, 0);
        if (!sticky.getStyleClass().contains("sticky")) {
            problems.add(name + ": precondition — section 0's header is not the sticky one");
        }
        Look stickyCursor = look(sticky);
        if (!rest.distinctFrom(hover)) {
            problems.add(name + ": hover " + hover + " looks like rest " + rest);
        }
        if (!rest.distinctFrom(cursor) || !hover.distinctFrom(cursor)) {
            problems.add(name + ": cursor " + cursor + " is not distinct (rest " + rest + ", hover " + hover + ")");
        }
        if (!rest.distinctFrom(stickyCursor)) {
            problems.add(name + ": sticky cursor " + stickyCursor + " looks like rest " + rest);
        }
    }

    // The real keyboard cursor: press an item, then Left moves the cursor to its header.
    private static void moveCursorToHeaderOf(RXListView<?> list, int itemIndex) {
        Node cell = null;
        for (Node node : list.lookupAll(".rx-list-cell")) {
            if (node instanceof RXListCell<?> candidate && candidate.isVisible() && candidate.getIndex() == itemIndex) {
                cell = candidate;
            }
        }
        if (cell == null) {
            throw new AssertionError("item " + itemIndex + " is not realized");
        }
        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, new PickResult(cell, 5, 5)));
        list.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.LEFT, false, false, false, false));
    }

    private static void pump(StackPane host) {
        for (int pass = 0; pass < 3; pass++) {
            host.applyCss();
            host.layout();
        }
    }

    // What a header shows: the color seen in the middle of the row (all layers
    // composited) and whether it draws a ring (an inset layer, like Modena's focus ring).
    private record Look(Color center, boolean ring) {

        // Below this per-channel difference two colors read as the same.
        private static final double MIN_DIFFERENCE = 0.03;

        boolean distinctFrom(Look other) {
            if (ring != other.ring) {
                return true;
            }
            double difference = Math.max(Math.abs(center.getRed() - other.center.getRed()),
                    Math.max(Math.abs(center.getGreen() - other.center.getGreen()),
                            Math.abs(center.getBlue() - other.center.getBlue())));
            return difference > MIN_DIFFERENCE;
        }
    }

    private static HoverableHeader header(RXListView<?> list, int sectionIndex) {
        for (Node node : list.lookupAll(".rx-list-section-header")) {
            if (node instanceof HoverableHeader header && header.getItem() != null
                    && header.getItem().sectionIndex() == sectionIndex) {
                return header;
            }
        }
        return null;
    }

    private static Look look(HoverableHeader header) {
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        boolean ring = false;
        Background background = header == null ? null : header.getBackground();
        if (background != null) {
            for (BackgroundFill fill : background.getFills()) {
                ring |= !Insets.EMPTY.equals(fill.getInsets());
                if (fill.getFill() instanceof Color color) {
                    double alpha = color.getOpacity();
                    red = color.getRed() * alpha + red * (1.0 - alpha);
                    green = color.getGreen() * alpha + green * (1.0 - alpha);
                    blue = color.getBlue() * alpha + blue * (1.0 - alpha);
                }
            }
        }
        return new Look(Color.color(red, green, blue), ring);
    }

    private static List<String> captureCssWarnings(Runnable fxBody) throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    messages.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        CSS_LOGGER.addHandler(handler);
        try {
            runOnFx(fxBody);
        } finally {
            CSS_LOGGER.removeHandler(handler);
        }
        return messages;
    }

    private static void runOnFx(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof AssertionError assertion) {
            throw assertion;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    /** A header whose hover state the test can set (Node.setHover is protected). */
    private static final class HoverableHeader extends RXListSectionCell {
        void hover(boolean on) {
            setHover(on);
        }

        @Override
        protected void updateItem(RXListSection section, boolean empty) {
            super.updateItem(section, empty);
            if (!empty && section != null) {
                setText(String.valueOf(section.key()));
            }
        }
    }

    /** A list that shows the focused state without a focused window. */
    private static final class FocusableList extends RXListView<Integer> {
        FocusableList(ObservableList<Integer> items) {
            super(items);
        }

        void showFocus() {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), true);
        }
    }
}
