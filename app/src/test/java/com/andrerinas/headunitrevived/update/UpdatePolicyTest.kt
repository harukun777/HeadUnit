package com.andrerinas.headunitrevived.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun rejectsDowngradesAndWrongVariants() {
        assertFalse(UpdatePolicy.eligible(80, 81, 22, 22, "android51", "android51"))
        assertFalse(UpdatePolicy.eligible(81, 81, 22, 22, "android51", "android51"))
        assertFalse(UpdatePolicy.eligible(82, 81, 24, 22, "android51", "android51"))
        assertFalse(UpdatePolicy.eligible(82, 81, 22, 22, "modern", "android51"))
        assertTrue(UpdatePolicy.eligible(82, 81, 22, 22, "android51", "android51"))
    }
    @Test fun requiresExactNonemptySignerSet() {
        assertTrue(UpdatePolicy.matchingSigners(setOf("a", "b"), setOf("b", "a")))
        assertFalse(UpdatePolicy.matchingSigners(setOf("a"), setOf("attacker")))
        assertFalse(UpdatePolicy.matchingSigners(setOf("a", "b"), setOf("a")))
        assertFalse(UpdatePolicy.matchingSigners(emptySet(), emptySet()))
    }
    @Test fun rejectsInsecureAndCredentialUrls() {
        for (url in listOf("http://example.com/update.json", "file:///tmp/update.apk", "https://user:pass@example.com/a", "https:///a")) {
            try { UpdatePolicy.requireHttps(url); fail(url) } catch (_: IllegalArgumentException) { }
        }
        UpdatePolicy.requireHttps("https://example.com/update.json")
    }
}
