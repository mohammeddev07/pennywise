package com.axel.pennywise.exception;

import org.springframework.http.HttpStatus;

/** Same shape as {@link ApiException}, plus a Retry-After hint for the 429 response header. */
public class RateLimitExceededException extends ApiException {

  private final int retryAfterSeconds;

  public RateLimitExceededException(String code, String message, int retryAfterSeconds) {
    super(HttpStatus.TOO_MANY_REQUESTS, code, message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
