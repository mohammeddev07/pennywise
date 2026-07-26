package com.axel.pennywise.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axel.pennywise.api.dto.user.MeUpdateRequest;
import com.axel.pennywise.domain.user.UserEntity;
import com.axel.pennywise.domain.user.UserService;
import com.axel.pennywise.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  private MockMvc mockMvc;

  @Mock private UserService userService;

  private ObjectMapper objectMapper;
  private UserEntity testUser;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();

    UserController controller = new UserController(userService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();

    testUser = new UserEntity();
    testUser.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    testUser.setEmail("user@example.com");
    testUser.setDefaultCurrencyCode("USD");
    testUser.setCreatedAt(OffsetDateTime.now());
  }

  private static RequestPostProcessor auth() {
    return request -> {
      request.setUserPrincipal(new TestingAuthenticationToken("test-user", "N/A"));
      return request;
    };
  }

  @Test
  void testPatchMeUpdatesDefaultCurrency() throws Exception {
    UserEntity updated = new UserEntity();
    updated.setId(testUser.getId());
    updated.setEmail(testUser.getEmail());
    updated.setDefaultCurrencyCode("EUR");
    updated.setCreatedAt(testUser.getCreatedAt());

    when(userService.getOrCreate(any(), any(), any())).thenReturn(testUser);
    when(userService.updateDefaultCurrency(testUser, "eur")).thenReturn(updated);

    MeUpdateRequest req = new MeUpdateRequest("eur");

    mockMvc
        .perform(
            patch("/v1/me")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
        .andExpect(jsonPath("$.email").value("user@example.com"))
        .andExpect(jsonPath("$.defaultCurrencyCode").value("EUR"));

    verify(userService).getOrCreate(any(), any(), any());
    verify(userService).updateDefaultCurrency(testUser, "eur");
    verifyNoMoreInteractions(userService);
  }

  @Test
  void testPatchMeRejectsInvalidDefaultCurrency() throws Exception {
    MeUpdateRequest req = new MeUpdateRequest("EURO");

    mockMvc
        .perform(
            patch("/v1/me")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService);
  }

  @Test
  void testPatchMeRejectsEmptyObjectWithoutErasingCurrency() throws Exception {
    mockMvc
        .perform(patch("/v1/me").with(auth()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(userService);
  }

  @Test
  void testPatchMeRejectsNullCurrencyWithoutErasingCurrency() throws Exception {
    mockMvc
        .perform(
            patch("/v1/me")
                .with(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultCurrencyCode\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(userService);
  }
}
