package io.cloudops.platform;

import io.cloudops.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end behaviour of the API through HTTP, security and a real database.
 */
class IncidentWorkflowIT extends PostgresIntegrationTest {

    private String adminToken;
    private String operatorToken;
    private String viewerToken;
    private String teamId;
    private String workloadId;

    @BeforeEach
    void seedCatalog() {
        adminToken = registerAndLogin(ADMIN_EMAIL, "Platform Admin");
        registerAndLogin("operator@cloudops.test", "Olu Operator");
        viewerToken = registerAndLogin("viewer@cloudops.test", "Vic Viewer");

        List<String> operatorIds = read(get("/api/v1/users?size=100", adminToken),
                "$.items[?(@.email=='operator@cloudops.test')].id");
        String operatorId = operatorIds.getFirst();
        assertThat(putJson("/api/v1/users/" + operatorId + "/role", adminToken, """
                {"role":"OPERATOR"}""")).hasStatusOk();
        operatorToken = login("operator@cloudops.test");

        teamId = read(postJson("/api/v1/teams", adminToken, """
                {"slug":"payments","name":"Payments","contactEmail":"payments@cloudops.test"}"""), "$.id");
        workloadId = read(postJson("/api/v1/workloads", operatorToken, """
                {"slug":"payments-api","name":"Payments API","teamId":"%s","criticality":"HIGH",
                 "runbookUrl":"https://runbooks.internal/payments-api"}""".formatted(teamId)), "$.id");
    }

    @Test
    void incidentLifecycleDrivesWorkloadHealth() {
        MvcTestResult opened = postJson("/api/v1/incidents", operatorToken, """
                {"workloadId":"%s","title":"Card authorisations failing","severity":"SEV1"}""".formatted(workloadId));
        assertThat(opened).hasStatus(HttpStatus.CREATED);
        String incidentId = read(opened, "$.id");

        assertThat(get("/api/v1/overview", viewerToken)).hasStatusOk().bodyJson()
                .extractingPath("$[0].health").isEqualTo("MAJOR_OUTAGE");

        assertThat(postJson("/api/v1/incidents/" + incidentId + "/updates", operatorToken, """
                {"message":"Failed over to secondary processor","status":"MITIGATED"}""")).hasStatusOk();
        assertThat(get("/api/v1/overview", viewerToken)).bodyJson()
                .extractingPath("$[0].health").isEqualTo("DEGRADED");

        assertThat(postJson("/api/v1/incidents/" + incidentId + "/updates", operatorToken, """
                {"message":"Processor patched","status":"RESOLVED"}""")).hasStatusOk();

        MvcTestResult detail = get("/api/v1/incidents/" + incidentId, viewerToken);
        assertThat(detail).hasStatusOk();
        List<String> statuses = read(detail, "$.timeline[*].status");
        assertThat(statuses).containsExactly("OPEN", "MITIGATED", "RESOLVED");
        assertThat(read(detail, "$.timeline[1].authorName").toString()).isEqualTo("Olu Operator");
        assertThat(get("/api/v1/overview", viewerToken)).bodyJson()
                .extractingPath("$[0].health").isEqualTo("OPERATIONAL");
    }

    @Test
    void resolvedIncidentsAreClosedToUpdates() {
        String incidentId = read(postJson("/api/v1/incidents", operatorToken, """
                {"workloadId":"%s","title":"Cert expiry warning","severity":"SEV4"}""".formatted(workloadId)), "$.id");
        postJson("/api/v1/incidents/" + incidentId + "/updates", operatorToken, """
                {"message":"Renewed","status":"RESOLVED"}""");

        assertThat(postJson("/api/v1/incidents/" + incidentId + "/updates", operatorToken, """
                {"message":"Reopening","status":"OPEN"}""")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void incidentListsFilterByStatus() {
        postJson("/api/v1/incidents", operatorToken, """
                {"workloadId":"%s","title":"Open one","severity":"SEV3"}""".formatted(workloadId));
        String resolvedId = read(postJson("/api/v1/incidents", operatorToken, """
                {"workloadId":"%s","title":"Resolved one","severity":"SEV3"}""".formatted(workloadId)), "$.id");
        postJson("/api/v1/incidents/" + resolvedId + "/updates", operatorToken, """
                {"message":"Fixed","status":"RESOLVED"}""");

        assertThat(get("/api/v1/incidents?status=OPEN", viewerToken)).hasStatusOk().bodyJson()
                .extractingPath("$.items[*].title").asArray().containsExactly("Open one");
        assertThat(get("/api/v1/incidents?workloadId=" + workloadId, viewerToken)).bodyJson()
                .extractingPath("$.totalItems").isEqualTo(2);
    }

    @Test
    void deploymentsAppearInHistoryAndOverview() {
        assertThat(postJson("/api/v1/workloads/" + workloadId + "/deployments", operatorToken, """
                {"environment":"PRODUCTION","version":"1.4.0","commitSha":"a1b2c3d","outcome":"SUCCEEDED",
                 "deployedAt":"2026-01-10T09:00:00Z"}""")).hasStatus(HttpStatus.CREATED);
        postJson("/api/v1/workloads/" + workloadId + "/deployments", operatorToken, """
                {"environment":"PRODUCTION","version":"1.5.0","outcome":"ROLLED_BACK","deployedAt":"2026-01-12T09:00:00Z"}""");
        postJson("/api/v1/workloads/" + workloadId + "/deployments", operatorToken, """
                {"environment":"STAGING","version":"1.6.0-rc.1","outcome":"SUCCEEDED"}""");

        assertThat(get("/api/v1/workloads/" + workloadId + "/deployments?environment=PRODUCTION", viewerToken))
                .bodyJson().extractingPath("$.items[*].version").asArray().containsExactly("1.5.0", "1.4.0");
        assertThat(get("/api/v1/overview", viewerToken)).bodyJson()
                .extractingPath("$[0].lastProductionDeployment.version").isEqualTo("1.5.0");
        assertThat(postJson("/api/v1/workloads/" + workloadId + "/deployments", operatorToken, """
                {"environment":"PRODUCTION","version":"2.0.0","outcome":"SUCCEEDED","deployedAt":"2999-01-01T00:00:00Z"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rolesAreEnforcedAcrossTheApi() {
        assertThat(postJson("/api/v1/workloads", viewerToken, """
                {"slug":"other","name":"Other","teamId":"%s","criticality":"LOW"}""".formatted(teamId)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(postJson("/api/v1/teams", operatorToken, """
                {"slug":"ops","name":"Ops"}""")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/users", operatorToken)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete("/api/v1/workloads/" + workloadId, operatorToken)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void catalogEnforcesUniquenessAndIncidentRetention() {
        assertThat(postJson("/api/v1/workloads", operatorToken, """
                {"slug":"payments-api","name":"Duplicate","teamId":"%s","criticality":"LOW"}""".formatted(teamId)))
                .hasStatus(HttpStatus.CONFLICT);

        postJson("/api/v1/incidents", operatorToken, """
                {"workloadId":"%s","title":"History","severity":"SEV4"}""".formatted(workloadId));
        assertThat(delete("/api/v1/workloads/" + workloadId, adminToken)).hasStatus(HttpStatus.CONFLICT);

        String unused = read(postJson("/api/v1/workloads", operatorToken, """
                {"slug":"batch-jobs","name":"Batch Jobs","teamId":"%s","criticality":"LOW"}""".formatted(teamId)), "$.id");
        postJson("/api/v1/workloads/" + unused + "/deployments", operatorToken, """
                {"environment":"DEVELOPMENT","version":"0.1.0","outcome":"FAILED"}""");
        assertThat(delete("/api/v1/workloads/" + unused, adminToken)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get("/api/v1/workloads/" + unused, viewerToken)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void workloadUpdatesUseOptimisticFields() {
        assertThat(putJson("/api/v1/workloads/" + workloadId, operatorToken, """
                {"name":"Payments API v2","teamId":"%s","criticality":"MEDIUM","repositoryUrl":"http://insecure"}"""
                .formatted(teamId))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(putJson("/api/v1/workloads/" + workloadId, operatorToken, """
                {"name":"Payments API v2","teamId":"%s","criticality":"MEDIUM"}""".formatted(teamId)))
                .hasStatusOk().bodyJson().extractingPath("$.criticality").isEqualTo("MEDIUM");
        assertThat(get("/api/v1/workloads?teamId=" + teamId, viewerToken)).bodyJson()
                .extractingPath("$.items[0].name").isEqualTo("Payments API v2");
    }
}
