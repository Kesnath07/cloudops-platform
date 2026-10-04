package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.Role;
import io.cloudops.platform.identity.domain.UserAccount;

import java.time.Instant;
import java.util.UUID;

public record UserView(UUID id, String email, String displayName, Role role, Instant createdAt) {

    static UserView of(UserAccount account) {
        return new UserView(account.getId(), account.getEmail(), account.getDisplayName(),
                account.getRole(), account.getCreatedAt());
    }
}
