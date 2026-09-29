package com.axel.pennywise.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axel.pennywise.domain.support.SupportMailSender;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.ApiException;
import com.axel.pennywise.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SupportControllerTest {
  private static final String KEY = "retry-key-123";
  private final UserService users = mock(UserService.class);
  private final SupportMailSender sender = mock(SupportMailSender.class);
  private MockMvc mvc;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new SupportController(users, sender))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "none"),
            Map.of("sub", "subject-1", "email", "user@example.com"));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    user = new UserEntity();
    user.setId(UUID.randomUUID());
    user.setEmail("user@example.com");
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void sendsTrimmedMessageFromAccountEmail() throws Exception {
    when(users.getOrCreate(any(), eq("subject-1"), eq("user@example.com"))).thenReturn(user);

    mvc.perform(
            post("/v1/support/contact")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"  Help  \",\"message\":\"  Please help.  \"}"))
        .andExpect(status().isNoContent());

    verify(sender).send(user.getId(), "user@example.com", "Help", "Please help.", KEY);
  }

  @Test
  void rejectsBlankFieldsAndInvalidKeyBeforeSending() throws Exception {
    mvc.perform(
            post("/v1/support/contact")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"  \",\"message\":\"hi\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/v1/support/contact")
                .header("Idempotency-Key", "bad key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Help\",\"message\":\"hi\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(users, sender);
  }

  @Test
  void requiresAnAccountEmail() throws Exception {
    user.setEmail(null);
    when(users.getOrCreate(any(), any(), any())).thenReturn(user);
    mvc.perform(
            post("/v1/support/contact")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Help\",\"message\":\"hi\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("EMAIL_REQUIRED"));
    verifyNoInteractions(sender);
  }

  @Test
  void reportsProviderFailureWithoutLosingClientDraft() throws Exception {
    when(users.getOrCreate(any(), any(), any())).thenReturn(user);
    org.mockito.Mockito.doThrow(
            new ApiException(HttpStatus.BAD_GATEWAY, "SUPPORT_SEND_FAILED", "Please retry."))
        .when(sender)
        .send(any(), any(), any(), any(), any());
    mvc.perform(
            post("/v1/support/contact")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"Help\",\"message\":\"hi\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error.code").value("SUPPORT_SEND_FAILED"));
  }
}
