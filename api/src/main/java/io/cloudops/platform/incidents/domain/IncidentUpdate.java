package io.cloudops.platform.incidents.domain;

import io.cloudops.platform.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry on an incident's timeline. Entries are append-only so the timeline is a faithful
 * record of how the incident was handled.
 */
@Entity
@Immutable
@Table(name = "incident_updates")
public class IncidentUpdate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @Column(nullable = false, length = 4000, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 20, updatable = false)
    private IncidentStatus statusAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_after", nullable = false, length = 10, updatable = false)
    private Severity severityAfter;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 100, updatable = false)
    private String authorName;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    protected IncidentUpdate() {
    }

    IncidentUpdate(Incident incident, String message, IncidentStatus statusAfter, Severity severityAfter,
                   UUID authorId, String authorName, Instant postedAt) {
        this.incident = incident;
        this.message = message;
        this.statusAfter = statusAfter;
        this.severityAfter = severityAfter;
        this.authorId = authorId;
        this.authorName = authorName;
        this.postedAt = postedAt;
    }

    public String getMessage() {
        return message;
    }

    public IncidentStatus getStatusAfter() {
        return statusAfter;
    }

    public Severity getSeverityAfter() {
        return severityAfter;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Instant getPostedAt() {
        return postedAt;
    }
}
