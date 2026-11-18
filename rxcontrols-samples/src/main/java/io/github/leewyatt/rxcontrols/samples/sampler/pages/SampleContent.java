package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import java.util.List;

/**
 * What one sampler page shows on its Examples tab.
 *
 * @param description one sentence on what the control is
 * @param whenToUse   one or two short lines on when to pick it, and what to pick instead
 * @param cards       the examples, in reading order
 * @param stylesheet  a page stylesheet for the example content, or {@code null}
 */
public record SampleContent(String description, List<String> whenToUse, List<SampleCard> cards,
                            String stylesheet) {
}
