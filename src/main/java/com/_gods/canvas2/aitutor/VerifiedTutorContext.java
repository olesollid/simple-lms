package com._gods.canvas2.aitutor;

/**
 * A page context the server has checked against the database for this student. The course fields
 * are null unless the student is inside a course.
 */
public record VerifiedTutorContext(TutorPageType pageType, Long courseId, String courseName) {
}
