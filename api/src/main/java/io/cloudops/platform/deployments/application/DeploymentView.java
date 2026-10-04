package io.cloudops.platform.deployments.application;

import io.cloudops.platform.deployments.domain.Deployment;
import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import io.cloudops.platform.deployments.domain.DeploymentOutcome;

import java.time.Instant;
import java.util.UUID;

public record DeploymentView(UUID id, UUID workloadId, DeploymentEnvironment environment, String version,
                             String commitSha, DeploymentOutcome outcome, String notes,
                             UUID deployedById, String deployedByName, Instant deployedAt) {

    static DeploymentView of(Deployment deployment) {
        return new DeploymentView(deployment.getId(), deployment.getWorkloadId(), deployment.getEnvironment(),
                deployment.getReleaseVersion(), deployment.getCommitSha(), deployment.getOutcome(),
                deployment.getNotes(), deployment.getDeployedById(), deployment.getDeployedByName(),
                deployment.getDeployedAt());
    }
}
