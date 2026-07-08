package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleAttribute;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless unit tests for {@link RXMaterialPasswordField}: defaults / plumbing of
 * the password-specific properties (revealPassword, echoChar, showRevealButton),
 * the {@code :revealed} pseudo-class, the built-in reveal button (presence,
 * click-toggles-reveal, coexistence with the clear button), the mask / reveal
 * display behavior (masked by default, plain when revealed, custom echo char),
 * and that the Material decoration (floating label) is inherited.
 * <p>
 * The mask degradation paths (discovery failure / ambiguity / rebind failure)
 * are unit-tested against stub skins in
 * {@code io.github.leewyatt.rxcontrols.internal.PasswordMaskSupportTest}.
 */
public class RXMaterialPasswordFieldTest {

    private static final PseudoClass REVEALED = PseudoClass.getPseudoClass("revealed");

    /**
     * Starts the toolkit and pins Modena so {@code -rx-*} tokens resolve.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    @Test
    public void defaultsAndPlumbing() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField();
            assertNull(field.getText(),
                    "no-arg construction yields null text, matching TextField(String) semantics");
            assertEquals("x", new RXMaterialPasswordField("x").getText());
            // shared Material defaults — must stay in parity with RXMaterialTextField
            assertTrue(field.isFloatingLabel());
            assertTrue(field.isAnimated());
            assertTrue(field.isShowClearButton());
            assertEquals(Duration.millis(180.0), field.getAnimationDuration());
            assertEquals(0.85, field.getLabelFloatScale(), 0.0);
            assertEquals("", field.getLabelText());
            assertEquals(Insets.EMPTY, field.getTextPadding());
            // password-specific defaults
            assertFalse(field.isRevealPassword());
            assertTrue(field.isShowRevealButton());
            assertEquals('●', field.getEchoChar());

            field.setFloatingLabel(false);
            field.setAnimated(false);
            field.setAnimationDuration(Duration.millis(90));
            field.setLabelFloatScale(0.7);
            field.setShowClearButton(false);
            field.setLabelText("Password");
            field.setHelperText("8+ chars");
            field.setErrorText("too short");
            Insets padding = new Insets(0, 4, 0, 6);
            field.setTextPadding(padding);
            field.setRevealPassword(true);
            field.setShowRevealButton(false);
            field.setEchoChar('*');

            assertFalse(field.isFloatingLabel());
            assertFalse(field.isAnimated());
            assertEquals(Duration.millis(90), field.getAnimationDuration());
            assertEquals(0.7, field.getLabelFloatScale(), 0.0);
            assertFalse(field.isShowClearButton());
            assertEquals("Password", field.getLabelText());
            assertEquals("8+ chars", field.getHelperText());
            assertEquals("too short", field.getErrorText());
            assertEquals(padding, field.getTextPadding());
            assertTrue(field.isRevealPassword());
            assertFalse(field.isShowRevealButton());
            assertEquals('*', field.getEchoChar());
        });
    }

    @Test
    public void sameNodeMigratesBetweenSlots() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField();
            Label icon = new Label("@");
            field.setTrailingNode(icon);
            field.setLeadingNode(icon);
            assertNull(field.getTrailingNode(), "same node must migrate out of the opposite slot");
            assertSame(icon, field.getLeadingNode());
            field.setTrailingNode(icon);
            assertNull(field.getLeadingNode());
            assertSame(icon, field.getTrailingNode());
        });
    }

    @Test
    public void revealedPseudoClassFollowsRevealPassword() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField();
            assertFalse(field.getPseudoClassStates().contains(REVEALED));
            field.setRevealPassword(true);
            assertTrue(field.getPseudoClassStates().contains(REVEALED));
            field.setRevealPassword(false);
            assertFalse(field.getPseudoClassStates().contains(REVEALED));
        });
    }

    @Test
    public void cssMetadataExposesEchoChar() {
        boolean hasEcho = RXMaterialPasswordField.getClassCssMetaData().stream()
                .anyMatch(m -> "-rx-echo-char".equals(m.getProperty()));
        assertTrue(hasEcho, "-rx-echo-char must be exposed as CSS metadata");
    }

    @Test
    public void revealButtonTogglesRevealAndHidesWhenDisabled() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("secret");
            field.setLabelText("Password");
            inScene(field);

            StackPane reveal = revealButton(field);
            assertNotNull(reveal, "reveal button must be present by default");
            assertFalse(field.isRevealPassword());
            reveal.fireEvent(click());
            assertTrue(field.isRevealPassword(), "clicking reveal must show the password");
            reveal.fireEvent(click());
            assertFalse(field.isRevealPassword(), "clicking reveal again must re-mask");

            field.setShowRevealButton(false);
            assertNull(revealButton(field), "showRevealButton=false must remove the reveal button");
        });
    }

    @Test
    public void revealButtonCoexistsWithClearButton() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("secret");
            field.setLabelText("Password");
            inScene(field);
            StackPane reveal = revealButton(field);
            StackPane clear = clearButton(field);
            assertNotNull(reveal, "reveal button present");
            assertNotNull(clear, "clear button present");
            assertEquals(reveal.getParent(), clear.getParent(),
                    "reveal and clear share the internal trailing container");
            HBox container = (HBox) reveal.getParent();
            assertTrue(container.getChildren().indexOf(reveal) < container.getChildren().indexOf(clear),
                    "the reveal button must precede the clear button");
        });
    }

    @Test
    public void cutStaysDisabledEvenWhenRevealed() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("secret");
            inScene(field);
            field.setRevealPassword(true);
            field.selectAll();
            field.cut();
            assertEquals("secret", field.getText(),
                    "cut() must stay a no-op even when revealed (no deletion, no clipboard)");
        });
    }

    @Test
    public void maskHidesTextUntilRevealed() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("secret");
            field.setLabelText("Password");
            inScene(field);

            Text textNode = (Text) editorTextNode(field);
            assertNotNull(textNode, "editor text node not found");
            assertEquals("●".repeat(6), textNode.getText(), "masked by default");

            field.setRevealPassword(true);
            assertEquals("secret", textNode.getText(), "revealed shows the plain text");

            field.setRevealPassword(false);
            assertEquals("●".repeat(6), textNode.getText(), "re-masked");

            // live text edits flow through the replacement binding (the reason it
            // replaces JavaFX's own binding)
            field.appendText("x");
            assertEquals("●".repeat(7), textNode.getText(), "an appended char is masked too");
            field.setRevealPassword(true);
            assertEquals("secretx", textNode.getText(), "revealed shows the live-edited plain text");
        });
    }

    @Test
    public void customEchoCharIsUsedForMasking() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("ab");
            field.setEchoChar('*');
            inScene(field);
            Text textNode = (Text) editorTextNode(field);
            assertNotNull(textNode);
            assertEquals("**", textNode.getText(), "custom echo char must drive the mask");

            field.setEchoChar(null);
            assertEquals("●●", textNode.getText(),
                    "null echoChar must fall back to the default mask character");
        });
    }

    @Test
    public void cssDrivesEchoCharMask() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("ab");
            // The password field duplicates all six CssMetaData wirings (own
            // class), so exercise every one — not just echo-char. Deliberately
            // not '#': a quoted hash does not survive the JavaFX CSS parser
            // (the whole declaration is silently dropped).
            field.setStyle("-rx-echo-char: 'x'; -rx-floating-label: false;"
                    + " -rx-animated: false; -rx-animation-duration: 250ms;"
                    + " -rx-label-float-scale: 0.9; -rx-text-padding: 1 2 3 4;");
            inScene(field);
            assertEquals('x', field.getEchoChar(),
                    "-rx-echo-char must travel CSS -> converter -> property");
            assertFalse(field.isFloatingLabel());
            assertFalse(field.isAnimated());
            assertEquals(Duration.millis(250), field.getAnimationDuration());
            assertEquals(0.9, field.getLabelFloatScale(), 0.001);
            assertEquals(new Insets(1, 2, 3, 4), field.getTextPadding());
            Text textNode = (Text) editorTextNode(field);
            assertNotNull(textNode);
            assertEquals("xx", textNode.getText(), "the CSS-set echo char must drive the mask");
        });
    }

    @Test
    public void inheritsFloatingLabelDecoration() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField();
            field.setLabelText("Password");
            inScene(field);
            assertNotNull(floatingLabel(field), "password field must inherit the floating label");
            assertNotNull(field.lookup(".activation-line"), "password field must inherit the activation line");
        });
    }

    @Test
    public void accessibleNameComesFromLabel() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField();
            inScene(field);
            field.setLabelText("Password");
            // The native prompt is suppressed, so the floating label must label the
            // control for assistive tech — most important for a password field. The
            // skin uses the LABELED_BY relation (labelFor); accessibleText stays
            // user-owned and untouched.
            Object labeledBy = field.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY);
            assertInstanceOf(Label.class, labeledBy,
                    "the control must be LABELED_BY the floating label");
            assertEquals("Password", ((Label) labeledBy).getText());
            assertNull(field.getAccessibleText(),
                    "the skin must not write the user-owned accessibleText");
        });
    }

    // ==================== Helpers ====================

    private static MouseEvent click() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, null);
    }

    private static StackPane revealButton(RXMaterialPasswordField field) {
        Node node = field.lookup(".reveal-button");
        return node instanceof StackPane stackPane ? stackPane : null;
    }

    private static StackPane clearButton(RXMaterialPasswordField field) {
        Node node = field.lookup(".clear-button");
        return node instanceof StackPane stackPane ? stackPane : null;
    }

    private static Label floatingLabel(RXMaterialPasswordField field) {
        for (Node node : field.lookupAll(".label")) {
            if (node instanceof Label label && !inSupporting(node)) {
                return label;
            }
        }
        return null;
    }

    private static boolean inSupporting(Node node) {
        for (Node p = node.getParent(); p != null; p = p.getParent()) {
            if (p.getStyleClass().contains("supporting")) {
                return true;
            }
        }
        return false;
    }

    private static Node editorTextNode(RXMaterialPasswordField field) {
        // Mirror PasswordMaskSupport: the editor text node is the bound-layoutX Text
        // inside the clipped text-group pane (not the floating label's text).
        for (Node node : field.lookupAll(".text")) {
            if (node instanceof Text && node.layoutXProperty().isBound()
                    && node.getParent() instanceof Pane pane && pane.getClip() instanceof Rectangle) {
                return node;
            }
        }
        return null;
    }

    private static void inScene(RXMaterialPasswordField field) {
        StackPane root = new StackPane(field);
        new Scene(root, 320, 160);
        root.applyCss();
        root.layout();
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
                throw new AssertionError("FX task did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for FX task", e);
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }
}
