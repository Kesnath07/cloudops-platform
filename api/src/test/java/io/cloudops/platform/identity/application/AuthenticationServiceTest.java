package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.Role;
import io.cloudops.platform.identity.domain.UserAccount;
import io.cloudops.platform.identity.persistence.UserAccountRepository;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.security.JwtProperties;
import io.cloudops.platform.shared.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("unit-test-signing-key-with-at-least-32-bytes", "cloudops-platform", Duration.ofMinutes(30));

    @Mock
    private UserAccountRepository accounts;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final SecurityConfig securityConfig = new SecurityConfig();
    private final JwtDecoder decoder = securityConfig.jwtDecoder(PROPERTIES);
    private AuthenticationService service;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(accounts, passwordEncoder, securityConfig.jwtEncoder(PROPERTIES),
                PROPERTIES, Clock.systemUTC());
        account = new UserAccount("ops@example.org", "Ops Person", passwordEncoder.encode("correct-horse-battery"), Role.OPERATOR);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
    }

    @Test
    void issuesTokenThatTheResourceServerAccepts() {
        when(accounts.findByEmail("ops@example.org")).thenReturn(Optional.of(account));

        AccessTokenView token = service.authenticate(new TokenRequest("OPS@example.org", "correct-horse-battery"));

        Jwt jwt = decoder.decode(token.accessToken());
        Actor actor = Actor.from(jwt);
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(1800);
        assertThat(actor.id()).isEqualTo(account.getId());
        assertThat(actor.role()).isEqualTo("OPERATOR");
        assertThat(actor.displayName()).isEqualTo("Ops Person");
        assertThat(jwt.getAudience()).containsExactly(SecurityConfig.TOKEN_AUDIENCE);
    }

    @Test
    void wrongPasswordAndUnknownAccountFailIdentically() {
        when(accounts.findByEmail("ops@example.org")).thenReturn(Optional.of(account));
        when(accounts.findByEmail("ghost@example.org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(new TokenRequest("ops@example.org", "not-the-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.authenticate(new TokenRequest("ghost@example.org", "whatever-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void decoderRejectsTokensFromAnotherIssuerOrKey() {
        when(accounts.findByEmail("ops@example.org")).thenReturn(Optional.of(account));
        JwtProperties otherIssuer = new JwtProperties(PROPERTIES.secret(), "someone-else", PROPERTIES.accessTokenTtl());
        JwtProperties otherKey = new JwtProperties("a-completely-different-signing-key-value", PROPERTIES.issuer(),
                PROPERTIES.accessTokenTtl());

        String forged = new AuthenticationService(accounts, passwordEncoder, securityConfig.jwtEncoder(otherIssuer),
                otherIssuer, Clock.systemUTC()).authenticate(new TokenRequest("ops@example.org", "correct-horse-battery")).accessToken();
        String wrongKey = new AuthenticationService(accounts, passwordEncoder, securityConfig.jwtEncoder(otherKey),
                otherKey, Clock.systemUTC()).authenticate(new TokenRequest("ops@example.org", "correct-horse-battery")).accessToken();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(wrongKey)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsExpiredTokens() {
        when(accounts.findByEmail("ops@example.org")).thenReturn(Optional.of(account));
        Clock longAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC);
        String expired = new AuthenticationService(accounts, passwordEncoder, securityConfig.jwtEncoder(PROPERTIES),
                PROPERTIES, longAgo).authenticate(new TokenRequest("ops@example.org", "correct-horse-battery")).accessToken();

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }
}
