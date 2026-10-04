package io.cloudops.platform.support;

import io.cloudops.platform.shared.security.Actor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Builds authenticated requests for web slice tests without going through the token endpoint.
 */
public final class TestTokens {

    private TestTokens() {
    }

    public static JwtRequestPostProcessor as(String role) {
        UUID id = UUID.randomUUID();
        return jwt()
                .jwt(token -> token.subject(id.toString())
                        .claim(Actor.CLAIM_EMAIL, role.toLowerCase() + "@cloudops.test")
                        .claim(Actor.CLAIM_NAME, "Test " + role)
                        .claim(Actor.CLAIM_ROLE, role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
