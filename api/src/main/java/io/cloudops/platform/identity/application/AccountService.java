package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.Role;
import io.cloudops.platform.identity.domain.UserAccount;
import io.cloudops.platform.identity.persistence.UserAccountRepository;
import io.cloudops.platform.shared.error.BusinessRuleViolationException;
import io.cloudops.platform.shared.error.ConflictException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Self-service account operations: registration, profile lookup and password changes.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    public AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
                          IdentityProperties properties) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public UserView register(RegisterAccountCommand command) {
        String email = UserAccount.normalizeEmail(command.email());
        if (accounts.existsByEmail(email)) {
            throw new ConflictException("An account with this email address already exists");
        }
        Role role = properties.isBootstrapAdmin(email) ? Role.ADMIN : Role.VIEWER;
        UserAccount account = accounts.save(new UserAccount(
                email, command.displayName(), hash(command.password()), role));
        log.info("Registered account {} with role {}", account.getId(), role);
        return UserView.of(account);
    }

    @Transactional(readOnly = true)
    public UserView get(UUID userId) {
        return UserView.of(load(userId));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordCommand command) {
        UserAccount account = load(userId);
        if (!passwordEncoder.matches(command.currentPassword(), account.getPasswordHash())) {
            throw new BusinessRuleViolationException("The current password is incorrect");
        }
        if (command.currentPassword().equals(command.newPassword())) {
            throw new BusinessRuleViolationException("The new password must differ from the current one");
        }
        account.changePasswordHash(hash(command.newPassword()));
        log.info("Password changed for account {}", userId);
    }

    private String hash(String rawPassword) {
        return Objects.requireNonNull(passwordEncoder.encode(rawPassword), "password encoder returned no hash");
    }

    private UserAccount load(UUID userId) {
        return accounts.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
