package com._gods.simplelms.aitutor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Page context sent by the frontend with each chat message. Only an id: the name is looked up on
 * the server, and the id itself is never trusted (see {@link TutorAccessService#verify}).
 */
public record TutorContextRequest(
    @JsonProperty("page_type") TutorPageType pageType,
    @JsonProperty("course_id") Long courseId) {
}
