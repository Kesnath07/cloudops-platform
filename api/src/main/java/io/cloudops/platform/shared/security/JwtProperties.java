package io.cloudops.platform.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token signing settings. The secret has no default: the application refuses to start unless a
 * key of at least 256 bits is supplied (HS256 requirement), which in AWS comes from Secrets Manager.
 */
@Validated
@ConfigurationProperties("cloudops.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters (256 bits)") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {
}
