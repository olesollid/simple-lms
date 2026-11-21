package com._gods.canvas2.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com._gods.canvas2.aitutor.RateLimitExceededException;
import com._gods.canvas2.web.dto.RateLimitErrorResponse;

class RateLimitHandlingTests {

  @Test
  void answersWith429TheWaitTimeAndAClearMessage() {
    ResponseEntity<RateLimitErrorResponse> response =
        new GlobalExceptionHandler().handleRateLimited(new RateLimitExceededException(30));

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    assertEquals("30", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    assertEquals(30, response.getBody().retryAfterSeconds());
    assertEquals("You've sent too many messages. Please wait 30 seconds and try again.",
        response.getBody().message());
  }
}
