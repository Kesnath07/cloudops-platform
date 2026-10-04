package io.cloudops.platform.catalog.domain;

/**
 * The mutable attributes of a workload, grouped so create and update share one validation path.
 */
public record WorkloadDetails(String name, String description, Criticality criticality,
                              String repositoryUrl, String runbookUrl) {
}
