package io.cloudops.platform.incidents.application;

import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.incidents.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param status   optional status transition
 * @param severity optional re-classification of the incident
 */
public record PostIncidentUpdateCommand(
        @NotBlank @Size(max = 4000) String message,
        IncidentStatus status,
        Severity severity) {
}
