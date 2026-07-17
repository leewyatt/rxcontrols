package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebarActionItem;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarNavItem;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code RXSidebarSkin}: the zero-jump icon column (icon scene
 * X / layoutX unchanged between EXPANDED and MINI), the locked rail width
 * (computeMin == computePref == computeMax == railWidth), the icon column
 * tracking miniWidth, and a headless layout smoke over the full container tree.
 */
public class RXSidebarSkinTest {

    private static final double EPSILON = 0.01;
    private static final double COLUMN_TOLERANCE = 2.0; // absorbs pixel snapping
    /** Mirrors RXSidebarSkin.ICON_SIZE and the stylesheet's .graphic size. */
    private static final double NOMINAL_ICON_SIZE = 24.0;

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
     * Zero-jump: the icon's scene X and its layoutX within the item are identical
     * in EXPANDED and MINI, and sit at the fixed left column (miniWidth/2 - icon/2).
     */
    @Test
    public void iconDoesNotMoveBetweenModes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false); // compare static EXPANDED vs MINI endpoints
            Region icon = fixedIcon();
            RXSidebarNavItem nav = new RXSidebarNavItem("Inbox", icon);
            sidebar.getItems().add(nav);

            Pane host = new Pane(sidebar);
            new Scene(host, 400, 600);
            host.applyCss();
            host.layout();

            double expandedSceneX = icon.localToScene(0, 0).getX();
            double expandedLayoutX = icon.getLayoutX();

            sidebar.setMode(SidebarMode.MINI);
            host.applyCss();
            host.layout();

            double miniSceneX = icon.localToScene(0, 0).getX();
            double miniLayoutX = icon.getLayoutX();

            assertEquals(expandedSceneX, miniSceneX, EPSILON, "icon scene X must not move");
            assertEquals(expandedLayoutX, miniLayoutX, EPSILON, "icon layoutX must not move");

            // Confirm the icon is genuinely left-anchored at the column inset
            // (default miniWidth 64, icon 24 => left inset 20), not centered.
            double expectedLeftInset = (RXSidebar.DEFAULT_MINI_WIDTH - 24.0) / 2.0;
            assertEquals(expectedLeftInset, expandedLayoutX, COLUMN_TOLERANCE);
        });
    }

    /**
     * The rail width is locked: computeMinWidth == computePrefWidth ==
     * computeMaxWidth == railWidth, in both modes (insets are zero).
     */
    @Test
    public void railWidthIsLockedInBothModes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false); // assert static end-state, not mid-animation width
            laidOut(sidebar);

            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.minWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.maxWidth(-1), EPSILON);

            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.minWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.maxWidth(-1), EPSILON);
        });
    }

    /**
     * Illegal width inputs fall back / clamp instead of throwing: negative mini,
     * NaN expanded, and expanded &lt; mini all resolve to safe locked widths.
     */
    @Test
    public void illegalWidthsResolveLeniently() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false); // assert resolved widths per mode, not mid-animation
            laidOut(sidebar);

            // Negative mini -> default mini (64); checked in MINI mode.
            sidebar.setMiniWidth(-50.0);
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);

            // NaN expanded -> max(mini, default expanded); checked in EXPANDED mode.
            sidebar.setMiniWidth(64.0);
            sidebar.setExpandedWidth(Double.NaN);
            sidebar.setMode(SidebarMode.EXPANDED);
            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);

            // expanded < mini -> max(mini, default expanded); checked in EXPANDED mode.
            sidebar.setExpandedWidth(40.0);
            sidebar.setMode(SidebarMode.EXPANDED);
            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);

            // Infinite mini -> default mini. Infinity passes a plain ">= 0" guard,
            // and railWidth() interpolates mini -> expanded, so an infinite bound
            // yields infinity - infinity = NaN and a NaN-wide rail.
            sidebar.setExpandedWidth(RXSidebar.DEFAULT_EXPANDED_WIDTH);
            sidebar.setMiniWidth(Double.POSITIVE_INFINITY);
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);

            // Infinite expanded -> max(mini, default expanded), in both modes:
            // MINI multiplies a zero fraction by an infinite span (0 * inf = NaN),
            // EXPANDED reaches the infinite bound itself.
            sidebar.setMiniWidth(RXSidebar.DEFAULT_MINI_WIDTH);
            sidebar.setExpandedWidth(Double.POSITIVE_INFINITY);
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);
            sidebar.setMode(SidebarMode.EXPANDED);
            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);

            // A NaN rail width is the failure this guards: assert the node itself,
            // not just prefWidth (Parent.prefWidth zeroes NaN and hides it).
            sidebar.getParent().layout();
            assertFalse(Double.isNaN(sidebar.getWidth()), "rail width must never be NaN");
        });
    }

    /**
     * Changing miniWidth re-centers the icon column.
     */
    @Test
    public void iconColumnTracksMiniWidth() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region icon = fixedIcon();
            sidebar.getItems().add(new RXSidebarNavItem("Inbox", icon));

            Pane host = new Pane(sidebar);
            new Scene(host, 400, 600);
            host.applyCss();
            host.layout();

            sidebar.setMiniWidth(80.0); // left inset becomes (80 - 24) / 2 = 28
            host.applyCss();
            host.layout();

            assertEquals(28.0, icon.getLayoutX(), COLUMN_TOLERANCE);
        });
    }

    /**
     * Headless layout smoke over the full five-region tree with header, footer,
     * nav + action items across all three lists, mode toggles, and item churn —
     * must not throw.
     */
    @Test
    public void fullTreeLayoutSmoke() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setHeader(new Label("Brand"));
            sidebar.setFooter(new Label("v1.0"));
            sidebar.getTopItems().add(new RXSidebarNavItem("Pinned", fixedIcon()));
            RXSidebarNavItem inbox = new RXSidebarNavItem("Inbox", fixedIcon());
            sidebar.getItems().addAll(inbox, new RXSidebarNavItem("Files", fixedIcon()));
            sidebar.getBottomItems().add(new RXSidebarActionItem("Settings", fixedIcon()));

            Pane host = new Pane(sidebar);
            new Scene(host, 400, 600);
            host.applyCss();
            host.layout();

            sidebar.selectItem(inbox);
            for (int i = 0; i < 3; i++) {
                sidebar.setMode(SidebarMode.MINI);
                host.applyCss();
                host.layout();
                sidebar.setMode(SidebarMode.EXPANDED);
                host.applyCss();
                host.layout();
            }

            sidebar.getItems().add(new RXSidebarNavItem("Late", fixedIcon()));
            sidebar.getItems().remove(inbox);
            host.applyCss();
            host.layout();

            assertEquals(2, sidebar.getItems().size());
            assertTrue(sidebar.getWidth() > 0.0);
            assertTrue(sidebar.getHeight() > 0.0);
        });
    }

    /**
     * During-transition proxy (no animation needed): with the row in
     * ContentDisplay.LEFT (its state throughout the width tween), the icon's
     * layoutX stays at the fixed column at every intermediate width.
     */
    @Test
    public void iconStaysFixedAcrossWidthsInLeftMode() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region icon = fixedIcon();
            RXSidebarNavItem nav = new RXSidebarNavItem("Inbox", icon);
            sidebar.getItems().add(nav);
            hostFor(sidebar);

            double expectedLeftInset = (RXSidebar.DEFAULT_MINI_WIDTH - 24.0) / 2.0;
            nav.setContentDisplay(ContentDisplay.LEFT);
            for (double width : new double[]{64.0, 100.0, 160.0, 220.0, 260.0}) {
                nav.resize(width, 40.0);
                nav.layout();
                assertEquals(expectedLeftInset, icon.getLayoutX(), COLUMN_TOLERANCE,
                        "icon must stay at the left column at width " + width);
            }
        });
    }

    /**
     * Explicit guard: the {@code .rx-sidebar .item} rule must resolve item
     * alignment to CENTER_LEFT (its specificity must beat modena's
     * {@code .toggle-button} CENTER). Zero-jump silently depends on this.
     */
    @Test
    public void itemAlignmentResolvesToCenterLeft() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem nav = new RXSidebarNavItem("Inbox", fixedIcon());
            RXSidebarActionItem action = new RXSidebarActionItem("Settings", fixedIcon());
            sidebar.getItems().add(nav);
            sidebar.getBottomItems().add(action);
            hostFor(sidebar);

            assertSame(Pos.CENTER_LEFT, nav.getAlignment());
            assertSame(Pos.CENTER_LEFT, action.getAlignment());
        });
    }

    /**
     * Zero-jump stability holds even when miniWidth &lt; icon size: leftInset
     * clamps to MIN_LEFT_INSET (0) and the icon left edge is identical in both
     * modes.
     */
    @Test
    public void iconStableWhenMiniWidthClampsLeftInset() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false); // compare static endpoints
            Region icon = fixedIcon();
            sidebar.getItems().add(new RXSidebarNavItem("A", icon));
            Pane host = hostFor(sidebar);

            sidebar.setMiniWidth(20.0); // (20 - 24) / 2 < 0 -> clamps to 0
            host.applyCss();
            host.layout();
            double expandedX = icon.getLayoutX();

            sidebar.setMode(SidebarMode.MINI);
            host.applyCss();
            host.layout();
            double miniX = icon.getLayoutX();

            assertEquals(expandedX, miniX, EPSILON);
            assertEquals(0.0, miniX, COLUMN_TOLERANCE);
        });
    }

    /**
     * The band mirrors every list mutation in order: add, replace, multi-index
     * add, permutation, removeAll, setAll, and clear.
     */
    @Test
    public void bandMirrorsListMutations() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            RXSidebarNavItem c = new RXSidebarNavItem("C");
            sidebar.getItems().addAll(a, b, c);
            laidOut(sidebar);
            VBox mainBox = mainBoxOf(sidebar);
            assertBandMatches(mainBox, a, b, c);

            RXSidebarNavItem b2 = new RXSidebarNavItem("B2");
            sidebar.getItems().set(1, b2); // replace -> remove+add at same index
            assertBandMatches(mainBox, a, b2, c);

            RXSidebarNavItem x = new RXSidebarNavItem("X");
            RXSidebarNavItem y = new RXSidebarNavItem("Y");
            sidebar.getItems().addAll(1, List.of(x, y)); // multi-index add
            assertBandMatches(mainBox, a, x, y, b2, c);

            FXCollections.reverse(sidebar.getItems()); // permutation -> setAll branch
            assertBandMatches(mainBox, c, b2, y, x, a);

            sidebar.getItems().removeAll(c, a);
            assertBandMatches(mainBox, b2, y, x);

            sidebar.getItems().setAll(a, b2);
            assertBandMatches(mainBox, a, b2);

            sidebar.getItems().clear();
            assertEquals(0, mainBox.getChildren().size());
        });
    }

    /**
     * Header/footer slots: shown + managed with their content node when set,
     * swapped (not appended) on re-set, hidden + unmanaged + emptied when null.
     */
    @Test
    public void headerFooterSlotToggling() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            laidOut(sidebar);
            StackPane headerSlot = headerSlotOf(sidebar);
            StackPane footerSlot = footerSlotOf(sidebar);

            // Empty by default.
            assertFalse(headerSlot.isVisible());
            assertFalse(headerSlot.isManaged());

            Label a = new Label("A");
            sidebar.setHeader(a);
            assertTrue(headerSlot.isVisible());
            assertTrue(headerSlot.isManaged());
            assertEquals(1, headerSlot.getChildren().size());
            assertSame(a, headerSlot.getChildren().get(0));

            Label b = new Label("B");
            sidebar.setHeader(b);
            assertEquals(1, headerSlot.getChildren().size()); // swapped, not appended
            assertSame(b, headerSlot.getChildren().get(0));

            sidebar.setHeader(null);
            assertFalse(headerSlot.isVisible());
            assertFalse(headerSlot.isManaged());
            assertEquals(0, headerSlot.getChildren().size());

            Label f = new Label("Footer");
            sidebar.setFooter(f);
            assertTrue(footerSlot.isVisible());
            assertSame(f, footerSlot.getChildren().get(0));
        });
    }

    /**
     * A replacing skin is constructed before the outgoing one is disposed, so by
     * the time the old skin tears down, the new skin has already wired these very
     * items. Tearing them down would destroy the live skin's work, not its own.
     */
    @Test
    public void replacingSkinKeepsItsOwnWiring() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Home", fixedIcon());
            sidebar.getItems().add(a);
            hostFor(sidebar);
            sidebar.setMode(SidebarMode.MINI);
            assertNotNull(a.getTooltip(), "precondition: MINI lends the item a tooltip");

            // A subclass is not short-circuited the way an identical class is, so
            // this really does construct-then-dispose. The incoming skin wires the
            // items first; the outgoing one must not then tear that down.
            sidebar.setSkin(new RXSidebarSkin(sidebar) {
            });

            assertNotNull(a.getTooltip(),
                    "the replacing skin's wiring must survive the outgoing skin's dispose");
            assertEquals(ContentDisplay.GRAPHIC_ONLY, a.getContentDisplay());

            // Surviving is not enough: the wiring has to still be the live skin's to
            // drive. A tooltip merely left behind by the outgoing skin would look
            // like the item's own to the new one, and never come off again.
            sidebar.setMode(SidebarMode.EXPANDED);
            assertNull(a.getTooltip(), "the live skin must still own the lent tooltip");
            assertEquals(ContentDisplay.LEFT, a.getContentDisplay());
        });
    }

    /**
     * The outgoing skin must not be the one to define "what the item looked like
     * before": it is disposed after the replacement is built, so its own overrides
     * are on the item by then. Capturing those as the caller's originals would make
     * a later removal restore the rail's own look instead of the caller's.
     */
    @Test
    public void skinSwapDoesNotLaunderTheSkinsOwnStateIntoTheCallersOriginals() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Home", fixedIcon());
            a.setContentDisplay(ContentDisplay.TOP);       // the caller's own look
            Insets ownPadding = new Insets(7.0);
            a.setPadding(ownPadding);
            sidebar.getItems().add(a);
            hostFor(sidebar);
            sidebar.setMode(SidebarMode.MINI);             // the rail overwrites both

            sidebar.setSkin(new RXSidebarSkin(sidebar) {
            });
            sidebar.getItems().remove(a);

            assertEquals(ContentDisplay.TOP, a.getContentDisplay(),
                    "the caller's content display must come back, not the rail's");
            assertEquals(ownPadding, a.getPadding(),
                    "the caller's padding must come back, not the rail's icon column");
        });
    }

    /**
     * A replaced skin must stop listening to the items it wired, even though it
     * leaves their shared state alone for the skin that took over. Its listeners
     * are its own, and nobody else can take them off; left attached they call back
     * into a disposed skin ({@code getSkinnable()} is null by then) on the next
     * visibility or disabled change — and JavaFX routes that failure to the
     * uncaught handler, so it never surfaces as a test failure on its own.
     */
    @Test
    public void aReplacedSkinStopsListeningToItsOldItems() throws Exception {
        runOnFx(() -> {
            AtomicReference<Throwable> uncaught = new AtomicReference<>();
            Thread fx = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = fx.getUncaughtExceptionHandler();
            fx.setUncaughtExceptionHandler((thread, error) -> uncaught.set(error));
            try {
                RXSidebar sidebar = new RXSidebar();
                sidebar.setAnimated(false);
                RXSidebarNavItem a = new RXSidebarNavItem("Home", fixedIcon());
                sidebar.getItems().add(a);
                hostFor(sidebar);

                sidebar.setSkin(new RXSidebarSkin(sidebar) {
                });

                a.setVisible(false);
                a.setVisible(true);
                assertNull(uncaught.get(),
                        "the outgoing skin must not still be reacting to its old items");
            } finally {
                fx.setUncaughtExceptionHandler(previous);
            }
        });
    }

    /**
     * Disposing the installed skin directly (rather than through the control) is
     * still nobody taking over: the control's skin is this very skin, so the items
     * are this skin's to hand back. Reading "the control still has a sidebar skin"
     * as "somebody adopted the items" would strand the rail's tooltip and content
     * display on them.
     */
    @Test
    public void disposingTheInstalledSkinDirectlyHandsItemsBack() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Home", fixedIcon());
            sidebar.getItems().add(a);
            hostFor(sidebar);
            sidebar.setMode(SidebarMode.MINI);
            assertNotNull(a.getTooltip());

            sidebar.getSkin().dispose();

            assertNull(a.getTooltip(), "the lent tooltip must be taken back");
            assertEquals(ContentDisplay.LEFT, a.getContentDisplay(), "content display restored");
        });
    }

    /**
     * A skin can reach dispose twice — disposed directly, then again when the
     * control drops it — and by the second pass its control reference is gone.
     */
    @Test
    public void disposingTwiceIsSafe() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            sidebar.getItems().add(new RXSidebarNavItem("Home", fixedIcon()));
            hostFor(sidebar);

            sidebar.getSkin().dispose();
            assertDoesNotThrow(() -> sidebar.setSkin(null), "the control's dispose must not throw");
        });
    }

    /**
     * The other half: when nothing takes the items over, dispose really must hand
     * them back. A guard that skipped here would leave every item carrying the
     * rail's tooltip and content display forever.
     */
    @Test
    public void disposeWithNoReplacementHandsItemsBack() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Home", fixedIcon());
            sidebar.getItems().add(a);
            hostFor(sidebar);
            sidebar.setMode(SidebarMode.MINI);
            assertNotNull(a.getTooltip());

            sidebar.setSkin(null);

            assertNull(a.getTooltip(), "the lent tooltip must be taken back");
            assertEquals(ContentDisplay.LEFT, a.getContentDisplay(), "content display restored");
        });
    }

    /**
     * Disposing the skin removes its root node and unregisters its list/property
     * listeners, so later model mutations never reach the disposed skin.
     */
    @Test
    public void disposeRemovesRootAndListeners() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.getItems().add(new RXSidebarNavItem("A", fixedIcon()));
            hostFor(sidebar);
            assertEquals(1, sidebar.getChildrenUnmodifiable().size());

            sidebar.setSkin(null); // disposes the skin

            assertEquals(0, sidebar.getChildrenUnmodifiable().size(), "root must be removed");
            // A leaked list/property listener would call into the disposed skin
            // (getSkinnable() == null) and throw; cleanly removed listeners do not.
            assertDoesNotThrow(() -> {
                sidebar.getItems().add(new RXSidebarNavItem("B", fixedIcon()));
                sidebar.setMode(SidebarMode.MINI);
                sidebar.setMiniWidth(80.0);
            });
        });
    }

    /**
     * A text-only item (no graphic) lays out without throwing in both modes.
     */
    @Test
    public void textOnlyItemLaysOutInBothModes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.getItems().add(new RXSidebarNavItem("NoIcon"));
            Pane host = hostFor(sidebar);
            assertDoesNotThrow(() -> {
                sidebar.setMode(SidebarMode.MINI);
                host.applyCss();
                host.layout();
                sidebar.setMode(SidebarMode.EXPANDED);
                host.applyCss();
                host.layout();
            });
        });
    }

    /**
     * The stylesheet's ".graphic" size and the skin's nominal icon size are two
     * halves of one contract — the column is centred for the size the stylesheet
     * hands out — and nothing but this test makes them move together.
     *
     * <p>Asserts the laid-out size, not the preferred size: MINI displays the icon
     * alone, which stretches an icon whose maximum is not pinned to fill the whole
     * row. Checking {@code prefWidth} would pass right through that.</p>
     */
    @Test
    public void styledGraphicKeepsTheNominalIconSizeInBothModes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            Region icon = new Region();
            icon.getStyleClass().add("graphic");   // the caller's whole obligation
            sidebar.getItems().add(new RXSidebarNavItem("A", icon));
            Pane host = hostFor(sidebar);

            assertEquals(NOMINAL_ICON_SIZE, icon.getWidth(), EPSILON, "EXPANDED icon width");
            assertEquals(NOMINAL_ICON_SIZE, icon.getHeight(), EPSILON, "EXPANDED icon height");

            sidebar.setMode(SidebarMode.MINI);
            host.applyCss();
            host.layout();
            assertEquals(NOMINAL_ICON_SIZE, icon.getWidth(), EPSILON,
                    "MINI must not stretch the icon to fill the row");
            assertEquals(NOMINAL_ICON_SIZE, icon.getHeight(), EPSILON,
                    "MINI must not stretch the icon to fill the row");
        });
    }

    /**
     * The point of the column: every icon shares one left edge, and that edge does
     * not move when the rail changes width. Asserted for a styled icon, i.e. one
     * sized by the stylesheet rather than pinned in Java by the caller.
     */
    @Test
    public void styledGraphicIsCentredInMiniAndDoesNotMove() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            Region icon = new Region();
            icon.getStyleClass().add("graphic");
            sidebar.getItems().add(new RXSidebarNavItem("A", icon));
            Pane host = hostFor(sidebar);

            double expandedX = icon.localToScene(0, 0).getX();
            sidebar.setMode(SidebarMode.MINI);
            host.applyCss();
            host.layout();
            double miniX = icon.localToScene(0, 0).getX();

            assertEquals(expandedX, miniX, EPSILON, "the icon must not move between modes");
            double iconCentre = miniX + NOMINAL_ICON_SIZE / 2.0;
            assertEquals(sidebar.getWidth() / 2.0, iconCentre, COLUMN_TOLERANCE,
                    "MINI centres the icon on the rail");
        });
    }

    // ==================== Helpers ====================

    private static Region fixedIcon() {
        Region icon = new Region();
        icon.getStyleClass().add("graphic");
        icon.setMinSize(24.0, 24.0);
        icon.setPrefSize(24.0, 24.0);
        icon.setMaxSize(24.0, 24.0);
        return icon;
    }

    private static RXSidebar laidOut(RXSidebar sidebar) {
        hostFor(sidebar);
        return sidebar;
    }

    private static Pane hostFor(RXSidebar sidebar) {
        Pane host = new Pane(sidebar);
        new Scene(host, 400, 600);
        host.applyCss();
        host.layout();
        return host;
    }

    private static VBox rootOf(RXSidebar sidebar) {
        return (VBox) sidebar.getChildrenUnmodifiable().get(0);
    }

    private static VBox mainBoxOf(RXSidebar sidebar) {
        ScrollPane scroll = (ScrollPane) rootOf(sidebar).getChildren().get(2);
        return (VBox) scroll.getContent();
    }

    private static StackPane headerSlotOf(RXSidebar sidebar) {
        return (StackPane) rootOf(sidebar).getChildren().get(0);
    }

    private static StackPane footerSlotOf(RXSidebar sidebar) {
        return (StackPane) rootOf(sidebar).getChildren().get(4);
    }

    private static void assertBandMatches(VBox band, RXSidebarItem... items) {
        assertEquals(items.length, band.getChildren().size());
        for (int i = 0; i < items.length; i++) {
            assertSame(items[i].asNode(), band.getChildren().get(i));
        }
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
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex != null) {
            throw new AssertionError(ex);
        }
    }
}
