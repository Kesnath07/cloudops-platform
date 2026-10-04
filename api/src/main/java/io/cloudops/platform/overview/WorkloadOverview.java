package io.cloudops.platform.overview;

import io.cloudops.platform.catalog.application.WorkloadView;
import io.cloudops.platform.deployments.application.DeploymentView;
import io.cloudops.platform.incidents.domain.WorkloadHealth;

/**
 * One row of the operations dashboard.
 *
 * @param lastProductionDeployment {@code null} if the workload has never been deployed to production
 */
public record WorkloadOverview(WorkloadView workload, WorkloadHealth health, int activeIncidents,
                               DeploymentView lastProductionDeployment) {
}
