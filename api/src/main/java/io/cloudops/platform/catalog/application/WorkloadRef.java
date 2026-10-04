package io.cloudops.platform.catalog.application;

import io.cloudops.platform.catalog.domain.Workload;

import java.util.UUID;

/**
 * Minimal workload identity shared with other modules, which refer to workloads by id only.
 */
public record WorkloadRef(UUID id, String slug, String name) {

    static WorkloadRef of(Workload workload) {
        return new WorkloadRef(workload.getId(), workload.getSlug(), workload.getName());
    }
}
