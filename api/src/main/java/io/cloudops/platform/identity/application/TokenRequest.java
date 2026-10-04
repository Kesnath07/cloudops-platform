package io.cloudops.platform.identity.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "TokenRequest[email=" + email + "]";
    }
}
