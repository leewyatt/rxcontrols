package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXBBCodeView;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodeImagePolicy;
import io.github.leewyatt.rxcontrols.bbcode.RXBBCodePolicy;
import javafx.scene.image.Image;

import java.util.List;

/**
 * Cross-block rendering context shared by the inline and block renderers. Holds only what
 * is stable across the whole document render (the control and the skin's live-image list);
 * the per-paragraph {@code TextFlow} is passed to the inline renderer separately, so this
 * type never changes shape as later renderer stages are added.
 */
final class RenderContext {

    private final RXBBCodeView control;
    private final List<Image> liveImages;

    RenderContext(RXBBCodeView control, List<Image> liveImages) {
        this.control = control;
        this.liveImages = liveImages;
    }

    RXBBCodeView control() {
        return control;
    }

    void registerLiveImage(Image image) {
        liveImages.add(image);
    }

    double imageMaxWidthOrDefault() {
        return control.getImageMaxWidth();
    }

    double imageMaxHeightOrDefault() {
        return control.getImageMaxHeight();
    }

    double maxFontSizeOrDefault() {
        return control.getMaxFontSize();
    }

    RXBBCodeImagePolicy imagePolicy() {
        RXBBCodePolicy policy = control.getPolicy();
        return (policy != null ? policy : RXBBCodePolicy.defaults()).imagePolicy();
    }
}
