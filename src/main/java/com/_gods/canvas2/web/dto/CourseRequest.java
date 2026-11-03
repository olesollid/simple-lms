package com._gods.canvas2.web.dto;

import com._gods.canvas2.model.Term;

public record CourseRequest(String title, String description, Term term, int year) {
}
