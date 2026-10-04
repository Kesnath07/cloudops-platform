package io.cloudops.platform.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated caller, reconstructed from verified token claims so that request handling
 * does not need a database round trip to know who is acting.
 */
public record Actor(UUID id, String email, String displayName, String role) {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_ROLE = "role";

    public static Actor from(Jwt jwt) {
        return new Actor(
                UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "token has no subject")),
                jwt.getClaimAsString(CLAIM_EMAIL),
                jwt.getClaimAsString(CLAIM_NAME),
                jwt.getClaimAsString(CLAIM_ROLE));
    }
}
