package com._gods.simplelms.web.dto;

import com._gods.simplelms.model.Term;

public record CourseRequest(String title, String description, Term term, int year) {
}
