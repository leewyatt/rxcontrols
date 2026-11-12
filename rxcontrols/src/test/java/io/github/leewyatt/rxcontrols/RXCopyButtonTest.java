package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.skins.RXCopyButtonSkin;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXCopyButton activation contract, the two-face skin, the copied
 * feedback state, the hand-wired button behavior and skin lifecycle. No test
 * writes the system clipboard: factories return null or empty content, and
 * feedback is driven by posting the result signal directly.
 */
public class RXCopyButtonTest {

    private static final boolean MAC = RXOS.isMacOS();
    private static final PseudoClass COPIED = PseudoClass.getPseudoClass("copied");
    private static final KeyCodeCombination ENTER = new KeyCodeCombination(KeyCode.ENTER);
    private static final KeyCodeCombination ESCAPE = new KeyCodeCombination(KeyCode.ESCAPE);

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
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

    // ==================== Control API ====================

    @Test
    public void defaultsAndCssMetadataMatchContract() {
        RXCopyButton button = new RXCopyButton("Copy");

        assertTrue(button.getStyleClass().contains("rx-copy-button"));
        assertTrue(button.getStyleClass().contains("rx-button"));
        assertNull(button.getContentFactory());
        assertNull(button.getCopiedText());
        assertEquals(Duration.millis(2000.0), RXCopyButton.DEFAULT_FEEDBACK_DURATION);
        assertEquals(RXCopyButton.DEFAULT_FEEDBACK_DURATION, button.getFeedbackDuration());
        assertFalse(button.isMnemonicParsing());
        assertFalse(button.getPseudoClassStates().contains(COPIED));

        List<CssMetaData<? extends Styleable, ?>> metaData = button.getCssMetaData();
        assertTrue(metaData.containsAll(RXButton.getClassCssMetaData()));
        CssMetaData<RXCopyButton, Duration> duration = feedbackDurationMetaData(button);
        assertTrue(duration.isSettable(button));

        button.feedbackDurationProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        assertFalse(duration.isSettable(button));
    }

    @Test
    public void feedbackDurationParsesIndefiniteFromCss() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));

        button.setStyle("-rx-feedback-duration: indefinite;");
        button.getParent().applyCss();

        assertEquals(Duration.INDEFINITE, button.getFeedbackDuration());
        button.getSkin().dispose();
    }

    @Test
    public void textContentHoldsPlainTextOrNothing() {
        assertTrue(RXCopyButton.textContent(null).isEmpty());
        assertTrue(RXCopyButton.textContent("").isEmpty());

        ClipboardContent content = RXCopyButton.textContent("abc");
        assertEquals(1, content.size());
        assertEquals("abc", content.getString());
        assertEquals("   ", RXCopyButton.textContent("   ").getString());

        ClipboardContent empty = RXCopyButton.textContent(null);
        empty.putHtml("<b>x</b>");
        assertTrue(empty.containsKey(DataFormat.HTML));
    }

    // ==================== Activation ====================

    @Test
    public void nothingToCopyStillFiresActionAndPostsFailure() {
        List<Supplier<ClipboardContent>> factories = new ArrayList<>();
        factories.add(null);
        factories.add(() -> null);
        factories.add(() -> RXCopyButton.textContent(""));

        for (Supplier<ClipboardContent> factory : factories) {
            RXCopyButton button = new RXCopyButton("Copy");
            button.setContentFactory(factory);
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> actions.incrementAndGet());

            button.fire();

            assertEquals(1, actions.get());
            assertEquals(Boolean.FALSE, button.getProperties().get(FeedbackSignals.COPY_RESULT));
        }
    }

    @Test
    public void factoryRunsOncePerActivationOnEveryPath() {
        RXCopyButton button = new RXCopyButton("Copy");
        AtomicInteger calls = countingFactory(button);

        button.fire();
        assertEquals(1, calls.get());
        button.executeAccessibleAction(AccessibleAction.FIRE);
        assertEquals(2, calls.get());
        button.fireEvent(new ActionEvent());
        assertEquals(3, calls.get());

        button.setDisable(true);
        button.fire();
        button.fireEvent(new ActionEvent());
        assertEquals(3, calls.get());
    }

    @Test
    public void consumingFilterVetoesCopyAndAction() {
        RXCopyButton button = new RXCopyButton("Copy");
        AtomicInteger calls = countingFactory(button);
        AtomicInteger actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());
        button.addEventFilter(ActionEvent.ACTION, Event::consume);

        button.fire();

        assertEquals(0, calls.get());
        assertEquals(0, actions.get());
    }

    @Test
    public void actionBubblingFromGraphicCopiesUnlessConsumed() {
        Label graphic = new Label("g");
        RXCopyButton button = laidOut(new RXCopyButton("Copy", graphic));
        AtomicInteger calls = countingFactory(button);

        Event.fireEvent(graphic, new ActionEvent());
        assertEquals(1, calls.get());

        graphic.addEventHandler(ActionEvent.ACTION, Event::consume);
        Event.fireEvent(graphic, new ActionEvent());
        assertEquals(1, calls.get());
        button.getSkin().dispose();
    }

    @Test
    public void throwingFactoryPropagatesAndPostsFailure() {
        RXCopyButton button = new RXCopyButton("Copy");
        AtomicInteger actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());
        List<Object> posted = recordPostedResults(button);
        button.setContentFactory(() -> {
            throw new IllegalStateException("boom");
        });

        assertThrows(IllegalStateException.class, button::fire);

        assertEquals(0, actions.get());
        assertEquals(List.of(Boolean.FALSE), posted);
    }

    // ==================== Faces ====================

    @Test
    public void captionsMirrorButtonWithoutLabelClass() {
        Label graphic = new Label("g");
        RXCopyButton button = laidOut(new RXCopyButton("Copy _link", graphic));
        Label normal = caption(button, ".copy-face");
        Label copied = caption(button, ".copied-face");

        assertFalse(normal.getStyleClass().contains("label"));
        assertFalse(copied.getStyleClass().contains("label"));
        assertEquals("Copy _link", normal.getText());
        assertEquals("Copy _link", copied.getText());
        assertFalse(normal.isMnemonicParsing());
        assertFalse(copied.isMnemonicParsing());
        assertSame(graphic, normal.getGraphic());
        assertNull(copied.getGraphic());

        button.setCopiedText("Copied");
        assertEquals("Copied", copied.getText());
        button.setCopiedText("");
        assertEquals("", copied.getText());
        button.setCopiedText(null);
        button.setText("Dup");
        assertEquals("Dup", copied.getText());

        Font font = Font.font(20.0);
        button.setFont(font);
        button.setTextFill(Color.RED);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        for (Label caption : List.of(normal, copied)) {
            assertEquals(font, caption.getFont());
            assertEquals(Color.RED, caption.getTextFill());
            assertEquals(ContentDisplay.GRAPHIC_ONLY, caption.getContentDisplay());
        }
        button.getSkin().dispose();
    }

    @Test
    public void prefSizeCoversBothFacesAndGapNeedsIndicatorAndCaption() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        button.setCopiedText("Copied to clipboard");
        button.setGraphicTextGap(6.0);
        Region normalFace = face(button, ".copy-face");
        Region copiedFace = face(button, ".copied-face");
        double normalCaption = caption(button, ".copy-face").prefWidth(-1);
        double copiedCaption = caption(button, ".copied-face").prefWidth(-1);
        double insets = button.snappedLeftInset() + button.snappedRightInset();

        setIndicatorSize(button, 0.0);
        assertEquals(Math.ceil(normalCaption), normalFace.prefWidth(-1), 1.0e-6);
        assertEquals(insets + Math.ceil(copiedCaption), button.prefWidth(-1), 1.0e-6);

        setIndicatorSize(button, 14.0);
        assertEquals(14.0 + 6.0 + Math.ceil(normalCaption), normalFace.prefWidth(-1), 1.0e-6);
        assertEquals(insets + copiedFace.prefWidth(-1), button.prefWidth(-1), 1.0e-6);
        assertEquals(button.prefWidth(-1), button.maxWidth(-1), 1.0e-6);
        assertEquals(button.prefHeight(-1), button.maxHeight(-1), 1.0e-6);

        button.setText("");
        button.setCopiedText(null);
        assertEquals(14.0, normalFace.prefWidth(-1), 1.0e-6);
        assertEquals(14.0, copiedFace.prefWidth(-1), 1.0e-6);
        button.getSkin().dispose();
    }

    @Test
    public void wrappedCaptionGrowsTallerInNarrowerWidth() {
        RXCopyButton button = laidOut(new RXCopyButton("A caption long enough to wrap onto several lines"));
        setIndicatorSize(button, 14.0);
        button.setWrapText(true);
        Region face = face(button, ".copy-face");

        assertTrue(face.prefHeight(80.0) > face.prefHeight(-1));
        assertTrue(button.prefHeight(100.0) > button.prefHeight(-1));
        button.getSkin().dispose();
    }

    @Test
    public void indicatorAndCaptionAlignAsOneBlock() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        button.setGraphicTextGap(6.0);
        setIndicatorSize(button, 14.0);
        button.setMaxWidth(Double.MAX_VALUE);
        Region face = face(button, ".copy-face");
        Region indicator = indicator(button, ".copy-face");
        Label caption = caption(button, ".copy-face");

        for (Pos alignment : List.of(Pos.CENTER_LEFT, Pos.CENTER, Pos.CENTER_RIGHT)) {
            button.setAlignment(alignment);
            button.getParent().layout();

            double blockWidth = 14.0 + 6.0 + caption.getWidth();
            assertTrue(face.getWidth() > blockWidth + 20.0);
            double expectedX = switch (alignment) {
                case CENTER_LEFT -> 0.0;
                case CENTER -> (face.getWidth() - blockWidth) / 2.0;
                default -> face.getWidth() - blockWidth;
            };
            assertEquals(expectedX, indicator.getLayoutX(), 1.0);
            assertEquals(indicator.getLayoutX() + 14.0 + 6.0, caption.getLayoutX(), 1.0e-6);
        }
        button.getSkin().dispose();
    }

    @Test
    public void accessibleTextFollowsShowingFace() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        button.setFeedbackDuration(Duration.INDEFINITE);

        assertEquals("Copy", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        post(button, Boolean.TRUE);
        assertEquals("Copy", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        button.setCopiedText("Copied");
        assertEquals("Copied", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));

        button.setAccessibleText("Copy API key");
        assertEquals("Copy API key", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        button.getSkin().dispose();
    }

    // ==================== Feedback ====================

    @Test
    public void successShowsFeedbackUntilTimerExpires() throws InterruptedException {
        AtomicReference<RXCopyButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXCopyButton button = laidOut(new RXCopyButton("Copy"));
            button.setFeedbackDuration(Duration.millis(80.0));
            ref.set(button);

            post(button, Boolean.TRUE);

            assertCopied(button, true);
            assertFalse(button.getProperties().containsKey(FeedbackSignals.COPY_RESULT));
        });
        Thread.sleep(400L);
        onFx(() -> {
            assertCopied(ref.get(), false);
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void repeatedSuccessRestartsTimer() throws InterruptedException {
        AtomicReference<RXCopyButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXCopyButton button = laidOut(new RXCopyButton("Copy"));
            button.setFeedbackDuration(Duration.millis(1000.0));
            ref.set(button);
            post(button, Boolean.TRUE);
        });
        Thread.sleep(600L);
        onFx(() -> post(ref.get(), Boolean.TRUE));
        Thread.sleep(700L);
        // 1300 ms in: the first timer alone would have expired at 1000 ms.
        onFx(() -> assertCopied(ref.get(), true));
        Thread.sleep(800L);
        onFx(() -> {
            assertCopied(ref.get(), false);
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void durationChangeDuringFeedbackAppliesFromNextCopy() throws InterruptedException {
        AtomicReference<RXCopyButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXCopyButton button = laidOut(new RXCopyButton("Copy"));
            button.setFeedbackDuration(Duration.millis(80.0));
            ref.set(button);
            post(button, Boolean.TRUE);
            button.setFeedbackDuration(Duration.INDEFINITE);
            // A disabled button keeps its running feedback.
            button.setDisable(true);
        });
        Thread.sleep(400L);
        onFx(() -> {
            assertCopied(ref.get(), false);
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void failureAndResetReturnToNormalFace() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        button.setFeedbackDuration(Duration.INDEFINITE);
        AtomicInteger calls = countingFactory(button);
        AtomicInteger actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());

        post(button, Boolean.FALSE);
        assertCopied(button, false);

        post(button, Boolean.TRUE);
        post(button, Boolean.TRUE);
        assertCopied(button, true);
        post(button, Boolean.FALSE);
        assertCopied(button, false);

        post(button, Boolean.TRUE);
        button.setDisable(true);
        button.resetFeedback();
        assertCopied(button, false);
        button.resetFeedback();
        assertCopied(button, false);
        assertEquals(0, calls.get());
        assertEquals(0, actions.get());

        // An activation with nothing to copy clears the feedback of the previous one.
        button.setDisable(false);
        post(button, Boolean.TRUE);
        button.fire();
        assertCopied(button, false);
        assertEquals(1, calls.get());
        button.getSkin().dispose();
    }

    @Test
    public void zeroDurationShowsNoFeedbackAndClearsRunningOne() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        button.setFeedbackDuration(Duration.INDEFINITE);
        post(button, Boolean.TRUE);
        assertCopied(button, true);

        button.setFeedbackDuration(Duration.ZERO);
        post(button, Boolean.TRUE);

        assertCopied(button, false);
        button.getSkin().dispose();
    }

    @Test
    public void invalidDurationsFallBackToDefault() throws InterruptedException {
        List<RXCopyButton> buttons = new ArrayList<>();
        onFx(() -> {
            for (Duration invalid : new Duration[] {null, Duration.millis(-5.0), Duration.UNKNOWN}) {
                RXCopyButton button = laidOut(new RXCopyButton("Copy"));
                button.setFeedbackDuration(invalid);
                post(button, Boolean.TRUE);
                buttons.add(button);
            }
        });
        Thread.sleep(300L);
        onFx(() -> {
            for (RXCopyButton button : buttons) {
                assertCopied(button, true);
                button.getSkin().dispose();
            }
        });
    }

    @Test
    public void resetWithoutSkinIsSafeAndSkinStartsNormal() {
        RXCopyButton button = new RXCopyButton("Copy");
        button.getProperties().put(FeedbackSignals.COPY_RESULT, Boolean.TRUE);
        button.resetFeedback();

        laidOut(button);

        assertCopied(button, false);
        assertFalse(button.getProperties().containsKey(FeedbackSignals.COPY_RESULT));
        button.getSkin().dispose();
    }

    @Test
    public void actionHandlersMayDisableDetachOrReskinTheButton() {
        List<Consumer<RXCopyButton>> reentries = new ArrayList<>();
        reentries.add(button -> button.setDisable(true));
        reentries.add(button -> ((StackPane) button.getParent()).getChildren().clear());
        reentries.add(RXCopyButtonTest::replaceSkin);

        for (Consumer<RXCopyButton> reentry : reentries) {
            RXCopyButton button = laidOut(new RXCopyButton("Copy"));
            button.setFeedbackDuration(Duration.INDEFINITE);
            post(button, Boolean.TRUE);
            button.setOnAction(event -> reentry.accept(button));

            button.fire();

            assertCopied(button, false);
            // The installed skin still tracks results after the re-entry.
            post(button, Boolean.TRUE);
            assertCopied(button, true);
            button.getSkin().dispose();
        }
    }

    // ==================== Button Behavior ====================

    @Test
    public void primaryPressArmsAndReleaseInsideFiresOnce() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        AtomicInteger calls = countingFactory(button);

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        assertTrue(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        assertEquals(1, calls.get());

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        assertEquals(1, calls.get());

        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY));
        assertFalse(button.isArmed());
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.SECONDARY));
        assertEquals(1, calls.get());
        button.getSkin().dispose();
    }

    @Test
    public void activationKeysFireAndOtherKeysStayUnconsumed() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        AtomicInteger calls = countingFactory(button);
        AtomicInteger spaceAccelerator = new AtomicInteger();
        AtomicInteger arrowAccelerator = new AtomicInteger();
        Scene scene = button.getScene();
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.SPACE),
                spaceAccelerator::incrementAndGet);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT),
                arrowAccelerator::incrementAndGet);

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
        assertTrue(button.isArmed());
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
        assertFalse(button.isArmed());
        assertEquals(1, calls.get());
        // Dispatch works on event copies, so consumption is asserted through
        // the scene accelerators, which only run for unconsumed events.
        assertEquals(0, spaceAccelerator.get());

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.ENTER));
        assertEquals(MAC ? 1 : 2, calls.get());

        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.RIGHT));
        assertEquals(1, arrowAccelerator.get());
        button.getSkin().dispose();
    }

    @Test
    public void defaultAndCancelAcceleratorsFollowTheCurrentScene() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        AtomicInteger calls = countingFactory(button);
        Scene first = button.getScene();

        button.setDefaultButton(true);
        button.setCancelButton(true);
        first.getAccelerators().get(ENTER).run();
        first.getAccelerators().get(ESCAPE).run();
        assertEquals(2, calls.get());

        button.setCancelButton(false);
        assertFalse(first.getAccelerators().containsKey(ESCAPE));

        ((StackPane) first.getRoot()).getChildren().clear();
        Scene second = new Scene(new StackPane(button), 200.0, 100.0);
        assertFalse(first.getAccelerators().containsKey(ENTER));
        assertTrue(second.getAccelerators().containsKey(ENTER));

        button.getSkin().dispose();
        assertFalse(second.getAccelerators().containsKey(ENTER));
    }

    // ==================== Dispose ====================

    @Test
    public void disposeClearsFeedbackAndStopsListening() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        button.setFeedbackDuration(Duration.INDEFINITE);
        post(button, Boolean.TRUE);
        assertCopied(button, true);

        button.getSkin().dispose();
        assertFalse(button.getPseudoClassStates().contains(COPIED));
        assertTrue(button.getChildrenUnmodifiable().isEmpty());

        post(button, Boolean.TRUE);
        assertFalse(button.getPseudoClassStates().contains(COPIED));
        assertTrue(button.getProperties().containsKey(FeedbackSignals.COPY_RESULT));
        button.getSkin().dispose();
    }

    @Test
    public void replacementSkinKeepsItsNodesAndStartsNormal() {
        RXCopyButton button = laidOut(new RXCopyButton("Copy"));
        button.setFeedbackDuration(Duration.INDEFINITE);
        post(button, Boolean.TRUE);

        replaceSkin(button);
        button.getParent().applyCss();
        button.getParent().layout();

        assertEquals(2, button.getChildrenUnmodifiable().size());
        assertCopied(button, false);
        post(button, Boolean.TRUE);
        assertCopied(button, true);
        button.getSkin().dispose();
    }

    // ==================== Helpers ====================

    private static RXCopyButton laidOut(RXCopyButton button) {
        StackPane root = new StackPane(button);
        new Scene(root, 300.0, 100.0);
        root.resize(300.0, 100.0);
        root.applyCss();
        root.layout();
        return button;
    }

    // JavaFX 17 ignores setSkin for a skin of the installed skin's class, so
    // the replacement is an anonymous subclass.
    private static void replaceSkin(RXCopyButton button) {
        button.setSkin(new RXCopyButtonSkin(button) {
        });
    }

    private static AtomicInteger countingFactory(RXCopyButton button) {
        AtomicInteger calls = new AtomicInteger();
        button.setContentFactory(() -> {
            calls.incrementAndGet();
            return null;
        });
        return calls;
    }

    private static List<Object> recordPostedResults(RXCopyButton button) {
        List<Object> posted = new ArrayList<>();
        button.getProperties().addListener((MapChangeListener<Object, Object>) change -> {
            if (FeedbackSignals.COPY_RESULT.equals(change.getKey()) && change.wasAdded()) {
                posted.add(change.getValueAdded());
            }
        });
        return posted;
    }

    private static void post(RXCopyButton button, Boolean result) {
        button.getProperties().put(FeedbackSignals.COPY_RESULT, result);
    }

    private static void assertCopied(RXCopyButton button, boolean copied) {
        assertEquals(copied, button.getPseudoClassStates().contains(COPIED));
        assertEquals(copied, face(button, ".copied-face").getParent().isVisible());
        assertEquals(!copied, face(button, ".copy-face").getParent().isVisible());
    }

    private static Region face(RXCopyButton button, String selector) {
        Node face = button.lookup(selector);
        assertNotNull(face, selector);
        return assertInstanceOf(Region.class, face);
    }

    private static Label caption(RXCopyButton button, String faceSelector) {
        return assertInstanceOf(Label.class, face(button, faceSelector).lookup(".caption"));
    }

    private static Region indicator(RXCopyButton button, String faceSelector) {
        return assertInstanceOf(Region.class, face(button, faceSelector).lookup(".indicator"));
    }

    private static void setIndicatorSize(RXCopyButton button, double size) {
        indicator(button, ".copy-face").setPrefSize(size, size);
        indicator(button, ".copied-face").setPrefSize(size, size);
    }

    @SuppressWarnings("unchecked")
    private static CssMetaData<RXCopyButton, Duration> feedbackDurationMetaData(RXCopyButton button) {
        for (CssMetaData<? extends Styleable, ?> metaData : button.getCssMetaData()) {
            if ("-rx-feedback-duration".equals(metaData.getProperty())) {
                return (CssMetaData<RXCopyButton, Duration>) metaData;
            }
        }
        throw new AssertionError("-rx-feedback-duration is not styleable");
    }

    private static void onFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static MouseEvent mouseEvent(EventType<MouseEvent> type, MouseButton button) {
        boolean pressed = type == MouseEvent.MOUSE_PRESSED;
        return new MouseEvent(type, 0.0, 0.0, 0.0, 0.0, button, 1,
                false, false, false, false,
                pressed && button == MouseButton.PRIMARY, false,
                pressed && button == MouseButton.SECONDARY,
                false, false, false, null);
    }

    private static KeyEvent keyEvent(EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
    }
}
