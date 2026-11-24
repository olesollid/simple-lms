package com._gods.simplelms.web.dto;

import java.time.LocalDateTime;

public record AssignmentRequest(String title, int position, String instructions,
    LocalDateTime deadline, boolean lateSubmissionsAccepted, boolean groupSubmission) {
}
