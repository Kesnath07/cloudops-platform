package io.cloudops.platform.incidents.events;

import io.cloudops.platform.incidents.domain.Severity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an incident has been committed to the database.
 */
public record IncidentOpenedEvent(UUID incidentId, UUID workloadId, String workloadName, String title,
                                  Severity severity, String openedBy, Instant occurredAt) {
}
