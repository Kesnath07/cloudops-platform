package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Team;

import java.time.Instant;
import java.util.UUID;

public record TeamView(UUID id, String slug, String name, String description, String contactEmail,
                       Instant createdAt, Instant updatedAt) {

    static TeamView of(Team team) {
        return new TeamView(team.getId(), team.getSlug(), team.getName(), team.getDescription(),
                team.getContactEmail(), team.getCreatedAt(), team.getUpdatedAt());
    }
}
