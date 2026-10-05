package io.cloudops.platform.incidents.api;

import io.cloudops.platform.catalog.application.WorkloadRef;
import io.cloudops.platform.incidents.application.IncidentDetailView;
import io.cloudops.platform.incidents.application.IncidentService;
import io.cloudops.platform.incidents.application.OpenIncidentCommand;
import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.Severity;
import io.cloudops.platform.shared.error.ConflictException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.cloudops.platform.support.TestTokens.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest(IncidentController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class IncidentControllerTest {

    private static final UUID WORKLOAD_ID = UUID.fromString("0190a5f1-0000-7000-8000-000000000001");
    private static final String VALID_INCIDENT = """
            {"workloadId":"%s","title":"Elevated 5xx rate","severity":"SEV2"}""".formatted(WORKLOAD_ID);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IncidentService incidentService;

    @Test
    void unauthenticatedRequestsAreRejected() {
        assertThat(mvc.get().uri("/api/v1/incidents")).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(incidentService);
    }

    @Test
    void viewersCannotOpenIncidents() {
        assertThat(mvc.post().uri("/api/v1/incidents").with(as("VIEWER"))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INCIDENT))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.title").isEqualTo("Forbidden");
        verifyNoInteractions(incidentService);
    }

    @Test
    void operatorsOpenIncidentsAndReceiveLocation() {
        UUID incidentId = UUID.randomUUID();
        when(incidentService.open(any(OpenIncidentCommand.class), any(Actor.class))).thenReturn(detail(incidentId));

        assertThat(mvc.post().uri("/api/v1/incidents").with(as("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INCIDENT))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/api/v1/incidents/" + incidentId)
                .bodyJson().extractingPath("$.workload.slug").isEqualTo("payments-api");
    }

    @Test
    void administratorsInheritOperatorPermissions() {
        when(incidentService.open(any(OpenIncidentCommand.class), any(Actor.class))).thenReturn(detail(UUID.randomUUID()));

        assertThat(mvc.post().uri("/api/v1/incidents").with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INCIDENT))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void invalidPayloadReturnsFieldErrors() {
        assertThat(mvc.post().uri("/api/v1/incidents").with(as("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"","severity":"SEV2"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("title", "workloadId");
        verifyNoInteractions(incidentService);
    }

    @Test
    void unknownEnumValueIsReportedAgainstTheField() {
        MvcTestResult result = mvc.post().uri("/api/v1/incidents").with(as("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"workloadId":"%s","title":"x","severity":"CATASTROPHIC"}""".formatted(WORKLOAD_ID))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isEqualTo("Validation failed");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("severity");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message")
                .isEqualTo("must be one of SEV1, SEV2, SEV3, SEV4");
        verifyNoInteractions(incidentService);
    }

    @Test
    void malformedJsonIsReportedWithoutParserDetails() {
        assertThat(mvc.post().uri("/api/v1/incidents").with(as("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("The request body is missing or is not valid JSON.");
        verifyNoInteractions(incidentService);
    }

    @Test
    void pageSizeIsBounded() {
        assertThat(mvc.get().uri("/api/v1/incidents?size=500").with(as("VIEWER")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
    }

    @Test
    void domainErrorsMapToProblemResponses() {
        UUID missing = UUID.randomUUID();
        UUID resolved = UUID.randomUUID();
        when(incidentService.get(missing)).thenThrow(new ResourceNotFoundException("Incident", missing));
        when(incidentService.postUpdate(eq(resolved), any(), any()))
                .thenThrow(new ConflictException("Incident is resolved"));

        assertThat(mvc.get().uri("/api/v1/incidents/{id}", missing).with(as("VIEWER")))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").asString().contains(missing.toString());
        assertThat(mvc.post().uri("/api/v1/incidents/{id}/updates", resolved).with(as("OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"message":"still broken"}"""))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void responsesCarrySecurityAndCorrelationHeaders() {
        when(incidentService.get(any())).thenReturn(detail(UUID.randomUUID()));

        assertThat(mvc.get().uri("/api/v1/incidents/{id}", UUID.randomUUID()).with(as("VIEWER"))
                .header("X-Request-Id", "trace-12345678"))
                .hasStatusOk()
                .hasHeader("X-Request-Id", "trace-12345678")
                .hasHeader("X-Content-Type-Options", "nosniff")
                .hasHeader("X-Frame-Options", "DENY")
                .containsHeader("Content-Security-Policy");
    }

    private static IncidentDetailView detail(UUID id) {
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        return new IncidentDetailView(id, new WorkloadRef(WORKLOAD_ID, "payments-api", "Payments API"),
                "Elevated 5xx rate", null, Severity.SEV2, IncidentStatus.OPEN, "Test OPERATOR", now,
                null, null, 0, List.of());
    }
}
