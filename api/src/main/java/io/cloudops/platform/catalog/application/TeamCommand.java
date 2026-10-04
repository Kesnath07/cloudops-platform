package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Slugs;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating a team. The slug is only honoured on creation.
 */
public record TeamCommand(
        @NotBlank @Pattern(regexp = Slugs.PATTERN, message = Slugs.MESSAGE) String slug,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @Email @Size(max = 254) String contactEmail) {
}
