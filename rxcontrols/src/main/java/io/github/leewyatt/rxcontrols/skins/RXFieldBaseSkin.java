/**
 * Copyright (c) 2013, 2019 ControlsFX
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of ControlsFX, any associated website, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL CONTROLSFX BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Modifications and additions Copyright (c) 2026 leewyatt (rxcontrols project).
 */
package io.github.leewyatt.rxcontrols.skins;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.StackPane;
import javafx.scene.text.HitInfo;

/**
 * Skin extension point — shared base for skins that need to render user-supplied
 * left and right nodes inside a {@link TextField}. The base skin reads the
 * "effective" left/right node and "effective" text padding from the three
 * {@link ObservableValue}s passed by the subclass, so subclasses can layer
 * defaults on top of the user's selection without writing back to the
 * control's own properties.
 * <p>
 * The class is concrete so it can be used directly for ad-hoc validation
 * during development; production code should prefer subclassing or using
 * one of the rxcontrols {@code RX*FieldSkin} classes.
 * <p>
 * Contract for the supplied observables: {@code effectiveLeading} and
 * {@code effectiveTrailing} should not resolve to the same {@link Node} instance
 * at the same time. {@link io.github.leewyatt.rxcontrols.RXTextField}
 * enforces this for its own setters by clearing the opposite slot when the
 * same node is moved across — see its slot migration semantics. Subclasses
 * that bypass {@code RXTextField}'s setters (or feed in custom effective
 * observables) are responsible for the same uniqueness; if both observables
 * resolve to the same node, only the right wrapper will keep it (JavaFX
 * reparents the node) and the left wrapper will render empty.
 * <p>
 * {@code effectiveTextPadding} resolves to the active text-editor inner
 * padding. A {@code null} value is treated as {@link Insets#EMPTY}.
 * Horizontal values are the exact gap between a present wrapper and the text;
 * vertical values stack on top of the control's own vertical padding and feed
 * into the pref/min height computation.
 * <p>
 * <b>Provenance:</b> this skin builds on the leading/trailing-node pattern from
 * ControlsFX's {@code CustomTextFieldSkin} — see the BSD-3-Clause notice in the
 * file header. Beyond that pattern it adds the effective left / right /
 * text-padding model, the laid-out-width hit-test correction in
 * {@code getIndex}, the wrapper relayout bridge, and the listener / dispose
 * handling.
 */
public class RXFieldBaseSkin extends TextFieldSkin {

    private static final PseudoClass HAS_LEADING = PseudoClass.getPseudoClass("has-leading");
    private static final PseudoClass HAS_TRAILING = PseudoClass.getPseudoClass("has-trailing");
    private static final PseudoClass HAS_NO_SLOTS = PseudoClass.getPseudoClass("has-no-slots");

    private static final String LEADING_WRAPPER_CLASS = "leading-wrapper";
    private static final String TRAILING_WRAPPER_CLASS = "trailing-wrapper";

    /**
     * Single cleanup channel for this skin and its subclasses. Subclasses
     * register their own listeners / handlers here too, so the whole chain
     * tears down through this base class's {@link #dispose()} in LIFO order
     * (subclass-registered tasks first, then base, then {@code super}).
     */
    protected final SkinDisposer disposer = new SkinDisposer();

    private final TextField control;
    private final ObservableValue<Node> effectiveLeading;
    private final ObservableValue<Node> effectiveTrailing;
    private final ObservableValue<Insets> effectiveTextPadding;

    private StackPane leadingWrapper;
    private StackPane trailingWrapper;

    private final ChangeListener<Boolean> wrapperNeedsLayoutListener = (obs, oldVal, newVal) -> {
        if (Boolean.TRUE.equals(newVal)) {
            getSkinnable().requestLayout();
        }
    };

    public RXFieldBaseSkin(TextField control,
                           ObservableValue<Node> effectiveLeading,
                           ObservableValue<Node> effectiveTrailing,
                           ObservableValue<Insets> effectiveTextPadding) {
        super(control);
        this.control = control;
        this.effectiveLeading = effectiveLeading;
        this.effectiveTrailing = effectiveTrailing;
        this.effectiveTextPadding = effectiveTextPadding;

        disposer.registerListener(effectiveLeading, (obs, oldVal, newVal) -> updateChildren());
        disposer.registerListener(effectiveTrailing, (obs, oldVal, newVal) -> updateChildren());
        disposer.registerListener(effectiveTextPadding,
                (obs, oldVal, newVal) -> getSkinnable().requestLayout());
        // Wrappers are rebuilt over the skin's lifetime, so the disposer reads
        // the live fields at dispose time instead of capturing a stale wrapper.
        // JFX17's SkinBase / TextFieldSkin dispose() do NOT remove children from
        // the control, so every skin must detach its own nodes — otherwise on
        // skin replacement the old wrappers linger in the children as ghosts.
        disposer.registerDisposeTask(() -> {
            leadingWrapper = releaseWrapper(leadingWrapper);
            trailingWrapper = releaseWrapper(trailingWrapper);
        });

        updateChildren();
    }

    // ==================== Children / pseudo-class wiring ====================

    private void updateChildren() {
        Node newLeading = effectiveLeading.getValue();
        Node newTrailing = effectiveTrailing.getValue();

        // Release both wrappers before constructing any new wrapper, so that
        // user nodes are fully detached from old parents before being
        // re-parented into freshly created wrappers below.
        leadingWrapper = releaseWrapper(leadingWrapper);
        trailingWrapper = releaseWrapper(trailingWrapper);

        if (newLeading != null) {
            leadingWrapper = createWrapper(newLeading, Pos.CENTER_LEFT, LEADING_WRAPPER_CLASS);
        }
        if (newTrailing != null) {
            trailingWrapper = createWrapper(newTrailing, Pos.CENTER_RIGHT, TRAILING_WRAPPER_CLASS);
        }

        control.pseudoClassStateChanged(HAS_LEADING, newLeading != null);
        control.pseudoClassStateChanged(HAS_TRAILING, newTrailing != null);
        control.pseudoClassStateChanged(HAS_NO_SLOTS, newLeading == null && newTrailing == null);
        // Removing an unmanaged child (releaseWrapper above) does not invalidate
        // the skin's layout on its own, so the text node would stay at its
        // previous left offset until something else triggered a layout pass.
        control.requestLayout();
    }

    private StackPane createWrapper(Node content, Pos alignment, String styleClass) {
        StackPane wrapper = new StackPane(content);
        // Unmanaged: we size/position it manually in layoutChildren. The
        // downside is that the wrapper's requestLayout() does not walk up to
        // the skin — see needsLayoutProperty listener below for the bridge.
        wrapper.setManaged(false);
        wrapper.setAlignment(alignment);
        wrapper.getStyleClass().add(styleClass);
        // CSS pseudo-class changes on the wrapper (e.g. :hover bumping padding)
        // flip its needsLayout flag. Without this bridge the skin would not
        // re-run layoutChildren and the wrapper's outer bounds would stay
        // stale until something else invalidated the control.
        wrapper.needsLayoutProperty().addListener(wrapperNeedsLayoutListener);
        getChildren().add(wrapper);
        return wrapper;
    }

    private StackPane releaseWrapper(StackPane wrapper) {
        if (wrapper != null) {
            wrapper.needsLayoutProperty().removeListener(wrapperNeedsLayoutListener);
            wrapper.getChildren().clear();
            getChildren().remove(wrapper);
        }
        return null;
    }

    // ==================== Layout ====================

    private Insets getEffectiveTextPadding() {
        Insets padding = effectiveTextPadding.getValue();
        return padding == null ? Insets.EMPTY : padding;
    }

    private double resolveLeadingAdjust(double leftInset, double leadingWidth) {
        Insets tp = getEffectiveTextPadding();
        double tpLeft = snapSizeX(tp.getLeft());
        if (leadingWrapper == null) {
            return tpLeft;
        }
        return leadingWidth + tpLeft - leftInset;
    }

    private double resolveTrailingAdjust(double rightInset, double trailingWidth) {
        Insets tp = getEffectiveTextPadding();
        double tpRight = snapSizeX(tp.getRight());
        if (trailingWrapper == null) {
            return tpRight;
        }
        return trailingWidth + tpRight - rightInset;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        final double topInset = snappedTopInset();
        final double bottomInset = snappedBottomInset();
        final double leftInset = snappedLeftInset();
        final double rightInset = snappedRightInset();
        final double fullH = h + topInset + bottomInset;

        final double leadingWidth = leadingWrapper == null
                ? 0.0 : snapSizeX(leadingWrapper.prefWidth(fullH));
        final double trailingWidth = trailingWrapper == null
                ? 0.0 : snapSizeX(trailingWrapper.prefWidth(fullH));

        final Insets tp = getEffectiveTextPadding();
        final double tpTop = snapSizeY(tp.getTop());
        final double tpBottom = snapSizeY(tp.getBottom());

        final double leadingAdjust = resolveLeadingAdjust(leftInset, leadingWidth);
        final double trailingAdjust = resolveTrailingAdjust(rightInset, trailingWidth);

        final double innerWidth = Math.max(0.0, w - leadingAdjust - trailingAdjust);
        final double innerHeight = Math.max(0.0, h - tpTop - tpBottom);

        super.layoutChildren(x + leadingAdjust, y + tpTop, innerWidth, innerHeight);

        // Wrappers sit flush against the control's outer bounds and span the
        // full control height — independent of -fx-padding and -rx-text-padding.
        if (leadingWrapper != null) {
            leadingWrapper.resizeRelocate(x - leftInset, y - topInset, leadingWidth, fullH);
        }
        if (trailingWrapper != null) {
            trailingWrapper.resizeRelocate(x + w + rightInset - trailingWidth, y - topInset, trailingWidth, fullH);
        }
    }

    /**
     * Horizontal offset, within the content area, from its left edge to where
     * the text editor begins — past any left wrapper and the left text padding.
     * Reflects the most recent {@link #layoutChildren} pass (it reads the
     * laid-out leading-wrapper width), so call it after {@code super.layoutChildren}.
     * Subclasses that position content which must line up with the editor text
     * (for example a floating label) use this so they track the editor exactly
     * in both the wrapper-present and wrapper-absent cases.
     *
     * @return the editor's left offset within the content area
     */
    protected final double editorLeadingOffset() {
        final double leftInset = snappedLeftInset();
        final double leadingWidth = leadingWrapper == null ? 0.0 : leadingWrapper.getWidth();
        return resolveLeadingAdjust(leftInset, leadingWidth);
    }

    /**
     * Trailing-side counterpart of {@link #editorLeadingOffset()}: the horizontal
     * offset, within the content area, from its right edge back to where the
     * text editor ends — past any right wrapper and the right text padding.
     * Reflects the most recent {@link #layoutChildren} pass, so call it after
     * {@code super.layoutChildren}.
     *
     * @return the editor's right offset within the content area
     */
    protected final double editorTrailingOffset() {
        final double rightInset = snappedRightInset();
        final double trailingWidth = trailingWrapper == null ? 0.0 : trailingWrapper.getWidth();
        return resolveTrailingAdjust(rightInset, trailingWidth);
    }

    @Override
    public HitInfo getIndex(double x, double y) {
        // Use the last-laid-out wrapper width rather than prefWidth: a CSS
        // state change (e.g. :hover bumping padding) can shift prefWidth before
        // relayout, which makes hit-testing drift by a few pixels.
        final double leftInset = snappedLeftInset();
        final double leadingWidth = leadingWrapper == null ? 0.0 : leadingWrapper.getWidth();
        final double leadingAdjust = resolveLeadingAdjust(leftInset, leadingWidth);
        // layoutChildren shifts the inner editor by +tpTop; getIndex must
        // subtract the same offset so click / drag-select hit-tests land on
        // the visible glyph instead of tpTop pixels above it.
        final Insets tp = getEffectiveTextPadding();
        final double tpTop = snapSizeY(tp.getTop());
        return super.getIndex(x - leadingAdjust, y - tpTop);
    }

    /** {@inheritDoc} */
    @Override
    public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
        // layoutChildren shifts the inner editor down by tpTop; the reported
        // baseline must shift with it (getIndex applies the same correction).
        return super.computeBaselineOffset(topInset, rightInset, bottomInset, leftInset)
                + snapSizeY(getEffectiveTextPadding().getTop());
    }

    @Override
    protected double computePrefWidth(double h, double topInset, double rightInset, double bottomInset, double leftInset) {
        final double pw = super.computePrefWidth(h, topInset, rightInset, bottomInset, leftInset);
        final double leadingWidth = leadingWrapper == null ? 0.0 : snapSizeX(leadingWrapper.prefWidth(h));
        final double trailingWidth = trailingWrapper == null ? 0.0 : snapSizeX(trailingWrapper.prefWidth(h));
        final double leadingAdjust = resolveLeadingAdjust(leftInset, leadingWidth);
        final double trailingAdjust = resolveTrailingAdjust(rightInset, trailingWidth);
        return pw + leadingAdjust + trailingAdjust;
    }

    @Override
    protected double computePrefHeight(double w, double topInset, double rightInset, double bottomInset, double leftInset) {
        final double ph = super.computePrefHeight(w, topInset, rightInset, bottomInset, leftInset);
        final double leadingHeight = leadingWrapper == null ? 0.0 : snapSizeY(leadingWrapper.prefHeight(-1));
        final double trailingHeight = trailingWrapper == null ? 0.0 : snapSizeY(trailingWrapper.prefHeight(-1));
        final Insets tp = getEffectiveTextPadding();
        final double tpTop = snapSizeY(tp.getTop());
        final double tpBottom = snapSizeY(tp.getBottom());
        // Wrappers span the full control height (resizeRelocate uses fullH),
        // so bare wrapper heights already represent the wrapper region without
        // needing the vertical insets added back. textPadding adds height to
        // the text-editor region only — it stacks on ph but not on sidesH.
        final double sidesH = Math.max(leadingHeight, trailingHeight);
        return Math.max(ph + tpTop + tpBottom, sidesH);
    }

    @Override
    protected double computeMinWidth(double h, double topInset, double rightInset, double bottomInset, double leftInset) {
        final double mw = super.computeMinWidth(h, topInset, rightInset, bottomInset, leftInset);
        final double leadingWidth = leadingWrapper == null ? 0.0 : snapSizeX(leadingWrapper.minWidth(h));
        final double trailingWidth = trailingWrapper == null ? 0.0 : snapSizeX(trailingWrapper.minWidth(h));
        final double leadingAdjust = resolveLeadingAdjust(leftInset, leadingWidth);
        final double trailingAdjust = resolveTrailingAdjust(rightInset, trailingWidth);
        return mw + leadingAdjust + trailingAdjust;
    }

    // computeMinHeight is deliberately not overridden: TextFieldSkin implements
    // it as `return computePrefHeight(...)` — a virtual call that already lands
    // on the most-derived, padding-inclusive override above. Re-adding the
    // vertical text padding (or subclass bands) here would double-count it and
    // break the min <= pref invariant, pushing every managed parent to lay the
    // control out taller than its pref.

    // ==================== Lifecycle ====================

    @Override
    public void dispose() {
        // Runs all registered cleanup (this skin's and any subclass's) in LIFO
        // order, then lets TextFieldSkin tear down its own nodes.
        SkinDisposer.disposeInOrder(disposer::dispose, super::dispose);
    }
}
