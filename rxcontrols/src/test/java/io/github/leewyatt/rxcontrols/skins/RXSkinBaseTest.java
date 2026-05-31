package io.github.leewyatt.rxcontrols.skins;

import javafx.application.Platform;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXSkinBase}.
 */
public class RXSkinBaseTest {

    /**
     * Starts the JavaFX toolkit before constructing controls.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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

    /**
     * Verifies subclass cleanup failures do not skip disposer or SkinBase cleanup.
     */
    @Test
    public void disposeRunsDisposerAndSkinBaseWhenDisposeSkinFails() {
        TestControl control = new TestControl();
        RuntimeException primary = new RuntimeException("primary");
        TestSkin skin = new TestSkin(control, primary, null);

        RuntimeException exception = assertThrows(RuntimeException.class, skin::dispose);

        assertSame(primary, exception);
        assertTrue(skin.disposerRan);
        assertNull(skin.getSkinnable());
    }

    /**
     * Verifies disposer failures become suppressed exceptions when subclass
     * cleanup already failed.
     */
    @Test
    public void disposeSuppressesDisposerFailureAfterDisposeSkinFailure() {
        TestControl control = new TestControl();
        RuntimeException primary = new RuntimeException("primary");
        RuntimeException secondary = new RuntimeException("secondary");
        TestSkin skin = new TestSkin(control, primary, secondary);

        RuntimeException exception = assertThrows(RuntimeException.class, skin::dispose);

        assertSame(primary, exception);
        assertTrue(skin.disposerRan);
        assertSame(secondary, exception.getSuppressed()[0]);
        assertNull(skin.getSkinnable());
    }

    private static final class TestControl extends Control {

        @Override
        protected Skin<?> createDefaultSkin() {
            return null;
        }
    }

    private static final class TestSkin extends RXSkinBase<TestControl> {

        private final RuntimeException failure;
        private final RuntimeException disposerFailure;
        private boolean disposerRan;

        private TestSkin(TestControl control, RuntimeException failure, RuntimeException disposerFailure) {
            super(control);
            this.failure = failure;
            this.disposerFailure = disposerFailure;
            disposer.registerDisposeTask(() -> {
                disposerRan = true;
                if (disposerFailure != null) {
                    throw disposerFailure;
                }
            });
        }

        @Override
        protected void disposeSkin() {
            throw failure;
        }
    }
}
