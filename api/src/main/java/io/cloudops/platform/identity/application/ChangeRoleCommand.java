package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleCommand(@NotNull Role role) {
}
