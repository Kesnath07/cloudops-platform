package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Criticality;
import io.cloudops.platform.catalog.domain.Slugs;
import io.cloudops.platform.catalog.domain.WorkloadDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWorkloadCommand(
        @NotBlank @Pattern(regexp = Slugs.PATTERN, message = Slugs.MESSAGE) String slug,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull UUID teamId,
        @NotNull Criticality criticality,
        @Size(max = 500) @Pattern(regexp = WorkloadLinks.HTTPS_URL, message = WorkloadLinks.MESSAGE) String repositoryUrl,
        @Size(max = 500) @Pattern(regexp = WorkloadLinks.HTTPS_URL, message = WorkloadLinks.MESSAGE) String runbookUrl) {

    WorkloadDetails details() {
        return new WorkloadDetails(name, description, criticality, repositoryUrl, runbookUrl);
    }
}
