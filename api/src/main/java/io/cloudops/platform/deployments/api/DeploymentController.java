package io.cloudops.platform.deployments.api;

import io.cloudops.platform.deployments.application.DeploymentService;
import io.cloudops.platform.deployments.application.DeploymentView;
import io.cloudops.platform.deployments.application.RecordDeploymentCommand;
import io.cloudops.platform.deployments.domain.DeploymentEnvironment;
import io.cloudops.platform.shared.security.Actor;
import io.cloudops.platform.shared.web.PageResponse;
import io.cloudops.platform.shared.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workloads/{workloadId}/deployments")
@Tag(name = "Deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @GetMapping
    @Operation(summary = "Deployment history of a workload, newest first")
    public PageResponse<DeploymentView> history(@PathVariable UUID workloadId,
                                                @RequestParam(required = false) DeploymentEnvironment environment,
                                                @RequestParam(defaultValue = "0") @Min(0) @Max(Paging.MAX_PAGE) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return deploymentService.history(workloadId, environment, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a deployment of the workload")
    public DeploymentView record(@PathVariable UUID workloadId, @Valid @RequestBody RecordDeploymentCommand command,
                                 Actor actor) {
        return deploymentService.record(workloadId, command, actor);
    }
}
