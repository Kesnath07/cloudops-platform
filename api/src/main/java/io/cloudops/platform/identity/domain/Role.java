package io.cloudops.platform.identity.domain;

/**
 * Platform roles, from least to most privileged. Higher roles inherit every permission of the
 * roles below them (see the role hierarchy in the security configuration).
 */
public enum Role {
    /** Read-only access to the catalog, deployments and incidents. */
    VIEWER,
    /** Engineers who register workloads, record deployments and run incidents. */
    OPERATOR,
    /** Manages teams, user roles and destructive operations. */
    ADMIN
}
