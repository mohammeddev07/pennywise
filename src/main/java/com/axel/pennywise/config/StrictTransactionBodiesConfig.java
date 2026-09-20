package com.axel.pennywise.config;

import com.axel.pennywise.api.dto.transaction.TransactionCreateRequest;
import com.axel.pennywise.api.dto.transaction.TransactionUpdateRequest;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.io.IOException;
import java.util.Set;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot turns off {@code FAIL_ON_UNKNOWN_PROPERTIES}, and {@code @JsonIgnoreProperties
 * (ignoreUnknown = false)} only means "use the global setting", so a client sending {@code
 * createdAt}, {@code version}, {@code externalId}, ... would be silently ignored. For the
 * transaction write bodies an unknown property must be rejected (400 VALIDATION_ERROR via {@code
 * GlobalExceptionHandler}); every other DTO keeps the lenient global behavior.
 */
@Configuration
public class StrictTransactionBodiesConfig {

  private static final Set<Class<?>> STRICT =
      Set.of(TransactionCreateRequest.class, TransactionUpdateRequest.class);

  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictTransactionBodies() {
    return builder ->
        builder.postConfigurer(
            mapper ->
                mapper.addHandler(
                    new DeserializationProblemHandler() {
                      @Override
                      public boolean handleUnknownProperty(
                          DeserializationContext ctxt,
                          JsonParser p,
                          JsonDeserializer<?> deserializer,
                          Object beanOrClass,
                          String propertyName)
                          throws IOException {
                        Class<?> type =
                            beanOrClass instanceof Class<?> c ? c : beanOrClass.getClass();
                        if (STRICT.contains(type)) {
                          throw UnrecognizedPropertyException.from(
                              p, beanOrClass, propertyName, null);
                        }
                        return false;
                      }
                    }));
  }
}
