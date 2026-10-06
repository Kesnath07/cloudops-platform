package io.cloudops.platform.incidents.domain;

import io.cloudops.platform.shared.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {

    private static final UUID WORKLOAD = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant OPENED = Instant.parse("2026-03-01T10:00:00Z");

    private Incident openIncident(Severity severity) {
        return Incident.open(WORKLOAD, "  Checkout latency spike ", "p99 above 2s", severity, ACTOR, "Dana", OPENED);
    }

    @Test
    void openingAnIncidentStartsTheTimeline() {
        Incident incident = openIncident(Severity.SEV2);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getTitle()).isEqualTo("Checkout latency spike");
        assertThat(incident.getOpenedAt()).isEqualTo(OPENED);
        assertThat(incident.getTimeline()).singleElement().satisfies(entry -> {
            assertThat(entry.getStatusAfter()).isEqualTo(IncidentStatus.OPEN);
            assertThat(entry.getSeverityAfter()).isEqualTo(Severity.SEV2);
            assertThat(entry.getAuthorName()).isEqualTo("Dana");
        });
    }

    @Test
    void mitigatingThenResolvingRecordsTimestamps() {
        Incident incident = openIncident(Severity.SEV1);
        Instant mitigated = OPENED.plus(Duration.ofMinutes(20));
        Instant resolved = OPENED.plus(Duration.ofHours(2));

        incident.postUpdate("Rolled back release", IncidentStatus.MITIGATED, null, ACTOR, "Dana", mitigated);
        incident.postUpdate("Root cause fixed", IncidentStatus.RESOLVED, null, ACTOR, "Dana", resolved);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getMitigatedAt()).isEqualTo(mitigated);
        assertThat(incident.getResolvedAt()).isEqualTo(resolved);
        assertThat(incident.getTimeline()).extracting(IncidentUpdate::getStatusAfter)
                .containsExactly(IncidentStatus.OPEN, IncidentStatus.MITIGATED, IncidentStatus.RESOLVED);
    }

    @Test
    void resolvingDirectlyAlsoMarksTheIncidentMitigated() {
        Incident incident = openIncident(Severity.SEV3);
        Instant resolved = OPENED.plusSeconds(600);

        incident.postUpdate("False alarm", IncidentStatus.RESOLVED, null, ACTOR, "Dana", resolved);

        assertThat(incident.getMitigatedAt()).isEqualTo(resolved);
    }

    @Test
    void reopeningAMitigatedIncidentClearsTheMitigationTime() {
        Incident incident = openIncident(Severity.SEV2);
        incident.postUpdate("Failover done", IncidentStatus.MITIGATED, null, ACTOR, "Dana", OPENED.plusSeconds(60));

        incident.postUpdate("Errors are back", IncidentStatus.OPEN, null, ACTOR, "Dana", OPENED.plusSeconds(120));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getMitigatedAt()).isNull();
    }

    @Test
    void severityCanBeReclassifiedWithoutChangingStatus() {
        Incident incident = openIncident(Severity.SEV3);

        incident.postUpdate("Impact is wider than thought", null, Severity.SEV1, ACTOR, "Dana", OPENED.plusSeconds(30));

        assertThat(incident.getSeverity()).isEqualTo(Severity.SEV1);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getTimeline()).last().extracting(IncidentUpdate::getSeverityAfter).isEqualTo(Severity.SEV1);
    }

    @Test
    void resolvedIncidentsAcceptNoFurtherUpdates() {
        Incident incident = openIncident(Severity.SEV2);
        incident.postUpdate("Done", IncidentStatus.RESOLVED, null, ACTOR, "Dana", OPENED.plusSeconds(60));

        assertThatThrownBy(() -> incident.postUpdate("One more thing", null, null, ACTOR, "Dana", OPENED.plusSeconds(90)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("resolved");
        assertThat(incident.getTimeline()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN, MITIGATED, true",
            "OPEN, RESOLVED, true",
            "MITIGATED, OPEN, true",
            "MITIGATED, RESOLVED, true",
            "RESOLVED, OPEN, false",
            "RESOLVED, MITIGATED, false",
            "OPEN, OPEN, false"
    })
    void lifecycleTransitions(IncidentStatus from, IncidentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void blankSummaryIsStoredAsAbsentAndUpdatesAreStripped() {
        Incident incident = Incident.open(WORKLOAD, "Checkout latency spike", "   ", Severity.SEV3, ACTOR, "Dana", OPENED);

        incident.postUpdate("  Rolled back the release \n", null, null, ACTOR, "Dana", OPENED.plusSeconds(60));

        assertThat(incident.getSummary()).isNull();
        assertThat(incident.getTimeline().getLast().getMessage()).isEqualTo("Rolled back the release");
    }
}
