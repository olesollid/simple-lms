package com._gods.simplelms.web.dto;

import java.time.LocalDateTime;

public record CodingExerciseRequest(String title, int position, String repositoryUrl,
    LocalDateTime deadline, boolean lateSubmissionsAccepted, boolean groupSubmission) {
}
