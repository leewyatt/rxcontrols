package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FXML smoke test for the typed number fields: {@code value} / {@code min} /
 * {@code max} attribute strings must coerce onto the exact property types
 * (Integer object value with primitive int bounds, Double with primitive
 * double bounds, BigDecimal throughout) — the FXML friendliness that motivated
 * the typed split.
 */
public class NumberFieldFxmlTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    private static <T> T onFx(Supplier<T> body) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(body.get());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task did not finish");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (err.get() != null) {
            throw new RuntimeException(err.get());
        }
        return out.get();
    }

    @Test
    public void fxmlAttributesLandOnTheExactTypes() {
        VBox root = onFx(() -> {
            String fxml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <?import io.github.leewyatt.rxcontrols.RXDecimalField?>
                    <?import io.github.leewyatt.rxcontrols.RXDoubleField?>
                    <?import io.github.leewyatt.rxcontrols.RXIntegerField?>
                    <?import javafx.scene.layout.VBox?>
                    <VBox xmlns="http://javafx.com/javafx/17">
                        <RXIntegerField value="42" min="0" max="100"/>
                        <RXDoubleField value="2.5" min="-1.5" max="9.75"/>
                        <RXDecimalField value="19.99" min="0.01" max="100.00"/>
                    </VBox>
                    """;
            try {
                FXMLLoader loader = new FXMLLoader();
                return loader.load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        RXIntegerField integerField = (RXIntegerField) root.getChildren().get(0);
        assertEquals(42, integerField.getValue(), "Integer value lands exactly");
        assertEquals(0, integerField.getMin());
        assertEquals(100, integerField.getMax());
        assertEquals("42", integerField.getText());

        RXDoubleField doubleField = (RXDoubleField) root.getChildren().get(1);
        assertEquals(2.5, doubleField.getValue(), "Double value lands exactly");
        assertEquals(-1.5, doubleField.getMin());
        assertEquals(9.75, doubleField.getMax());

        RXDecimalField decimalField = (RXDecimalField) root.getChildren().get(2);
        assertEquals(0, new BigDecimal("19.99").compareTo(decimalField.getValue()),
                "BigDecimal value lands via the exact String constructor");
        assertEquals(2, decimalField.getValue().scale(), "scale from the attribute string is preserved");
        assertEquals(0, new BigDecimal("0.01").compareTo(decimalField.getMin()));
        assertEquals(0, new BigDecimal("100.00").compareTo(decimalField.getMax()));
    }
}
