package com._gods.canvas2.web.dto;

import java.time.LocalDateTime;

public record SubmissionPolicyRequest(LocalDateTime deadline, boolean lateSubmissionsAccepted) {}
