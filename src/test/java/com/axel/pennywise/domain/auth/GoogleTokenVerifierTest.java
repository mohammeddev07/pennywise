package com.axel.pennywise.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.axel.pennywise.exception.ApiException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class GoogleTokenVerifierTest {

  private static final String CLIENT_ID = "my-client.apps.googleusercontent.com";

  private static KeyPair keys;
  private static KeyPair otherKeys;
  private final GoogleTokenVerifier verifier =
      new GoogleTokenVerifier(
          List.of(CLIENT_ID),
          NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());

  @BeforeAll
  static void generateKeys() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    keys = gen.generateKeyPair();
    otherKeys = gen.generateKeyPair();
  }

  private JWTClaimsSet.Builder validClaims() {
    return new JWTClaimsSet.Builder()
        .issuer("https://accounts.google.com")
        .audience(CLIENT_ID)
        .subject("g-123")
        .claim("email", "mak@example.com")
        .claim("email_verified", true)
        .issueTime(new Date())
        .expirationTime(Date.from(Instant.now().plusSeconds(600)));
  }

  private String sign(JWTClaimsSet claims, KeyPair signer) throws JOSEException {
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(signer.getPrivate()));
    return jwt.serialize();
  }

  private void assertInvalid(String token) {
    ApiException ex = assertThrows(ApiException.class, () -> verifier.verify(token));
    assertEquals("GOOGLE_TOKEN_INVALID", ex.code());
  }

  @Test
  void acceptsAValidToken() throws Exception {
    var identity = verifier.verify(sign(validClaims().build(), keys));
    assertEquals("g-123", identity.sub());
    assertEquals("mak@example.com", identity.email());
  }

  @Test
  void acceptsTheBareGoogleIssuerSpelling() throws Exception {
    verifier.verify(sign(validClaims().issuer("accounts.google.com").build(), keys));
  }

  @Test
  void rejectsATokenIssuedForAnotherClientId() throws Exception {
    assertInvalid(sign(validClaims().audience("someone-elses-client").build(), keys));
  }

  @Test
  void rejectsAWrongIssuer() throws Exception {
    assertInvalid(sign(validClaims().issuer("https://evil.example.com").build(), keys));
  }

  @Test
  void rejectsATokenWithNoIssuer() throws Exception {
    assertInvalid(sign(validClaims().issuer(null).build(), keys));
  }

  @Test
  void rejectsAnExpiredToken() throws Exception {
    assertInvalid(
        sign(
            validClaims().expirationTime(Date.from(Instant.now().minusSeconds(600))).build(),
            keys));
  }

  @Test
  void rejectsATokenSignedWithAnUnknownKey() throws Exception {
    assertInvalid(sign(validClaims().build(), otherKeys));
  }

  @Test
  void rejectsAnUnverifiedEmail() throws Exception {
    assertInvalid(sign(validClaims().claim("email_verified", false).build(), keys));
  }

  @Test
  void rejectsGarbage() {
    assertInvalid("not-a-jwt");
  }

  @Test
  void reportsNotConfiguredWhenNoClientIdIsSet() {
    var unconfigured =
        new GoogleTokenVerifier(
            List.of(" "), NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());
    ApiException ex = assertThrows(ApiException.class, () -> unconfigured.verify("x"));
    assertEquals("GOOGLE_SIGN_IN_NOT_CONFIGURED", ex.code());
  }
}
