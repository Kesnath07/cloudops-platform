package io.cloudops.platform.notifications;

import io.cloudops.platform.incidents.domain.Severity;

import java.util.UUID;

/**
 * A channel-agnostic notification. {@code eventType} and {@code severity} are exposed as message
 * attributes so subscribers can filter (for example, only SEV1 pages to a phone).
 */
public record IncidentNotification(UUID incidentId, String eventType, Severity severity,
                                   String subject, String body) {
}
