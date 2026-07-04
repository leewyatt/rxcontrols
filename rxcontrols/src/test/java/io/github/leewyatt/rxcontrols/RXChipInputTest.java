package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXChipInputSkin;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXChipInput}: defaults, chip-node mirroring (add / remove /
 * replace / permute), the headline duplicate-delete safety, the STRICT / FREE /
 * CREATE commit policies, converter failure handling, duplicate rejection, Backspace
 * removal, the vetoable delete event flow, wrap sizing, the tamed editor min width
 * and the maxRows scroll cap.
 */
public class RXChipInputTest {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    /**
     * Starts the JavaFX toolkit.
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
    }

    // ==================== Defaults ====================

    /**
     * Verifies default state, style class, skin and horizontal content bias.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void defaultStateAndSkin() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = new RXChipInput<>();
            assertTrue(input.getStyleClass().contains("rx-chip-input"));
            assertSame(RXChipInput.CustomInputPolicy.FREE, input.getCustomInputPolicy());
            assertFalse(input.isAllowDuplicates());
            assertTrue(input.isEditable());
            assertEquals(RXChipInput.DEFAULT_EDITOR_MIN_WIDTH, input.getEditorMinWidth());
            assertEquals(RXChipInput.DEFAULT_MAX_ROWS, input.getMaxRows());
            assertEquals(RXChipInput.DEFAULT_GAP, input.getHgap());
            assertEquals(RXChipInput.DEFAULT_GAP, input.getVgap());
            assertFalse(input.isAutoSelectFirstSuggestion());
            assertTrue(input.getChips().isEmpty());
            assertSame(Orientation.HORIZONTAL, input.getContentBias());
            assertNotNull(input.getUserAgentStylesheet());
            assertTrue(input.createDefaultSkin() instanceof RXChipInputSkin<?>);
        });
    }

    /**
     * The chip gaps are real styleable properties: they round-trip through the Java
     * setters and expose {@code -rx-hgap} / {@code -rx-vgap} CSS metadata.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void gapsAreStyleableAndRoundTrip() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = new RXChipInput<>();
            input.setHgap(12.0);
            input.setVgap(3.0);
            assertEquals(12.0, input.getHgap());
            assertEquals(3.0, input.getVgap());
            assertSame(input.hgapProperty(), input.hgapProperty());

            List<String> cssNames = RXChipInput.getClassCssMetaData().stream()
                    .map(m -> m.getProperty())
                    .collect(Collectors.toList());
            assertTrue(cssNames.contains("-rx-hgap"), "hgap is settable from CSS");
            assertTrue(cssNames.contains("-rx-vgap"), "vgap is settable from CSS");
        });
    }

    // ==================== Node mirroring ====================

    /**
     * Verifies external chip mutations (add / remove / replace / permute) stay in
     * sync with the rendered chip nodes.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void chipMutationsMirrorIntoNodes() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b");
            assertEquals(List.of("a", "b"), texts(input));

            input.getChips().add("c");
            assertEquals(List.of("a", "b", "c"), texts(input));

            input.getChips().remove("b");
            assertEquals(List.of("a", "c"), texts(input));

            input.getChips().set(0, "z");
            assertEquals(List.of("z", "c"), texts(input));

            input.getChips().setAll("banana", "apple", "cherry");
            FXCollections.sort(input.getChips());
            assertEquals(List.of("apple", "banana", "cherry"), texts(input),
                    "a permutation reorders the chip nodes to match");
        });
    }

    /**
     * Verifies removing one of two equal chips removes the exact node clicked, not
     * the first {@code equals} match — the headline JFoenix duplicate-desync fix.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void duplicateChipDeleteRemovesTheExactNode() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.setAllowDuplicates(true);
            input.getChips().addAll("dup", "dup");
            List<RXChip> nodes = chipNodesOf(input);
            RXChip first = nodes.get(0);
            RXChip second = nodes.get(1);

            // Close the SECOND duplicate.
            second.remove();

            assertEquals(List.of("dup"), input.getChips());
            assertEquals(List.of(first), chipNodesOf(input),
                    "the first node survives; index-based removal took the clicked node");
        });
    }

    // ==================== Commit policies ====================

    /**
     * Verifies STRICT rejects unmatched text: no chip, error flashed, text kept.
     */
    @Test
    public void strictPolicyRejectsUnmatchedText() {
        RXChipInput<String> input = new RXChipInput<>();
        input.setCustomInputPolicy(RXChipInput.CustomInputPolicy.STRICT);
        input.setEditorText("hello");
        input.commitInput();
        assertTrue(input.getChips().isEmpty());
        assertTrue(hasError(input));
        assertEquals("hello", input.getEditorText());
    }

    /**
     * Verifies FREE turns unmatched text into a chip and clears the editor.
     */
    @Test
    public void freePolicyAddsChip() {
        RXChipInput<String> input = new RXChipInput<>();
        input.setCustomInputPolicy(RXChipInput.CustomInputPolicy.FREE);
        input.setEditorText("tag1");
        input.commitInput();
        assertEquals(List.of("tag1"), input.getChips());
        assertEquals("", input.getEditorText());
        assertFalse(hasError(input));
    }

    /**
     * Verifies CREATE adds a chip and appends it to the suggestion source.
     */
    @Test
    public void createPolicyAddsChipAndSuggestion() {
        RXChipInput<String> input = new RXChipInput<>();
        input.setCustomInputPolicy(RXChipInput.CustomInputPolicy.CREATE);
        input.setEditorText("newtag");
        input.commitInput();
        assertEquals(List.of("newtag"), input.getChips());
        assertTrue(input.getSuggestions().contains("newtag"));
        assertEquals("", input.getEditorText());
    }

    /**
     * Verifies a converter that throws on parse rejects the text with {@code :error}.
     */
    @Test
    public void converterParseFailureFlagsError() {
        RXChipInput<Integer> input = new RXChipInput<>(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return String.valueOf(value);
            }

            @Override
            public Integer fromString(String text) {
                return Integer.parseInt(text);
            }
        });
        input.setCustomInputPolicy(RXChipInput.CustomInputPolicy.FREE);
        input.setEditorText("abc");
        input.commitInput();
        assertTrue(input.getChips().isEmpty());
        assertTrue(hasError(input));
        assertEquals("abc", input.getEditorText());
    }

    /**
     * Verifies an onCreateItem returning null rejects the text with {@code :error}.
     */
    @Test
    public void onCreateItemNullRejectsWithError() {
        RXChipInput<String> input = new RXChipInput<>();
        input.setOnCreateItem(text -> null);
        input.setEditorText("x");
        input.commitInput();
        assertTrue(input.getChips().isEmpty());
        assertTrue(hasError(input));
    }

    /**
     * Verifies duplicates are ignored (no second chip, no error) unless allowed.
     */
    @Test
    public void duplicatesIgnoredUnlessAllowed() {
        RXChipInput<String> input = new RXChipInput<>();
        input.getChips().add("a");
        input.setEditorText("a");
        input.commitInput();
        assertEquals(List.of("a"), input.getChips());
        assertFalse(hasError(input));

        input.setAllowDuplicates(true);
        input.setEditorText("a");
        input.commitInput();
        assertEquals(List.of("a", "a"), input.getChips());
    }

    // ==================== Editor keys ====================

    /**
     * Verifies Backspace on an empty editor removes the last chip.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void backspaceOnEmptyEditorRemovesLastChip() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b");
            TextField editor = (TextField) input.lookup(".editor");
            editor.fireEvent(key(KeyCode.BACK_SPACE));
            assertEquals(List.of("a"), input.getChips());
        });
    }

    // ==================== Delete event flow ====================

    /**
     * Verifies a consumed REMOVE vetoes removal, and an un-consumed one removes the
     * chip and fires REMOVED / not the added handler.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void removeIsVetoableAndFiresRemovedWhenNotVetoed() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            AtomicInteger removedCount = new AtomicInteger();
            input.setOnChipRemoved(event -> removedCount.incrementAndGet());
            input.getChips().addAll("a", "b");
            List<RXChip> nodes = chipNodesOf(input);

            // Veto the first chip's removal.
            nodes.get(0).setOnRemove(Event::consume);
            nodes.get(0).remove();
            assertEquals(List.of("a", "b"), input.getChips(), "consumed REMOVE vetoes removal");
            assertEquals(0, removedCount.get());

            // The second removes normally.
            nodes.get(1).remove();
            assertEquals(List.of("a"), input.getChips());
            assertEquals(1, removedCount.get());
        });
    }

    /**
     * Verifies adding a chip fires the ADDED handler.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void addingChipFiresAddedHandler() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            AtomicInteger addedCount = new AtomicInteger();
            input.setOnChipAdded(event -> addedCount.incrementAndGet());
            input.getChips().add("x");
            assertEquals(1, addedCount.get());
        });
    }

    // ==================== Public methods ====================

    /**
     * Verifies removeChip removes the first equal item and clearInput keeps chips.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void removeChipAndClearInput() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b", "c");
            input.setEditorText("draft");

            assertTrue(input.removeChip("b"));
            assertEquals(List.of("a", "c"), input.getChips());
            assertFalse(input.removeChip("zzz"));

            input.clearInput();
            assertEquals("", input.getEditorText());
            assertEquals(List.of("a", "c"), input.getChips(), "clearInput does not touch chips");
        });
    }

    // ==================== Layout ====================

    /**
     * Verifies the input grows taller when a narrow width wraps the chips.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void narrowWidthWrapsAndGrowsTaller() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            for (int i = 0; i < 8; i++) {
                input.getChips().add("chip-" + i);
            }
            input.applyCss();
            input.layout();
            double wide = input.prefHeight(2000.0);
            double narrow = input.prefHeight(80.0);
            assertTrue(narrow > wide, "wrapping into more rows grows the height");
        });
    }

    /**
     * Verifies the tamed editor honours editorMinWidth instead of a wide column
     * default.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void editorHonoursEditorMinWidth() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.setEditorMinWidth(40.0);
            TextField editor = (TextField) input.lookup(".editor");
            assertEquals(40.0, editor.minWidth(-1), 0.5);
        });
    }

    /**
     * Verifies maxRows caps the height and wraps the content in a scroll pane.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void maxRowsCapsHeightWithScrollPane() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            for (int i = 0; i < 12; i++) {
                input.getChips().add("chip-" + i);
            }
            input.applyCss();
            input.layout();
            double uncapped = input.prefHeight(80.0);
            input.setMaxRows(2);
            double capped = input.prefHeight(80.0);
            assertTrue(capped < uncapped, "maxRows caps the height");
            assertNotNull(input.lookup(".chip-scroll-pane"), "the content is wrapped in a scroll pane");
        });
    }

    // ==================== Suggestion popup (P4) ====================

    /**
     * Verifies the suggestion-popup property defaults and that getSuggestions is a
     * live mutable list. The interactive dropdown itself is a real-machine item.
     */
    @Test
    public void suggestionPopupPropertyDefaults() {
        RXChipInput<String> input = new RXChipInput<>();
        assertTrue(input.getSuggestions().isEmpty());
        assertSame(null, input.getFilterFunction());
        assertFalse(input.isFilterSelectedOptions());
        assertSame(null, input.getSuggestionCellFactory());
        assertEquals(RXChipInput.DEFAULT_VISIBLE_ROW_COUNT, input.getVisibleRowCount());
        assertTrue(input.isHideOnSelect());
        assertTrue(input.isAnimated());
        assertFalse(input.isPopupShowing());

        input.getSuggestions().add("apple");
        assertEquals(List.of("apple"), input.getSuggestions());
        input.setHideOnSelect(false);
        assertFalse(input.isHideOnSelect());
    }

    /**
     * Verifies the popupShowing state drives the {@code :popup-showing} pseudo-class.
     */
    @Test
    public void popupShowingTogglesPseudoClass() {
        RXChipInput<String> input = new RXChipInput<>();
        PseudoClass popupShowing = PseudoClass.getPseudoClass("popup-showing");
        assertFalse(input.getPseudoClassStates().contains(popupShowing));
        input.setPopupShowing(true);
        assertTrue(input.getPseudoClassStates().contains(popupShowing));
        input.setPopupShowing(false);
        assertFalse(input.getPseudoClassStates().contains(popupShowing));
    }

    /**
     * Verifies showSuggestions / hideSuggestions are null-safe without a skin.
     */
    @Test
    public void showHideSuggestionsAreNullSafeWithoutSkin() {
        RXChipInput<String> input = new RXChipInput<>();
        input.getSuggestions().add("a");
        input.showSuggestions();
        input.hideSuggestions();
        assertFalse(input.isPopupShowing());
    }

    /**
     * Verifies disposing the skin tears down the popup / listeners cleanly and clears
     * the mirrored popupShowing state (regression for the P4 review stuck-true finding).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void skinDisposeResetsPopupShowingAndDoesNotThrow() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getSuggestions().addAll("a", "b");
            input.getChips().add("a");
            // Simulate the dropdown being open, then dispose the skin directly: a
            // same-class setSkin is a no-op in JavaFX 17, so it would not dispose.
            input.setPopupShowing(true);
            input.getSkin().dispose();
            assertFalse(input.isPopupShowing(), "disposing the skin clears the popup-showing mirror");
        });
    }

    // ==================== Chip-focus navigation ====================

    /**
     * Verifies chip-focus navigation: Left from the empty editor focuses the last chip,
     * Left / Right / Home move focus between chips, and End returns focus to the editor.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void chipCursorNavigation() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b", "c");
            input.applyCss();
            input.layout();
            Scene scene = input.getScene();
            List<RXChip> nodes = chipNodesOf(input);
            TextField editor = (TextField) input.lookup(".editor");

            editor.requestFocus();
            assertSame(editor, scene.getFocusOwner());

            // Left from the empty editor lands on the last chip.
            editor.fireEvent(key(KeyCode.LEFT));
            assertSame(nodes.get(2), scene.getFocusOwner());

            input.fireEvent(key(KeyCode.LEFT));
            assertSame(nodes.get(1), scene.getFocusOwner());

            input.fireEvent(key(KeyCode.HOME));
            assertSame(nodes.get(0), scene.getFocusOwner());

            input.fireEvent(key(KeyCode.RIGHT));
            assertSame(nodes.get(1), scene.getFocusOwner());

            input.fireEvent(key(KeyCode.END));
            assertSame(editor, scene.getFocusOwner(), "End returns focus to the editor");
        });
    }

    /**
     * Verifies Delete on a focused chip removes it and moves focus to the chip that shifts
     * into its slot (the next one), so repeated Delete removes a run rightwards.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void deleteOnFocusedChipRemovesAndMovesFocus() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b", "c");
            input.applyCss();
            input.layout();
            Scene scene = input.getScene();
            List<RXChip> nodes = chipNodesOf(input);

            nodes.get(1).requestFocus();
            assertSame(nodes.get(1), scene.getFocusOwner());

            nodes.get(1).fireEvent(key(KeyCode.DELETE));
            assertEquals(List.of("a", "c"), input.getChips(), "Delete removes the focused chip");
            // Focus moved to the chip now occupying index 1 (the former "c").
            assertSame(chipNodesOf(input).get(1), scene.getFocusOwner());

            chipNodesOf(input).get(1).fireEvent(key(KeyCode.DELETE));
            assertEquals(List.of("a"), input.getChips(), "repeated Delete removes the run rightwards");
        });
    }

    /**
     * Verifies Backspace on a focused chip removes it and moves focus to the previous chip,
     * so repeated Backspace removes a run leftwards.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void backspaceOnFocusedChipRemovesAndFocusesPrevious() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b", "c");
            input.applyCss();
            input.layout();
            Scene scene = input.getScene();
            List<RXChip> nodes = chipNodesOf(input);

            nodes.get(2).requestFocus();
            nodes.get(2).fireEvent(key(KeyCode.BACK_SPACE));
            assertEquals(List.of("a", "b"), input.getChips(), "Backspace removes the focused chip");
            // Focus moved to the previous chip ("b").
            assertSame(chipNodesOf(input).get(1), scene.getFocusOwner());

            chipNodesOf(input).get(1).fireEvent(key(KeyCode.BACK_SPACE));
            assertEquals(List.of("a"), input.getChips(), "repeated Backspace removes the run leftwards");
            assertSame(chipNodesOf(input).get(0), scene.getFocusOwner());
        });
    }

    /**
     * Verifies a separator key (comma) commits the current text like Enter, and that
     * COMMA maps to the "," character the KEY_TYPED strip relies on.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void separatorKeyCommitsLikeEnter() throws Exception {
        assertEquals(",", KeyCode.COMMA.getChar(), "the separator-character strip depends on this");
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getSeparatorKeys().add(KeyCode.COMMA);
            input.setEditorText("tag");
            TextField editor = (TextField) input.lookup(".editor");

            editor.fireEvent(key(KeyCode.COMMA));
            assertEquals(List.of("tag"), input.getChips());
            assertEquals("", input.getEditorText());
        });
    }

    /**
     * Verifies Escape on a focused chip returns focus to the editor.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void escapeOnFocusedChipReturnsToEditor() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b");
            input.applyCss();
            input.layout();
            Scene scene = input.getScene();
            TextField editor = (TextField) input.lookup(".editor");

            chipNodesOf(input).get(0).requestFocus();
            input.fireEvent(key(KeyCode.ESCAPE));
            assertSame(editor, scene.getFocusOwner());
        });
    }

    /**
     * Verifies a modified separator chord (Shift+Comma) neither commits nor is treated
     * as a separator (regression for the P5 review modifier finding).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void shiftSeparatorDoesNotCommit() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getSeparatorKeys().add(KeyCode.COMMA);
            input.setEditorText("hello");
            TextField editor = (TextField) input.lookup(".editor");

            editor.fireEvent(keyShift(KeyCode.COMMA));
            assertTrue(input.getChips().isEmpty(), "Shift+Comma is '<', not a separator");
            assertEquals("hello", input.getEditorText());
        });
    }

    // ==================== Field interaction ====================

    /**
     * Verifies pressing the mouse on empty field area (not a chip) focuses the editor,
     * so the whole field behaves like a text input.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressingEmptyAreaFocusesEditor() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b");
            input.applyCss();
            input.layout();
            Scene scene = input.getScene();
            TextField editor = (TextField) input.lookup(".editor");

            input.fireEvent(mousePress());
            assertSame(editor, scene.getFocusOwner(), "a press on empty field area focuses the editor");
        });
    }

    /**
     * Verifies the field sizes to its content height rather than the unbounded
     * SkinBase default (which let a parent stretch it into a tall empty box).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void fieldSizesToContentNotUnbounded() throws Exception {
        runOnFx(() -> {
            RXChipInput<String> input = attach(new RXChipInput<>());
            input.getChips().addAll("a", "b");
            input.applyCss();
            input.layout();

            double pref = input.prefHeight(300.0);
            double max = input.maxHeight(300.0);
            assertEquals(pref, max, 0.5, "max height equals pref (content) height");
            assertTrue(max < 1000.0, "max height is not the unbounded Double.MAX_VALUE default");
        });
    }

    // ==================== Helpers ====================

    private static RXChipInput<String> attach(RXChipInput<String> input) {
        input.setSkin(new RXChipInputSkin<>(input));
        StackPane root = new StackPane(input);
        new Scene(root, 400.0, 200.0);
        root.applyCss();
        root.layout();
        return input;
    }

    private static List<RXChip> chipNodesOf(RXChipInput<?> input) {
        Parent content = (Parent) input.lookup(".content");
        List<RXChip> chips = new ArrayList<>();
        for (Node node : content.getChildrenUnmodifiable()) {
            if (node instanceof RXChip chip) {
                chips.add(chip);
            }
        }
        return chips;
    }

    private static List<String> texts(RXChipInput<?> input) {
        return chipNodesOf(input).stream().map(RXChip::getText).collect(Collectors.toList());
    }

    private static boolean hasError(RXChipInput<?> input) {
        return input.getPseudoClassStates().contains(ERROR);
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static KeyEvent keyShift(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, true, false, false, false);
    }

    private static MouseEvent mousePress() {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 10.0, 10.0, 10.0, 10.0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, false, null);
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception exception) {
            throw exception;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
