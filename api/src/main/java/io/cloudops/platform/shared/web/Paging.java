package io.cloudops.platform.shared.web;

/**
 * Bounds shared by every paginated endpoint. The page index is capped so that page × size can
 * never exceed the integer row offset the persistence layer accepts; an unbounded index would
 * otherwise turn a bad query parameter into a server error.
 */
public final class Paging {

    public static final int MAX_PAGE = 10_000;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }
}
