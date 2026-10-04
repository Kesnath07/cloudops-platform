package io.cloudops.platform.shared.error;

/**
 * The request is valid but clashes with the current state of a resource (duplicate key,
 * illegal state transition).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
