package com.axel.pennywise.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateLimitInterceptorCostTest {

  private final RateLimitInterceptor interceptor =
      new RateLimitInterceptor(
          new RateLimitProperties(
              true,
              new RateLimitProperties.Bucket(60, 2),
              new RateLimitProperties.Ip(8, 0.1),
              new RateLimitProperties.Global(200, 50),
              new RateLimitProperties.Costs(1, 2, 2, 3, 5, 20, 25, 40, 1),
              new RateLimitProperties.Support(2, 2.0 / 3600, 10, 10.0 / 3600, 20, 20.0 / 86400)),
          null);

  @Test
  void costsFollowTheWorkEachEndpointDoes() {
    String tx = "/v1/books/{bookId}/transactions";
    assertEquals(40, interceptor.cost("POST", tx + "/import"));
    assertEquals(25, interceptor.cost("GET", tx + "/export"));
    assertEquals(25, interceptor.cost("POST", tx + "/export/query"));
    assertEquals(5, interceptor.cost("GET", "/v1/books/{bookId}/summary/range"));
    assertEquals(3, interceptor.cost("POST", tx + "/search"));
    assertEquals(2, interceptor.cost("GET", "/v1/books"));
    assertEquals(2, interceptor.cost("GET", tx));
    assertEquals(1, interceptor.cost("GET", tx + "/{txId}"));
    assertEquals(2, interceptor.cost("POST", tx));
    assertEquals(2, interceptor.cost("DELETE", tx + "/{txId}"));
    assertEquals(1, interceptor.cost("POST", "/v1/auth/login"));
  }
}
