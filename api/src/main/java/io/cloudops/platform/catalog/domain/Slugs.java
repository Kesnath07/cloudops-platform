package io.cloudops.platform.catalog.domain;

/**
 * Slugs are DNS-label compatible so they can be reused for resource names and URLs.
 */
public final class Slugs {

    public static final String PATTERN = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$";
    public static final String MESSAGE = "must be lowercase letters, digits and hyphens (max 63 characters)";

    private Slugs() {
    }
}
