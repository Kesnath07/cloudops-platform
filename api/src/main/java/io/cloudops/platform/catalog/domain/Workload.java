package io.cloudops.platform.catalog.domain;

import io.cloudops.platform.shared.domain.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * A deployable unit of software (API, worker, frontend) owned by exactly one team.
 */
@Entity
@Table(name = "workloads")
public class Workload extends VersionedEntity {

    @Column(nullable = false, unique = true, length = 63, updatable = false)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Criticality criticality;

    @Column(name = "repository_url", length = 500)
    private String repositoryUrl;

    @Column(name = "runbook_url", length = 500)
    private String runbookUrl;

    protected Workload() {
    }

    public Workload(String slug, WorkloadDetails details, Team team) {
        this.slug = Objects.requireNonNull(slug);
        update(details, team);
    }

    public final void update(WorkloadDetails details, Team owningTeam) {
        this.name = details.name().strip();
        this.description = details.description();
        this.criticality = Objects.requireNonNull(details.criticality());
        this.repositoryUrl = details.repositoryUrl();
        this.runbookUrl = details.runbookUrl();
        this.team = Objects.requireNonNull(owningTeam);
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Team getTeam() {
        return team;
    }

    public Criticality getCriticality() {
        return criticality;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getRunbookUrl() {
        return runbookUrl;
    }
}
