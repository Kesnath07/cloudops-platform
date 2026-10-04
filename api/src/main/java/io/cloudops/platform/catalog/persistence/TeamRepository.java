package io.cloudops.platform.catalog.persistence;

import io.cloudops.platform.catalog.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    boolean existsBySlug(String slug);
}
