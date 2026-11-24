package com._gods.simplelms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** An {@link ErrorResponse} that also says how long to wait; snake_case matches the tutor API. */
public record RateLimitErrorResponse(
    String message,
    @JsonProperty("retry_after_seconds") long retryAfterSeconds) {
}
