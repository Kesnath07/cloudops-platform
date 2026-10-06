package io.cloudops.platform.overview;

import io.cloudops.platform.catalog.application.WorkloadService;
import io.cloudops.platform.catalog.application.WorkloadView;
import io.cloudops.platform.deployments.application.DeploymentService;
import io.cloudops.platform.deployments.application.DeploymentView;
import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import io.cloudops.platform.incidents.application.IncidentService;
import io.cloudops.platform.incidents.application.WorkloadHealthView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read model composed from the catalog, incident and deployment modules. It owns no data, which
 * keeps the dependency direction one-way: no other module depends on the overview.
 */
@Service
public class OverviewService {

    private static final Comparator<WorkloadOverview> MOST_IMPACTED_FIRST = Comparator
            .comparing(WorkloadOverview::health, Comparator.reverseOrder())
            .thenComparing(row -> row.workload().name())
            .thenComparing(row -> row.workload().slug());

    private final WorkloadService workloadService;
    private final IncidentService incidentService;
    private final DeploymentService deploymentService;

    public OverviewService(WorkloadService workloadService, IncidentService incidentService,
                           DeploymentService deploymentService) {
        this.workloadService = workloadService;
        this.incidentService = incidentService;
        this.deploymentService = deploymentService;
    }

    @Transactional(readOnly = true)
    public List<WorkloadOverview> workloads() {
        List<WorkloadView> workloads = workloadService.listAll();
        Map<UUID, WorkloadHealthView> health = incidentService.healthByWorkload();
        Map<UUID, DeploymentView> production = deploymentService.latestByWorkload(DeploymentEnvironment.PRODUCTION);
        return workloads.stream()
                .map(workload -> {
                    WorkloadHealthView current = health.getOrDefault(workload.id(), WorkloadHealthView.HEALTHY);
                    return new WorkloadOverview(workload, current.health(), current.activeIncidents(),
                            production.get(workload.id()));
                })
                .sorted(MOST_IMPACTED_FIRST)
                .toList();
    }
}
