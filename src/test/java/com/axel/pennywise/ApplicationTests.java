package com.axel.pennywise;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
// src/test/resources/application.properties replaces the main file, so mirror the production value.
@TestPropertySource(properties = "management.endpoints.web.cors.allowed-origins=*")
@Testcontainers(disabledWithoutDocker = true)
class ApplicationTests {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("pennywise_test")
          .withUsername("postgres")
          .withPassword("postgres");

  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoads() {}

  @Test
  void actuatorHealth_isReadableByBrowserClients() throws Exception {
    mockMvc
        .perform(get("/actuator/health").header("Origin", "https://pennywise.example"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "*"));
  }
}
