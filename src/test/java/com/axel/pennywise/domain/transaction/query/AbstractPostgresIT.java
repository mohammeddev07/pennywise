package com.axel.pennywise.domain.transaction.query;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL for the query-engine integration tests. Uses a Testcontainers Postgres like the rest
 * of the suite; when {@code PW_TEST_DB_URL} is set (e.g. {@code
 * jdbc:postgresql://localhost:5432/pw_test?user=postgres}) that database is used instead, so the
 * tests also run on machines without Docker. Skipped when neither is available.
 */
public abstract class AbstractPostgresIT {

  private static final String EXTERNAL_URL = System.getenv("PW_TEST_DB_URL");
  private static PostgreSQLContainer<?> container;

  @BeforeAll
  static void requireDatabase() {
    Assumptions.assumeTrue(
        EXTERNAL_URL != null || DockerClientFactory.instance().isDockerAvailable(),
        "needs Docker or PW_TEST_DB_URL");
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    if (EXTERNAL_URL != null) {
      registry.add("spring.datasource.url", () -> EXTERNAL_URL);
      return;
    }
    registry.add("spring.datasource.url", () -> container().getJdbcUrl());
    registry.add("spring.datasource.username", () -> container().getUsername());
    registry.add("spring.datasource.password", () -> container().getPassword());
  }

  private static synchronized PostgreSQLContainer<?> container() {
    if (container == null) {
      container = new PostgreSQLContainer<>("postgres:16-alpine");
      container.start();
    }
    return container;
  }
}
