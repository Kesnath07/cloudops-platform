package io.cloudops.platform.incidents.application;

import io.cloudops.platform.catalog.application.WorkloadRef;
import io.cloudops.platform.incidents.domain.Incident;
import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.IncidentUpdate;
import io.cloudops.platform.incidents.domain.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentDetailView(UUID id, WorkloadRef workload, String title, String summary, Severity severity,
                                 IncidentStatus status, String openedByName, Instant openedAt,
                                 Instant mitigatedAt, Instant resolvedAt, long version,
                                 List<TimelineEntry> timeline) {

    public record TimelineEntry(UUID id, String message, IncidentStatus status, Severity severity,
                                String authorName, Instant postedAt) {

        static TimelineEntry of(IncidentUpdate update) {
            return new TimelineEntry(update.getId(), update.getMessage(), update.getStatusAfter(),
                    update.getSeverityAfter(), update.getAuthorName(), update.getPostedAt());
        }
    }

    static IncidentDetailView of(Incident incident, WorkloadRef workload) {
        return new IncidentDetailView(incident.getId(), workload, incident.getTitle(), incident.getSummary(),
                incident.getSeverity(), incident.getStatus(), incident.getOpenedByName(), incident.getOpenedAt(),
                incident.getMitigatedAt(), incident.getResolvedAt(), incident.getVersion(),
                incident.getTimeline().stream().map(TimelineEntry::of).toList());
    }
}
