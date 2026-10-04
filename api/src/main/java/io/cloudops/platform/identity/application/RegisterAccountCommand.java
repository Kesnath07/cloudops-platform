package io.cloudops.platform.identity.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAccountCommand(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 12, max = 72) String password) {

    @Override
    public String toString() {
        return "RegisterAccountCommand[email=" + email + ", displayName=" + displayName + "]";
    }
}
