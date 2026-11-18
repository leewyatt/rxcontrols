package io.github.leewyatt.rxcontrols.samples.support;

import io.github.leewyatt.rxcontrols.animation.page.AnimAccordion;
import io.github.leewyatt.rxcontrols.animation.page.AnimAround;
import io.github.leewyatt.rxcontrols.animation.page.AnimBarn;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlend;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimBounce;
import io.github.leewyatt.rxcontrols.animation.page.AnimBox;
import io.github.leewyatt.rxcontrols.animation.page.AnimCards;
import io.github.leewyatt.rxcontrols.animation.page.AnimCascade;
import io.github.leewyatt.rxcontrols.animation.page.AnimCheckerboard;
import io.github.leewyatt.rxcontrols.animation.page.AnimCircle;
import io.github.leewyatt.rxcontrols.animation.page.AnimCircleWave;
import io.github.leewyatt.rxcontrols.animation.page.AnimCircles;
import io.github.leewyatt.rxcontrols.animation.page.AnimComb;
import io.github.leewyatt.rxcontrols.animation.page.AnimConveyor;
import io.github.leewyatt.rxcontrols.animation.page.AnimCorner;
import io.github.leewyatt.rxcontrols.animation.page.AnimCross;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube4;
import io.github.leewyatt.rxcontrols.animation.page.AnimCurtain;
import io.github.leewyatt.rxcontrols.animation.page.AnimDiamond;
import io.github.leewyatt.rxcontrols.animation.page.AnimDipToColor;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimDomino;
import io.github.leewyatt.rxcontrols.animation.page.AnimDoorway;
import io.github.leewyatt.rxcontrols.animation.page.AnimDrain;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlash;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimFold;
import io.github.leewyatt.rxcontrols.animation.page.AnimGallery;
import io.github.leewyatt.rxcontrols.animation.page.AnimGaussianBlur;
import io.github.leewyatt.rxcontrols.animation.page.AnimGlitch;
import io.github.leewyatt.rxcontrols.animation.page.AnimHoneycomb;
import io.github.leewyatt.rxcontrols.animation.page.AnimIris;
import io.github.leewyatt.rxcontrols.animation.page.AnimLouver;
import io.github.leewyatt.rxcontrols.animation.page.AnimMelt;
import io.github.leewyatt.rxcontrols.animation.page.AnimMosaic;
import io.github.leewyatt.rxcontrols.animation.page.AnimMotionBlur;
import io.github.leewyatt.rxcontrols.animation.page.AnimNewspaper;
import io.github.leewyatt.rxcontrols.animation.page.AnimNone;
import io.github.leewyatt.rxcontrols.animation.page.AnimParallax;
import io.github.leewyatt.rxcontrols.animation.page.AnimPeel;
import io.github.leewyatt.rxcontrols.animation.page.AnimPinwheel;
import io.github.leewyatt.rxcontrols.animation.page.AnimPixelate;
import io.github.leewyatt.rxcontrols.animation.page.AnimRandomTiles;
import io.github.leewyatt.rxcontrols.animation.page.AnimRectangle;
import io.github.leewyatt.rxcontrols.animation.page.AnimReveal;
import io.github.leewyatt.rxcontrols.animation.page.AnimRipple;
import io.github.leewyatt.rxcontrols.animation.page.AnimRotate;
import io.github.leewyatt.rxcontrols.animation.page.AnimSector;
import io.github.leewyatt.rxcontrols.animation.page.AnimSelector;
import io.github.leewyatt.rxcontrols.animation.page.AnimSerpentine;
import io.github.leewyatt.rxcontrols.animation.page.AnimShapeReveal;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatterRadial;
import io.github.leewyatt.rxcontrols.animation.page.AnimShred;
import io.github.leewyatt.rxcontrols.animation.page.AnimShrink;
import io.github.leewyatt.rxcontrols.animation.page.AnimShutter;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlideIn;
import io.github.leewyatt.rxcontrols.animation.page.AnimSpinStrips;
import io.github.leewyatt.rxcontrols.animation.page.AnimSpiralTiles;
import io.github.leewyatt.rxcontrols.animation.page.AnimSplit;
import io.github.leewyatt.rxcontrols.animation.page.AnimSplitWipe;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.AnimStack;
import io.github.leewyatt.rxcontrols.animation.page.AnimSwap;
import io.github.leewyatt.rxcontrols.animation.page.AnimSwing;
import io.github.leewyatt.rxcontrols.animation.page.AnimTiltSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimWave;
import io.github.leewyatt.rxcontrols.animation.page.AnimWedge;
import io.github.leewyatt.rxcontrols.animation.page.AnimWhipPan;
import io.github.leewyatt.rxcontrols.animation.page.AnimWind;
import io.github.leewyatt.rxcontrols.animation.page.AnimWipe;
import io.github.leewyatt.rxcontrols.animation.page.AnimZigzagWipe;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import javafx.geometry.Orientation;
import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The page animation presets the samples offer, grouped by category: every
 * {@code AnimXxx} class with its notable constructor variants. Each supplier makes a
 * new instance, so hosts never share one.
 */
public final class PageAnimationPresets {

    private PageAnimationPresets() {
    }

    /**
     * Returns the presets by category, both in display order.
     *
     * @return category name to preset name to a supplier of a new animation
     */
    public static Map<String, Map<String, Supplier<PageAnimation>>> byCategory() {
        Map<String, Map<String, Supplier<PageAnimation>>> categories = new LinkedHashMap<>();

        // Slide / Push
        Map<String, Supplier<PageAnimation>> slide = new LinkedHashMap<>();
        slide.put("Slide (H)", AnimSlide::new);
        slide.put("Slide (V)", () -> new AnimSlide(Orientation.VERTICAL));
        slide.put("Stack", AnimStack::new);
        slide.put("Conveyor (H)", AnimConveyor::new);
        slide.put("Conveyor (V)", () -> new AnimConveyor(Orientation.VERTICAL));
        slide.put("SlideIn", AnimSlideIn::new);
        slide.put("Cards", AnimCards::new);
        slide.put("Newspaper", AnimNewspaper::new);
        slide.put("Rotate", AnimRotate::new);
        slide.put("Gallery", AnimGallery::new);
        slide.put("Parallax", AnimParallax::new);
        slide.put("Bounce (H)", AnimBounce::new);
        slide.put("Bounce (V)", () -> new AnimBounce(Orientation.VERTICAL, 1.5));
        slide.put("Reveal (H)", AnimReveal::new);
        slide.put("Reveal (V)", () -> new AnimReveal(Orientation.VERTICAL));
        slide.put("WhipPan (H)", AnimWhipPan::new);
        slide.put("WhipPan (V)", () -> new AnimWhipPan(Orientation.VERTICAL));
        slide.put("Curtain", AnimCurtain::new);
        slide.put("TiltSlide", AnimTiltSlide::new);
        categories.put("Slide / Push", slide);

        // Fade / Blur
        Map<String, Supplier<PageAnimation>> fade = new LinkedHashMap<>();
        fade.put("Fade", AnimFade::new);
        fade.put("Blend", AnimBlend::new);
        fade.put("GaussianBlur", AnimGaussianBlur::new);
        fade.put("MotionBlur", AnimMotionBlur::new);
        fade.put("Zoom", AnimZoom::new);
        fade.put("Doorway", AnimDoorway::new);
        fade.put("Dip to Black", AnimDipToColor::new);
        fade.put("Dip to White", () -> new AnimDipToColor(Color.WHITE));
        categories.put("Fade / Blur", fade);

        // 3D Transform
        Map<String, Supplier<PageAnimation>> transform3d = new LinkedHashMap<>();
        transform3d.put("Flip (H)", AnimFlip::new);
        transform3d.put("Flip (V)", () -> new AnimFlip(Orientation.VERTICAL));
        transform3d.put("Box (H)", () -> new AnimBox(Orientation.HORIZONTAL));
        transform3d.put("Box (V)", () -> new AnimBox(Orientation.VERTICAL));
        transform3d.put("Cube (H)", AnimCube::new);
        transform3d.put("Cube (V)", () -> new AnimCube(Orientation.VERTICAL));
        transform3d.put("Cube4 (H)", AnimCube4::new);
        transform3d.put("Cube4 (V)", () -> new AnimCube4(Orientation.VERTICAL));
        transform3d.put("Fold (H)", AnimFold::new);
        transform3d.put("Fold (V)", () -> new AnimFold(Orientation.VERTICAL));
        transform3d.put("Swing (H)", AnimSwing::new);
        transform3d.put("Swing (V)", () -> new AnimSwing(Orientation.VERTICAL));
        transform3d.put("Barn (H)", AnimBarn::new);
        transform3d.put("Barn (V)", () -> new AnimBarn(Orientation.VERTICAL));
        categories.put("3D Transform", transform3d);

        // Shape Reveal
        Map<String, Supplier<PageAnimation>> shape = new LinkedHashMap<>();
        shape.put("Rectangle", AnimRectangle::new);
        shape.put("Circle", AnimCircle::new);
        shape.put("Diamond", AnimDiamond::new);
        shape.put("Cross", AnimCross::new);
        shape.put("Corner", AnimCorner::new);
        shape.put("Sector", AnimSector::new);
        shape.put("ShapeReveal", AnimShapeReveal::new);
        shape.put("Iris", AnimIris::new);
        shape.put("Shutter", AnimShutter::new);
        shape.put("Wedge", AnimWedge::new);
        shape.put("Peel", AnimPeel::new);
        shape.put("Pinwheel", AnimPinwheel::new);
        shape.put("Pinwheel (6)", () -> new AnimPinwheel(6));
        categories.put("Shape Reveal", shape);

        // Wipe / Pattern
        Map<String, Supplier<PageAnimation>> wipe = new LinkedHashMap<>();
        wipe.put("Wipe (H)", () -> new AnimWipe(Orientation.HORIZONTAL));
        wipe.put("Wipe (V)", () -> new AnimWipe(Orientation.VERTICAL));
        wipe.put("SplitWipe (H)", AnimSplitWipe::new);
        wipe.put("SplitWipe (V)", () -> new AnimSplitWipe(Orientation.VERTICAL));
        wipe.put("Blinds (H)", () -> new AnimBlinds(Orientation.HORIZONTAL, 10));
        wipe.put("Blinds (V)", () -> new AnimBlinds(Orientation.VERTICAL, 10));
        wipe.put("Comb (H)", AnimComb::new);
        wipe.put("Comb (V)", () -> new AnimComb(Orientation.VERTICAL));
        wipe.put("Checkerboard", AnimCheckerboard::new);
        wipe.put("Serpentine", AnimSerpentine::new);
        wipe.put("Ripple", AnimRipple::new);
        wipe.put("CircleWave", AnimCircleWave::new);
        wipe.put("Wave", AnimWave::new);
        wipe.put("Wave (choppy)", () -> new AnimWave(4, 16));
        wipe.put("Circles", AnimCircles::new);
        wipe.put("Spiral Tiles", AnimSpiralTiles::new);
        wipe.put("Spiral Tiles (8x10)", () -> new AnimSpiralTiles(8, 10));
        wipe.put("ZigzagWipe (H)", AnimZigzagWipe::new);
        wipe.put("ZigzagWipe (V)", () -> new AnimZigzagWipe(Orientation.VERTICAL));
        categories.put("Wipe / Pattern", wipe);

        // Fragment (snapshot-based)
        Map<String, Supplier<PageAnimation>> fragment = new LinkedHashMap<>();
        fragment.put("Shatter", AnimShatter::new);
        fragment.put("Shatter (Center)", () -> new AnimShatterRadial(0.5, 0.5));
        fragment.put("Shatter (Random)", AnimShatterRadial::new);
        fragment.put("Accordion (H)", AnimAccordion::new);
        fragment.put("Accordion (V)", () -> new AnimAccordion(Orientation.VERTICAL, 10));
        fragment.put("Domino (H)", AnimDomino::new);
        fragment.put("Domino (V)", () -> new AnimDomino(Orientation.VERTICAL, 12));
        fragment.put("Spin (H)", AnimSpinStrips::new);
        fragment.put("Spin (V)", () -> new AnimSpinStrips(Orientation.VERTICAL));
        fragment.put("Wind (H)", AnimWind::new);
        fragment.put("Wind (V)", () -> new AnimWind(Orientation.VERTICAL));
        fragment.put("Shred (H)", AnimShred::new);
        fragment.put("Shred (V)", () -> new AnimShred(Orientation.VERTICAL));
        fragment.put("Mosaic (H)", AnimMosaic::new);
        fragment.put("Mosaic (V)", () -> new AnimMosaic(Orientation.VERTICAL));
        fragment.put("Tiles", AnimRandomTiles::new);
        fragment.put("Honeycomb", AnimHoneycomb::new);
        fragment.put("Dissolve", AnimDissolve::new);
        fragment.put("Dissolve (Fine)", () -> new AnimDissolve(40, 24));
        fragment.put("Pixelate", AnimPixelate::new);
        fragment.put("Pixelate (Fine)", () -> new AnimPixelate(15));
        fragment.put("Louver (H)", AnimLouver::new);
        fragment.put("Louver (V)", () -> new AnimLouver(Orientation.VERTICAL));
        fragment.put("Melt", AnimMelt::new);
        fragment.put("Melt (Fine)", () -> new AnimMelt(28));
        fragment.put("Split (H)", AnimSplit::new);
        fragment.put("Split (V)", () -> new AnimSplit(Orientation.VERTICAL));
        fragment.put("Cascade (H)", AnimCascade::new);
        fragment.put("Cascade (V)", () -> new AnimCascade(Orientation.VERTICAL));
        fragment.put("Cascade (Fine)", () -> new AnimCascade(Orientation.HORIZONTAL, 24));
        categories.put("Fragment", fragment);

        // Special
        Map<String, Supplier<PageAnimation>> special = new LinkedHashMap<>();
        special.put("Around", AnimAround::new);
        special.put("Around (Side)", () -> new AnimAround(true));
        special.put("Swap", AnimSwap::new);
        special.put("Drain", AnimDrain::new);
        special.put("Drain (720°)", () -> new AnimDrain(720));
        special.put("Glitch", AnimGlitch::new);
        special.put("Glitch (Fast)", () -> new AnimGlitch(16, 0.15, 50));
        special.put("Squeeze (H)", AnimSqueeze::new);
        special.put("Squeeze (V)", () -> new AnimSqueeze(Orientation.VERTICAL));
        special.put("Flash (White)", AnimFlash::new);
        special.put("Flash (Black)", () -> new AnimFlash(Color.BLACK));
        special.put("Shrink", AnimShrink::new);
        special.put("None", AnimNone::new);
        special.put("Random", () -> AnimSelector.random(
                new AnimSlide(),
                new AnimSlide(Orientation.VERTICAL),
                new AnimFade(),
                new AnimFlip(),
                new AnimBox(Orientation.HORIZONTAL),
                new AnimBox(Orientation.VERTICAL),
                new AnimFold(),
                new AnimFold(Orientation.VERTICAL),
                new AnimBlinds(Orientation.HORIZONTAL, 10),
                new AnimShatterRadial(),
                new AnimHoneycomb()
        ));
        categories.put("Special", special);

        return categories;
    }
}
