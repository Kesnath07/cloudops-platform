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
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    /** Identifiers break ties between deployments recorded for the same instant, keeping pages stable. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "deployedAt", "id");
    private static final Comparator<DeploymentView> CHRONOLOGICAL =
            Comparator.comparing(DeploymentView::deployedAt).thenComparing(DeploymentView::id);

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
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return PageResponse.from(environment == null
                ? deployments.findByWorkloadId(workloadId, pageable)
                : deployments.findByWorkloadIdAndEnvironment(workloadId, environment, pageable),
                DeploymentView::of);
    }

    /**
     * Two deployments of one workload can share the latest timestamp; the one with the higher
     * (time-ordered) identifier wins so the result does not depend on row order.
     */
    @Transactional(readOnly = true)
    public Map<UUID, DeploymentView> latestByWorkload(DeploymentEnvironment environment) {
        return deployments.findLatestPerWorkload(environment).stream()
                .map(DeploymentView::of)
                .collect(Collectors.toMap(DeploymentView::workloadId, Function.identity(),
                        BinaryOperator.maxBy(CHRONOLOGICAL)));
    }
}
