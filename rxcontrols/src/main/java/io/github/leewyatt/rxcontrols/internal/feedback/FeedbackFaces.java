package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Shows one of several faces at a time on a two-page transition surface,
 * with a page animation or a direct cut between them.
 *
 * <p>Every face is parented by one of the two pages at all times. Only the
 * current face can be seen: every other face is hidden or sits on the hidden
 * page. All of them stay managed, so both pages keep measuring them and the
 * surface is as large as the largest face, whichever page a face happens to
 * be on. To show a face that sits on the page being shown, a face is carried
 * over to the other page first.</p>
 */
public final class FeedbackFaces {

    private static final String PAGE_STYLE_CLASS = "page";
    private static final int PAGE_COUNT = 2;

    private final TransitionPages pages = new TransitionPages(PAGE_STYLE_CLASS, true);
    private final Node initialFace;
    private Node currentFace;

    /**
     * Creates the surface. The first face is the initial one and is showing.
     *
     * @param faces the faces, at least one
     */
    public FeedbackFaces(Node... faces) {
        initialFace = faces[0];
        currentFace = initialFace;
        pages.getPageA().getChildren().add(initialFace);
        for (int i = 1; i < faces.length; i++) {
            faces[i].setVisible(false);
            pages.getPageB().getChildren().add(faces[i]);
        }
    }

    /**
     * Returns the node to add to the skin: it holds both pages and clips
     * them.
     *
     * @return the content pane
     */
    public StackPane getContentPane() {
        return pages.getContentPane();
    }

    /**
     * Returns the face being shown, or being swapped in.
     *
     * @return the current face
     */
    public Node getCurrentFace() {
        return currentFace;
    }

    /**
     * Shows the given face. Does nothing when it is the current face. A swap
     * still running jumps to its end first, so the latest call wins.
     *
     * @param face      the face to show, one of those given to the
     *                  constructor
     * @param direction the direction handed to the animation
     * @param animation the animation, or {@code null} for a direct cut
     * @param duration  the duration of the animation; a duration that is not
     *                  positive and finite gives a direct cut
     * @param animate   {@code false} to cut directly whatever the animation
     */
    public void show(Node face, TransitionDirection direction, PageAnimation animation,
                     Duration duration, boolean animate) {
        if (face == currentFace) {
            return;
        }
        // From here on the method always ends in a swap or a cut: an
        // interrupted animation leaves the visibility of the pages to it.
        pages.interrupt();
        if (face.getParent() == pages.getCurrentPage()) {
            if (face == initialFace) {
                // The initial face never changes its page: it holds the
                // application's own graphic, and a node notices when it
                // leaves the scene and comes back. The face on show steps
                // over to the other page instead; the picture stays as it is.
                StackPane spare = pages.getSparePage();
                moveTo(currentFace, spare);
                showOnly(spare, currentFace);
                pages.directCutTo(spare);
            } else {
                moveTo(face, pages.getSparePage());
            }
        }
        StackPane target = pages.getSparePage();
        showOnly(target, face);
        currentFace = face;
        pages.clearEffectsIfChanged(animation, direction, duration);
        if (animate && PageTransitionEngine.canAnimate(animation, true, PAGE_COUNT, duration, false)) {
            pages.transitionTo(target, animation, direction, duration, null, null, null);
        } else {
            pages.directCutTo(target);
        }
    }

    private static void moveTo(Node face, StackPane page) {
        ((StackPane) face.getParent()).getChildren().remove(face);
        page.getChildren().add(face);
    }

    private static void showOnly(StackPane page, Node face) {
        for (Node child : page.getChildren()) {
            child.setVisible(child == face);
        }
    }

    // ==================== Layout ====================

    /**
     * Lays the surface out in the given content area.
     *
     * @param x      the content x
     * @param y      the content y
     * @param width  the content width
     * @param height the content height
     */
    public void layout(double x, double y, double width, double height) {
        pages.layout(x, y, width, height);
    }

    /**
     * Returns the minimum width of the largest face.
     *
     * @param height the height available, or -1
     * @return the minimum width
     */
    public double minWidth(double height) {
        return Math.max(pages.getPageA().minWidth(height), pages.getPageB().minWidth(height));
    }

    /**
     * Returns the preferred width of the largest face.
     *
     * @param height the height available, or -1
     * @return the preferred width
     */
    public double prefWidth(double height) {
        return Math.max(pages.getPageA().prefWidth(height), pages.getPageB().prefWidth(height));
    }

    /**
     * Returns the minimum height of the largest face.
     *
     * @param width the width available, or -1
     * @return the minimum height
     */
    public double minHeight(double width) {
        return Math.max(pages.getPageA().minHeight(width), pages.getPageB().minHeight(width));
    }

    /**
     * Returns the preferred height of the largest face.
     *
     * @param width the width available, or -1
     * @return the preferred height
     */
    public double prefHeight(double width) {
        return Math.max(pages.getPageA().prefHeight(width), pages.getPageB().prefHeight(width));
    }

    // ==================== Dispose ====================

    /**
     * Ends a running swap without starting another, returns to the initial
     * face and releases the pages.
     *
     * @param configuredAnimation the animation currently configured on the
     *                            button, may be {@code null}
     */
    public void dispose(PageAnimation configuredAnimation) {
        pages.interrupt();
        StackPane home = (StackPane) initialFace.getParent();
        showOnly(home, initialFace);
        currentFace = initialFace;
        pages.directCutTo(home);
        pages.dispose(configuredAnimation);
    }
}
