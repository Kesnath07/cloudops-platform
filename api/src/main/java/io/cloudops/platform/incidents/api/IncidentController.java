package io.cloudops.platform.incidents.api;

import io.cloudops.platform.incidents.application.IncidentDetailView;
import io.cloudops.platform.incidents.application.IncidentService;
import io.cloudops.platform.incidents.application.IncidentView;
import io.cloudops.platform.incidents.application.OpenIncidentCommand;
import io.cloudops.platform.incidents.application.PostIncidentUpdateCommand;
import io.cloudops.platform.incidents.domain.IncidentStatus;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    @Operation(summary = "List incidents, newest first, optionally filtered by status and workload")
    public PageResponse<IncidentView> list(@RequestParam(required = false) IncidentStatus status,
                                           @RequestParam(required = false) UUID workloadId,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return incidentService.list(status, workloadId, page, size);
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Get an incident with its full timeline")
    public IncidentDetailView get(@PathVariable UUID incidentId) {
        return incidentService.get(incidentId);
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Open an incident against a workload")
    public ResponseEntity<IncidentDetailView> open(@Valid @RequestBody OpenIncidentCommand command, Actor actor) {
        IncidentDetailView incident = incidentService.open(command, actor);
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + incident.id())).body(incident);
    }

    @PostMapping("/{incidentId}/updates")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Post a timeline update, optionally changing status or severity")
    public IncidentDetailView postUpdate(@PathVariable UUID incidentId,
                                         @Valid @RequestBody PostIncidentUpdateCommand command, Actor actor) {
        return incidentService.postUpdate(incidentId, command, actor);
    }
}
