package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Objects;

/**
 * Immutable security boundary for BBCode parsing: the URL and image scheme
 * allow-lists. This object carries no resource limits — input length and node
 * count are the caller's responsibility, and the nesting-depth guard is a control
 * parse option rather than a policy.
 *
 * @param urlPolicy   the link-scheme allow-list; never {@code null}
 * @param imagePolicy the image policy; never {@code null}
 * @throws NullPointerException if {@code urlPolicy} or {@code imagePolicy} is {@code null}
 */
public record RXBBCodePolicy(RXBBCodeUrlPolicy urlPolicy, RXBBCodeImagePolicy imagePolicy) {

    /**
     * Creates a policy, rejecting null sub-policies fail-fast so the parser and
     * skin never dereference a null policy component.
     */
    public RXBBCodePolicy {
        Objects.requireNonNull(urlPolicy, "urlPolicy");
        Objects.requireNonNull(imagePolicy, "imagePolicy");
    }

    /**
     * Returns the default policy: the default URL and image allow-lists.
     *
     * @return the default policy
     */
    public static RXBBCodePolicy defaults() {
        return new RXBBCodePolicy(RXBBCodeUrlPolicy.defaults(), RXBBCodeImagePolicy.defaults());
    }
}
