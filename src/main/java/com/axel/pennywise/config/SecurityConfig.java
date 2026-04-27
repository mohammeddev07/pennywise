package com.axel.pennywise.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${environment:local}")
    private String environment;

    /**
     * From application.properties:
     * app.security.auth-enabled=${APP_SECURITY_AUTH_ENABLED:}
     * This may be blank when env var not set.
     */
    @Value("${app.security.auth-enabled:}")
    private String authEnabledRaw;

    @Value("${app.security.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${app.security.jwt.audience:}")
    private String audience;

    private boolean isAuthEnabled() {
        if (authEnabledRaw != null && !authEnabledRaw.isBlank()) {
            return Boolean.parseBoolean(authEnabledRaw);
        }
        // default: local -> false; otherwise true
        return !"local".equalsIgnoreCase(environment);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean authEnabled = isAuthEnabled();

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                ).permitAll()
        );

        if (authEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (!isAuthEnabled()) {
            // not used when auth is disabled
            return token -> { throw new IllegalStateException("JWT decoder not configured (auth disabled)"); };
        }
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("app.security.jwt.issuer-uri must be set when auth is enabled");
        }

        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new JwtAudienceValidator(audience);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);

        decoder.setJwtValidator(validator);
        return decoder;
    }
}
