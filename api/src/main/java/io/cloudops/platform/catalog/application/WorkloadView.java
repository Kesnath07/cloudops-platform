package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Criticality;
import io.cloudops.platform.catalog.domain.Team;
import io.cloudops.platform.catalog.domain.Workload;

import java.time.Instant;
import java.util.UUID;

public record WorkloadView(UUID id, String slug, String name, String description, Criticality criticality,
                           TeamSummary team, String repositoryUrl, String runbookUrl,
                           Instant createdAt, Instant updatedAt) {

    public record TeamSummary(UUID id, String slug, String name) {
    }

    static WorkloadView of(Workload workload) {
        Team team = workload.getTeam();
        return new WorkloadView(workload.getId(), workload.getSlug(), workload.getName(),
                workload.getDescription(), workload.getCriticality(),
                new TeamSummary(team.getId(), team.getSlug(), team.getName()),
                workload.getRepositoryUrl(), workload.getRunbookUrl(),
                workload.getCreatedAt(), workload.getUpdatedAt());
    }
}
