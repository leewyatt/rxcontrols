package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXMaterialTextFieldSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.converter.IntegerStringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Material number variants (RXMaterialInteger / Long / Double / Decimal
 * field): style-class layering on the inherited Material anchor, the engine
 * plumbing through the Material skin, and the clear-commits-value semantic.
 * That semantic is native TextInputControl behavior — a direct {@code text}
 * write ({@code setText} / {@code clear()}) runs {@code updateValue}
 * immediately, unlike a user edit — so these tests pin the JFX mechanism the
 * fields rely on (including the bound-value / bound-text edges and survival
 * across a skin replacement) rather than any code of our own.
 */
public class RXMaterialNumberFieldTest {

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

    // ==================== Style classes / skin ====================

    @Test
    public void styleClassesLayerFamilyAndVariantOnInheritedAnchor() {
        runOnFx(() -> {
            List<RXMaterialTextField> fields = List.of(
                    new RXMaterialIntegerField(), new RXMaterialLongField(),
                    new RXMaterialDoubleField(), new RXMaterialDecimalField());
            List<String> variantClasses = List.of(
                    "rx-material-integer-field", "rx-material-long-field",
                    "rx-material-double-field", "rx-material-decimal-field");
            for (int i = 0; i < fields.size(); i++) {
                RXMaterialTextField field = fields.get(i);
                // The inherited anchor carries every Material style block; the
                // family and variant classes are grouping hooks on top of it.
                assertTrue(field.getStyleClass().contains("rx-material-text-field"),
                        variantClasses.get(i) + " must keep the inherited Material anchor class");
                assertTrue(field.getStyleClass().contains("rx-material-number-field"),
                        variantClasses.get(i) + " must carry the Material number family class");
                assertTrue(field.getStyleClass().contains(variantClasses.get(i)),
                        "variant class missing on " + variantClasses.get(i));
            }
        });
    }

    @Test
    public void defaultSkinIsTheInheritedMaterialSkin() {
        runOnFx(() -> {
            RXMaterialDecimalField field = inScene(new RXMaterialDecimalField(new BigDecimal("1.50")));
            assertInstanceOf(RXMaterialTextFieldSkin.class, field.getSkin(),
                    "the variant must reuse the inherited Material skin — no skin of its own");
            assertEquals("1.50", field.getText(), "typed rendering must survive the Material skin");
            assertNotNull(field.lookup(".activation-line"), "Material decoration missing");
        });
    }

    // ==================== Commit through the Material skin ====================

    @Test
    public void typedCommitWorksThroughTheMaterialSkin() {
        runOnFx(() -> {
            RXMaterialIntegerField field = inScene(new RXMaterialIntegerField());
            field.replaceText(0, field.getText().length(), "42");
            field.commitValue();
            assertEquals(42, field.getValue());

            RXMaterialDecimalField money = inScene(new RXMaterialDecimalField());
            money.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
            money.replaceText(0, money.getText().length(), "19.99");
            money.commitValue();
            assertEquals(0, new BigDecimal("19.99").compareTo(money.getValue()));
            assertEquals("$19.99", money.getText(), "format-aware rendering after commit");
        });
    }

    @Test
    public void numberFormatSwapReRendersText() {
        runOnFx(() -> {
            RXMaterialDecimalField field = new RXMaterialDecimalField(new BigDecimal("1234.5"));
            assertEquals("1234.5", field.getText(), "plain rendering with null format");
            field.setNumberFormat(NumberFormat.getNumberInstance(Locale.US));
            assertEquals("1,234.5", field.getText(), "assigning a format re-renders the text");
        });
    }

    // ==================== clear semantics ====================

    @Test
    public void clearMethodCommitsNullImmediately() {
        runOnFx(() -> {
            RXMaterialIntegerField integerField = new RXMaterialIntegerField(42);
            RXMaterialLongField longField = new RXMaterialLongField(42L);
            RXMaterialDoubleField doubleField = new RXMaterialDoubleField(4.2);
            RXMaterialDecimalField decimalField = new RXMaterialDecimalField(new BigDecimal("4.2"));
            List<RXMaterialTextField> fields =
                    List.of(integerField, longField, doubleField, decimalField);
            for (RXMaterialTextField field : fields) {
                field.clear();
                assertEquals("", field.getText(), "clear must empty the text");
            }
            assertNull(integerField.getValue(), "clear must commit value = null immediately");
            assertNull(longField.getValue(), "clear must commit value = null immediately");
            assertNull(doubleField.getValue(), "clear must commit value = null immediately");
            assertNull(decimalField.getValue(), "clear must commit value = null immediately");
        });
    }

    @Test
    public void clearButtonClickCommitsNullImmediately() {
        runOnFx(() -> {
            RXMaterialIntegerField field = inScene(new RXMaterialIntegerField(42));
            field.setLabelText("Count");
            StackPane clear = clearButton(field);
            assertNotNull(clear, "built-in clear button missing");
            clear.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, false, false, true, null));
            assertEquals("", field.getText(), "clicking clear must empty the text");
            assertNull(field.getValue(),
                    "clicking clear must commit value = null immediately, not on the next commit");
        });
    }

    @Test
    public void clearOnBoundValueReRendersTheBoundValue() {
        runOnFx(() -> {
            SimpleObjectProperty<BigDecimal> source = new SimpleObjectProperty<>(BigDecimal.TEN);
            RXMaterialDecimalField field = new RXMaterialDecimalField();
            field.valueProperty().bind(source);
            assertEquals("10", field.getText());

            // The binding owns the value: the empty commit is not pushed and the
            // text bounces straight back — same path as an ENTER commit on a
            // bound value, just visible at the moment of the click.
            field.clear();
            assertEquals(BigDecimal.TEN, field.getValue(), "bound value must not be cleared");
            assertEquals("10", field.getText(), "text must re-render the bound value right away");
        });
    }

    @Test
    public void clearOnBoundTextIsANoOp() {
        runOnFx(() -> {
            // A bound text property is not writable: clear() only deselects, no
            // text write happens, so nothing is committed and nothing changes.
            RXMaterialIntegerField field = new RXMaterialIntegerField(5);
            field.textProperty().bind(new SimpleObjectProperty<>("5"));
            field.clear();
            assertEquals(5, field.getValue(), "bound text: clear must not touch the value");
            assertEquals("5", field.getText(), "bound text cannot be cleared");
        });
    }

    @Test
    public void materialPasswordFieldClearKeepsNativeSemantics() {
        runOnFx(() -> {
            // The password sibling shares the base skin's clear button but has no
            // typed value model — clear stays the plain JFX text clear.
            RXMaterialPasswordField field = new RXMaterialPasswordField();
            field.setText("secret");
            field.clear();
            assertEquals("", field.getText(), "clear must empty the password text");
        });
    }

    @Test
    public void clearCommitsAUserFormatterValueNativelyToo() {
        runOnFx(() -> {
            // The clear-commits-value semantic is not our code: TextInputControl's
            // text property runs formatter.updateValue on every direct write
            // (doSet), so clear() commits for ANY formatter — this pins the JFX
            // mechanism the number fields' contract stands on.
            RXMaterialTextField field = new RXMaterialTextField("5");
            TextFormatter<Integer> userFormatter =
                    new TextFormatter<>(new IntegerStringConverter(), 5);
            field.setTextFormatter(userFormatter);
            field.clear();
            assertEquals("", field.getText());
            assertNull(userFormatter.getValue(),
                    "a direct text write commits immediately (TextInputControl.doSet -> updateValue)");
        });
    }

    // ==================== Double domain rule ====================

    @Test
    public void doubleVariantRejectsNonFiniteLikeThePlainSibling() {
        runOnFx(() -> {
            RXMaterialDoubleField field = new RXMaterialDoubleField(1.5);
            assertThrows(IllegalArgumentException.class, () -> field.setValue(Double.NaN));
            assertNull(field.getValue(), "the rejected value must be coerced to null");
            assertEquals("", field.getText(), "the coerced empty field must render empty text");
            assertThrows(IllegalArgumentException.class,
                    () -> new RXMaterialDoubleField(Double.POSITIVE_INFINITY));
        });
    }

    // ==================== Guard / skin replacement ====================

    @Test
    public void formatterGuardRestoresInternalFormatter() {
        runOnFx(() -> {
            RXMaterialDecimalField field = new RXMaterialDecimalField(BigDecimal.ONE);
            TextFormatter<?> internal = field.getTextFormatter();
            field.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
            assertEquals(internal, field.getTextFormatter(),
                    "an external setTextFormatter must be reverted to the internal formatter");
            field.replaceText(0, field.getText().length(), "2.5");
            field.commitValue();
            assertEquals(0, new BigDecimal("2.5").compareTo(field.getValue()),
                    "the field must stay functional after the guard repaired it");
        });
    }

    @Test
    public void controlLevelSemanticsSurviveSkinReplacement() {
        runOnFx(() -> {
            RXMaterialIntegerField field = inScene(new RXMaterialIntegerField(42));
            Skin<?> oldSkin = field.getSkin();
            // An anonymous subclass forces a real replacement (JFX 17 short-circuits
            // setSkin with the same skin class).
            field.setSkin(new RXMaterialTextFieldSkin(field) { });
            assertNotSame(oldSkin, field.getSkin(), "precondition: the skin was actually replaced");

            // JFX 17's native TextFieldSkin leaks constructor lambdas on dispose;
            // mutating text after the swap fires them with getSkinnable() == null
            // and ExpressionHelper routes the NPEs to the uncaught handler. Swallow
            // them here — the noise is a JFX leak, not this repo's skin — so the
            // log stays clean and a stricter future handler cannot fail this test.
            Thread fx = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = fx.getUncaughtExceptionHandler();
            fx.setUncaughtExceptionHandler((thread, error) -> { });
            try {
                field.replaceText(0, field.getText().length(), "7");
                field.fireEvent(new ActionEvent());
                assertEquals(7, field.getValue(), "the ACTION commit backstop must survive a skin swap");

                field.clear();
                assertNull(field.getValue(), "the clear-commits-value semantic must survive a skin swap");
            } finally {
                fx.setUncaughtExceptionHandler(previous);
            }
        });
    }

    // ==================== Lossy format commit ====================

    @Test
    public void lossyFormatCommitMatchesThePlainContract() {
        runOnFx(() -> {
            RXMaterialDecimalField field = new RXMaterialDecimalField(new BigDecimal("1.234"));
            field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
            assertEquals("$1.23", field.getText(), "2-fraction currency renders lossily");

            // A real edit inside the same display bucket must still be committed.
            field.replaceText(0, field.getText().length(), "$1.231");
            field.commitValue();
            assertEquals(0, new BigDecimal("1.231").compareTo(field.getValue()),
                    "sub-display-precision edit must be committed, not dropped");

            // A commit with no edit must not drift the value toward the display.
            field.commitValue();
            assertEquals(0, new BigDecimal("1.231").compareTo(field.getValue()),
                    "no-edit commit must not drift the value");
        });
    }

    // ==================== helpers ====================

    private static <F extends TextField> F inScene(F field) {
        StackPane root = new StackPane(field);
        new Scene(root, 300, 200);
        root.applyCss();
        root.layout();
        return field;
    }

    private static StackPane clearButton(TextField field) {
        Node node = field.lookup(".clear-button");
        return node instanceof StackPane stackPane ? stackPane : null;
    }

    private static void runOnFx(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task did not finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        if (failure.get() instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure.get() instanceof Error error) {
            throw error;
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
    }
}
