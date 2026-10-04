package io.cloudops.platform.deployments.application;

import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import io.cloudops.platform.deployments.domain.DeploymentOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * @param deployedAt when the release went out; defaults to the time of recording when omitted.
 */
public record RecordDeploymentCommand(
        @NotNull DeploymentEnvironment environment,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._+-]+$",
                message = "may contain letters, digits, '.', '_', '+' and '-'") String version,
        @Pattern(regexp = "^[0-9a-f]{7,40}$", message = "must be a 7-40 character lowercase hex git SHA")
        String commitSha,
        @NotNull DeploymentOutcome outcome,
        @Size(max = 2000) String notes,
        @PastOrPresent Instant deployedAt) {
}
