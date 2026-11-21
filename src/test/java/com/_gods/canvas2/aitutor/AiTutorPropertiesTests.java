package com._gods.canvas2.aitutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiTutorPropertiesTests {

  private static AiTutorProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("app.ai-tutor", AiTutorProperties.class);
  }

  @Test
  void isOffWithNoConfiguration() {
    AiTutorProperties properties = bind(Map.of());

    assertFalse(properties.enabled());
    assertEquals(20, properties.rateLimit().maxRequests());
    assertEquals(Duration.ofMinutes(1), properties.rateLimit().window());
  }

  @Test
  void bindsTheSwitchAndTheLimits() {
    AiTutorProperties properties = bind(Map.of(
        "app.ai-tutor.enabled", "true",
        "app.ai-tutor.rate-limit.max-requests", "5",
        "app.ai-tutor.rate-limit.window", "30s"));

    assertTrue(properties.enabled());
    assertEquals(5, properties.rateLimit().maxRequests());
    assertEquals(Duration.ofSeconds(30), properties.rateLimit().window());
  }

  @Test
  void hasNoPerCourseSettings() {
    // The one switch covers every course, so these settings are gone and are ignored if present.
    AiTutorProperties properties = bind(Map.of(
        "app.ai-tutor.enabled", "true",
        "app.ai-tutor.enabled-course-ids", "1",
        "app.ai-tutor.all-courses", "false"));

    assertTrue(properties.enabled());
    assertEquals(java.util.Set.of("enabled", "rateLimit"),
        java.util.Arrays.stream(AiTutorProperties.class.getRecordComponents())
            .map(component -> component.getName()).collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  void rejectsNonsenseLimits() {
    assertThrows(Exception.class, () -> bind(Map.of("app.ai-tutor.rate-limit.max-requests", "0")));
    assertThrows(Exception.class, () -> bind(Map.of("app.ai-tutor.rate-limit.window", "0s")));
  }
}
