package com._gods.canvas2.aitutor;

/**
 * A page context the server has checked against the database for this student. Course and
 * assignment fields are null when the page type does not carry them.
 */
public record VerifiedTutorContext(
    TutorPageType pageType,
    Long courseId,
    String courseName,
    Long assignmentId,
    String assignmentTitle) {
}
