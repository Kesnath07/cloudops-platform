package io.cloudops.platform.incidents.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadHealthTest {

    private static final UUID WORKLOAD = UUID.randomUUID();

    private static IncidentSignal signal(Severity severity, IncidentStatus status) {
        return new IncidentSignal(WORKLOAD, severity, status);
    }

    @Test
    void noActiveIncidentsMeansOperational() {
        assertThat(WorkloadHealth.assess(List.of())).isEqualTo(WorkloadHealth.OPERATIONAL);
    }

    @Test
    void openIncidentSeverityDrivesHealth() {
        assertThat(WorkloadHealth.assess(List.of(signal(Severity.SEV1, IncidentStatus.OPEN))))
                .isEqualTo(WorkloadHealth.MAJOR_OUTAGE);
        assertThat(WorkloadHealth.assess(List.of(signal(Severity.SEV2, IncidentStatus.OPEN))))
                .isEqualTo(WorkloadHealth.PARTIAL_OUTAGE);
        assertThat(WorkloadHealth.assess(List.of(signal(Severity.SEV4, IncidentStatus.OPEN))))
                .isEqualTo(WorkloadHealth.DEGRADED);
    }

    @Test
    void mitigatedIncidentsCapAtDegraded() {
        assertThat(WorkloadHealth.assess(List.of(signal(Severity.SEV1, IncidentStatus.MITIGATED))))
                .isEqualTo(WorkloadHealth.DEGRADED);
    }

    @Test
    void worstIncidentWins() {
        assertThat(WorkloadHealth.assess(List.of(
                signal(Severity.SEV3, IncidentStatus.OPEN),
                signal(Severity.SEV1, IncidentStatus.MITIGATED),
                signal(Severity.SEV2, IncidentStatus.OPEN))))
                .isEqualTo(WorkloadHealth.PARTIAL_OUTAGE);
    }

    @Test
    void severityComparisons() {
        assertThat(Severity.SEV1.isAtLeast(Severity.SEV2)).isTrue();
        assertThat(Severity.SEV2.isAtLeast(Severity.SEV2)).isTrue();
        assertThat(Severity.SEV3.isAtLeast(Severity.SEV2)).isFalse();
        assertThat(Severity.SEV2.isMoreSevereThan(Severity.SEV3)).isTrue();
        assertThat(Severity.SEV2.isMoreSevereThan(Severity.SEV2)).isFalse();
    }

    @Test
    void healthIsOrderedFromOperationalToMajorOutage() {
        assertThat(WorkloadHealth.MAJOR_OUTAGE.isWorseThan(WorkloadHealth.PARTIAL_OUTAGE)).isTrue();
        assertThat(WorkloadHealth.DEGRADED.isWorseThan(WorkloadHealth.OPERATIONAL)).isTrue();
        assertThat(WorkloadHealth.DEGRADED.isWorseThan(WorkloadHealth.DEGRADED)).isFalse();
        assertThat(WorkloadHealth.OPERATIONAL.isWorseThan(WorkloadHealth.DEGRADED)).isFalse();
    }
}
