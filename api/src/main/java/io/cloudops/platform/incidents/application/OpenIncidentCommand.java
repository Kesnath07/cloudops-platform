package io.cloudops.platform.incidents.application;

import io.cloudops.platform.incidents.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OpenIncidentCommand(
        @NotNull UUID workloadId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String summary,
        @NotNull Severity severity) {
}
