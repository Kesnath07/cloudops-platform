package io.cloudops.platform.incidents.application;

import io.cloudops.platform.catalog.application.WorkloadRef;
import io.cloudops.platform.incidents.domain.Incident;
import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.Severity;

import java.time.Instant;
import java.util.UUID;

public record IncidentView(UUID id, WorkloadRef workload, String title, Severity severity, IncidentStatus status,
                           String openedByName, Instant openedAt, Instant mitigatedAt, Instant resolvedAt,
                           Instant updatedAt) {

    static IncidentView of(Incident incident, WorkloadRef workload) {
        return new IncidentView(incident.getId(), workload, incident.getTitle(), incident.getSeverity(),
                incident.getStatus(), incident.getOpenedByName(), incident.getOpenedAt(),
                incident.getMitigatedAt(), incident.getResolvedAt(), incident.getUpdatedAt());
    }
}
