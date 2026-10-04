package io.cloudops.platform.catalog.api;

import io.cloudops.platform.catalog.application.CreateWorkloadCommand;
import io.cloudops.platform.catalog.application.UpdateWorkloadCommand;
import io.cloudops.platform.catalog.application.WorkloadService;
import io.cloudops.platform.catalog.application.WorkloadView;
import io.cloudops.platform.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workloads")
@Tag(name = "Workloads")
public class WorkloadController {

    private final WorkloadService workloadService;

    public WorkloadController(WorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    @GetMapping
    @Operation(summary = "List workloads, optionally filtered by owning team")
    public PageResponse<WorkloadView> list(@RequestParam(required = false) UUID teamId,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return workloadService.list(teamId, page, size);
    }

    @GetMapping("/{workloadId}")
    @Operation(summary = "Get a workload")
    public WorkloadView get(@PathVariable UUID workloadId) {
        return workloadService.get(workloadId);
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Register a workload in the catalog")
    public ResponseEntity<WorkloadView> create(@Valid @RequestBody CreateWorkloadCommand command) {
        WorkloadView workload = workloadService.create(command);
        return ResponseEntity.created(URI.create("/api/v1/workloads/" + workload.id())).body(workload);
    }

    @PutMapping("/{workloadId}")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Update a workload (slug is immutable)")
    public WorkloadView update(@PathVariable UUID workloadId, @Valid @RequestBody UpdateWorkloadCommand command) {
        return workloadService.update(workloadId, command);
    }

    @DeleteMapping("/{workloadId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a workload that has no incident history")
    public void delete(@PathVariable UUID workloadId) {
        workloadService.delete(workloadId);
    }
}
