package io.cloudops.platform.identity.api;

import io.cloudops.platform.identity.application.AccessTokenView;
import io.cloudops.platform.identity.application.AccountService;
import io.cloudops.platform.identity.application.AuthenticationService;
import io.cloudops.platform.identity.application.RegisterAccountCommand;
import io.cloudops.platform.identity.application.TokenRequest;
import io.cloudops.platform.identity.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
@SecurityRequirements
public class AuthController {

    private final AccountService accountService;
    private final AuthenticationService authenticationService;

    public AuthController(AccountService accountService, AuthenticationService authenticationService) {
        this.accountService = accountService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account with the VIEWER role")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterAccountCommand command) {
        UserView user = accountService.register(command);
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(user);
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange email and password for a bearer access token")
    public AccessTokenView token(@Valid @RequestBody TokenRequest request) {
        return authenticationService.authenticate(request);
    }
}
