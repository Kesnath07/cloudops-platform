package io.cloudops.platform.incidents.persistence;

import io.cloudops.platform.incidents.domain.Incident;
import io.cloudops.platform.incidents.domain.IncidentSignal;
import io.cloudops.platform.incidents.domain.IncidentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    @EntityGraph(attributePaths = "timeline")
    Optional<Incident> findWithTimelineById(UUID id);

    /**
     * Projection of all unresolved incidents, served by the partial index on active incidents.
     */
    List<IncidentSignal> findByStatusNot(IncidentStatus status);
}
