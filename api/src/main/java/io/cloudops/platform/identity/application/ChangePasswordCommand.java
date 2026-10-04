package io.cloudops.platform.identity.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordCommand(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordCommand[***]";
    }
}
