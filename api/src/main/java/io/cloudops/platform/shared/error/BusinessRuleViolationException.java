package io.cloudops.platform.shared.error;

/**
 * The request is well-formed but breaks a business rule that does not depend on concurrent state.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
