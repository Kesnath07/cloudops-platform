package io.cloudops.platform.deployments.domain;

import io.cloudops.platform.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable record of a release reaching an environment. The workload is referenced by id
 * because it belongs to the catalog module; the database foreign key keeps the reference valid.
 */
@Entity
@Immutable
@Table(name = "deployments")
public class Deployment extends BaseEntity {

    @Column(name = "workload_id", nullable = false, updatable = false)
    private UUID workloadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DeploymentEnvironment environment;

    @Column(name = "release_version", nullable = false, length = 100, updatable = false)
    private String releaseVersion;

    @Column(name = "commit_sha", length = 40, updatable = false)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private DeploymentOutcome outcome;

    @Column(length = 2000, updatable = false)
    private String notes;

    @Column(name = "deployed_by_id", nullable = false, updatable = false)
    private UUID deployedById;

    @Column(name = "deployed_by_name", nullable = false, length = 100, updatable = false)
    private String deployedByName;

    @Column(name = "deployed_at", nullable = false, updatable = false)
    private Instant deployedAt;

    protected Deployment() {
    }

    public Deployment(UUID workloadId, DeploymentEnvironment environment, String releaseVersion, String commitSha,
                      DeploymentOutcome outcome, String notes, UUID deployedById, String deployedByName,
                      Instant deployedAt) {
        this.workloadId = Objects.requireNonNull(workloadId);
        this.environment = Objects.requireNonNull(environment);
        this.releaseVersion = Objects.requireNonNull(releaseVersion);
        this.commitSha = commitSha;
        this.outcome = Objects.requireNonNull(outcome);
        this.notes = notes;
        this.deployedById = Objects.requireNonNull(deployedById);
        this.deployedByName = Objects.requireNonNull(deployedByName);
        this.deployedAt = Objects.requireNonNull(deployedAt);
    }

    public UUID getWorkloadId() {
        return workloadId;
    }

    public DeploymentEnvironment getEnvironment() {
        return environment;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public DeploymentOutcome getOutcome() {
        return outcome;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getDeployedById() {
        return deployedById;
    }

    public String getDeployedByName() {
        return deployedByName;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }
}
