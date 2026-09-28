package com.axel.pennywise.domain.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.axel.pennywise.api.dto.auth.AuthResponse;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserRepository;
import com.axel.pennywise.exception.ApiException;
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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepo;
  @Mock private GoogleTokenVerifier googleVerifier;

  private AuthService service;
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    JwtEncoder jwtEncoder =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(
                    "pennywise-auth-service-test-secret".getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256")));
    service = new AuthService(userRepo, passwordEncoder, jwtEncoder, googleVerifier);
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

    when(userRepo.findByAuthSubjectAndDeletedAtIsNull("local:mak@example.com"))
        .thenReturn(Optional.of(user));

    AuthResponse response = service.login("MAK@example.com", "password123");

    assertNotNull(response.accessToken());
    assertEquals("mak@example.com", response.user().email());
    verify(userRepo).findByAuthSubjectAndDeletedAtIsNull("local:mak@example.com");
  }

  @Test
  void signup_rejectsEmailAlreadyUsedByAGoogleAccount() {
    when(userRepo.existsByEmailAndDeletedAtIsNull("mak@example.com")).thenReturn(true);

    ApiException ex =
        assertThrows(
            ApiException.class, () -> service.signup("mak@example.com", "password123", null));

    assertEquals("EMAIL_ALREADY_REGISTERED", ex.code());
    verify(userRepo, never()).save(any());
  }

  @Test
  void googleLogin_createsPasswordlessUserForNewGoogleAccount() {
    when(googleVerifier.verify("tok"))
        .thenReturn(new GoogleTokenVerifier.GoogleIdentity("g-123", "Mak@Example.com"));
    when(userRepo.findByGoogleSubAndDeletedAtIsNull("g-123")).thenReturn(Optional.empty());
    when(userRepo.existsByEmailAndDeletedAtIsNull("mak@example.com")).thenReturn(false);
    when(userRepo.save(any(UserEntity.class))).thenAnswer(inv -> savedUser(inv.getArgument(0)));

    AuthResponse response = service.googleLogin("tok");

    assertEquals("mak@example.com", response.user().email());
    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepo).save(captor.capture());
    assertEquals("google:g-123", captor.getValue().getAuthSubject());
    assertEquals("g-123", captor.getValue().getGoogleSub());
    assertNull(captor.getValue().getPasswordHash());
  }

  @Test
  void googleLogin_returnsExistingLinkedUserWithoutCreatingOne() {
    UserEntity user = savedUser(new UserEntity());
    user.setAuthSubject("local:mak@example.com");
    user.setEmail("mak@example.com");
    user.setGoogleSub("g-123");
    when(googleVerifier.verify("tok"))
        .thenReturn(new GoogleTokenVerifier.GoogleIdentity("g-123", "mak@example.com"));
    when(userRepo.findByGoogleSubAndDeletedAtIsNull("g-123")).thenReturn(Optional.of(user));

    AuthResponse response = service.googleLogin("tok");

    assertEquals(user.getId(), response.user().id());
    verify(userRepo, never()).save(any());
  }

  @Test
  void googleLogin_neverMergesIntoAnExistingAccountWithTheSameEmail() {
    when(googleVerifier.verify("tok"))
        .thenReturn(new GoogleTokenVerifier.GoogleIdentity("g-123", "mak@example.com"));
    when(userRepo.findByGoogleSubAndDeletedAtIsNull("g-123")).thenReturn(Optional.empty());
    when(userRepo.existsByEmailAndDeletedAtIsNull("mak@example.com")).thenReturn(true);

    ApiException ex = assertThrows(ApiException.class, () -> service.googleLogin("tok"));

    assertEquals("GOOGLE_ACCOUNT_LINK_REQUIRED", ex.code());
    verify(userRepo, never()).save(any());
  }

  @Test
  void linkGoogle_attachesGoogleSubToTheCaller() {
    UserEntity user = savedUser(new UserEntity());
    when(googleVerifier.verify("tok"))
        .thenReturn(new GoogleTokenVerifier.GoogleIdentity("g-123", "other@example.com"));
    when(userRepo.lockActiveById(user.getId())).thenReturn(Optional.of(user));
    when(userRepo.findByGoogleSubAndDeletedAtIsNull("g-123")).thenReturn(Optional.empty());
    when(userRepo.save(user)).thenReturn(user);

    service.linkGoogle(user, "tok");

    assertEquals("g-123", user.getGoogleSub());
  }

  @Test
  void linkGoogle_isIdempotentForTheSameGoogleAccount() {
    UserEntity user = savedUser(new UserEntity());
    user.setGoogleSub("g-123");
    when(googleVerifier.verify("tok"))
        .thenReturn(new GoogleTokenVerifier.GoogleIdentity("g-123", "x@example.com"));
    when(userRepo.lockActiveById(user.getId())).thenReturn(Optional.of(user));
    when(userRepo.findByGoogleSubAndDeletedAtIsNull("g-123")).thenReturn(Optional.of(user));
    when(userRepo.save(user)).thenReturn(user);

    assertDoesNotThrow(() -> service.linkGoogle(user, "tok"));
  }

  @Test
  void linkGoogle_rejectsGoogleAccountOwnedBySomeoneElse() {
    UserEntity caller = savedUser(new UserEntity());
    UserEntity other = new UserEntity();
    other.setId(UUID.randomUUID());
    other.setGoogleSub("g-123");
    when(googleVerifier.verify("tok"))
        .thenReturn(new GoogleTokenVerifier.GoogleIdentity("g-123", "x@example.com"));
    when(userRepo.lockActiveById(caller.getId())).thenReturn(Optional.of(caller));
    when(userRepo.findByGoogleSubAndDeletedAtIsNull("g-123")).thenReturn(Optional.of(other));

    ApiException ex = assertThrows(ApiException.class, () -> service.linkGoogle(caller, "tok"));

    assertEquals("GOOGLE_ACCOUNT_ALREADY_LINKED", ex.code());
    assertNull(caller.getGoogleSub());
  }

  @Test
  void linkGoogle_rejectsSwitchingAnAlreadyLinkedAccountToADifferentGoogleAccount() {
    UserEntity user = savedUser(new UserEntity());
    user.setGoogleSub("g-old");
    when(googleVerifier.verify("tok"))
        .thenReturn(new GoogleTokenVerifier.GoogleIdentity("g-new", "x@example.com"));
    when(userRepo.lockActiveById(user.getId())).thenReturn(Optional.of(user));
    when(userRepo.findByGoogleSubAndDeletedAtIsNull("g-new")).thenReturn(Optional.empty());

    assertThrows(ApiException.class, () -> service.linkGoogle(user, "tok"));
    assertEquals("g-old", user.getGoogleSub());
  }

  private UserEntity savedUser(UserEntity user) {
    user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    return user;
  }
}
