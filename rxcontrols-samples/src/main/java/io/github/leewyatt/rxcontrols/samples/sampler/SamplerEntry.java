package io.github.leewyatt.rxcontrols.samples.sampler;

import java.util.List;
import java.util.Locale;

/**
 * One navigation entry, and one page of the sampler. The entry without variants is the
 * home page.
 *
 * @param name     the navigation label
 * @param category the navigation group
 * @param variants the controls shown on the page, in switch order
 * @param isNew    whether the navigation marks the entry as recently added
 */
record SamplerEntry(String name, SamplerCategory category, List<SamplerVariant> variants, boolean isNew) {

    boolean isHome() {
        return variants.isEmpty();
    }

    /**
     * Matches the query against the entry name and the type names of its variants, so a
     * search for a member of a merged page finds that page.
     */
    boolean matches(String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (name.toLowerCase(Locale.ROOT).contains(query)) {
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
