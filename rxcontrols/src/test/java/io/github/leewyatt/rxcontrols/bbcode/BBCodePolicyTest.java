package io.github.leewyatt.rxcontrols.bbcode;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the immutable BBCode policy records: default values, defensive scheme
 * copies, case-insensitive scheme matching, and fail-fast rejection of null
 * sub-policies.
 */
public class BBCodePolicyTest {

    @Test
    public void defaultsHaveExpectedValues() {
        RXBBCodePolicy policy = RXBBCodePolicy.defaults();

        assertEquals(Set.of("http", "https", "mailto"), policy.urlPolicy().allowedSchemes());
        assertEquals(Set.of("http", "https"), policy.imagePolicy().allowedSchemes());
        assertTrue(policy.imagePolicy().loadImages());
    }

    @Test
    public void schemeMatchingIsCaseInsensitive() {
        RXBBCodeUrlPolicy url = new RXBBCodeUrlPolicy(Set.of("HTTPS"));

        assertTrue(url.isSchemeAllowed("https"));
        assertTrue(url.isSchemeAllowed("HTTPS"));
        assertFalse(url.isSchemeAllowed("http"));
        assertFalse(url.isSchemeAllowed(null));

        RXBBCodeImagePolicy image = new RXBBCodeImagePolicy(Set.of("HTTP"), true);
        assertTrue(image.isSchemeAllowed("http"));
        assertFalse(image.isSchemeAllowed("https"));
    }

    @Test
    public void allowedSchemesSetIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> RXBBCodeUrlPolicy.defaults().allowedSchemes().add("ftp"));
        assertThrows(UnsupportedOperationException.class,
                () -> RXBBCodeImagePolicy.defaults().allowedSchemes().add("ftp"));
    }

    @Test
    public void nullSubPoliciesAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new RXBBCodePolicy(null, RXBBCodeImagePolicy.defaults()));
        assertThrows(NullPointerException.class,
                () -> new RXBBCodePolicy(RXBBCodeUrlPolicy.defaults(), null));
    }

    @Test
    public void customPolicyRoundTrips() {
        RXBBCodeUrlPolicy url = new RXBBCodeUrlPolicy(Set.of("http"));
        RXBBCodeImagePolicy image = new RXBBCodeImagePolicy(Set.of("https"), false);
        RXBBCodePolicy policy = new RXBBCodePolicy(url, image);

        assertFalse(policy.imagePolicy().loadImages());
        assertEquals(Set.of("http"), policy.urlPolicy().allowedSchemes());
    }
}
