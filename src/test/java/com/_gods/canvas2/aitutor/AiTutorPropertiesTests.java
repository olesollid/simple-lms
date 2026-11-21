package com._gods.canvas2.aitutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiTutorPropertiesTests {

  private static AiTutorProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("app.ai-tutor", AiTutorProperties.class);
  }

  @Test
  void everythingIsOffWithNoConfiguration() {
    AiTutorProperties properties = bind(Map.of());

    assertFalse(properties.enabled());
    assertTrue(properties.enabledCourseIds().isEmpty());
    assertFalse(properties.isCourseEnabled(1L));
    assertEquals(20, properties.rateLimit().maxRequests());
    assertEquals(Duration.ofMinutes(1), properties.rateLimit().window());
  }

  @Test
  void bindsTheSwitchCourseListAndLimits() {
    AiTutorProperties properties = bind(Map.of(
        "app.ai-tutor.enabled", "true",
        "app.ai-tutor.enabled-course-ids", "1, 3",
        "app.ai-tutor.rate-limit.max-requests", "5",
        "app.ai-tutor.rate-limit.window", "30s"));

    assertTrue(properties.enabled());
    assertEquals(Set.of(1L, 3L), properties.enabledCourseIds());
    assertEquals(5, properties.rateLimit().maxRequests());
    assertEquals(Duration.ofSeconds(30), properties.rateLimit().window());
  }

  @Test
  void aBlankCourseListMeansNoCourses() {
    AiTutorProperties properties = bind(Map.of(
        "app.ai-tutor.enabled", "true", "app.ai-tutor.enabled-course-ids", ""));

    assertTrue(properties.enabledCourseIds().isEmpty());
  }

  @Test
  void aCourseNeedsBothTheGlobalSwitchAndItsOwnOptIn() {
    AiTutorProperties off = bind(Map.of("app.ai-tutor.enabled-course-ids", "1"));
    AiTutorProperties on = bind(Map.of(
        "app.ai-tutor.enabled", "true", "app.ai-tutor.enabled-course-ids", "1"));

    assertFalse(off.isCourseEnabled(1L));
    assertTrue(on.isCourseEnabled(1L));
    assertFalse(on.isCourseEnabled(2L));
    assertFalse(on.isCourseEnabled(null));
  }

  @Test
  void rejectsNonsenseLimits() {
    assertThrows(Exception.class, () -> bind(Map.of("app.ai-tutor.rate-limit.max-requests", "0")));
    assertThrows(Exception.class, () -> bind(Map.of("app.ai-tutor.rate-limit.window", "0s")));
  }
}
