package com.axel.pennywise.domain.support;

import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ResendSupportMailSender implements SupportMailSender {
  private static final URI ENDPOINT = URI.create("https://api.resend.com/emails");
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final String apiKey;
  private final String fromEmail;
  private final String toEmail;

  @Autowired
  public ResendSupportMailSender(
      ObjectMapper mapper,
      @Value("${app.support.resend-api-key:}") String apiKey,
      @Value("${app.support.from-email:}") String fromEmail,
      @Value("${app.support.to-email:}") String toEmail) {
    this(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        mapper,
        apiKey,
        fromEmail,
        toEmail);
  }

  ResendSupportMailSender(
      HttpClient client, ObjectMapper mapper, String apiKey, String fromEmail, String toEmail) {
    this.client = client;
    this.mapper = mapper;
    this.apiKey = apiKey;
    this.fromEmail = fromEmail;
    this.toEmail = toEmail;
  }

  @Override
  public void send(
      UUID userId, String userEmail, String subject, String message, String idempotencyKey) {
    if (apiKey.isBlank() || fromEmail.isBlank() || toEmail.isBlank()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "SUPPORT_NOT_CONFIGURED",
          "Support is temporarily unavailable. Please try again later.");
    }

    try {
      Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("from", fromEmail);
      fields.put("to", new String[] {toEmail});
      fields.put("reply_to", userEmail);
      fields.put("subject", subject);
      fields.put("text", "From: " + userEmail + "\nUser ID: " + userId + "\n\n" + message);
      String payload = mapper.writeValueAsString(fields);
      HttpRequest request =
          HttpRequest.newBuilder(ENDPOINT)
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", userId + ":" + idempotencyKey)
              .POST(HttpRequest.BodyPublishers.ofString(payload))
              .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw sendFailed();
      }
    } catch (JsonProcessingException e) {
      throw sendFailed();
    } catch (IOException e) {
      throw sendFailed();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw sendFailed();
    }
  }

  private ApiException sendFailed() {
    return new ApiException(
        HttpStatus.BAD_GATEWAY, "SUPPORT_SEND_FAILED", "Couldn't send your message. Please retry.");
  }
}
