package io.cloudops.platform.identity.application;

import io.cloudops.platform.identity.domain.UserAccount;
import io.cloudops.platform.identity.persistence.UserAccountRepository;
import io.cloudops.platform.shared.error.BusinessRuleViolationException;
import io.cloudops.platform.shared.error.ResourceNotFoundException;
import io.cloudops.platform.shared.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserAdministrationService {

    private static final Logger log = LoggerFactory.getLogger(UserAdministrationService.class);

    private final UserAccountRepository accounts;

    public UserAdministrationService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> list(int page, int size) {
        return PageResponse.from(
                accounts.findAll(PageRequest.of(page, size, Sort.by("email"))), UserView::of);
    }

    /**
     * Administrators cannot change their own role; this guarantees an administrator can never
     * accidentally remove the last administrative access to the platform.
     */
    @Transactional
    public UserView changeRole(UUID actingUserId, UUID targetUserId, ChangeRoleCommand command) {
        if (actingUserId.equals(targetUserId)) {
            throw new BusinessRuleViolationException("Administrators cannot change their own role");
        }
        UserAccount account = accounts.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));
        account.assignRole(command.role());
        log.info("Account {} assigned role {} by {}", targetUserId, command.role(), actingUserId);
        return UserView.of(account);
    }
}
