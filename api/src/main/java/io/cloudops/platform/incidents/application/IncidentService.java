package io.cloudops.platform.incidents.application;

import io.cloudops.platform.catalog.application.WorkloadRef;
import io.cloudops.platform.catalog.application.WorkloadService;
import io.cloudops.platform.incidents.domain.Incident;
import io.cloudops.platform.incidents.domain.IncidentSignal;
import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.Severity;
import io.cloudops.platform.incidents.domain.WorkloadHealth;
import io.cloudops.platform.incidents.events.IncidentOpenedEvent;
import io.cloudops.platform.incidents.events.IncidentUpdatedEvent;
import io.cloudops.platform.incidents.persistence.IncidentRepository;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidents;
    private final WorkloadService workloadService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public IncidentService(IncidentRepository incidents, WorkloadService workloadService,
                           ApplicationEventPublisher events, Clock clock) {
        this.incidents = incidents;
        this.workloadService = workloadService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public IncidentDetailView open(OpenIncidentCommand command, Actor actor) {
        WorkloadRef workload = workloadService.reference(command.workloadId());
        Instant now = clock.instant();
        Incident incident = incidents.save(Incident.open(workload.id(), command.title(), command.summary(),
                command.severity(), actor.id(), actor.displayName(), now));
        log.info("Incident {} opened on workload {} with severity {}", incident.getId(), workload.slug(),
                incident.getSeverity());
        events.publishEvent(new IncidentOpenedEvent(incident.getId(), workload.id(), workload.name(),
                incident.getTitle(), incident.getSeverity(), actor.displayName(), now));
        return IncidentDetailView.of(incident, workload);
    }

    @Transactional
    public IncidentDetailView postUpdate(UUID incidentId, PostIncidentUpdateCommand command, Actor actor) {
        Incident incident = loadWithTimeline(incidentId);
        IncidentStatus previousStatus = incident.getStatus();
        Severity previousSeverity = incident.getSeverity();
        Instant now = clock.instant();

        incident.postUpdate(command.message(), command.status(), command.severity(),
                actor.id(), actor.displayName(), now);
        incidents.flush();

        WorkloadRef workload = workloadService.reference(incident.getWorkloadId());
        if (previousStatus != incident.getStatus()) {
            log.info("Incident {} moved from {} to {}", incidentId, previousStatus, incident.getStatus());
        }
        events.publishEvent(new IncidentUpdatedEvent(incident.getId(), workload.id(), workload.name(),
                incident.getTitle(), previousStatus, incident.getStatus(), previousSeverity,
                incident.getSeverity(), command.message(), actor.displayName(), now));
        return IncidentDetailView.of(incident, workload);
    }

    @Transactional(readOnly = true)
    public IncidentDetailView get(UUID incidentId) {
        Incident incident = loadWithTimeline(incidentId);
        return IncidentDetailView.of(incident, workloadService.reference(incident.getWorkloadId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentView> list(IncidentStatus status, UUID workloadId, int page, int size) {
        Page<Incident> result = incidents.findAll(filter(status, workloadId),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt")));
        Map<UUID, WorkloadRef> workloads = workloadService.references(
                result.stream().map(Incident::getWorkloadId).collect(Collectors.toSet()));
        return PageResponse.from(result, incident -> IncidentView.of(incident, workloads.get(incident.getWorkloadId())));
    }

    /**
     * Health of every workload that currently has active incidents. Workloads absent from the
     * map are operational.
     */
    @Transactional(readOnly = true)
    public Map<UUID, WorkloadHealthView> healthByWorkload() {
        Map<UUID, List<IncidentSignal>> active = incidents.findByStatusNot(IncidentStatus.RESOLVED).stream()
                .collect(Collectors.groupingBy(IncidentSignal::workloadId));
        return active.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new WorkloadHealthView(WorkloadHealth.assess(entry.getValue()), entry.getValue().size())));
    }

    private Incident loadWithTimeline(UUID incidentId) {
        return incidents.findWithTimelineById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));
    }

    private static Specification<Incident> filter(IncidentStatus status, UUID workloadId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (workloadId != null) {
                predicates.add(cb.equal(root.get("workloadId"), workloadId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
