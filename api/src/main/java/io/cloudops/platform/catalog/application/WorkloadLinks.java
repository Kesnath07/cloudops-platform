package io.cloudops.platform.catalog.application;

final class WorkloadLinks {

    /** Only HTTPS links are stored; the UI renders them as clickable anchors. */
    static final String HTTPS_URL = "^https://[^\\s]+$";
    static final String MESSAGE = "must be an https:// URL";

    private WorkloadLinks() {
    }
}
