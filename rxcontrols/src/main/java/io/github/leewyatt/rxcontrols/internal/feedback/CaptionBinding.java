package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;

/**
 * Binds the caption label of a feedback face to the text appearance of the
 * button that owns it, so every face is drawn like the button's own text.
 */
public final class CaptionBinding {

    private CaptionBinding() {
    }

    /**
     * Binds the appearance properties of a caption to those of its owner.
     * The caption's text and graphic are not bound: each face has its own.
     *
     * @param disposer the disposer that unbinds on dispose
     * @param caption  the caption label of a face
     * @param owner    the button the face belongs to
     */
    public static void bindAppearance(SkinDisposer disposer, Label caption, Labeled owner) {
        disposer.registerBinding(caption.fontProperty(), owner.fontProperty());
        disposer.registerBinding(caption.textFillProperty(), owner.textFillProperty());
        disposer.registerBinding(caption.ellipsisStringProperty(), owner.ellipsisStringProperty());
        disposer.registerBinding(caption.contentDisplayProperty(), owner.contentDisplayProperty());
        disposer.registerBinding(caption.graphicTextGapProperty(), owner.graphicTextGapProperty());
        disposer.registerBinding(caption.alignmentProperty(), owner.alignmentProperty());
        disposer.registerBinding(caption.textAlignmentProperty(), owner.textAlignmentProperty());
        disposer.registerBinding(caption.textOverrunProperty(), owner.textOverrunProperty());
        disposer.registerBinding(caption.wrapTextProperty(), owner.wrapTextProperty());
        disposer.registerBinding(caption.underlineProperty(), owner.underlineProperty());
        disposer.registerBinding(caption.lineSpacingProperty(), owner.lineSpacingProperty());
    }
}
