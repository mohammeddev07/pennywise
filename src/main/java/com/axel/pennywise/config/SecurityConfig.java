package com.axel.pennywise.config;

import com.axel.pennywise.exception.ErrorResponse;
import com.axel.pennywise.util.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.io.IOException;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import jakarta.servlet.http.HttpServletResponse;

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

    @Value("${app.security.jwt.issuer:pennywise}")
    private String localIssuer;

    @Value("${app.security.jwt.local-secret:}")
    private String localSecret;

    private boolean isAuthEnabled() {
        if (authEnabledRaw != null && !authEnabledRaw.isBlank()) {
            return Boolean.parseBoolean(authEnabledRaw);
        }
        // default: local -> false; otherwise true
        return !"local".equalsIgnoreCase(environment);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        boolean authEnabled = isAuthEnabled();
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, authException) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            writeSecurityError(
                    response,
                    objectMapper,
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Missing, invalid, or expired bearer token"
            );
        };
        AccessDeniedHandler accessDeniedHandler = (request, response, accessDeniedException) -> writeSecurityError(
                response,
                objectMapper,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "Authenticated user is not allowed to access this resource"
        );

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
        );

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/actuator/health/**",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v1/auth/**"
                ).permitAll()
        );

        if (authEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            );
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

        NimbusJwtDecoder decoder;
        OAuth2TokenValidator<Jwt> withIssuer;

        if (issuerUri != null && !issuerUri.isBlank()) {
            decoder = JwtDecoders.fromIssuerLocation(issuerUri);
            withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        } else {
            decoder = NimbusJwtDecoder
                    .withSecretKey(localSecretKey())
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
            withIssuer = JwtValidators.createDefaultWithIssuer(localIssuer);
        }

        OAuth2TokenValidator<Jwt> withAudience = new JwtAudienceValidator(audience);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);

        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(localSecretKey()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private SecretKey localSecretKey() {
        if (localSecret == null || localSecret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.local-secret must be set for backend-owned auth");
        }
        return new SecretKeySpec(localSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
    }

    private void writeSecurityError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(new ErrorResponse.Error(
                code,
                message,
                List.of(),
                MDC.get(RequestIdFilter.MDC_KEY)
        ));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
