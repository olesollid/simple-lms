package com._gods.canvas2.aitutor;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The kind of page the student is on, as reported by the frontend (lowercase on the wire). */
public enum TutorPageType {
  @JsonProperty("dashboard") DASHBOARD,
  @JsonProperty("course") COURSE,
  @JsonProperty("assignment") ASSIGNMENT,
  @JsonProperty("other") OTHER
}
