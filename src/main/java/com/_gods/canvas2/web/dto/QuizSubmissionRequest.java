package com._gods.canvas2.web.dto;

import java.util.List;
import java.util.Map;

public record QuizSubmissionRequest(Map<Long, List<Long>> answers) {
}
