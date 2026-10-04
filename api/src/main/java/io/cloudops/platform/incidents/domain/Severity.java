package io.cloudops.platform.incidents.domain;

/**
 * Incident severity, declared from most to least severe.
 */
public enum Severity {
    /** Complete outage of a critical capability for all users. */
    SEV1,
    /** Major functionality unavailable or a large share of users affected. */
    SEV2,
    /** Partial degradation with a workaround available. */
    SEV3,
    /** Minor issue with no meaningful customer impact. */
    SEV4;

    public boolean isAtLeast(Severity threshold) {
        return ordinal() <= threshold.ordinal();
    }

    public boolean isMoreSevereThan(Severity other) {
        return ordinal() < other.ordinal();
    }
}
