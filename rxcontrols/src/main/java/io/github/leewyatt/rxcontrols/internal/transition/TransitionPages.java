package io.github.leewyatt.rxcontrols.internal.transition;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * The double-page surface shared by skins that transition between two
 * content states: a clipped content pane holding two reusable page wrappers,
 * a current-page pointer, and the {@link PageTransitionEngine} glue for
 * playing a transition towards a target page.
 *
 * <p>Hosts assemble content into the pages themselves (text, user nodes,
 * mirror labels), keep their own sizing semantics, and drive this surface
 * with {@link #transitionTo} or {@link #directCutTo}. Rotating hosts treat
 * {@link #getSparePage()} as the incoming page; fixed-face hosts address
 * {@link #getPageA()}/{@link #getPageB()} directly.</p>
 *
 * <p>The surface is itself the clipped content pane: the owning skin adds it to
 * its children, places it from {@code layoutChildren} like any child, and calls
 * {@link #dispose} from its
 * dispose chain. Mirror state belongs in the {@code onStarted} callback
 * (zero-duration animations complete synchronously inside {@code play}).</p>
 *
 * <p>The surface offers two idle-attachment modes. In the default
 * <em>detach</em> mode only the current page stays parented while idle; the
 * spare is re-attached for the next transition. Value-follow hosts (which size
 * to the current page only) use this mode. Fixed-face hosts that size to the
 * max of both faces opt into <em>keep-both</em> mode via
 * {@link #TransitionPages(String, boolean)}: both pages stay parented (and
 * therefore laid out and styled) at all times, with exactly the current page
 * visible while idle. Keep-both mode guarantees the hidden face receives the
 * scene stylesheet, so its CSS-driven size is measured correctly before any
 * flip.</p>
 */
public class TransitionPages extends StackPane {

    private static final String CONTENT_PANE_STYLE_CLASS = "content-pane";

    private final Rectangle clip = new Rectangle();
    private final StackPane pageA;
    private final StackPane pageB;
    private final PageTransitionEngine engine = new PageTransitionEngine();
    private final boolean keepBothAttached;

    private StackPane currentPage;
    private int generation;

    /**
     * Creates the surface with two empty pages in detach mode (only the current
     * page is parented while idle).
     *
     * @param pageStyleClass the style class for both page wrappers
     *                       (e.g. {@code page}, or {@code line} for lyric hosts)
     */
    public TransitionPages(String pageStyleClass) {
        this(pageStyleClass, false);
    }

    /**
     * Creates the surface with two empty pages.
     *
     * @param pageStyleClass   the style class for both page wrappers
     *                         (e.g. {@code page}, or {@code line} for lyric hosts)
     * @param keepBothAttached when {@code true}, both pages stay parented (and
     *                         thus laid out and styled) at all times, with only
     *                         the current page visible while idle; when
     *                         {@code false}, only the current page stays
     *                         parented while idle
     */
    public TransitionPages(String pageStyleClass, boolean keepBothAttached) {
        this.keepBothAttached = keepBothAttached;
        pageA = createPage(pageStyleClass);
        pageB = createPage(pageStyleClass);
        getStyleClass().add(CONTENT_PANE_STYLE_CLASS);
        // The viewport clip follows the surface's own size.
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        currentPage = pageA;
        if (keepBothAttached) {
            pageB.setVisible(false);
            getChildren().addAll(pageA, pageB);
        } else {
            getChildren().add(currentPage);
        }
    }

    private static StackPane createPage(String styleClass) {
        StackPane page = new StackPane();
        page.getStyleClass().add(styleClass);
        return page;
    }

    // ==================== Structure ====================

    /**
     * Returns the first page wrapper (fixed-face addressing).
     *
     * @return page A
     */
    public StackPane getPageA() {
        return pageA;
    }

    /**
     * Returns the second page wrapper (fixed-face addressing).
     *
     * @return page B
     */
    public StackPane getPageB() {
        return pageB;
    }

    /**
     * Returns the page showing (or animating in) the current content.
     *
     * @return the current page
     */
    public StackPane getCurrentPage() {
        return currentPage;
    }

    /**
     * Returns the off-stage page reused for the next transition.
     *
     * @return the spare page
     */
    public StackPane getSparePage() {
        return currentPage == pageA ? pageB : pageA;
    }

    // ==================== Layout ====================

    /**
     * Resizes the surface, taking a negative size (a host whose insets exceed its
     * size) as zero.
     *
     * @param width  the width
     * @param height the height
     */
    @Override
    public void resize(double width, double height) {
        super.resize(Math.max(0.0, width), Math.max(0.0, height));
    }

    // ==================== Transitions ====================

    /**
     * Returns whether a transition is currently playing.
     *
     * @return true while a transition is running
     */
    public boolean isTransitioning() {
        return engine.isTransitioning();
    }

    /**
     * Jumps the running transition to its end state. Safe to call when idle.
     */
    public void interrupt() {
        engine.interrupt();
    }

    /**
     * Clears the previous animation's leftover effects when the host is about
     * to use a different animation instance.
     *
     * @param next      the animation about to be used
     * @param direction the direction for the cleanup context
     * @param duration  the duration for the cleanup context
     * @return the previous animation if its effects were cleared, else {@code null}
     */
    public PageAnimation clearEffectsIfChanged(PageAnimation next,
                                               TransitionDirection direction, Duration duration) {
        return engine.clearEffectsIfChanged(next,
                () -> buildContext(currentPage, getSparePage(), direction, duration));
    }

    /**
     * Hides the page and makes sure it is parented to the content pane, so its
     * content is part of the scene graph before a later
     * {@link #transitionTo} plays it in.
     *
     * @param page the page to attach hidden
     */
    public void attachHidden(StackPane page) {
        page.setVisible(false);
        if (!getChildren().contains(page)) {
            getChildren().add(page);
        }
    }

    /**
     * Shows the target page with a direct cut: it becomes the current page,
     * is made visible, and every other page is hidden ({@code setVisible(false)}
     * in keep-both mode, removed from the content pane in detach mode). In
     * detach mode every caller cuts to the already-attached current page, so no
     * re-attachment is needed.
     *
     * @param target the page to show
     */
    public void directCutTo(StackPane target) {
        generation++;
        currentPage = target;
        target.setVisible(true);
        hideNonCurrentPages();
    }

    /**
     * Plays a transition from the current page to the target page, which
     * becomes the current page immediately. The host fills the target's
     * content before calling.
     *
     * @param target         the incoming page
     * @param animation      the animation to play
     * @param direction      the transition direction
     * @param duration       the transition duration
     * @param onStarted      runs once the animation is playing (mirror state), may be {@code null}
     * @param onSettled      runs after natural completion, before off-stage pages are removed;
     *                       if it starts another transition or cut, the pages are left to
     *                       that one; may be {@code null}
     * @param onExternalStop runs after an external stop, may be {@code null}
     */
    public void transitionTo(StackPane target, PageAnimation animation,
                             TransitionDirection direction, Duration duration,
                             Runnable onStarted, Runnable onSettled, Runnable onExternalStop) {
        StackPane outgoing = currentPage;
        currentPage = target;

        outgoing.setVisible(true);
        if (!getChildren().contains(target)) {
            target.setVisible(false);
            getChildren().add(target);
        }

        int playing = ++generation;
        TransitionContext context = buildContext(outgoing, target, direction, duration);
        engine.play(animation, context, onStarted,
                () -> {
                    if (onSettled != null) {
                        onSettled.run();
                    }
                    // onSettled runs host and user code, which may already have
                    // started the next transition; its outgoing page must stay.
                    if (generation == playing) {
                        hideNonCurrentPages();
                    }
                },
                onExternalStop);
    }

    // Settle idle state so exactly the current page shows. In keep-both mode
    // every page stays parented and the non-current pages are merely hidden, so
    // their CSS-driven size keeps being measured. In detach mode off-stage
    // pages leave the tree and are re-added on the next transition. External
    // stops do not reach here: they fire during Animation.stop(), before finish
    // actions restore visual state.
    private void hideNonCurrentPages() {
        if (keepBothAttached) {
            for (Node child : getChildren()) {
                if (child != currentPage) {
                    child.setVisible(false);
                }
            }
        } else {
            getChildren().removeIf(child -> child != currentPage);
        }
    }

    private TransitionContext buildContext(Node outgoing, Node incoming,
                                           TransitionDirection direction, Duration duration) {
        return new TransitionContext(
                outgoing, incoming,
                0, 1, 2,
                direction, duration,
                this,
                index -> index == 0 ? outgoing : incoming);
    }

    // ==================== Dispose ====================

    /**
     * Stops tracking, disposes the animations and clears the surface.
     *
     * @param configuredAnimation the host's currently configured animation, may be {@code null}
     */
    public void dispose(PageAnimation configuredAnimation) {
        engine.dispose(configuredAnimation);
        setClip(null);
        getChildren().clear();
    }
}
