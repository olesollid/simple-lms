package com._gods.canvas2.messaging;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Published to RabbitMQ when a student submits quiz answers. Serialized as JSON (see
 * {@code RabbitConfig}), so this must stay a plain data carrier.
 */
public record SubmissionCreatedEvent(Long quizId, String studentEmail, Map<Long, List<Long>> answers,
    LocalDateTime submittedAt) implements Serializable {
}
