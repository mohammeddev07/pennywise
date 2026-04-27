package com.axel.pennywise.domain.auth;

import com.axel.pennywise.api.dto.auth.AuthResponse;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepo;

    private AuthService service;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("pennywise-auth-service-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256")
        ));
        service = new AuthService(userRepo, passwordEncoder, jwtEncoder);
        ReflectionTestUtils.setField(service, "issuer", "pennywise-test");
        ReflectionTestUtils.setField(service, "audience", "");
        ReflectionTestUtils.setField(service, "tokenTtlMinutes", 60L);
    }

    @Test
    void signup_normalizesEmailHashesPasswordAndReturnsToken() {
        when(userRepo.existsByAuthSubjectAndDeletedAtIsNull("local:mak@example.com")).thenReturn(false);
        when(userRepo.save(any(UserEntity.class))).thenAnswer(inv -> savedUser(inv.getArgument(0)));

        AuthResponse response = service.signup(" Mak@Example.COM ", "password123", "usd");

        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("mak@example.com", response.user().email());
        assertEquals("USD", response.user().defaultCurrencyCode());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepo).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals("local:mak@example.com", saved.getAuthSubject());
        assertTrue(passwordEncoder.matches("password123", saved.getPasswordHash()));
        assertNotEquals("password123", saved.getPasswordHash());
    }

    @Test
    void login_returnsTokenWhenPasswordMatches() {
        UserEntity user = new UserEntity();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setAuthSubject("local:mak@example.com");
        user.setEmail("mak@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setCreatedAt(OffsetDateTime.now());

        when(userRepo.findByAuthSubjectAndDeletedAtIsNull("local:mak@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = service.login("MAK@example.com", "password123");

        assertNotNull(response.accessToken());
        assertEquals("mak@example.com", response.user().email());
        verify(userRepo).findByAuthSubjectAndDeletedAtIsNull("local:mak@example.com");
    }

    private UserEntity savedUser(UserEntity user) {
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }
}
