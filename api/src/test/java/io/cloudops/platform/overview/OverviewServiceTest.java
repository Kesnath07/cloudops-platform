package io.cloudops.platform.overview;

import io.cloudops.platform.catalog.application.WorkloadService;
import io.cloudops.platform.catalog.application.WorkloadView;
import io.cloudops.platform.catalog.domain.Criticality;
import io.cloudops.platform.deployments.application.DeploymentService;
import io.cloudops.platform.deployments.application.DeploymentView;
import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import io.cloudops.platform.deployments.domain.DeploymentOutcome;
import io.cloudops.platform.incidents.application.IncidentService;
import io.cloudops.platform.incidents.application.WorkloadHealthView;
import io.cloudops.platform.incidents.domain.WorkloadHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final WorkloadView.TeamSummary TEAM =
            new WorkloadView.TeamSummary(UUID.randomUUID(), "payments", "Payments");

    @Mock
    private WorkloadService workloadService;

    @Mock
    private IncidentService incidentService;

    @Mock
    private DeploymentService deploymentService;

    private OverviewService service;

    @BeforeEach
    void setUp() {
        service = new OverviewService(workloadService, incidentService, deploymentService);
    }

    @Test
    void mostImpactedWorkloadsComeFirstThenByNameAndSlug() {
        WorkloadView ledger = workload("ledger", "Ledger");
        WorkloadView checkoutEu = workload("checkout-eu", "Checkout");
        WorkloadView checkoutAu = workload("checkout-au", "Checkout");
        WorkloadView payments = workload("payments-api", "Payments API");
        when(workloadService.listAll()).thenReturn(List.of(ledger, checkoutEu, checkoutAu, payments));
        when(incidentService.healthByWorkload()).thenReturn(Map.of(
                payments.id(), new WorkloadHealthView(WorkloadHealth.MAJOR_OUTAGE, 2),
                ledger.id(), new WorkloadHealthView(WorkloadHealth.DEGRADED, 1)));
        when(deploymentService.latestByWorkload(DeploymentEnvironment.PRODUCTION)).thenReturn(Map.of());

        List<WorkloadOverview> rows = service.workloads();

        assertThat(rows).extracting(row -> row.workload().slug())
                .containsExactly("payments-api", "ledger", "checkout-au", "checkout-eu");
        assertThat(rows.getFirst().activeIncidents()).isEqualTo(2);
    }

    @Test
    void workloadsWithoutActiveIncidentsAreOperationalAndCarryTheirLatestRelease() {
        WorkloadView ledger = workload("ledger", "Ledger");
        DeploymentView release = new DeploymentView(UUID.randomUUID(), ledger.id(), DeploymentEnvironment.PRODUCTION,
                "2.4.1", null, DeploymentOutcome.SUCCEEDED, null, UUID.randomUUID(), "Olu Operator", NOW);
        when(workloadService.listAll()).thenReturn(List.of(ledger));
        when(incidentService.healthByWorkload()).thenReturn(Map.of());
        when(deploymentService.latestByWorkload(DeploymentEnvironment.PRODUCTION))
                .thenReturn(Map.of(ledger.id(), release));

        WorkloadOverview row = service.workloads().getFirst();

        assertThat(row.health()).isEqualTo(WorkloadHealth.OPERATIONAL);
        assertThat(row.activeIncidents()).isZero();
        assertThat(row.lastProductionDeployment()).isEqualTo(release);
    }

    private static WorkloadView workload(String slug, String name) {
        return new WorkloadView(UUID.randomUUID(), slug, name, null, Criticality.MEDIUM, TEAM, null, null, NOW, NOW);
    }
}
