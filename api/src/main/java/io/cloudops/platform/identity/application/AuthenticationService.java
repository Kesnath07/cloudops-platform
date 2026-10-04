package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.UserAccount;
import io.cloudops.platform.identity.persistence.UserAccountRepository;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.security.JwtProperties;
import io.cloudops.platform.shared.security.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.function.SingletonSupplier;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Exchanges credentials for a short-lived signed access token.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    /** Hash compared against when the account does not exist, so response time does not reveal it. */
    private final SingletonSupplier<String> timingEqualizerHash;

    public AuthenticationService(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
                                 JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.timingEqualizerHash = SingletonSupplier.of(() ->
                Objects.requireNonNull(passwordEncoder.encode(UUID.randomUUID().toString())));
    }

    @Transactional(readOnly = true)
    public AccessTokenView authenticate(TokenRequest request) {
        Optional<UserAccount> account = accounts.findByEmail(UserAccount.normalizeEmail(request.email()));
        String hash = account.map(UserAccount::getPasswordHash).orElseGet(timingEqualizerHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (account.isEmpty() || !passwordMatches) {
            log.info("Rejected token request for unknown account or wrong password");
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        return issueToken(account.get());
    }

    private AccessTokenView issueToken(UserAccount account) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .audience(List.of(SecurityConfig.TOKEN_AUDIENCE))
                .subject(account.getId().toString())
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .claim(Actor.CLAIM_EMAIL, account.getEmail())
                .claim(Actor.CLAIM_NAME, account.getDisplayName())
                .claim(Actor.CLAIM_ROLE, account.getRole().name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AccessTokenView(token, "Bearer", jwtProperties.accessTokenTtl().toSeconds(), UserView.of(account));
    }
}
