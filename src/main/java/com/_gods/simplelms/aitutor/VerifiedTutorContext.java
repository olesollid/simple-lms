package com._gods.simplelms.aitutor;

/**
 * A page context the server has checked against the database for this student. The course fields
 * are null unless the student is inside a course.
 */
public record VerifiedTutorContext(TutorPageType pageType, Long courseId, String courseName) {
}
