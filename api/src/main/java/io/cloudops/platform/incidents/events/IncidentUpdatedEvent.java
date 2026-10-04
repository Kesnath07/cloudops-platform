package io.cloudops.platform.incidents.events;

import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.Severity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a timeline entry has been committed, carrying before and after state so
 * listeners can react to transitions without querying the incident again.
 */
public record IncidentUpdatedEvent(UUID incidentId, UUID workloadId, String workloadName, String title,
                                   IncidentStatus previousStatus, IncidentStatus status,
                                   Severity previousSeverity, Severity severity,
                                   String message, String updatedBy, Instant occurredAt) {

    public boolean statusChanged() {
        return previousStatus != status;
    }

    public boolean escalated() {
        return severity.isMoreSevereThan(previousSeverity);
    }
}
