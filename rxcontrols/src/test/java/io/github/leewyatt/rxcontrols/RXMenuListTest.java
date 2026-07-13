package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXMenuListSkin;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.scene.AccessibleRole;
import javafx.scene.control.ToggleGroup;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR2 gate tests for {@link RXMenuList} at the control level: default style
 * class, user-agent stylesheet, default skin type, accessible role, property
 * defaults and pass-through, the ripple styleable metadata, the items list with
 * the single-parent / steal reparent contract, and the {@code activate}
 * command hook (inline fire + unified onAction).
 */
public class RXMenuListTest {

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
     * Verifies the default style class, UA stylesheet, skin type, and role.
     */
    @Test
    public void skeleton() {
        RXMenuList list = new RXMenuList();
        assertTrue(list.getStyleClass().contains("rx-menu-list"));
        assertEquals(RXResources.USER_AGENT_STYLESHEET, list.getUserAgentStylesheet());
        assertSame(AccessibleRole.CONTEXT_MENU, list.getAccessibleRole());
        assertTrue(list.createDefaultSkin() instanceof RXMenuListSkin);
    }

    /**
     * Verifies property defaults.
     */
    @Test
    public void propertyDefaults() {
        RXMenuList list = new RXMenuList();
        assertTrue(list.getItems().isEmpty());
        assertNull(list.getConverter());
        assertTrue(list.isWrapAround());
        assertSame(RXMenuList.InitialFocus.FIRST, list.getInitialFocus());
        assertNull(list.getOnAction());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_FILL, list.getRippleFill());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_OPACITY, list.getRippleOpacity(), 0.0);
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_ENABLED, list.isRippleEnabled());
        assertEquals(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED, list.isStateOverlayEnabled());
        assertTrue(list.isAnimated());
        assertEquals(Duration.millis(120), list.getAnimationDuration());
        assertSame(Interpolator.EASE_OUT, list.getAnimationInterpolator());
    }

    /**
     * Verifies getters/setters are pure pass-through.
     */
    @Test
    public void gettersSettersPassThrough() {
        RXMenuList list = new RXMenuList();
        StringConverter<RXMenuItem> converter = new StringConverter<>() {
            @Override
            public String toString(RXMenuItem item) {
                return item == null ? "" : item.getText();
            }

            @Override
            public RXMenuItem fromString(String string) {
                return null;
            }
        };
        list.setConverter(converter);
        list.setWrapAround(false);
        list.setInitialFocus(RXMenuList.InitialFocus.SELECTED);
        list.setRippleOpacity(0.5);
        list.setRippleEnabled(false);
        list.setStateOverlayEnabled(false);
        list.setAnimated(false);
        list.setAnimationDuration(Duration.millis(50));
        list.setAnimationInterpolator(Interpolator.LINEAR);

        assertSame(converter, list.getConverter());
        assertFalse(list.isWrapAround());
        assertSame(RXMenuList.InitialFocus.SELECTED, list.getInitialFocus());
        assertEquals(0.5, list.getRippleOpacity(), 0.0);
        assertFalse(list.isRippleEnabled());
        assertFalse(list.isStateOverlayEnabled());
        assertFalse(list.isAnimated());
        assertEquals(Duration.millis(50), list.getAnimationDuration());
        assertSame(Interpolator.LINEAR, list.getAnimationInterpolator());
    }

    /**
     * Verifies the four ripple styleables are exposed as CSS metadata.
     */
    @Test
    public void rippleStyleableMetadata() {
        List<CssMetaData<? extends Styleable, ?>> metadata = RXMenuList.getClassCssMetaData();
        assertTrue(metadata.stream().anyMatch(m -> m.getProperty().equals("-rx-ripple-fill")));
        assertTrue(metadata.stream().anyMatch(m -> m.getProperty().equals("-rx-ripple-opacity")));
        assertTrue(metadata.stream().anyMatch(m -> m.getProperty().equals("-rx-ripple-enabled")));
        assertTrue(metadata.stream()
                .anyMatch(m -> m.getProperty().equals("-rx-ripple-state-overlay-enabled")));
        assertTrue(metadata.stream().anyMatch(m -> m.getProperty().equals("-rx-animation-duration")));
    }

    /**
     * Verifies items back-fill the parent-list reference on add and clear it on
     * remove.
     */
    @Test
    public void itemsBackFillParentList() {
        RXMenuList list = new RXMenuList();
        RXMenuItem item = RXMenuItem.of("A");
        assertNull(item.getParentList());
        list.getItems().add(item);
        assertSame(list, item.getParentList());
        list.getItems().remove(item);
        assertNull(item.getParentList());
    }

    /**
     * Verifies adding an item that already belongs to another list moves it
     * (steal): it is removed from the old list and re-parented here.
     */
    @Test
    public void addingStealsFromPreviousList() {
        RXMenuList first = new RXMenuList();
        RXMenuList second = new RXMenuList();
        RXMenuItem item = RXMenuItem.of("Shared");
        first.getItems().add(item);
        assertSame(first, item.getParentList());

        second.getItems().add(item);
        assertSame(second, item.getParentList());
        assertFalse(first.getItems().contains(item), "old list must drop a stolen item");
        assertTrue(second.getItems().contains(item));
    }

    /**
     * Verifies separators and headers live inline in the same items list.
     */
    @Test
    public void separatorAndHeaderAreItems() {
        RXMenuList list = new RXMenuList();
        RXMenuSeparator sep = RXMenuSeparator.create();
        RXMenuHeader header = RXMenuHeader.of("Group");
        list.getItems().addAll(RXMenuItem.of("A"), sep, header, RXMenuItem.of("B"));
        assertEquals(4, list.getItems().size());
        assertSame(list, sep.getParentList());
        assertSame(list, header.getParentList());
    }

    /**
     * Verifies {@code activate} fires the item and the unified onAction hook,
     * with the item as source, when used inline (no popup host).
     */
    @Test
    public void activateFiresItemAndUnifiedHook() {
        RXMenuList list = new RXMenuList();
        RXMenuItem item = RXMenuItem.of("Run");
        list.getItems().add(item);

        AtomicInteger itemFired = new AtomicInteger();
        AtomicReference<Object> listSource = new AtomicReference<>();
        item.setOnAction(e -> itemFired.incrementAndGet());
        list.setOnAction(e -> listSource.set(e.getSource()));

        list.activate(item);
        assertEquals(1, itemFired.get());
        assertSame(item, listSource.get());
    }

    /**
     * Verifies {@code activate} ignores disabled items.
     */
    @Test
    public void activateIgnoresDisabledItem() {
        RXMenuList list = new RXMenuList();
        RXMenuItem item = RXMenuItem.of("Run");
        item.setDisable(true);
        list.getItems().add(item);

        AtomicInteger fired = new AtomicInteger();
        item.setOnAction(e -> fired.incrementAndGet());
        list.setOnAction(e -> fired.incrementAndGet());

        list.activate(item);
        assertEquals(0, fired.get());
    }

    // ==================== PR5: dense / disabledItemsFocusable / selectable activate ====================

    /**
     * Verifies {@code dense} defaults off, passes through, and flips the
     * {@code :dense} pseudo-class on the list.
     */
    @Test
    public void densePropertyAndPseudoClass() {
        RXMenuList list = new RXMenuList();
        PseudoClass dense = PseudoClass.getPseudoClass("dense");
        assertFalse(list.isDense());
        assertFalse(list.getPseudoClassStates().contains(dense));

        list.setDense(true);
        assertTrue(list.isDense());
        assertTrue(list.getPseudoClassStates().contains(dense));

        list.setDense(false);
        assertFalse(list.getPseudoClassStates().contains(dense));
    }

    /**
     * Verifies {@code disabledItemsFocusable} defaults off and passes through.
     */
    @Test
    public void disabledItemsFocusableDefaultAndSetter() {
        RXMenuList list = new RXMenuList();
        assertFalse(list.isDisabledItemsFocusable());
        list.setDisabledItemsFocusable(true);
        assertTrue(list.isDisabledItemsFocusable());
    }

    /**
     * Verifies activating a selectable item toggles its checked state (and still
     * fires the action / unified hook).
     */
    @Test
    public void activateTogglesSelectableChecked() {
        RXMenuList list = new RXMenuList();
        RXMenuItem check = new RXMenuItem("Wrap");
        check.setSelectable(true);
        check.setKeepOpen(true);
        list.getItems().add(check);
        AtomicInteger fired = new AtomicInteger();
        check.setOnAction(e -> fired.incrementAndGet());

        list.activate(check);
        assertTrue(check.isSelected(), "activation checks the item");
        assertEquals(1, fired.get());

        list.activate(check);
        assertFalse(check.isSelected(), "re-activation unchecks it");
    }

    /**
     * Verifies activating radio items in a shared group is mutually exclusive.
     */
    @Test
    public void activateRadioIsMutuallyExclusive() {
        RXMenuList list = new RXMenuList();
        ToggleGroup group = new ToggleGroup();
        RXMenuItem a = RXMenuItem.radio("A", group);
        RXMenuItem b = RXMenuItem.radio("B", group);
        list.getItems().addAll(a, b);

        list.activate(a);
        assertTrue(a.isSelected());
        list.activate(b);
        assertTrue(b.isSelected());
        assertFalse(a.isSelected(), "activating B deselects A");
    }

    /**
     * Verifies re-activating the currently-selected radio keeps it selected (does
     * not empty the group), mirroring {@code ToggleButton.fire()}.
     */
    @Test
    public void reactivatingSelectedRadioKeepsItSelected() {
        RXMenuList list = new RXMenuList();
        ToggleGroup group = new ToggleGroup();
        RXMenuItem a = RXMenuItem.radio("A", group);
        RXMenuItem b = RXMenuItem.radio("B", group);
        list.getItems().addAll(a, b);

        list.activate(a);
        assertTrue(a.isSelected());
        list.activate(a);
        assertTrue(a.isSelected(), "re-activating the selected radio keeps it selected");
        assertSame(a, group.getSelectedToggle(), "the group is not emptied");
    }
}
