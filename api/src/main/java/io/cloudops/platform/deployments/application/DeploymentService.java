package io.cloudops.platform.deployments.application;

import io.cloudops.platform.catalog.application.WorkloadService;
import io.cloudops.platform.deployments.domain.Deployment;
import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import io.cloudops.platform.deployments.persistence.DeploymentRepository;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    private final DeploymentRepository deployments;
    private final WorkloadService workloadService;
    private final Clock clock;

    public DeploymentService(DeploymentRepository deployments, WorkloadService workloadService, Clock clock) {
        this.deployments = deployments;
        this.workloadService = workloadService;
        this.clock = clock;
    }

    @Transactional
    public DeploymentView record(UUID workloadId, RecordDeploymentCommand command, Actor actor) {
        workloadService.reference(workloadId);
        Deployment deployment = deployments.save(new Deployment(
                workloadId,
                command.environment(),
                command.version(),
                command.commitSha(),
                command.outcome(),
                command.notes(),
                actor.id(),
                actor.displayName(),
                command.deployedAt() != null ? command.deployedAt() : clock.instant()));
        log.info("Recorded {} deployment of workload {} version {} to {}",
                command.outcome(), workloadId, command.version(), command.environment());
        return DeploymentView.of(deployment);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeploymentView> history(UUID workloadId, DeploymentEnvironment environment,
                                                int page, int size) {
        workloadService.reference(workloadId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deployedAt"));
        return PageResponse.from(environment == null
                ? deployments.findByWorkloadId(workloadId, pageable)
                : deployments.findByWorkloadIdAndEnvironment(workloadId, environment, pageable),
                DeploymentView::of);
    }

    @Transactional(readOnly = true)
    public Map<UUID, DeploymentView> latestByWorkload(DeploymentEnvironment environment) {
        return deployments.findLatestPerWorkload(environment).stream()
                .map(DeploymentView::of)
                .collect(Collectors.toMap(DeploymentView::workloadId, Function.identity(),
                        (first, second) -> first.deployedAt().isAfter(second.deployedAt()) ? first : second));
    }
}
