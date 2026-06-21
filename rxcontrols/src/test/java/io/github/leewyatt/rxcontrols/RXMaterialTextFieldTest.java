package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.RXFieldVariant;
import io.github.leewyatt.rxcontrols.skins.RXMaterialTextFieldSkin;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless unit tests for {@link RXMaterialTextField} and its skin: property
 * plumbing, variant / invalid / floated pseudo-classes, CSS metadata, the band
 * height contract (label + line + supporting, each counted once; max == pref),
 * prompt-text label fallback, accessible name, hit-test correctness, snapped
 * animation end-values, and the built-in clear button (presence by editable +
 * showClearButton, opacity following text, stable reserved width, pickability,
 * coexistence with a trailing node, and click-to-clear). The snap-vs-animate
 * gate while showing lives in {@link RXMaterialTextFieldUITest}.
 */
public class RXMaterialTextFieldTest {

    private static final PseudoClass UNDERLINE = PseudoClass.getPseudoClass("underline");
    private static final PseudoClass FILLED = PseudoClass.getPseudoClass("filled");
    private static final PseudoClass OUTLINED = PseudoClass.getPseudoClass("outlined");
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final PseudoClass FLOATED = PseudoClass.getPseudoClass("floated");

    /**
     * Starts the toolkit and pins Modena so {@code -rx-*} tokens (which alias
     * {@code -fx-*}) resolve deterministically.
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

    // ==================== Enum ====================

    @Test
    public void fromKeywordNormalizesAndReturnsNullForUnknown() {
        assertEquals(RXFieldVariant.UNDERLINE, RXFieldVariant.fromKeyword("underline"));
        assertEquals(RXFieldVariant.FILLED, RXFieldVariant.fromKeyword("  FILLED "));
        assertEquals(RXFieldVariant.OUTLINED, RXFieldVariant.fromKeyword("OUTLINED"));
        assertNull(RXFieldVariant.fromKeyword("bogus"));
        assertNull(RXFieldVariant.fromKeyword(null));
    }

    // ==================== Property plumbing ====================

    @Test
    public void defaultsAndPlumbing() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            assertEquals(RXFieldVariant.UNDERLINE, field.getVariant());
            assertTrue(field.isFloatingLabel());
            assertTrue(field.isAnimated());
            assertTrue(field.isShowClearButton());
            assertFalse(field.isInvalid());
            assertEquals(Duration.millis(180.0), field.getAnimationDuration());
            assertEquals(0.85, field.getLabelFloatScale(), 0.0);
            assertEquals("", field.getLabelText());
            assertEquals(Insets.EMPTY, field.getTextPadding());

            field.setLabelText("Name");
            field.setHelperText("required");
            field.setErrorText("too short");
            field.setVariant(RXFieldVariant.FILLED);
            field.setFloatingLabel(false);
            field.setAnimated(false);
            field.setAnimationDuration(Duration.millis(90));
            field.setLabelFloatScale(0.7);
            field.setShowClearButton(false);
            Label leading = new Label("@");
            field.setLeadingNode(leading);
            Label trailing = new Label("x");
            field.setTrailingNode(trailing);
            Insets padding = new Insets(0, 4, 0, 6);
            field.setTextPadding(padding);

            assertEquals("Name", field.getLabelText());
            assertEquals("required", field.getHelperText());
            assertEquals("too short", field.getErrorText());
            assertEquals(RXFieldVariant.FILLED, field.getVariant());
            assertFalse(field.isFloatingLabel());
            assertFalse(field.isAnimated());
            assertEquals(Duration.millis(90), field.getAnimationDuration());
            assertEquals(0.7, field.getLabelFloatScale(), 0.0);
            assertFalse(field.isShowClearButton());
            assertEquals(leading, field.getLeadingNode());
            assertEquals(trailing, field.getTrailingNode());
            assertEquals(padding, field.getTextPadding());
        });
    }

    // ==================== Pseudo-classes ====================

    @Test
    public void variantDrivesPseudoClasses() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            assertTrue(field.getPseudoClassStates().contains(UNDERLINE));
            assertFalse(field.getPseudoClassStates().contains(FILLED));

            field.setVariant(RXFieldVariant.FILLED);
            assertFalse(field.getPseudoClassStates().contains(UNDERLINE));
            assertTrue(field.getPseudoClassStates().contains(FILLED));

            field.setVariant(RXFieldVariant.OUTLINED);
            assertTrue(field.getPseudoClassStates().contains(OUTLINED));
            assertFalse(field.getPseudoClassStates().contains(FILLED));

            // null is lenient: getter returns null, pseudo-class falls back to default.
            field.setVariant(null);
            assertNull(field.getVariant());
            assertTrue(field.getPseudoClassStates().contains(UNDERLINE));
            assertFalse(field.getPseudoClassStates().contains(OUTLINED));
        });
    }

    @Test
    public void invalidDrivesPseudoClass() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            assertFalse(field.getPseudoClassStates().contains(INVALID));
            field.setInvalid(true);
            assertTrue(field.getPseudoClassStates().contains(INVALID));
            field.setInvalid(false);
            assertFalse(field.getPseudoClassStates().contains(INVALID));
        });
    }

    @Test
    public void floatedReflectsTextAndFloatingLabelToggle() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            inScene(field);

            // floatingLabel on, empty, unfocused -> resting (not floated).
            assertFalse(field.getPseudoClassStates().contains(FLOATED));

            // non-empty text floats the label without needing focus.
            field.setText("x");
            field.applyCss();
            field.layout();
            assertTrue(field.getPseudoClassStates().contains(FLOATED));

            // floatingLabel off -> always floated (static top label).
            field.setText("");
            field.setFloatingLabel(false);
            field.applyCss();
            field.layout();
            assertTrue(field.getPseudoClassStates().contains(FLOATED));
        });
    }

    // ==================== CSS metadata ====================

    @Test
    public void cssMetadataExposesMaterialStyleables() {
        Set<String> names = RXMaterialTextField.getClassCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .collect(Collectors.toSet());
        assertTrue(names.contains("-rx-field-variant"), names::toString);
        assertTrue(names.contains("-rx-floating-label"), names::toString);
        assertTrue(names.contains("-rx-animated"), names::toString);
        assertTrue(names.contains("-rx-animation-duration"), names::toString);
        assertTrue(names.contains("-rx-label-float-scale"), names::toString);
        assertTrue(names.contains("-rx-text-padding"), names::toString);

        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            assertEquals(RXMaterialTextField.getClassCssMetaData(), field.getControlCssMetaData());
        });
    }

    // ==================== Structure ====================

    @Test
    public void skinBuildsDecorationNodes() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setHelperText("required");
            inScene(field);

            assertNotNull(floatingLabel(field), "floating label node missing");
            assertEquals("Name", floatingLabel(field).getText());
            assertNotNull(field.lookup(".activation-line"), "activation line missing");
            assertNotNull(field.lookup(".accent-line"), "accent line missing");
            assertNotNull(field.lookup(".supporting"), "supporting row missing");

            Region accent = (Region) field.lookup(".accent-line");
            assertEquals(0.0, accent.getOpacity(), 0.0, "accent line should be hidden when unfocused");
        });
    }

    // ==================== Animation end values ====================

    @Test
    public void labelSnapsBetweenRestingAndFloatedEndValues() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setLabelFloatScale(0.7);
            inScene(field);
            Label label = floatingLabel(field);
            Scale scale = scaleOf(label);
            assertNotNull(scale, "label scale transform missing");

            // empty + unfocused -> resting end value
            assertEquals(0.0, label.getTranslateY(), 0.001, "resting label must not be translated");
            assertEquals(1.0, scale.getX(), 0.001, "resting label must be full scale");

            // non-empty floats the label (no focus needed) -> floated end value
            field.setText("hello");
            field.applyCss();
            field.layout();
            assertTrue(label.getTranslateY() < 0.0,
                    "floated label must move up; translateY=" + label.getTranslateY());
            assertEquals(0.7, scale.getX(), 0.001, "floated scale must equal labelFloatScale");
            assertEquals(0.7, scale.getY(), 0.001);
        });
    }

    @Test
    public void floatingLabelDisabledKeepsLabelFloated() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setFloatingLabel(false);
            inScene(field);
            Label label = floatingLabel(field);
            assertTrue(label.getTranslateY() < 0.0,
                    "floatingLabel=false must keep the label in the floated position");
            assertEquals(field.getLabelFloatScale(), scaleOf(label).getX(), 0.001);
        });
    }

    @Test
    public void accentLineIsCollapsedWhenUnfocused() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            inScene(field);
            Region accent = (Region) field.lookup(".accent-line");
            assertNotNull(accent, "accent line missing");
            assertEquals(0.0, accent.getOpacity(), 0.001, "accent line must be hidden when unfocused");
            assertEquals(0.05, scaleOf(accent).getX(), 0.001,
                    "accent line must be horizontally collapsed when unfocused");
        });
    }

    // The snap-vs-animate gate while showing (animated flag / duration<=0) lives in
    // the @Tag("ui") RXMaterialTextFieldUITest. The focus-driven states — the focus
    // branch of :floated and the accent-line expansion to scaleX=1 / opacity=1 — are
    // verified manually / via TestFX (design §19); headless focus is unreliable.

    // ==================== Height bands ====================

    @Test
    public void labelBandReservesHeight() {
        runOnFx(() -> {
            RXMaterialTextField noLabel = inScene(new RXMaterialTextField());
            RXMaterialTextField withLabel = new RXMaterialTextField();
            withLabel.setLabelText("Name");
            inScene(withLabel);
            assertTrue(withLabel.prefHeight(-1) > noLabel.prefHeight(-1),
                    "a floating label must reserve a top band");
            assertTrue(withLabel.minHeight(-1) > noLabel.minHeight(-1),
                    "the label band must also be reflected in min height");
        });
    }

    @Test
    public void supportingBandReservesHeight() {
        runOnFx(() -> {
            RXMaterialTextField noHelper = new RXMaterialTextField();
            noHelper.setLabelText("Name");
            inScene(noHelper);
            RXMaterialTextField withHelper = new RXMaterialTextField();
            withHelper.setLabelText("Name");
            withHelper.setHelperText("required");
            inScene(withHelper);
            assertTrue(withHelper.prefHeight(-1) > noHelper.prefHeight(-1),
                    "helper text must reserve a supporting band");
        });
    }

    @Test
    public void maxHeightTracksBandInclusivePrefHeight() {
        runOnFx(() -> {
            RXMaterialTextField bare = inScene(new RXMaterialTextField());
            RXMaterialTextField banded = new RXMaterialTextField();
            banded.setLabelText("Name");
            banded.setHelperText("required");
            inScene(banded);

            double bandedMax = banded.maxHeight(-1);
            // max must equal pref (a field does not grow vertically) ...
            assertEquals(banded.prefHeight(-1), bandedMax, 0.01,
                    "max height must equal pref height");
            // ... be finite (not the SkinBase Double.MAX_VALUE default) ...
            assertTrue(bandedMax < 10_000, "max height must be finite, not MAX_VALUE");
            // ... and be band-inclusive: a labelled+helper field is taller than a bare one,
            // so the bands genuinely flow through into max (not just into pref).
            assertTrue(bandedMax > bare.maxHeight(-1),
                    "label + supporting bands must be reflected in max height");
        });
    }

    @Test
    public void invalidToggleKeepsSupportingBandStable() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setHelperText("required");
            field.setErrorText("too short");
            inScene(field);
            double before = field.prefHeight(-1);
            field.setInvalid(true);
            field.applyCss();
            field.layout();
            double after = field.prefHeight(-1);
            assertEquals(before, after, 0.01,
                    "switching helper<->error must not resize the field (band stays reserved, counted once)");
        });
    }

    // ==================== Label fallback + accessibility ====================

    @Test
    public void labelFallsBackToPromptText() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setPromptText("Search");
            inScene(field);
            assertEquals("Search", floatingLabel(field).getText(),
                    "blank labelText must fall back to promptText as the label source");

            field.setLabelText("Query");
            field.applyCss();
            field.layout();
            assertEquals("Query", floatingLabel(field).getText(),
                    "explicit labelText must win over promptText");
        });
    }

    @Test
    public void accessibleNameComesFromLabel() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            inScene(field);
            field.setLabelText("Email");
            field.applyCss();
            field.layout();
            assertEquals("Email", field.getAccessibleText(),
                    "the floating label must supply the accessible name");
        });
    }

    // ==================== Hit-test ====================

    @Test
    public void getIndexMapsHitsAcrossTheEditorLine() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField("Hello World");
            field.setLabelText("Name");
            field.setHelperText("required");
            inScene(field, 320, 160);

            Node textNode = editorTextNode(field);
            assertNotNull(textNode, "editor text node not found");
            assertNotNull(field.getSkin(), "skin not created");
            RXMaterialTextFieldSkin skin = (RXMaterialTextFieldSkin) field.getSkin();

            // getIndex expects control-local coordinates (as MouseEvent delivers).
            // Convert the editor text node's geometry into the control's frame.
            Bounds tb = textNode.getBoundsInLocal();
            Point2D centerScene = textNode.localToScene(tb.getMinX() + tb.getWidth() / 2.0,
                    tb.getMinY() + tb.getHeight() / 2.0);
            double y = field.sceneToLocal(centerScene).getY();
            double textLeftLocal = field.sceneToLocal(textNode.localToScene(tb.getMinX(), 0)).getX();

            int atStart = skin.getIndex(textLeftLocal + 1, y).getInsertionIndex();
            int nearMid = skin.getIndex(textLeftLocal + tb.getWidth() / 2.0, y).getInsertionIndex();
            int atEnd = skin.getIndex(textLeftLocal + tb.getWidth() + 40, y).getInsertionIndex();

            assertEquals(0, atStart, "click at the text start must map to index 0");
            assertTrue(nearMid > atStart && nearMid < atEnd,
                    "a mid click must land strictly between start and end; mid=" + nearMid);
            assertEquals("Hello World".length(), atEnd,
                    "a click past the text end must map to the last insertion index");
        });
    }

    @Test
    public void floatingLabelAlignsWithEditorTextWhenLeadingPresent() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField("value");
            field.setLabelText("Name");
            Region leading = new Region();
            leading.setMinSize(20, 20);
            leading.setPrefSize(20, 20);
            leading.setMaxSize(20, 20);
            field.setLeadingNode(leading);
            inScene(field, 320, 160);

            Label label = floatingLabel(field);
            Node textNode = editorTextNode(field);
            assertNotNull(label, "floating label missing");
            assertNotNull(textNode, "editor text node missing");

            double labelX = field.sceneToLocal(label.localToScene(0, 0)).getX();
            double textX = field.sceneToLocal(textNode.localToScene(0, 0)).getX();
            // Without the editorLeftOffset() fix the label would sit ~one left-inset
            // (~7px) to the right of the editor text; the fix collapses that gap.
            assertEquals(textX, labelX, 2.0,
                    "with a leading node, the floating label must start where the editor text starts");
        });
    }

    // ==================== Clear button ====================

    @Test
    public void clearButtonOpacityFollowsText() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            inScene(field);
            StackPane clear = clearButton(field);
            assertNotNull(clear, "clear button must be present when editable + showClearButton");
            assertTrue(clear.isManaged(), "clear button reserves space while the affordance is active");
            assertEquals(0.0, clear.getOpacity(), 0.001, "empty field -> clear faded out");

            field.setText("abc");
            assertEquals(1.0, clear.getOpacity(), 0.001, "non-empty field -> clear visible");

            field.setText("");
            assertEquals(0.0, clear.getOpacity(), 0.001, "cleared field -> clear faded out again");
        });
    }

    @Test
    public void clearButtonAbsentWhenNotEditableOrShowClearButtonFalse() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField("abc");
            field.setLabelText("Name");
            inScene(field);
            assertNotNull(clearButton(field), "precondition: clear present");

            field.setEditable(false);
            assertNull(clearButton(field), "non-editable field must not offer a clear button");

            field.setEditable(true);
            field.setShowClearButton(false);
            assertNull(clearButton(field), "showClearButton=false removes the clear button");
        });
    }

    @Test
    public void clearButtonCoexistsWithTrailingNode() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField("abc");
            field.setLabelText("Name");
            Label trailing = new Label("T");
            field.setTrailingNode(trailing);
            inScene(field);

            StackPane clear = clearButton(field);
            assertNotNull(clear, "clear button must coexist with a user trailing node");
            assertEquals(1.0, clear.getOpacity(), 0.001, "non-empty -> clear visible alongside trailing node");
            assertNotNull(trailing.getParent(), "user trailing node must remain in the scene graph");
            // Both share the same trailing container; neither overwrites the other.
            assertEquals(trailing.getParent(), clear.getParent(),
                    "user trailing and clear button share the internal trailing container");
            HBox container = (HBox) clear.getParent();
            assertTrue(container.getChildren().indexOf(trailing) < container.getChildren().indexOf(clear),
                    "user trailing node must precede the built-in clear button");
        });
    }

    @Test
    public void clearButtonNotPickableWhenEmpty() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            inScene(field);
            StackPane clear = clearButton(field);
            assertNotNull(clear);
            assertTrue(clear.isMouseTransparent(),
                    "an empty field's faded clear button must not intercept editor clicks");
            field.setText("abc");
            assertFalse(clear.isMouseTransparent(),
                    "a non-empty field's visible clear button must be pickable");
        });
    }

    @Test
    public void clearButtonReservesStableWidthAcrossEmptyToNonEmpty() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            inScene(field, 320, 120);
            Region rightWrapper = (Region) field.lookup(".right-wrapper");
            assertNotNull(rightWrapper, "active clear button must reserve a right wrapper");
            double emptyWidth = rightWrapper.getWidth();
            assertTrue(emptyWidth > 0.0, "the clear button reserves space while active");

            field.setText("abc");
            field.applyCss();
            field.layout();
            assertEquals(emptyWidth, rightWrapper.getWidth(), 0.5,
                    "empty<->non-empty must not change the reserved width (no jump)");
        });
    }

    @Test
    public void deactivatingClearReclaimsWidthWithTrailingPresent() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField("abc");
            field.setLabelText("Name");
            field.setTrailingNode(new Label("T"));
            inScene(field, 320, 120);
            double withClear = field.prefWidth(-1);

            field.setShowClearButton(false);
            double withoutClear = field.prefWidth(-1);
            assertTrue(withoutClear < withClear,
                    "deactivating the clear button must reclaim its reserved width even with a "
                            + "trailing node present (withClear=" + withClear + " withoutClear=" + withoutClear + ")");
        });
    }

    @Test
    public void clickingClearButtonEmptiesTheField() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField("secret");
            field.setLabelText("Name");
            inScene(field);
            StackPane clear = clearButton(field);
            assertNotNull(clear);
            clear.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, false, false, true, null));
            assertEquals("", field.getText(), "clicking the clear button must empty the field");
        });
    }

    // ==================== FILLED variant + supporting ====================

    @Test
    public void filledVariantFillsTheContainerWithSurfaceVariant() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setVariant(RXFieldVariant.FILLED);
            inScene(field);
            Region container = (Region) field.lookup(".container");
            assertNotNull(container, "filled container node must exist");
            assertNotNull(container.getBackground(), "FILLED variant must fill the container");
            assertFalse(container.getBackground().getFills().isEmpty(), "FILLED container must have a fill");
            Paint fill = container.getBackground().getFills().get(0).getFill();
            // pinned Modena baseline: -rx-surface-variant resolves to #f5f5f5 and must be opaque
            // (a regression to `transparent` would still be a non-empty fill, hence this check)
            assertEquals(Color.web("#f5f5f5"), fill,
                    "FILLED container must resolve -rx-surface-variant (#f5f5f5 under the Modena baseline)");
        });
    }

    @Test
    public void underlineVariantLeavesContainerUnfilled() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name"); // default UNDERLINE
            inScene(field);
            Region container = (Region) field.lookup(".container");
            assertNotNull(container, "container node exists in every variant");
            assertTrue(container.getBackground() == null || container.getBackground().getFills().isEmpty(),
                    "UNDERLINE variant must not fill the container");
        });
    }

    @Test
    public void filledContainerSitsBehindAndExcludesSupporting() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setHelperText("required");
            field.setVariant(RXFieldVariant.FILLED);
            inScene(field, 320, 140);
            Region container = (Region) field.lookup(".container");
            Region supporting = (Region) field.lookup(".supporting");
            assertNotNull(container);
            assertNotNull(supporting);
            assertEquals(0, field.getChildrenUnmodifiable().indexOf(container),
                    "filled box must sit at z-index 0 (behind the editor + lines)");
            assertTrue(container.isMouseTransparent(), "filled box must be mouse-transparent");
            assertFalse(container.isManaged(), "filled box must be unmanaged (reserves no layout space)");
            assertTrue(container.getBoundsInParent().getMaxY() <= supporting.getBoundsInParent().getMinY() + 0.5,
                    "filled box must stop above the supporting row");
            assertEquals(field.getWidth(), container.getWidth(), 1.0,
                    "filled box must span the full control width");
        });
    }

    @Test
    public void filledVariantUsesSameHeightAsUnderline() {
        runOnFx(() -> {
            RXMaterialTextField underline = new RXMaterialTextField();
            underline.setLabelText("Name");
            underline.setHelperText("required");
            inScene(underline);
            RXMaterialTextField filled = new RXMaterialTextField();
            filled.setLabelText("Name");
            filled.setHelperText("required");
            filled.setVariant(RXFieldVariant.FILLED);
            inScene(filled);
            assertEquals(underline.prefHeight(-1), filled.prefHeight(-1), 0.01,
                    "the filled box is unmanaged; FILLED must use the same band math as UNDERLINE");
        });
    }

    @Test
    public void variantToggleRefillsAndClearsContainer() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            inScene(field); // UNDERLINE
            Region container = (Region) field.lookup(".container");
            assertNotNull(container);
            assertTrue(container.getBackground() == null || container.getBackground().getFills().isEmpty(),
                    "UNDERLINE: unfilled");

            field.setVariant(RXFieldVariant.FILLED);
            field.applyCss();
            field.layout();
            assertNotNull(container.getBackground(), "FILLED after toggle: filled");

            field.setVariant(RXFieldVariant.UNDERLINE);
            field.applyCss();
            field.layout();
            assertTrue(container.getBackground() == null || container.getBackground().getFills().isEmpty(),
                    "back to UNDERLINE: unfilled again");
        });
    }

    @Test
    public void supportingSwitchesBetweenHelperAndError() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setHelperText("required");
            field.setErrorText("too short");
            inScene(field);
            Label helper = (Label) field.lookup(".helper");
            Label error = (Label) field.lookup(".error");
            assertNotNull(helper, "helper label missing");
            assertNotNull(error, "error label missing");

            // valid -> helper shown, error hidden
            assertTrue(helper.isVisible() && helper.isManaged(), "valid: helper visible");
            assertFalse(error.isVisible(), "valid: error hidden");

            // invalid + errorText -> error replaces helper in the same slot
            field.setInvalid(true);
            assertTrue(error.isVisible() && error.isManaged(), "invalid: error visible");
            assertFalse(helper.isVisible(), "invalid: helper hidden");
            assertEquals("too short", error.getText());

            // invalid + no errorText -> helper stays (it turns red via :invalid CSS)
            field.setErrorText("");
            assertTrue(helper.isVisible(), "invalid without errorText: helper shown");
            assertFalse(error.isVisible(), "invalid without errorText: error hidden");
        });
    }

    // ==================== Icon alignment / readonly ====================

    @Test
    public void clearButtonIsVerticallyCenteredOnEditorLine() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField("hello");
            field.setLabelText("Name");
            field.setHelperText("required");
            inScene(field, 320, 140);
            StackPane clear = clearButton(field);
            Node textNode = editorTextNode(field);
            assertNotNull(clear);
            assertNotNull(textNode);
            assertEquals(centerYInControl(field, textNode), centerYInControl(field, clear), 4.0,
                    "the built-in clear icon must sit on the editor text line, not in a band");
        });
    }

    @Test
    public void readonlyPseudoClassIsManagedByTextInputControl() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            PseudoClass readonly = PseudoClass.getPseudoClass("readonly");
            // The control does not declare :readonly; TextInputControl flips it from editable.
            assertFalse(field.getPseudoClassStates().contains(readonly));
            field.setEditable(false);
            assertTrue(field.getPseudoClassStates().contains(readonly),
                    "non-editable field must expose :readonly (auto-managed by TextInputControl)");
            field.setEditable(true);
            assertFalse(field.getPseudoClassStates().contains(readonly));
        });
    }

    @Test
    public void hasSideNodePseudoClassesReflectEffectiveSlots() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            inScene(field);
            PseudoClass hasLeft = PseudoClass.getPseudoClass("has-left-node");
            PseudoClass hasRight = PseudoClass.getPseudoClass("has-right-node");

            assertFalse(field.getPseudoClassStates().contains(hasLeft), "no leading node -> no :has-left-node");
            field.setLeadingNode(new Label("@"));
            assertTrue(field.getPseudoClassStates().contains(hasLeft), "leading node -> :has-left-node");

            // The built-in trailing affordance container is the effective right slot,
            // so a default (editable + showClearButton) field has :has-right-node.
            assertTrue(field.getPseudoClassStates().contains(hasRight),
                    "active clear affordance occupies the right slot -> :has-right-node");
            field.setShowClearButton(false);
            assertFalse(field.getPseudoClassStates().contains(hasRight),
                    "no trailing node and no clear affordance -> no :has-right-node");
            field.setTrailingNode(new Label("x"));
            assertTrue(field.getPseudoClassStates().contains(hasRight),
                    "user trailing node -> :has-right-node");
        });
    }

    // ==================== Dispose ====================

    @Test
    public void disposeDetachesListeners() {
        runOnFx(() -> {
            RXMaterialTextField field = new RXMaterialTextField();
            field.setLabelText("Name");
            field.setHelperText("required");
            inScene(field);
            Label label = floatingLabel(field);
            assertNotNull(label, "floating label missing");
            assertEquals("Name", label.getText());

            field.getSkin().dispose();

            // After dispose the skin's listeners must be detached: mutating the
            // observed properties must NOT reach the now-orphaned decoration.
            // (Mutating text is avoided — it trips JFX TextFieldSkin's own
            // post-dispose internals, unrelated to this skin's cleanup.)
            field.setLabelText("Changed");
            field.setHelperText("other");
            field.setInvalid(true);
            assertEquals("Name", label.getText(),
                    "the label-source listener must be detached after dispose");
        });
    }

    // ==================== Helpers ====================

    private static Label floatingLabel(RXMaterialTextField field) {
        // helper / error are also Labels (built-in ".label"); the floating label
        // is the direct child of the control, never under ".supporting".
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

    private static StackPane clearButton(RXMaterialTextField field) {
        Node node = field.lookup(".clear-button");
        return node instanceof StackPane stackPane ? stackPane : null;
    }

    private static double centerYInControl(Node control, Node node) {
        Bounds local = node.getBoundsInLocal();
        double topY = control.sceneToLocal(node.localToScene(0, local.getMinY())).getY();
        double bottomY = control.sceneToLocal(node.localToScene(0, local.getMaxY())).getY();
        return (topY + bottomY) / 2.0;
    }

    private static Scale scaleOf(Node node) {
        return node.getTransforms().stream()
                .filter(Scale.class::isInstance)
                .map(Scale.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static Node editorTextNode(RXMaterialTextField field) {
        for (Node node : field.lookupAll(".text")) {
            if (node.layoutXProperty().isBound()) {
                return node;
            }
        }
        return null;
    }

    private static RXMaterialTextField inScene(RXMaterialTextField field) {
        return inScene(field, 300, 200);
    }

    private static RXMaterialTextField inScene(RXMaterialTextField field, double w, double h) {
        StackPane root = new StackPane(field);
        new Scene(root, w, h);
        root.applyCss();
        root.layout();
        return field;
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
