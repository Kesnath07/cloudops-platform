package io.cloudops.platform.identity.application;

public record AccessTokenView(String accessToken, String tokenType, long expiresIn, UserView user) {
}
