package io.cloudops.platform.deployments.application;

import io.cloudops.platform.catalog.application.WorkloadRef;
import io.cloudops.platform.catalog.application.WorkloadService;
import io.cloudops.platform.deployments.domain.Deployment;
import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import io.cloudops.platform.deployments.domain.DeploymentOutcome;
import io.cloudops.platform.deployments.persistence.DeploymentRepository;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import io.cloudops.platform.shared.security.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final UUID WORKLOAD_ID = UUID.randomUUID();
    private static final Actor OPERATOR =
            new Actor(UUID.randomUUID(), "olu@example.org", "Olu Operator", "OPERATOR");

    @Mock
    private DeploymentRepository deployments;

    @Mock
    private WorkloadService workloadService;

    private DeploymentService service;

    @BeforeEach
    void setUp() {
        service = new DeploymentService(deployments, workloadService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordingWithoutATimestampUsesTheClockAndTheActor() {
        when(workloadService.reference(WORKLOAD_ID)).thenReturn(new WorkloadRef(WORKLOAD_ID, "payments-api", "Payments API"));
        when(deployments.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentView recorded = service.record(WORKLOAD_ID, new RecordDeploymentCommand(
                DeploymentEnvironment.PRODUCTION, "1.8.0", "abc1234", DeploymentOutcome.SUCCEEDED, "  ", null), OPERATOR);

        assertThat(recorded.deployedAt()).isEqualTo(NOW);
        assertThat(recorded.deployedByName()).isEqualTo("Olu Operator");
        assertThat(recorded.deployedById()).isEqualTo(OPERATOR.id());
        assertThat(recorded.notes()).isNull();
    }

    @Test
    void recordingKeepsAnExplicitTimestamp() {
        Instant earlier = NOW.minusSeconds(3600);
        when(workloadService.reference(WORKLOAD_ID)).thenReturn(new WorkloadRef(WORKLOAD_ID, "payments-api", "Payments API"));
        when(deployments.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentView recorded = service.record(WORKLOAD_ID, new RecordDeploymentCommand(
                DeploymentEnvironment.STAGING, "1.8.0", null, DeploymentOutcome.FAILED, null, earlier), OPERATOR);

        assertThat(recorded.deployedAt()).isEqualTo(earlier);
    }

    @Test
    void recordingForAnUnknownWorkloadStoresNothing() {
        when(workloadService.reference(WORKLOAD_ID)).thenThrow(new ResourceNotFoundException("Workload", WORKLOAD_ID));

        assertThatThrownBy(() -> service.record(WORKLOAD_ID, new RecordDeploymentCommand(
                DeploymentEnvironment.PRODUCTION, "1.8.0", null, DeploymentOutcome.SUCCEEDED, null, null), OPERATOR))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(deployments, never()).save(any());
    }

    @Test
    void historyIsNewestFirstWithIdentifierTieBreak() {
        when(deployments.findByWorkloadIdAndEnvironment(eq(WORKLOAD_ID), eq(DeploymentEnvironment.PRODUCTION), any()))
                .thenReturn(Page.empty());

        service.history(WORKLOAD_ID, DeploymentEnvironment.PRODUCTION, 0, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(deployments).findByWorkloadIdAndEnvironment(eq(WORKLOAD_ID), eq(DeploymentEnvironment.PRODUCTION),
                pageable.capture());
        assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.desc("deployedAt"), Sort.Order.desc("id"));
    }

    @Test
    void latestDeploymentTieIsResolvedByIdentifierRegardlessOfRowOrder() {
        UUID lower = UUID.fromString("0190a5f1-0000-7000-8000-000000000001");
        UUID higher = UUID.fromString("0190a5f1-0000-7000-8000-000000000002");
        Deployment first = deployment(lower, NOW);
        Deployment second = deployment(higher, NOW);

        when(deployments.findLatestPerWorkload(DeploymentEnvironment.PRODUCTION)).thenReturn(List.of(first, second));
        Map<UUID, DeploymentView> inOrder = service.latestByWorkload(DeploymentEnvironment.PRODUCTION);
        when(deployments.findLatestPerWorkload(DeploymentEnvironment.PRODUCTION)).thenReturn(List.of(second, first));
        Map<UUID, DeploymentView> reversed = service.latestByWorkload(DeploymentEnvironment.PRODUCTION);

        assertThat(inOrder.get(WORKLOAD_ID).id()).isEqualTo(higher);
        assertThat(reversed.get(WORKLOAD_ID).id()).isEqualTo(higher);
    }

    @Test
    void latestDeploymentPrefersTheMostRecent() {
        Deployment older = deployment(UUID.fromString("0190a5f1-0000-7000-8000-000000000009"), NOW.minusSeconds(60));
        Deployment newer = deployment(UUID.fromString("0190a5f1-0000-7000-8000-000000000001"), NOW);
        when(deployments.findLatestPerWorkload(DeploymentEnvironment.PRODUCTION)).thenReturn(List.of(newer, older));

        assertThat(service.latestByWorkload(DeploymentEnvironment.PRODUCTION).get(WORKLOAD_ID).deployedAt())
                .isEqualTo(NOW);
    }

    private static Deployment deployment(UUID id, Instant deployedAt) {
        Deployment deployment = new Deployment(WORKLOAD_ID, DeploymentEnvironment.PRODUCTION, "1.8.0", null,
                DeploymentOutcome.SUCCEEDED, null, OPERATOR.id(), OPERATOR.displayName(), deployedAt);
        ReflectionTestUtils.setField(deployment, "id", id);
        return deployment;
    }
}
