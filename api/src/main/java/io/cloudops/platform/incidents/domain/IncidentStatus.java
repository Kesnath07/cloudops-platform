package io.cloudops.platform.incidents.domain;

import java.util.Set;

/**
 * Incident lifecycle. An incident can bounce between OPEN and MITIGATED if a mitigation fails,
 * but RESOLVED is terminal: a recurrence is tracked as a new incident so that timelines and
 * resolution metrics stay accurate.
 */
public enum IncidentStatus {
    OPEN,
    MITIGATED,
    RESOLVED;

    public boolean canTransitionTo(IncidentStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isActive() {
        return this != RESOLVED;
    }

    private Set<IncidentStatus> allowedTargets() {
        return switch (this) {
            case OPEN -> Set.of(MITIGATED, RESOLVED);
            case MITIGATED -> Set.of(OPEN, RESOLVED);
            case RESOLVED -> Set.of();
        };
    }
}
