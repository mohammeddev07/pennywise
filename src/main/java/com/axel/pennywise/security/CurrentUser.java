package com.axel.pennywise.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<String> subject() {
        return jwtAuth().map(a -> a.getToken().getSubject());
    }

    public static Optional<String> email() {
        return jwtAuth().map(a -> a.getToken().getClaimAsString("email"));
    }

    private static Optional<JwtAuthenticationToken> jwtAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth);
        }
        return Optional.empty();
    }
}
