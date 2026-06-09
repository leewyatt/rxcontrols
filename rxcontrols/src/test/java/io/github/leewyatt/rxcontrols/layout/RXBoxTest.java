package io.github.leewyatt.rxcontrols.layout;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout parity tests for {@link RXBox}.
 */
public class RXBoxTest {

    private static final double EPSILON = 0.0001;

    /**
     * Verifies default state and child constraint helpers.
     */
    @Test
    public void defaultStateAndConstraints() {
        FixedRegion first = fixedRegion(30.0, 10.0);
        FixedRegion second = fixedRegion(40.0, 20.0);
        RXBox box = new RXBox(first, second);

        assertSame(Orientation.HORIZONTAL, box.getOrientation());
        assertTrue(box.getStyleClass().contains("rx-box"));
        assertSame(first, box.getChildren().get(0));
        assertSame(second, box.getChildren().get(1));

        Insets margin = new Insets(1.0, 2.0, 3.0, 4.0);
        RXBox.setGrow(first, Priority.ALWAYS);
        RXBox.setMargin(first, margin);
        assertSame(Priority.ALWAYS, RXBox.getGrow(first));
        assertEquals(margin, RXBox.getMargin(first));

        RXBox.clearConstraints(first);
        assertNull(RXBox.getGrow(first));
        assertNull(RXBox.getMargin(first));
    }

    /**
     * Verifies a null orientation is accepted and degrades to the default at layout time.
     */
    @Test
    public void orientationNullDegradesToDefault() {
        FixedRegion first = fixedRegion(30.0, 10.0);
        FixedRegion second = fixedRegion(40.0, 20.0);
        RXBox box = new RXBox(Orientation.VERTICAL, first, second);

        box.setOrientation(null);

        layout(box, 100.0, 100.0);

        // null orientation resolves to the default (HORIZONTAL): children form a row.
        assertTrue(second.getLayoutX() > first.getLayoutX(),
                "null orientation lays out as the default (horizontal)");
    }

    /**
     * Verifies non-finite spacing is accepted and clamped to the default at layout time.
     */
    @Test
    public void spacingNonFiniteClampsToDefault() {
        FixedRegion nanFirst = fixedRegion(30.0, 10.0);
        FixedRegion nanSecond = fixedRegion(40.0, 20.0);
        RXBox nanBox = new RXBox(Orientation.HORIZONTAL, nanFirst, nanSecond);
        nanBox.setSpacing(Double.NaN);

        FixedRegion zeroFirst = fixedRegion(30.0, 10.0);
        FixedRegion zeroSecond = fixedRegion(40.0, 20.0);
        RXBox zeroBox = new RXBox(Orientation.HORIZONTAL, 0.0, zeroFirst, zeroSecond);

        assertClose(zeroBox.prefWidth(-1), nanBox.prefWidth(-1), "nan pref width");
        layout(nanBox, 200.0, 40.0);
        layout(zeroBox, 200.0, 40.0);
        assertNodeMatches(zeroSecond, nanSecond, "nan second");

        nanBox.setSpacing(Double.POSITIVE_INFINITY);
        layout(nanBox, 200.0, 40.0);
        assertNodeMatches(zeroSecond, nanSecond, "infinite second");
    }

    /**
     * Verifies negative finite spacing is preserved and not clamped.
     */
    @Test
    public void spacingNegativeFiniteIsPreserved() {
        RXBox box = new RXBox();
        box.setSpacing(-4.0);

        assertClose(-4.0, box.getSpacing(), "spacing");
    }

    /**
     * Verifies a null alignment is accepted and degrades to the default at layout time.
     */
    @Test
    public void alignmentNullDegradesToDefault() {
        FixedRegion nullFirst = fixedRegion(30.0, 10.0);
        FixedRegion nullSecond = fixedRegion(40.0, 20.0);
        RXBox nullBox = new RXBox(Orientation.HORIZONTAL, nullFirst, nullSecond);
        nullBox.setAlignment(Pos.BOTTOM_RIGHT);
        nullBox.setAlignment(null);

        FixedRegion defaultFirst = fixedRegion(30.0, 10.0);
        FixedRegion defaultSecond = fixedRegion(40.0, 20.0);
        RXBox defaultBox = new RXBox(Orientation.HORIZONTAL, defaultFirst, defaultSecond);
        defaultBox.setAlignment(RXBox.DEFAULT_ALIGNMENT);

        layout(nullBox, 200.0, 80.0);
        layout(defaultBox, 200.0, 80.0);

        assertNodeMatches(defaultFirst, nullFirst, "first");
        assertNodeMatches(defaultSecond, nullSecond, "second");
    }

    /**
     * Verifies horizontal size and child layout against {@link HBox}.
     */
    @Test
    public void horizontalLayoutMatchesHBox() {
        FixedRegion hFirst = fixedRegion(20.0, 8.0, 30.0, 10.0, 80.0, 100.0);
        FixedRegion hSecond = fixedRegion(15.0, 9.0, 40.0, 20.0, 70.0, 100.0);
        FixedRegion hThird = fixedRegion(10.0, 7.0, 25.0, 15.0, 60.0, 100.0);
        HBox hbox = new HBox(8.0, hFirst, hSecond, hThird);
        hbox.setAlignment(Pos.CENTER_RIGHT);
        hbox.setFillHeight(true);
        hbox.setPadding(new Insets(3.0, 4.0, 5.0, 6.0));
        HBox.setMargin(hSecond, new Insets(2.0, 3.0, 4.0, 5.0));
        HBox.setHgrow(hSecond, Priority.ALWAYS);

        FixedRegion rFirst = fixedRegion(20.0, 8.0, 30.0, 10.0, 80.0, 100.0);
        FixedRegion rSecond = fixedRegion(15.0, 9.0, 40.0, 20.0, 70.0, 100.0);
        FixedRegion rThird = fixedRegion(10.0, 7.0, 25.0, 15.0, 60.0, 100.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 8.0, rFirst, rSecond, rThird);
        rxBox.setAlignment(Pos.CENTER_RIGHT);
        rxBox.setFillCrossAxis(true);
        rxBox.setPadding(new Insets(3.0, 4.0, 5.0, 6.0));
        RXBox.setMargin(rSecond, new Insets(2.0, 3.0, 4.0, 5.0));
        RXBox.setGrow(rSecond, Priority.ALWAYS);

        assertClose(hbox.prefWidth(-1), rxBox.prefWidth(-1), "pref width");
        assertClose(hbox.prefHeight(-1), rxBox.prefHeight(-1), "pref height");
        layout(hbox, 220.0, 90.0);
        layout(rxBox, 220.0, 90.0);

        assertPaneMatches(hbox, rxBox);
        assertNodeMatches(hFirst, rFirst, "first");
        assertNodeMatches(hSecond, rSecond, "second");
        assertNodeMatches(hThird, rThird, "third");
    }

    /**
     * Verifies vertical size and child layout against {@link VBox}.
     */
    @Test
    public void verticalLayoutMatchesVBox() {
        FixedRegion vFirst = fixedRegion(20.0, 8.0, 30.0, 10.0, 100.0, 50.0);
        FixedRegion vSecond = fixedRegion(15.0, 9.0, 40.0, 20.0, 100.0, 80.0);
        FixedRegion vThird = fixedRegion(10.0, 7.0, 25.0, 15.0, 100.0, 60.0);
        VBox vbox = new VBox(6.0, vFirst, vSecond, vThird);
        vbox.setAlignment(Pos.BOTTOM_CENTER);
        vbox.setFillWidth(true);
        vbox.setPadding(new Insets(3.0, 4.0, 5.0, 6.0));
        VBox.setMargin(vSecond, new Insets(2.0, 3.0, 4.0, 5.0));
        VBox.setVgrow(vSecond, Priority.ALWAYS);

        FixedRegion rFirst = fixedRegion(20.0, 8.0, 30.0, 10.0, 100.0, 50.0);
        FixedRegion rSecond = fixedRegion(15.0, 9.0, 40.0, 20.0, 100.0, 80.0);
        FixedRegion rThird = fixedRegion(10.0, 7.0, 25.0, 15.0, 100.0, 60.0);
        RXBox rxBox = new RXBox(Orientation.VERTICAL, 6.0, rFirst, rSecond, rThird);
        rxBox.setAlignment(Pos.BOTTOM_CENTER);
        rxBox.setFillCrossAxis(true);
        rxBox.setPadding(new Insets(3.0, 4.0, 5.0, 6.0));
        RXBox.setMargin(rSecond, new Insets(2.0, 3.0, 4.0, 5.0));
        RXBox.setGrow(rSecond, Priority.ALWAYS);

        assertClose(vbox.prefWidth(-1), rxBox.prefWidth(-1), "pref width");
        assertClose(vbox.prefHeight(-1), rxBox.prefHeight(-1), "pref height");
        layout(vbox, 140.0, 170.0);
        layout(rxBox, 140.0, 170.0);

        assertPaneMatches(vbox, rxBox);
        assertNodeMatches(vFirst, rFirst, "first");
        assertNodeMatches(vSecond, rSecond, "second");
        assertNodeMatches(vThird, rThird, "third");
    }

    /**
     * Verifies grow priority distribution on the main axis.
     */
    @Test
    public void growPriorityMatchesHBox() {
        FixedRegion hFirst = fixedRegion(10.0, 10.0, 20.0, 10.0, 70.0, 40.0);
        FixedRegion hSecond = fixedRegion(10.0, 10.0, 20.0, 10.0, 80.0, 40.0);
        FixedRegion hThird = fixedRegion(10.0, 10.0, 20.0, 10.0, 90.0, 40.0);
        HBox hbox = new HBox(0.0, hFirst, hSecond, hThird);
        HBox.setHgrow(hFirst, Priority.ALWAYS);
        HBox.setHgrow(hSecond, Priority.SOMETIMES);
        HBox.setHgrow(hThird, Priority.NEVER);

        FixedRegion rFirst = fixedRegion(10.0, 10.0, 20.0, 10.0, 70.0, 40.0);
        FixedRegion rSecond = fixedRegion(10.0, 10.0, 20.0, 10.0, 80.0, 40.0);
        FixedRegion rThird = fixedRegion(10.0, 10.0, 20.0, 10.0, 90.0, 40.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 0.0, rFirst, rSecond, rThird);
        RXBox.setGrow(rFirst, Priority.ALWAYS);
        RXBox.setGrow(rSecond, Priority.SOMETIMES);
        RXBox.setGrow(rThird, Priority.NEVER);

        layout(hbox, 180.0, 40.0);
        layout(rxBox, 180.0, 40.0);

        assertNodeMatches(hFirst, rFirst, "always");
        assertNodeMatches(hSecond, rSecond, "sometimes");
        assertNodeMatches(hThird, rThird, "never");
    }

    /**
     * Verifies fractional grow extra is distributed on pixel portions like {@link HBox}.
     */
    @Test
    public void fractionalGrowMatchesHBoxPixelSnapping() {
        FixedRegion hFirst = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion hSecond = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion hThird = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        HBox hbox = new HBox(0.0, hFirst, hSecond, hThird);
        HBox.setHgrow(hFirst, Priority.ALWAYS);
        HBox.setHgrow(hSecond, Priority.ALWAYS);
        HBox.setHgrow(hThird, Priority.ALWAYS);

        FixedRegion rFirst = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion rSecond = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion rThird = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 0.0, rFirst, rSecond, rThird);
        RXBox.setGrow(rFirst, Priority.ALWAYS);
        RXBox.setGrow(rSecond, Priority.ALWAYS);
        RXBox.setGrow(rThird, Priority.ALWAYS);

        layout(hbox, 32.5, 40.0);
        layout(rxBox, 32.5, 40.0);

        assertNodeMatches(hFirst, rFirst, "first");
        assertNodeMatches(hSecond, rSecond, "second");
        assertNodeMatches(hThird, rThird, "third");
        assertClose(11.0, rFirst.getWidth(), "first snapped width");
        assertClose(11.0, rSecond.getWidth(), "second snapped width");
        assertClose(10.0, rThird.getWidth(), "third snapped width");
    }

    /**
     * Verifies fractional vertical grow extra is distributed on pixel portions like {@link VBox}.
     */
    @Test
    public void fractionalVerticalGrowMatchesVBoxPixelSnapping() {
        FixedRegion vFirst = fixedRegion(10.0, 0.0, 10.0, 10.0, 40.0, 100.0);
        FixedRegion vSecond = fixedRegion(10.0, 0.0, 10.0, 10.0, 40.0, 100.0);
        FixedRegion vThird = fixedRegion(10.0, 0.0, 10.0, 10.0, 40.0, 100.0);
        VBox vbox = new VBox(0.0, vFirst, vSecond, vThird);
        VBox.setVgrow(vFirst, Priority.ALWAYS);
        VBox.setVgrow(vSecond, Priority.ALWAYS);
        VBox.setVgrow(vThird, Priority.ALWAYS);

        FixedRegion rFirst = fixedRegion(10.0, 0.0, 10.0, 10.0, 40.0, 100.0);
        FixedRegion rSecond = fixedRegion(10.0, 0.0, 10.0, 10.0, 40.0, 100.0);
        FixedRegion rThird = fixedRegion(10.0, 0.0, 10.0, 10.0, 40.0, 100.0);
        RXBox rxBox = new RXBox(Orientation.VERTICAL, 0.0, rFirst, rSecond, rThird);
        RXBox.setGrow(rFirst, Priority.ALWAYS);
        RXBox.setGrow(rSecond, Priority.ALWAYS);
        RXBox.setGrow(rThird, Priority.ALWAYS);

        layout(vbox, 40.0, 32.5);
        layout(rxBox, 40.0, 32.5);

        assertNodeMatches(vFirst, rFirst, "first");
        assertNodeMatches(vSecond, rSecond, "second");
        assertNodeMatches(vThird, rThird, "third");
        assertClose(11.0, rFirst.getHeight(), "first snapped height");
        assertClose(11.0, rSecond.getHeight(), "second snapped height");
        assertClose(10.0, rThird.getHeight(), "third snapped height");
    }

    /**
     * Verifies shrink behavior does not go below child minimum sizes.
     */
    @Test
    public void shrinkMatchesHBox() {
        FixedRegion hFirst = fixedRegion(25.0, 10.0, 60.0, 10.0, 100.0, 40.0);
        FixedRegion hSecond = fixedRegion(20.0, 10.0, 50.0, 10.0, 100.0, 40.0);
        HBox hbox = new HBox(4.0, hFirst, hSecond);

        FixedRegion rFirst = fixedRegion(25.0, 10.0, 60.0, 10.0, 100.0, 40.0);
        FixedRegion rSecond = fixedRegion(20.0, 10.0, 50.0, 10.0, 100.0, 40.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 4.0, rFirst, rSecond);

        layout(hbox, 30.0, 40.0);
        layout(rxBox, 30.0, 40.0);

        assertNodeMatches(hFirst, rFirst, "first");
        assertNodeMatches(hSecond, rSecond, "second");
        assertClose(25.0, rFirst.getWidth(), "first min width");
        assertClose(20.0, rSecond.getWidth(), "second min width");
    }

    /**
     * Verifies fractional shrink extra is distributed on pixel portions like {@link HBox}.
     */
    @Test
    public void fractionalShrinkMatchesHBoxPixelSnapping() {
        FixedRegion hFirst = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion hSecond = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion hThird = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        HBox hbox = new HBox(0.0, hFirst, hSecond, hThird);

        FixedRegion rFirst = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion rSecond = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        FixedRegion rThird = fixedRegion(0.0, 10.0, 10.0, 10.0, 100.0, 40.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 0.0, rFirst, rSecond, rThird);

        layout(hbox, 27.5, 40.0);
        layout(rxBox, 27.5, 40.0);

        assertNodeMatches(hFirst, rFirst, "first");
        assertNodeMatches(hSecond, rSecond, "second");
        assertNodeMatches(hThird, rThird, "third");
        assertClose(9.0, rFirst.getWidth(), "first snapped width");
        assertClose(9.0, rSecond.getWidth(), "second snapped width");
        assertClose(10.0, rThird.getWidth(), "third snapped width");
    }

    /**
     * Verifies negative spacing layout against {@link HBox}.
     */
    @Test
    public void negativeSpacingMatchesHBox() {
        FixedRegion hFirst = fixedRegion(30.0, 10.0);
        FixedRegion hSecond = fixedRegion(40.0, 20.0);
        HBox hbox = new HBox(-6.0, hFirst, hSecond);

        FixedRegion rFirst = fixedRegion(30.0, 10.0);
        FixedRegion rSecond = fixedRegion(40.0, 20.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, -6.0, rFirst, rSecond);

        assertClose(hbox.prefWidth(-1), rxBox.prefWidth(-1), "pref width");
        layout(hbox, 100.0, 40.0);
        layout(rxBox, 100.0, 40.0);

        assertNodeMatches(hFirst, rFirst, "first");
        assertNodeMatches(hSecond, rSecond, "second");
        assertTrue(rSecond.getLayoutX() < rFirst.getLayoutX() + rFirst.getWidth());
    }

    /**
     * Verifies unmanaged children are ignored and invisible managed children remain laid out.
     */
    @Test
    public void managedAndVisibleSemanticsMatchHBox() {
        FixedRegion hFirst = fixedRegion(30.0, 10.0);
        FixedRegion hInvisible = fixedRegion(40.0, 20.0);
        FixedRegion hUnmanaged = fixedRegion(100.0, 50.0);
        hInvisible.setVisible(false);
        hUnmanaged.setManaged(false);
        HBox hbox = new HBox(5.0, hFirst, hInvisible, hUnmanaged);

        FixedRegion rFirst = fixedRegion(30.0, 10.0);
        FixedRegion rInvisible = fixedRegion(40.0, 20.0);
        FixedRegion rUnmanaged = fixedRegion(100.0, 50.0);
        rInvisible.setVisible(false);
        rUnmanaged.setManaged(false);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 5.0, rFirst, rInvisible, rUnmanaged);

        assertClose(hbox.prefWidth(-1), rxBox.prefWidth(-1), "pref width");
        layout(hbox, 120.0, 50.0);
        layout(rxBox, 120.0, 50.0);

        assertNodeMatches(hFirst, rFirst, "first");
        assertNodeMatches(hInvisible, rInvisible, "invisible");
    }

    /**
     * Verifies horizontal baseline alignment against {@link HBox}.
     */
    @Test
    public void horizontalBaselineMatchesHBox() {
        BaselineRegion hFirst = baselineRegion(20.0, 32.0, 9.0);
        BaselineRegion hSecond = baselineRegion(35.0, 18.0, 13.0);
        FixedRegion hThird = fixedRegion(24.0, 20.0);
        HBox hbox = new HBox(7.0, hFirst, hSecond, hThird);
        hbox.setAlignment(Pos.BASELINE_RIGHT);
        hbox.setFillHeight(true);
        hbox.setPadding(new Insets(2.0, 3.0, 4.0, 5.0));
        HBox.setMargin(hSecond, new Insets(3.0, 1.0, 5.0, 2.0));

        BaselineRegion rFirst = baselineRegion(20.0, 32.0, 9.0);
        BaselineRegion rSecond = baselineRegion(35.0, 18.0, 13.0);
        FixedRegion rThird = fixedRegion(24.0, 20.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 7.0, rFirst, rSecond, rThird);
        rxBox.setAlignment(Pos.BASELINE_RIGHT);
        rxBox.setFillCrossAxis(true);
        rxBox.setPadding(new Insets(2.0, 3.0, 4.0, 5.0));
        RXBox.setMargin(rSecond, new Insets(3.0, 1.0, 5.0, 2.0));

        assertClose(hbox.prefHeight(-1), rxBox.prefHeight(-1), "pref height");
        layout(hbox, 180.0, 90.0);
        layout(rxBox, 180.0, 90.0);

        assertPaneMatches(hbox, rxBox);
        assertNodeMatches(hFirst, rFirst, "first");
        assertNodeMatches(hSecond, rSecond, "second");
        assertNodeMatches(hThird, rThird, "third");
        assertClose(hFirst.getLayoutY() + hFirst.getBaselineOffset(),
                rFirst.getLayoutY() + rFirst.getBaselineOffset(), "first baseline");
        assertClose(hSecond.getLayoutY() + hSecond.getBaselineOffset(),
                rSecond.getLayoutY() + rSecond.getBaselineOffset(), "second baseline");
    }

    /**
     * Verifies horizontal baseline alignment ignores cross-axis fill.
     */
    @Test
    public void horizontalBaselineIgnoresFillCrossAxis() {
        BaselineRegion baselineChild = baselineRegion(40.0, 18.0, 12.0);
        baselineChild.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, baselineChild);
        rxBox.setAlignment(Pos.BASELINE_LEFT);
        rxBox.setFillCrossAxis(true);

        layout(rxBox, 120.0, 80.0);

        assertClose(18.0, baselineChild.getHeight(), "baseline child height");
    }

    /**
     * Verifies vertical baseline alignment falls back to top-axis behavior.
     */
    @Test
    public void verticalBaselineFallsBackToTopAxisBehavior() {
        FixedRegion baselineFirst = fixedRegion(30.0, 10.0);
        FixedRegion baselineSecond = fixedRegion(40.0, 20.0);
        RXBox baselineBox = new RXBox(Orientation.VERTICAL, 5.0, baselineFirst, baselineSecond);
        baselineBox.setAlignment(Pos.BASELINE_LEFT);

        FixedRegion topFirst = fixedRegion(30.0, 10.0);
        FixedRegion topSecond = fixedRegion(40.0, 20.0);
        RXBox topBox = new RXBox(Orientation.VERTICAL, 5.0, topFirst, topSecond);
        topBox.setAlignment(Pos.TOP_LEFT);

        layout(baselineBox, 100.0, 100.0);
        layout(topBox, 100.0, 100.0);

        assertNodeMatches(topFirst, baselineFirst, "first");
        assertNodeMatches(topSecond, baselineSecond, "second");
        assertClose(Node.BASELINE_OFFSET_SAME_AS_HEIGHT, baselineBox.getBaselineOffset(),
                "baseline offset");
    }

    /**
     * Verifies content-bias size calculation on the horizontal path.
     */
    @Test
    public void horizontalContentBiasPrefHeightMatchesHBox() {
        WidthBiasedRegion hFirst = widthBiasedRegion(40.0, 12.0);
        FixedRegion hSecond = fixedRegion(30.0, 20.0);
        HBox hbox = new HBox(4.0, hFirst, hSecond);
        HBox.setHgrow(hFirst, Priority.ALWAYS);

        WidthBiasedRegion rFirst = widthBiasedRegion(40.0, 12.0);
        FixedRegion rSecond = fixedRegion(30.0, 20.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 4.0, rFirst, rSecond);
        RXBox.setGrow(rFirst, Priority.ALWAYS);

        assertClose(hbox.prefHeight(180.0), rxBox.prefHeight(180.0), "pref height");
    }

    /**
     * Verifies content-biased grow respects finite child max width like {@link HBox}.
     */
    @Test
    public void contentBiasedGrowRespectsFiniteMaxWidthLikeHBox() {
        HeightBiasedRegion hFirst = heightBiasedRegion(20.0);
        FixedRegion hSecond = fixedRegion(30.0, 20.0);
        HBox hbox = new HBox(0.0, hFirst, hSecond);
        HBox.setHgrow(hFirst, Priority.ALWAYS);

        HeightBiasedRegion rFirst = heightBiasedRegion(20.0);
        FixedRegion rSecond = fixedRegion(30.0, 20.0);
        RXBox rxBox = new RXBox(Orientation.HORIZONTAL, 0.0, rFirst, rSecond);
        RXBox.setGrow(rFirst, Priority.ALWAYS);

        layout(hbox, 180.0, 40.0);
        layout(rxBox, 180.0, 40.0);

        assertNodeMatches(hFirst, rFirst, "biased");
        assertNodeMatches(hSecond, rSecond, "fixed");
        assertClose(60.0, rFirst.getWidth(), "finite max width");
    }

    /**
     * Verifies vertical content-bias size calculation against {@link VBox}.
     */
    @Test
    public void verticalContentBiasPrefWidthMatchesVBox() {
        HeightBiasedRegion vFirst = heightBiasedRegion(20.0);
        FixedRegion vSecond = fixedRegion(30.0, 20.0);
        VBox vbox = new VBox(4.0, vFirst, vSecond);
        VBox.setVgrow(vFirst, Priority.ALWAYS);

        HeightBiasedRegion rFirst = heightBiasedRegion(20.0);
        FixedRegion rSecond = fixedRegion(30.0, 20.0);
        RXBox rxBox = new RXBox(Orientation.VERTICAL, 4.0, rFirst, rSecond);
        RXBox.setGrow(rFirst, Priority.ALWAYS);

        assertClose(vbox.prefWidth(120.0), rxBox.prefWidth(120.0), "pref width");
        layout(vbox, 90.0, 120.0);
        layout(rxBox, 90.0, 120.0);

        assertNodeMatches(vFirst, rFirst, "biased");
        assertNodeMatches(vSecond, rSecond, "fixed");
    }

    /**
     * Verifies CSS metadata exposes RXBox styleable properties.
     */
    @Test
    public void cssMetadataContainsRxProperties() {
        assertTrue(hasCssProperty("-rx-orientation"));
        assertTrue(hasCssProperty("-rx-spacing"));
        assertTrue(hasCssProperty("-rx-alignment"));
        assertTrue(hasCssProperty("-rx-fill-cross-axis"));
    }

    // ==================== Assertions ====================

    private static void assertPaneMatches(Region expected, Region actual) {
        assertClose(expected.getWidth(), actual.getWidth(), "pane width");
        assertClose(expected.getHeight(), actual.getHeight(), "pane height");
    }

    private static void assertNodeMatches(Region expected, Region actual, String label) {
        assertClose(expected.getLayoutX(), actual.getLayoutX(), label + " layout x");
        assertClose(expected.getLayoutY(), actual.getLayoutY(), label + " layout y");
        assertClose(expected.getWidth(), actual.getWidth(), label + " width");
        assertClose(expected.getHeight(), actual.getHeight(), label + " height");
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }

    // ==================== Helpers ====================

    private static FixedRegion fixedRegion(double prefWidth, double prefHeight) {
        return fixedRegion(0.0, 0.0, prefWidth, prefHeight,
                Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private static FixedRegion fixedRegion(double minWidth, double minHeight,
                                           double prefWidth, double prefHeight,
                                           double maxWidth, double maxHeight) {
        FixedRegion region = new FixedRegion();
        region.setMinSize(minWidth, minHeight);
        region.setPrefSize(prefWidth, prefHeight);
        region.setMaxSize(maxWidth, maxHeight);
        return region;
    }

    private static BaselineRegion baselineRegion(double prefWidth, double prefHeight,
                                                 double baselineOffset) {
        BaselineRegion region = new BaselineRegion(baselineOffset);
        region.setMinSize(0.0, 0.0);
        region.setPrefSize(prefWidth, prefHeight);
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return region;
    }

    private static WidthBiasedRegion widthBiasedRegion(double prefWidth,
                                                       double baseHeight) {
        WidthBiasedRegion region = new WidthBiasedRegion(baseHeight);
        region.setMinSize(20.0, 8.0);
        region.setPrefSize(prefWidth, baseHeight);
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return region;
    }

    private static HeightBiasedRegion heightBiasedRegion(double baseWidth) {
        HeightBiasedRegion region = new HeightBiasedRegion(baseWidth);
        region.setMinHeight(10.0);
        region.setPrefHeight(20.0);
        region.setMaxHeight(40.0);
        return region;
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
    }

    private static boolean hasCssProperty(String property) {
        return RXBox.getClassCssMetaData().stream()
                .anyMatch(cssMetaData -> property.equals(cssMetaData.getProperty()));
    }

    // ==================== Test nodes ====================

    private static class FixedRegion extends Region {
    }

    private static final class BaselineRegion extends FixedRegion {

        private final double baselineOffset;

        private BaselineRegion(double baselineOffset) {
            this.baselineOffset = baselineOffset;
        }

        @Override
        public double getBaselineOffset() {
            return baselineOffset;
        }
    }

    private static final class WidthBiasedRegion extends FixedRegion {

        private final double baseHeight;

        private WidthBiasedRegion(double baseHeight) {
            this.baseHeight = baseHeight;
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computeMinHeight(double width) {
            return computeHeight(width, 0.5);
        }

        @Override
        protected double computePrefHeight(double width) {
            return computeHeight(width, 1.0);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computeHeight(width, 2.0);
        }

        private double computeHeight(double width, double multiplier) {
            double dependentWidth = width == -1.0 ? prefWidth(-1.0) : width;
            return baseHeight * multiplier + dependentWidth / 10.0;
        }
    }

    private static final class HeightBiasedRegion extends FixedRegion {

        private final double baseWidth;

        private HeightBiasedRegion(double baseWidth) {
            this.baseWidth = baseWidth;
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.VERTICAL;
        }

        @Override
        protected double computeMinWidth(double height) {
            return computeWidth(height, 0.5);
        }

        @Override
        protected double computePrefWidth(double height) {
            return computeWidth(height, 1.0);
        }

        @Override
        protected double computeMaxWidth(double height) {
            return computeWidth(height, 2.0);
        }

        private double computeWidth(double height, double multiplier) {
            double dependentHeight = height == -1.0 ? prefHeight(-1.0) : height;
            return baseWidth * multiplier + dependentHeight / 2.0;
        }
    }
}
