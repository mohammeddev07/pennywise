package com.axel.pennywise.domain.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResendSupportMailSenderTest {
  private final HttpClient client = mock(HttpClient.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void sendsWithServerOnlyRecipientAndStableProviderKey() throws Exception {
    @SuppressWarnings("unchecked")
    HttpResponse<Void> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    var sender =
        new ResendSupportMailSender(
            client, mapper, "secret", "support@example.com", "inbox@example.com");

    sender.send(userId, "user@example.com", "Help", "My message", "retry-key-123");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    HttpRequest request = captor.getValue();
    assertEquals("https://api.resend.com/emails", request.uri().toString());
    assertEquals("Bearer secret", request.headers().firstValue("Authorization").orElseThrow());
    assertEquals(
        userId + ":retry-key-123", request.headers().firstValue("Idempotency-Key").orElseThrow());
    var payload = mapper.readTree(body(request));
    assertEquals("support@example.com", payload.get("from").asText());
    assertEquals("inbox@example.com", payload.get("to").get(0).asText());
    assertEquals("user@example.com", payload.get("reply_to").asText());
    assertEquals("Help", payload.get("subject").asText());
    assertEquals(
        "From: user@example.com\nUser ID: " + userId + "\n\nMy message",
        payload.get("text").asText());
  }

  @Test
  void rejectsMissingConfigAndProviderFailure() throws Exception {
    var missing = new ResendSupportMailSender(client, mapper, "", "", "");
    ApiException configFailure =
        assertThrows(
            ApiException.class,
            () -> missing.send(userId, "user@example.com", "Help", "Message", "retry-key-123"));
    assertEquals("SUPPORT_NOT_CONFIGURED", configFailure.code());

    @SuppressWarnings("unchecked")
    HttpResponse<Void> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(422);
    when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
    var sender =
        new ResendSupportMailSender(
            client, mapper, "secret", "support@example.com", "inbox@example.com");
    ApiException sendFailure =
        assertThrows(
            ApiException.class,
            () -> sender.send(userId, "user@example.com", "Help", "Message", "retry-key-123"));
    assertEquals("SUPPORT_SEND_FAILED", sendFailure.code());
  }

  private String body(HttpRequest request) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    CompletableFuture<String> result = new CompletableFuture<>();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<>() {
              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
              }

              @Override
              public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
              }

              @Override
              public void onComplete() {
                result.complete(bytes.toString(StandardCharsets.UTF_8));
              }
            });
    return result.get();
  }
}
