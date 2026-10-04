package io.cloudops.platform;

import io.cloudops.platform.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AccountsAndOperationsIT extends PostgresIntegrationTest {

    @Test
    void registrationLoginAndProfile() {
        String token = registerAndLogin("New.User@CloudOps.test", "New User");

        assertThat(get("/api/v1/users/me", token)).hasStatusOk().bodyJson()
                .extractingPath("$.email").isEqualTo("new.user@cloudops.test");
        assertThat(get("/api/v1/users/me", token)).bodyJson().extractingPath("$.role").isEqualTo("VIEWER");
        assertThat(postJson("/api/v1/auth/register", null, """
                {"email":"new.user@cloudops.test","displayName":"Again","password":"%s"}""".formatted(PASSWORD)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void bootstrapAdministratorIsGrantedAdmin() {
        String token = registerAndLogin(ADMIN_EMAIL, "Admin");

        assertThat(get("/api/v1/users/me", token)).bodyJson().extractingPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    void passwordChangeInvalidatesTheOldPassword() {
        String token = registerAndLogin("rotate@cloudops.test", "Rotator");

        assertThat(putJson("/api/v1/users/me/password", token, """
                {"currentPassword":"%s","newPassword":"an-entirely-new-password"}""".formatted(PASSWORD)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postJson("/api/v1/auth/token", null, """
                {"email":"rotate@cloudops.test","password":"%s"}""".formatted(PASSWORD)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void weakPasswordsAndBadTokensAreRejected() {
        assertThat(postJson("/api/v1/auth/register", null, """
                {"email":"weak@cloudops.test","displayName":"Weak","password":"short"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/users/me", "not-a-jwt")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void operationalEndpointsArePublicButMinimal() {
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/platform/info")).hasStatusOk()
                .bodyJson().extractingPath("$.service").isEqualTo("cloudops-api");
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/health")).bodyJson()
                .doesNotHavePath("$.components")
                .doesNotHavePath("$.details");
    }
}
