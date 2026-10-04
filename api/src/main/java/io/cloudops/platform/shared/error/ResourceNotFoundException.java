package io.cloudops.platform.shared.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(resource + " '" + identifier + "' was not found");
    }
}
