package com._gods.canvas2.aitutor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Whether the student is inside a course. The tutor deliberately knows nothing finer than this:
 * no assignment, module or content page is ever part of the context.
 */
public enum TutorPageType {
  @JsonProperty("dashboard") DASHBOARD,
  @JsonProperty("course") COURSE,
  @JsonProperty("other") OTHER
}
