package io.cloudops.platform.catalog.api;

import io.cloudops.platform.catalog.application.TeamCommand;
import io.cloudops.platform.catalog.application.TeamService;
import io.cloudops.platform.catalog.application.TeamView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @Operation(summary = "List all teams")
    public List<TeamView> list() {
        return teamService.list();
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "Get a team")
    public TeamView get(@PathVariable UUID teamId) {
        return teamService.get(teamId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a team")
    public ResponseEntity<TeamView> create(@Valid @RequestBody TeamCommand command) {
        TeamView team = teamService.create(command);
        return ResponseEntity.created(URI.create("/api/v1/teams/" + team.id())).body(team);
    }

    @PutMapping("/{teamId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a team's name, description and contact (slug is immutable)")
    public TeamView update(@PathVariable UUID teamId, @Valid @RequestBody TeamCommand command) {
        return teamService.update(teamId, command);
    }
}
