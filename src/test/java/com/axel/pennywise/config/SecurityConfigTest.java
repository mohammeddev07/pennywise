package com.axel.pennywise.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TestSecureController.class)
@Import(SecurityConfig.class)
@TestPropertySource(
    properties = {
      "app.security.auth-enabled=true",
      "app.security.jwt.issuer=pennywise-test",
      "app.security.jwt.local-secret=01234567890123456789012345678912",
      "app.security.jwt.audience="
    })
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private JwtDecoder jwtDecoder;

  @Test
  void locallyIssuedTokenWithoutOptionalAudience_isAccepted() {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("pennywise-test")
            .subject("local:test@example.com")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    String token =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();

    assertThatCode(() -> jwtDecoder.decode(token)).doesNotThrowAnyException();
  }

  @Test
  void protectedEndpointWithoutToken_returnsJson401() throws Exception {
    mockMvc
        .perform(get("/secure"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("Missing, invalid, or expired bearer token"));
  }

  @Test
  void protectedEndpointWithInvalidToken_returnsJson401() throws Exception {
    mockMvc
        .perform(get("/secure").header("Authorization", "Bearer not-a-valid-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.message").value("Missing, invalid, or expired bearer token"));
  }

  @Test
  void protectedEndpointWithoutRequiredAuthority_returnsJson403() throws Exception {
    mockMvc
        .perform(get("/secure/admin").with(jwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        .andExpect(
            jsonPath("$.error.message")
                .value("Authenticated user is not allowed to access this resource"));
  }
}
