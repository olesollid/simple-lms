package com._gods.simplelms.web.dto;

/** Returned with 202 Accepted: the attempt was queued for grading under this number. */
public record QuizSubmissionResponse(int attemptNumber) {
}
