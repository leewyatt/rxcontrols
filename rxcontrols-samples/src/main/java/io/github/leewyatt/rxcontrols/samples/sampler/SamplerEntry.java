package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleContent;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * One navigation entry, and one page of the sampler. The entry without variants is the
 * home page.
 *
 * @param name     the navigation label
 * @param category the navigation group
 * @param variants the controls shown on the page, in switch order
 * @param samples  builds the page's example cards, or {@code null} for a page that still
 *                 hosts the showcase of each variant
 */
record SamplerEntry(String name, SamplerCategory category, List<SamplerVariant> variants,
                    Supplier<SampleContent> samples) {

    SamplerEntry(String name, SamplerCategory category, List<SamplerVariant> variants) {
        this(name, category, variants, null);
    }

    SamplerEntry withSamples(Supplier<SampleContent> value) {
        return new SamplerEntry(name, category, variants, value);
    }

    boolean isHome() {
        return variants.isEmpty();
    }

    /**
     * Matches the query against the entry name, its category and the type names of its
     * variants, so a search for a member of a merged page finds that page.
     */
    boolean matches(String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (name.toLowerCase(Locale.ROOT).contains(query)
                || category.title().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (SamplerVariant variant : variants) {
            if (variant.typeName().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }
}
