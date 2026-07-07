package io.github.leewyatt.rxcontrols.internal;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link PasswordMaskSupport}'s degradation and lifecycle
 * contract, driven through stub skins: discovery failure and ambiguous text
 * nodes fail closed (permanent mask + warning + degradation callback), a rebind
 * failure falls back to a text-tracking masked binding, dispose unbinds the
 * text node and pins it back to a masked snapshot, and the pending skin hook
 * detaches itself once a different skin wins the control.
 */
public class PasswordMaskSupportTest {

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

    // ==================== Stub skins ====================

    /** Skin with no discoverable text node (empty scene graph). */
    private static final class EmptySkin extends SkinBase<PasswordField> {
        EmptySkin(PasswordField control) {
            super(control);
        }
    }

    /** Skin mimicking TextFieldSkin's clipped pane + bound-layoutX text node. */
    private static final class TextNodeSkin extends SkinBase<PasswordField> {
        final Text textNode = new Text();

        TextNodeSkin(PasswordField control) {
            super(control);
            Pane group = new Pane();
            group.setClip(new Rectangle());
            textNode.layoutXProperty().bind(new SimpleDoubleProperty(0));
            group.getChildren().add(textNode);
            getChildren().add(group);
        }
    }

    /** Skin whose clipped pane holds two qualifying text nodes (ambiguous). */
    private static final class AmbiguousSkin extends SkinBase<PasswordField> {
        AmbiguousSkin(PasswordField control) {
            super(control);
            Pane group = new Pane();
            group.setClip(new Rectangle());
            Text a = new Text();
            a.layoutXProperty().bind(new SimpleDoubleProperty(0));
            Text b = new Text();
            b.layoutXProperty().bind(new SimpleDoubleProperty(0));
            group.getChildren().addAll(a, b);
            getChildren().add(group);
        }
    }

    // ==================== Degradation paths ====================

    @Test
    public void discoveryFailureDegradesToPermanentMaskAndWarns() {
        runOnFx(() -> {
            PasswordField control = new PasswordField();
            EmptySkin skin = new EmptySkin(control);
            AtomicBoolean degraded = new AtomicBoolean();
            PasswordMaskSupport support = new PasswordMaskSupport(skin, control,
                    raw -> "*".repeat(raw.length()), new SimpleBooleanProperty(false));
            support.setOnDegraded(() -> degraded.set(true));
            support.install();
            assertFalse(support.isInstalled());
            assertFalse(support.isFailed());

            List<LogRecord> records = withCapturedLog(() -> control.setSkin(skin));

            assertFalse(support.isInstalled(), "discovery failure must not install");
            assertTrue(support.isFailed());
            assertTrue(degraded.get(), "the degradation callback must fire");
            assertTrue(records.stream().anyMatch(r -> r.getLevel() == Level.WARNING),
                    "the degradation must log a warning");
            support.dispose();
        });
    }

    @Test
    public void ambiguousTextNodesDegrade() {
        runOnFx(() -> {
            PasswordField control = new PasswordField();
            AmbiguousSkin skin = new AmbiguousSkin(control);
            PasswordMaskSupport support = new PasswordMaskSupport(skin, control,
                    raw -> "*".repeat(raw.length()), new SimpleBooleanProperty(false));
            support.install();

            control.setSkin(skin);

            assertFalse(support.isInstalled(),
                    "two candidate text nodes must fail closed, not guess");
            assertTrue(support.isFailed());
            support.dispose();
        });
    }

    @Test
    public void rebindFailureFallsBackToTextTrackingMask() {
        runOnFx(() -> {
            PasswordField control = new PasswordField();
            control.setText("abc");
            TextNodeSkin skin = new TextNodeSkin(control);
            // A throwing mask function cannot drive the rebind catch — the
            // Bindings.createStringBinding computeValue swallows it. What CAN
            // throw inside rebind is a dependency whose addListener fails; the
            // helper's constructor performs the first addListener (tolerated),
            // the binding's constructor performs the second (throws).
            Observable flakyDependency = new Observable() {
                private int calls;

                @Override
                public void addListener(InvalidationListener listener) {
                    calls++;
                    if (calls > 1) {
                        throw new IllegalStateException("simulated rebind failure");
                    }
                }

                @Override
                public void removeListener(InvalidationListener listener) {
                }
            };
            AtomicBoolean degraded = new AtomicBoolean();
            PasswordMaskSupport support = new PasswordMaskSupport(skin, control,
                    raw -> "*".repeat(raw.length()), flakyDependency);
            support.setOnDegraded(() -> degraded.set(true));
            support.install();

            control.setSkin(skin);

            assertFalse(support.isInstalled(), "a failed rebind must not install");
            assertTrue(support.isFailed());
            assertTrue(degraded.get(), "the degradation callback must fire");
            assertEquals("***", skin.textNode.getText(), "degraded display must stay masked");
            control.setText("abcdef");
            assertEquals("******", skin.textNode.getText(),
                    "the degraded mask must keep tracking text edits");
            support.dispose();
        });
    }

    // ==================== Dispose / lifecycle ====================

    @Test
    public void disposeUnbindsAndRemasksTheTextNode() {
        runOnFx(() -> {
            PasswordField control = new PasswordField();
            control.setText("secret");
            BooleanProperty reveal = new SimpleBooleanProperty(false);
            TextNodeSkin skin = new TextNodeSkin(control);
            // The self-referencing mask mirrors the production maskText's
            // isInstalled guard: dispose flips installed BEFORE the snapshot,
            // which is exactly what the "******" remask assertion locks in.
            AtomicReference<PasswordMaskSupport> ref = new AtomicReference<>();
            PasswordMaskSupport support = new PasswordMaskSupport(skin, control,
                    raw -> (ref.get() != null && ref.get().isInstalled() && reveal.get())
                            ? raw : "*".repeat(raw.length()),
                    reveal);
            ref.set(support);
            support.install();
            control.setSkin(skin);

            assertTrue(support.isInstalled());
            assertTrue(skin.textNode.textProperty().isBound());
            assertEquals("******", skin.textNode.getText());
            reveal.set(true);
            assertEquals("secret", skin.textNode.getText(),
                    "reveal must show plain text while installed");

            support.dispose();

            assertFalse(skin.textNode.textProperty().isBound(),
                    "dispose must unbind the text node from the dead binding");
            assertEquals("******", skin.textNode.getText(),
                    "dispose while revealed must pin the node back to a masked snapshot");
            control.setText("changed");
            assertEquals("******", skin.textNode.getText(),
                    "the display must not keep updating after dispose");
        });
    }

    @Test
    public void pendingListenerDetachesWhenAnotherSkinInstalls() {
        runOnFx(() -> {
            PasswordField control = new PasswordField();
            TextNodeSkin ghostOwner = new TextNodeSkin(control);
            PasswordMaskSupport support = new PasswordMaskSupport(ghostOwner, control,
                    raw -> "*".repeat(raw.length()), new SimpleBooleanProperty(false));
            support.install();

            control.setSkin(new EmptySkin(control));
            assertFalse(support.isInstalled());
            assertFalse(support.isFailed(), "not a failure — the owner simply never attached");

            // Even a later attach of the never-installed owner stays inert:
            // the pending hook removed itself when another skin won.
            control.setSkin(ghostOwner);
            assertFalse(support.isInstalled());
            support.dispose();
        });
    }

    // ==================== Helpers ====================

    private static List<LogRecord> withCapturedLog(Runnable body) {
        Logger logger = Logger.getLogger(PasswordMaskSupport.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            body.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
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
