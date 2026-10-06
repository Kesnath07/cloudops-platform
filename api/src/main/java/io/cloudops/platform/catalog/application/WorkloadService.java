package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Team;
import io.cloudops.platform.catalog.domain.Workload;
import io.cloudops.platform.catalog.persistence.WorkloadRepository;
import io.cloudops.platform.shared.error.ConflictException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import io.cloudops.platform.shared.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkloadService {

    private static final Logger log = LoggerFactory.getLogger(WorkloadService.class);
    /** Names are not unique; the slug breaks ties so pages are stable. */
    private static final Sort BY_NAME = Sort.by("name", "slug");

    private final WorkloadRepository workloads;
    private final TeamService teamService;

    public WorkloadService(WorkloadRepository workloads, TeamService teamService) {
        this.workloads = workloads;
        this.teamService = teamService;
    }

    @Transactional
    public WorkloadView create(CreateWorkloadCommand command) {
        if (workloads.existsBySlug(command.slug())) {
            throw new ConflictException("A workload with slug '" + command.slug() + "' already exists");
        }
        Team team = teamService.load(command.teamId());
        Workload workload;
        try {
            // Flushing surfaces a concurrent registration of the same slug here, as a conflict.
            workload = workloads.saveAndFlush(new Workload(command.slug(), command.details(), team));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A workload with slug '" + command.slug() + "' already exists");
        }
        log.info("Registered workload {} ({}) for team {}", workload.getSlug(), workload.getId(), team.getSlug());
        return WorkloadView.of(workload);
    }

    @Transactional
    public WorkloadView update(UUID workloadId, UpdateWorkloadCommand command) {
        Workload workload = loadWithTeam(workloadId);
        workload.update(command.details(), teamService.load(command.teamId()));
        return WorkloadView.of(workload);
    }

    @Transactional(readOnly = true)
    public WorkloadView get(UUID workloadId) {
        return WorkloadView.of(loadWithTeam(workloadId));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkloadView> list(UUID teamId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, BY_NAME);
        return PageResponse.from(teamId == null
                ? workloads.findAllBy(pageable)
                : workloads.findByTeamId(teamId, pageable), WorkloadView::of);
    }

    @Transactional(readOnly = true)
    public List<WorkloadView> listAll() {
        return workloads.findAllBy(BY_NAME).stream().map(WorkloadView::of).toList();
    }

    /**
     * Incident history is retained for audit purposes, so a workload that has incidents cannot be
     * removed; the database foreign key enforces this and is surfaced as a conflict.
     */
    @Transactional
    public void delete(UUID workloadId) {
        Workload workload = workloads.findById(workloadId)
                .orElseThrow(() -> new ResourceNotFoundException("Workload", workloadId));
        try {
            workloads.delete(workload);
            workloads.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Workload has incident history and cannot be deleted");
        }
        log.info("Deleted workload {} ({})", workload.getSlug(), workloadId);
    }

    /**
     * Resolves a workload reference for another module, failing if it does not exist.
     */
    @Transactional(readOnly = true)
    public WorkloadRef reference(UUID workloadId) {
        return workloads.findById(workloadId).map(WorkloadRef::of)
                .orElseThrow(() -> new ResourceNotFoundException("Workload", workloadId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, WorkloadRef> references(Collection<UUID> workloadIds) {
        if (workloadIds.isEmpty()) {
            return Map.of();
        }
        return workloads.findByIdIn(workloadIds).stream()
                .map(WorkloadRef::of)
                .collect(Collectors.toMap(WorkloadRef::id, Function.identity()));
    }

    private Workload loadWithTeam(UUID workloadId) {
        return workloads.findWithTeamById(workloadId)
                .orElseThrow(() -> new ResourceNotFoundException("Workload", workloadId));
    }
}
