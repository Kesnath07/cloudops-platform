package io.cloudops.platform.incidents.application;

import io.cloudops.platform.incidents.domain.WorkloadHealth;

public record WorkloadHealthView(WorkloadHealth health, int activeIncidents) {

    public static final WorkloadHealthView HEALTHY = new WorkloadHealthView(WorkloadHealth.OPERATIONAL, 0);
}
