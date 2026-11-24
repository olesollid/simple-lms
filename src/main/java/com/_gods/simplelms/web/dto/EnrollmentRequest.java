package com._gods.simplelms.web.dto;

import com._gods.simplelms.model.Role;

public record EnrollmentRequest(String email, Role role) {
}
