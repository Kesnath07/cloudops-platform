package io.cloudops.platform.catalog.persistence;

import io.cloudops.platform.catalog.domain.Workload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkloadRepository extends JpaRepository<Workload, UUID> {

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "team")
    Optional<Workload> findWithTeamById(UUID id);

    @EntityGraph(attributePaths = "team")
    Page<Workload> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "team")
    Page<Workload> findByTeamId(UUID teamId, Pageable pageable);

    @EntityGraph(attributePaths = "team")
    List<Workload> findAllBy(Sort sort);

    List<Workload> findByIdIn(Collection<UUID> ids);
}
