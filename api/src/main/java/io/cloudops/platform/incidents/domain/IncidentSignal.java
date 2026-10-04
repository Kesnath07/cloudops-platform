package io.cloudops.platform.incidents.domain;

import java.util.UUID;

/**
 * The minimal facts about an active incident needed to assess workload health.
 */
public record IncidentSignal(UUID workloadId, Severity severity, IncidentStatus status) {
}
