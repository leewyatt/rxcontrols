package io.github.leewyatt.rxcontrols.internal.feedback;

import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Labeled;

/**
 * Accessible name of a feedback button, which changes with the face it
 * shows.
 */
public final class AccessibleNames {

    private AccessibleNames() {
    }

    /**
     * Resolves the accessible name of a button that currently shows a face
     * with the given text. The first of these that is not empty wins: the
     * button's accessible text, the text of the face, the button's own text,
     * the accessible text of the button's graphic. A face without a text is
     * a visual choice (an indicator alone); the button still needs a name.
     *
     * @param owner    the button
     * @param faceText the text of the face being shown, may be {@code null}
     * @return the accessible name, may be {@code null}
     */
    public static Object resolve(Labeled owner, String faceText) {
        String accessibleText = owner.getAccessibleText();
        if (accessibleText != null && !accessibleText.isEmpty()) {
            return accessibleText;
        }
        if (faceText != null && !faceText.isEmpty()) {
            return faceText;
        }
        String text = owner.getText();
        if (text != null && !text.isEmpty()) {
            return text;
        }
        // Like a standard button, an icon-only button falls back to the
        // accessible text of its graphic.
        Node graphic = owner.getGraphic();
        return graphic == null ? text : graphic.queryAccessibleAttribute(AccessibleAttribute.TEXT);
    }
}
