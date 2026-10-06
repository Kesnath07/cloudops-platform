package io.cloudops.platform.incidents.domain;

import io.cloudops.platform.shared.domain.Text;
import io.cloudops.platform.shared.domain.VersionedEntity;
import io.cloudops.platform.shared.error.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for an incident and its timeline. All state changes go through
 * {@link #postUpdate}, which enforces the lifecycle and records who changed what.
 */
@Entity
@Table(name = "incidents")
public class Incident extends VersionedEntity {

    @Column(name = "workload_id", nullable = false, updatable = false)
    private UUID workloadId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(name = "opened_by_id", nullable = false, updatable = false)
    private UUID openedById;

    @Column(name = "opened_by_name", nullable = false, length = 100, updatable = false)
    private String openedByName;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "mitigated_at")
    private Instant mitigatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("postedAt ASC")
    private List<IncidentUpdate> timeline = new ArrayList<>();

    protected Incident() {
    }

    private Incident(UUID workloadId, String title, String summary, Severity severity,
                     UUID openedById, String openedByName, Instant openedAt) {
        this.workloadId = Objects.requireNonNull(workloadId);
        this.title = Text.required(title);
        this.summary = Text.optional(summary);
        this.severity = Objects.requireNonNull(severity);
        this.status = IncidentStatus.OPEN;
        this.openedById = Objects.requireNonNull(openedById);
        this.openedByName = Objects.requireNonNull(openedByName);
        this.openedAt = Objects.requireNonNull(openedAt);
    }

    public static Incident open(UUID workloadId, String title, String summary, Severity severity,
                                UUID actorId, String actorName, Instant now) {
        Incident incident = new Incident(workloadId, title, summary, severity, actorId, actorName, now);
        incident.timeline.add(new IncidentUpdate(incident, "Incident opened", IncidentStatus.OPEN, severity,
                actorId, actorName, now));
        return incident;
    }

    /**
     * Appends a timeline entry and optionally moves the incident to a new status or severity.
     *
     * @param requestedStatus   the new status, or {@code null} to keep the current one
     * @param requestedSeverity the new severity, or {@code null} to keep the current one
     * @throws ConflictException if the incident is resolved or the transition is not allowed
     */
    public void postUpdate(String message, IncidentStatus requestedStatus, Severity requestedSeverity,
                           UUID actorId, String actorName, Instant now) {
        if (status == IncidentStatus.RESOLVED) {
            throw new ConflictException("Incident is resolved; open a new incident if the problem recurs");
        }
        if (requestedStatus != null && requestedStatus != status) {
            transitionTo(requestedStatus, now);
        }
        if (requestedSeverity != null) {
            severity = requestedSeverity;
        }
        timeline.add(new IncidentUpdate(this, Text.required(message), status, severity,
                actorId, actorName, now));
    }

    private void transitionTo(IncidentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Incident cannot move from " + status + " to " + target);
        }
        switch (target) {
            case MITIGATED -> mitigatedAt = now;
            case RESOLVED -> {
                resolvedAt = now;
                if (mitigatedAt == null) {
                    mitigatedAt = now;
                }
            }
            case OPEN -> mitigatedAt = null;
        }
        status = target;
    }

    public UUID getWorkloadId() {
        return workloadId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public Severity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public UUID getOpenedById() {
        return openedById;
    }

    public String getOpenedByName() {
        return openedByName;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getMitigatedAt() {
        return mitigatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public List<IncidentUpdate> getTimeline() {
        return Collections.unmodifiableList(timeline);
    }
}
