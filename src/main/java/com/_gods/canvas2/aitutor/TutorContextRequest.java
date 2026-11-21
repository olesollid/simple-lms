package com._gods.canvas2.aitutor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Page context sent by the frontend with each chat message. Only ids: names are looked up on the
 * server, and the ids themselves are never trusted (see {@link TutorAccessService#verify}).
 */
public record TutorContextRequest(
    @JsonProperty("page_type") TutorPageType pageType,
    @JsonProperty("course_id") Long courseId,
    @JsonProperty("assignment_id") Long assignmentId) {
}
