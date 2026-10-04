package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.Role;
import io.cloudops.platform.identity.domain.UserAccount;
import io.cloudops.platform.identity.persistence.UserAccountRepository;
import io.cloudops.platform.shared.error.BusinessRuleViolationException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    @Mock
    private UserAccountRepository accounts;

    @InjectMocks
    private UserAdministrationService service;

    @Test
    void administratorsCannotChangeTheirOwnRole() {
        UUID self = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeRole(self, self, new ChangeRoleCommand(Role.VIEWER)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void assignsRoleToAnotherUser() {
        UUID target = UUID.randomUUID();
        UserAccount account = new UserAccount("sam@example.org", "Sam", "{bcrypt}hash", Role.VIEWER);
        when(accounts.findById(target)).thenReturn(Optional.of(account));

        UserView updated = service.changeRole(UUID.randomUUID(), target, new ChangeRoleCommand(Role.OPERATOR));

        assertThat(updated.role()).isEqualTo(Role.OPERATOR);
        assertThat(account.getRole()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void unknownUserIsNotFound() {
        UUID target = UUID.randomUUID();
        when(accounts.findById(target)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(UUID.randomUUID(), target, new ChangeRoleCommand(Role.ADMIN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
