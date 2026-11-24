package com._gods.simplelms.web.dto;

import java.time.LocalDateTime;

public record SubmissionPolicyRequest(LocalDateTime deadline, boolean lateSubmissionsAccepted) {}
