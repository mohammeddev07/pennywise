package com.axel.pennywise.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        "/swagger-ui.html",
                        "/v1/auth/**"
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
}
