package com.axel.pennywise.domain.transaction.query.ai;

import com.axel.pennywise.domain.transaction.query.FilterOperator;
import com.axel.pennywise.domain.transaction.query.TxField;
import com.axel.pennywise.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin adapter over Gemini's {@code generateContent} REST call for structured JSON output. One call
 * per proposal, no retries, no streaming, no chat history: {@link FilterProposalService} sends a
 * single question and gets back a single {@link ProviderProposal} or a mapped failure. Prompts and
 * raw model output are never logged, only latency/status/token counts.
 */
@Slf4j
@Component
public class GeminiFilterClient {

  private static final String ENDPOINT_TEMPLATE =
      "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
  private static final int MAX_OUTPUT_TOKENS = 1024;

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Value("${app.ai.gemini.api-key:}")
  private String apiKey;

  @Value("${app.ai.gemini.model:gemini-2.5-flash-lite}")
  private String model;

  @Value("${app.ai.gemini.timeout-seconds:10}")
  private int timeoutSeconds;

  private final RestClient.Builder restClientBuilder;

  // Built once from @Value fields that are fixed for the process lifetime (like every other
  // config value in this app - changing them means a restart). Building per-request would mean
  // mutating the shared RestClient.Builder bean, which is not thread-safe.
  private volatile RestClient restClient;

  public GeminiFilterClient(RestClient.Builder restClientBuilder) {
    this.restClientBuilder = restClientBuilder;
  }

  boolean configured() {
    return apiKey != null && !apiKey.isBlank();
  }

  private RestClient client() {
    RestClient existing = restClient;
    if (existing != null) return existing;
    synchronized (this) {
      if (restClient == null) {
        restClient =
            restClientBuilder
                .baseUrl(ENDPOINT_TEMPLATE.formatted(model))
                .requestFactory(timeoutBoundedFactory())
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
      }
      return restClient;
    }
  }

  ProviderProposal propose(String systemInstruction, String userQuestion) {
    if (!configured()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_FILTERS_DISABLED", "AI filters are not configured");
    }

    GeminiRequest body =
        new GeminiRequest(
            List.of(new Content("user", List.of(new Part(userQuestion)))),
            new SystemInstruction(List.of(new Part(systemInstruction))),
            new GenerationConfig(
                "application/json", buildResponseSchema(), MAX_OUTPUT_TOKENS, 0.0));

    RestClient client = client();

    long start = System.nanoTime();
    GeminiResponse response;
    try {
      response =
          client
              .post()
              .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(GeminiResponse.class);
    } catch (ResourceAccessException e) {
      log.warn("Gemini call timed out or was unreachable: model={}", model);
      throw new ApiException(
          HttpStatus.GATEWAY_TIMEOUT,
          "AI_FILTER_TIMEOUT",
          "The AI took too long to respond. Try again, or use the manual filter.");
    } catch (RestClientResponseException e) {
      log.warn("Gemini call failed: model={}, httpStatus={}", model, e.getStatusCode().value());
      if (e.getStatusCode().value() == 429) {
        throw new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AI_FILTER_PROVIDER_BUSY",
            "The AI provider is busy. Try again shortly, or use the manual filter.");
      }
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "AI_FILTER_PROVIDER_ERROR",
          "The AI provider returned an error. Try again, or use the manual filter.");
    }
    long latencyMs = (System.nanoTime() - start) / 1_000_000;

    if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
      String blockReason =
          response != null && response.promptFeedback() != null
              ? response.promptFeedback().blockReason()
              : null;
      log.info(
          "Gemini returned no candidates: model={}, latencyMs={}, blockReason={}",
          model,
          latencyMs,
          blockReason);
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "AI_FILTER_REFUSED",
          "The AI declined to answer that question. Try rephrasing, or use the manual filter.");
    }

    Candidate candidate = response.candidates().get(0);
    var usage = response.usageMetadata();
    log.info(
        "Gemini filter proposal: model={}, latencyMs={}, finishReason={}, promptTokens={}, "
            + "candidateTokens={}, totalTokens={}",
        model,
        latencyMs,
        candidate.finishReason(),
        usage != null ? usage.promptTokenCount() : null,
        usage != null ? usage.candidatesTokenCount() : null,
        usage != null ? usage.totalTokenCount() : null);

    if (!"STOP".equals(candidate.finishReason())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "AI_FILTER_REFUSED",
          "The AI could not complete that request. Try rephrasing, or use the manual filter.");
    }

    String text = firstPartText(candidate);
    try {
      return mapper.readValue(text, ProviderProposal.class);
    } catch (Exception e) {
      log.warn("Gemini response was not valid JSON for the expected schema: model={}", model);
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "AI_FILTER_PROVIDER_ERROR",
          "The AI response could not be understood. Try again, or use the manual filter.");
    }
  }

  private static String firstPartText(Candidate candidate) {
    if (candidate.content() == null
        || candidate.content().parts() == null
        || candidate.content().parts().isEmpty()
        || candidate.content().parts().get(0).text() == null) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "AI_FILTER_PROVIDER_ERROR",
          "The AI response was empty. Try again, or use the manual filter.");
    }
    return candidate.content().parts().get(0).text();
  }

  private SimpleClientHttpRequestFactory timeoutBoundedFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    int millis = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
    factory.setConnectTimeout(millis);
    factory.setReadTimeout(millis);
    return factory;
  }

  /**
   * Non-recursive schema: a top-level status plus at most two levels of grouping (group of groups
   * of conditions), which comfortably covers what a natural-language question needs and stays well
   * under the P1 AST's own {@code MAX_DEPTH}. A condition's value is a set of mutually-exclusive
   * typed slots rather than a polymorphic field, since structured-output schemas don't reliably
   * support {@code anyOf} across scalar/array types.
   */
  Map<String, Object> buildResponseSchema() {
    List<String> fieldNames =
        AiFilterFields.ALLOWED.stream().map(TxField::wireName).sorted().toList();
    List<String> operatorNames = Arrays.stream(FilterOperator.values()).map(Enum::name).toList();
    List<String> datePresetNames = Arrays.stream(DatePreset.values()).map(Enum::name).toList();

    Map<String, Object> condition = new LinkedHashMap<>();
    condition.put("type", "OBJECT");
    condition.put(
        "properties",
        Map.of(
            "field", enumSchema(fieldNames),
            "operator", enumSchema(operatorNames),
            "stringValue", Map.of("type", "STRING"),
            "numberValue", Map.of("type", "NUMBER"),
            "dateValue", Map.of("type", "STRING"),
            "datePreset", enumSchema(datePresetNames),
            "stringArrayValue", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
            "numberArrayValue", Map.of("type", "ARRAY", "items", Map.of("type", "NUMBER"))));
    condition.put("required", List.of("field", "operator"));

    Map<String, Object> group = new LinkedHashMap<>();
    group.put("type", "OBJECT");
    group.put(
        "properties",
        Map.of(
            "op", enumSchema(List.of("AND", "OR")),
            "conditions", Map.of("type", "ARRAY", "items", condition)));
    group.put("required", List.of("op", "conditions"));

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "OBJECT");
    root.put(
        "properties",
        Map.of(
            "status", enumSchema(List.of("PROPOSAL", "CLARIFY", "UNSUPPORTED")),
            "clarification", Map.of("type", "STRING"),
            "limitation", Map.of("type", "STRING"),
            "topOp", enumSchema(List.of("AND", "OR")),
            "groups", Map.of("type", "ARRAY", "items", group),
            "sortField", enumSchema(fieldNames),
            "sortDirection", enumSchema(List.of("ASC", "DESC"))));
    root.put("required", List.of("status"));
    return root;
  }

  private static Map<String, Object> enumSchema(List<String> values) {
    return Map.of("type", "STRING", "enum", values);
  }

  // ---------------------------------------------------------------- wire records

  private record GeminiRequest(
      List<Content> contents,
      SystemInstruction systemInstruction,
      GenerationConfig generationConfig) {}

  private record Content(String role, List<Part> parts) {}

  private record SystemInstruction(List<Part> parts) {}

  private record Part(String text) {}

  private record GenerationConfig(
      String responseMimeType, Object responseSchema, int maxOutputTokens, double temperature) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record GeminiResponse(
      List<Candidate> candidates, UsageMetadata usageMetadata, PromptFeedback promptFeedback) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record Candidate(Content content, String finishReason) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record UsageMetadata(
      Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PromptFeedback(String blockReason) {}
}
