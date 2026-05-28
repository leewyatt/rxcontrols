package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight verification runner for {@link RXBox}.
 */
public final class RXBoxVerification {

    private RXBoxVerification() {
    }

    public static void main(String[] args) {
        List<Result> results = new ArrayList<>();
        runAll(results);
        printSummary(results);
        System.exit(anyFailed(results) ? 1 : 0);
    }

    // ==================== Test cases ====================

    private static void runAll(List<Result> results) {
        check(results, "default state and constraints", () -> {
            FixedRegion first = fixedRegion(30.0, 10.0);
            FixedRegion second = fixedRegion(40.0, 20.0);
            RXBox box = new RXBox(first, second);

            assertSame(Orientation.HORIZONTAL, box.getOrientation(), "default orientation");
            assertTrue(box.getStyleClass().contains("rx-box"), "style class missing");
            assertSame(first, box.getChildren().get(0), "first child");
            assertSame(second, box.getChildren().get(1), "second child");

            Insets margin = new Insets(1.0, 2.0, 3.0, 4.0);
            RXBox.setGrow(first, Priority.ALWAYS);
            RXBox.setMargin(first, margin);
            assertSame(Priority.ALWAYS, RXBox.getGrow(first), "grow");
            assertEquals(margin, RXBox.getMargin(first), "margin");

            RXBox.clearConstraints(first);
            assertSame(null, RXBox.getGrow(first), "cleared grow");
            assertSame(null, RXBox.getMargin(first), "cleared margin");
        });

        check(results, "horizontal layout uses spacing and fills height", () -> {
            FixedRegion first = fixedRegion(30.0, 10.0);
            FixedRegion second = fixedRegion(40.0, 20.0);
            RXBox box = new RXBox(Orientation.HORIZONTAL, 10.0, first, second);

            box.resize(200.0, 50.0);
            box.layout();

            assertClose(0.0, first.getLayoutX(), "first x");
            assertClose(0.0, first.getLayoutY(), "first y");
            assertClose(30.0, first.getWidth(), "first width");
            assertClose(50.0, first.getHeight(), "first height");
            assertClose(40.0, second.getLayoutX(), "second x");
            assertClose(0.0, second.getLayoutY(), "second y");
            assertClose(40.0, second.getWidth(), "second width");
            assertClose(50.0, second.getHeight(), "second height");
        });

        check(results, "vertical layout uses spacing and fills width", () -> {
            FixedRegion first = fixedRegion(30.0, 10.0);
            FixedRegion second = fixedRegion(40.0, 20.0);
            RXBox box = new RXBox(Orientation.VERTICAL, 5.0, first, second);

            box.resize(100.0, 100.0);
            box.layout();

            assertClose(0.0, first.getLayoutX(), "first x");
            assertClose(0.0, first.getLayoutY(), "first y");
            assertClose(100.0, first.getWidth(), "first width");
            assertClose(10.0, first.getHeight(), "first height");
            assertClose(0.0, second.getLayoutX(), "second x");
            assertClose(15.0, second.getLayoutY(), "second y");
            assertClose(100.0, second.getWidth(), "second width");
            assertClose(20.0, second.getHeight(), "second height");
        });

        check(results, "grow consumes extra main-axis space", () -> {
            FixedRegion first = fixedRegion(30.0, 10.0);
            FixedRegion second = fixedRegion(20.0, 10.0);
            RXBox box = new RXBox(first, second);

            RXBox.setGrow(first, Priority.ALWAYS);
            box.resize(100.0, 20.0);
            box.layout();

            assertClose(80.0, first.getWidth(), "grown width");
            assertClose(80.0, second.getLayoutX(), "second x");
            assertClose(20.0, second.getWidth(), "second width");
        });

        check(results, "orientation switch does not reparent child", () -> {
            FixedRegion child = fixedRegion(30.0, 10.0);
            RXBox box = new RXBox(child);

            box.setOrientation(Orientation.VERTICAL);

            assertSame(box, child.getParent(), "parent");
            assertSame(Orientation.VERTICAL, box.getOrientation(), "orientation");
        });

        check(results, "negative spacing overlaps managed children", () -> {
            FixedRegion first = fixedRegion(30.0, 10.0);
            FixedRegion second = fixedRegion(20.0, 10.0);
            RXBox box = new RXBox(Orientation.HORIZONTAL, -10.0, first, second);

            box.resize(100.0, 20.0);
            box.layout();

            assertClose(20.0, second.getLayoutX(), "second x");
        });

        check(results, "unmanaged children do not affect preferred width", () -> {
            FixedRegion first = fixedRegion(30.0, 10.0);
            FixedRegion second = fixedRegion(40.0, 20.0);
            second.setManaged(false);
            RXBox box = new RXBox(Orientation.HORIZONTAL, 10.0, first, second);

            assertClose(30.0, box.prefWidth(-1), "pref width");
        });

        check(results, "CSS metadata contains RXBox properties", () -> {
            assertTrue(hasCssProperty("-rx-orientation"), "orientation metadata");
            assertTrue(hasCssProperty("-rx-spacing"), "spacing metadata");
            assertTrue(hasCssProperty("-rx-alignment"), "alignment metadata");
            assertTrue(hasCssProperty("-rx-fill-cross-axis"), "fill metadata");
        });
    }

    // ==================== Assertions ====================

    private static void check(List<Result> results, String name, ThrowingRunnable runnable) {
        try {
            runnable.run();
            results.add(new Result(name, null));
        } catch (Throwable t) {
            results.add(new Result(name, t.getMessage()));
        }
    }

    private static void assertSame(Object expected, Object actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected same " + expected + ", actual " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", actual " + actual);
        }
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(label + ": expected " + expected + ", actual " + actual);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    // ==================== Helpers ====================

    private static FixedRegion fixedRegion(double prefWidth, double prefHeight) {
        FixedRegion region = new FixedRegion();
        region.setMinSize(0.0, 0.0);
        region.setPrefSize(prefWidth, prefHeight);
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return region;
    }

    private static boolean hasCssProperty(String property) {
        return RXBox.getClassCssMetaData().stream()
                .anyMatch(cssMetaData -> property.equals(cssMetaData.getProperty()));
    }

    private static void printSummary(List<Result> results) {
        long passed = results.stream().filter(Result::passed).count();
        for (Result result : results) {
            if (result.passed()) {
                System.out.println("PASS " + result.name());
            } else {
                System.out.println("FAIL " + result.name() + " - " + result.failure());
            }
        }
        System.out.println(passed + " / " + results.size() + " checks passed");
    }

    private static boolean anyFailed(List<Result> results) {
        return results.stream().anyMatch(result -> !result.passed());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record Result(String name, String failure) {
        private boolean passed() {
            return failure == null;
        }
    }

    private static final class FixedRegion extends Region {
    }
}
