package io.cloudops.platform.shared.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    @Test
    void reusesWellFormedUpstreamIds() {
        assertThat(RequestCorrelationFilter.resolveRequestId("Root-1a2b3c4d")).isEqualTo("Root-1a2b3c4d");
    }

    @Test
    void replacesMissingOrUnsafeIds() {
        for (String candidate : new String[]{null, "", "short", "bad id\nINJECTED", "x".repeat(65)}) {
            String resolved = RequestCorrelationFilter.resolveRequestId(candidate);
            assertThat(UUID.fromString(resolved)).isNotNull();
        }
    }
}
