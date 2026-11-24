package com._gods.simplelms.messaging;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Published to RabbitMQ when a student submits quiz answers. Serialized as JSON (see
 * {@code RabbitConfig}), so this must stay a plain data carrier.
 */
public record SubmissionCreatedEvent(Long quizId, String studentEmail, int attemptNumber,
    Map<Long, List<Long>> answers, Map<Long, List<String>> textAnswers,
    LocalDateTime submittedAt) implements Serializable {
}
