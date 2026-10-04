package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.Role;
import io.cloudops.platform.identity.domain.UserAccount;
import io.cloudops.platform.identity.persistence.UserAccountRepository;
import io.cloudops.platform.shared.error.BusinessRuleViolationException;
import io.cloudops.platform.shared.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserAccountRepository accounts;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, passwordEncoder, new IdentityProperties("Admin@Example.org"));
    }

    @Test
    void registersNewAccountsAsViewersWithNormalizedEmailAndHashedPassword() {
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserView user = service.register(new RegisterAccountCommand("  Sam@Example.ORG ", "Sam", "correct-horse-battery"));

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(user.email()).isEqualTo("sam@example.org");
        assertThat(user.role()).isEqualTo(Role.VIEWER);
        assertThat(saved.getValue().getPasswordHash())
                .startsWith("{bcrypt}")
                .doesNotContain("correct-horse-battery");
    }

    @Test
    void bootstrapAdminEmailReceivesAdminRole() {
        when(accounts.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserView user = service.register(new RegisterAccountCommand("admin@example.org", "Ops Lead", "correct-horse-battery"));

        assertThat(user.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejectsDuplicateEmail() {
        when(accounts.existsByEmail("sam@example.org")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterAccountCommand("SAM@example.org", "Sam", "correct-horse-battery")))
                .isInstanceOf(ConflictException.class);
        verify(accounts, never()).save(any());
    }

    @Test
    void changePasswordRequiresTheCurrentPassword() {
        UUID id = UUID.randomUUID();
        UserAccount account = new UserAccount("sam@example.org", "Sam", passwordEncoder.encode("original-password"), Role.VIEWER);
        when(accounts.findById(id)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changePassword(id, new ChangePasswordCommand("wrong-password", "brand-new-password")))
                .isInstanceOf(BusinessRuleViolationException.class);

        service.changePassword(id, new ChangePasswordCommand("original-password", "brand-new-password"));
        assertThat(passwordEncoder.matches("brand-new-password", account.getPasswordHash())).isTrue();
    }

    @Test
    void newPasswordMustDiffer() {
        UUID id = UUID.randomUUID();
        UserAccount account = new UserAccount("sam@example.org", "Sam", passwordEncoder.encode("original-password"), Role.VIEWER);
        when(accounts.findById(id)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.changePassword(id, new ChangePasswordCommand("original-password", "original-password")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("differ");
    }

    @Test
    void commandToStringNeverRevealsPasswords() {
        assertThat(new RegisterAccountCommand("a@b.io", "A", "super-secret-pass").toString()).doesNotContain("super-secret-pass");
        assertThat(new TokenRequest("a@b.io", "super-secret-pass").toString()).doesNotContain("super-secret-pass");
        assertThat(new ChangePasswordCommand("old-secret-pass", "new-secret-pass").toString()).doesNotContain("secret");
    }
}
