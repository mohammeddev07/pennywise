package com.axel.pennywise.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityConfigLocalSecretTest {

  private static final String LOCAL_DEVELOPMENT_SECRET =
      "pennywise-local-development-secret-change-me";

  @Test
  void localAuthDisabled_allowsDevelopmentSecret() {
    SecurityConfig config = config("local", "", LOCAL_DEVELOPMENT_SECRET);

    assertThatCode(() -> ReflectionTestUtils.invokeMethod(config, "localSecretKey"))
        .doesNotThrowAnyException();
  }

  @Test
  void nonLocalEnvironment_rejectsDevelopmentSecret() {
    SecurityConfig config = config("production", "", LOCAL_DEVELOPMENT_SECRET);

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(config, "localSecretKey"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "app.security.jwt.local-secret must be overridden outside local auth-disabled mode");
  }

  @Test
  void enabledLocalAuth_rejectsDevelopmentSecret() {
    SecurityConfig config = config("local", "true", LOCAL_DEVELOPMENT_SECRET);

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(config, "localSecretKey"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "app.security.jwt.local-secret must be overridden outside local auth-disabled mode");
  }

  @Test
  void nonLocalEnvironment_allowsOverriddenSecret() {
    SecurityConfig config = config("production", "", "01234567890123456789012345678912");

    assertThatCode(() -> ReflectionTestUtils.invokeMethod(config, "localSecretKey"))
        .doesNotThrowAnyException();
  }

  @Test
  void shortSecret_isRejectedBeforeTokenUse() {
    SecurityConfig config = config("production", "", "too-short");

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(config, "localSecretKey"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("app.security.jwt.local-secret must be at least 32 bytes for HS256");
  }

  @Test
  void invalidExplicitAuthSetting_isRejectedInsteadOfDisablingAuth() {
    SecurityConfig config = config("production", "treu", "01234567890123456789012345678912");

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(config, "isAuthEnabled"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("app.security.auth-enabled must be either true, false, or blank");
  }

  private SecurityConfig config(String environment, String authEnabled, String localSecret) {
    SecurityConfig config = new SecurityConfig();
    ReflectionTestUtils.setField(config, "environment", environment);
    ReflectionTestUtils.setField(config, "authEnabledRaw", authEnabled);
    ReflectionTestUtils.setField(config, "localSecret", localSecret);
    return config;
  }
}
