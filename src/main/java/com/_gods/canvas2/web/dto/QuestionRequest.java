package com._gods.canvas2.web.dto;

import java.util.List;

public record QuestionRequest(String text, String hint, List<AnswerOptionRequest> options) {
}
