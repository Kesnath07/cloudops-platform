package io.cloudops.platform.catalog.domain;

/**
 * Business impact of a workload being unavailable; drives how urgently incidents are handled.
 */
public enum Criticality {
    HIGH,
    MEDIUM,
    LOW
}
