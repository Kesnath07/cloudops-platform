package io.cloudops.platform.incidents.domain;

import java.util.Collection;

/**
 * Health of a workload as implied by its active incidents.
 */
public enum WorkloadHealth {
    OPERATIONAL,
    DEGRADED,
    PARTIAL_OUTAGE,
    MAJOR_OUTAGE;

    /**
     * Open incidents drive health by severity. Once an incident is mitigated, users are no longer
     * fully impacted, so it caps at DEGRADED until resolved.
     */
    public static WorkloadHealth assess(Collection<IncidentSignal> activeIncidents) {
        WorkloadHealth worst = OPERATIONAL;
        for (IncidentSignal incident : activeIncidents) {
            WorkloadHealth impact = impactOf(incident);
            if (impact.ordinal() > worst.ordinal()) {
                worst = impact;
            }
        }
        return worst;
    }

    private static WorkloadHealth impactOf(IncidentSignal incident) {
        return switch (incident.status()) {
            case RESOLVED -> OPERATIONAL;
            case MITIGATED -> DEGRADED;
            case OPEN -> switch (incident.severity()) {
                case SEV1 -> MAJOR_OUTAGE;
                case SEV2 -> PARTIAL_OUTAGE;
                case SEV3, SEV4 -> DEGRADED;
            };
        };
    }
}
