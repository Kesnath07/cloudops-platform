package io.cloudops.platform.identity.api;

import io.cloudops.platform.identity.application.AccountService;
import io.cloudops.platform.identity.application.ChangePasswordCommand;
import io.cloudops.platform.identity.application.ChangeRoleCommand;
import io.cloudops.platform.identity.application.UserAdministrationService;
import io.cloudops.platform.identity.application.UserView;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final AccountService accountService;
    private final UserAdministrationService administrationService;

    public UserController(AccountService accountService, UserAdministrationService administrationService) {
        this.accountService = accountService;
        this.administrationService = administrationService;
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the authenticated user")
    public UserView me(Actor actor) {
        return accountService.get(actor.id());
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change the authenticated user's password")
    public void changePassword(Actor actor, @Valid @RequestBody ChangePasswordCommand command) {
        accountService.changePassword(actor.id(), command);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all user accounts")
    public PageResponse<UserView> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return administrationService.list(page, size);
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a role to another user")
    public UserView changeRole(Actor actor, @PathVariable UUID userId,
                               @Valid @RequestBody ChangeRoleCommand command) {
        return administrationService.changeRole(actor.id(), userId, command);
    }
}
