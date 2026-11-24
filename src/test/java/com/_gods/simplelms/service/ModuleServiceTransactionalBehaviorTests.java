package com._gods.simplelms.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;
import com._gods.simplelms.repository.UserRepository;
import com._gods.simplelms.service.CourseService;
import com._gods.simplelms.service.ModuleService;

/**
 * Deliberately NOT @DataJpaTest and NOT @Transactional: a @DataJpaTest wraps every test in a
 * single transaction and persistence context, which hides lazy-loading and cross-session
 * identity bugs that only show up once each repository call opens and closes its own session
 * (the real behavior with open-in-view=false and no @Transactional on the service).
 */
@SpringBootTest
class ModuleServiceTransactionalBehaviorTests {

  @Autowired
  private ModuleService moduleService;

  @Autowired
  private CourseService courseService;

  @Autowired
  private UserRepository userRepository;

  @Test
  void readsModuleWithDependenciesOutsideAnyTestTransaction() {
    userRepository.save(new User("transactional-test-teacher@example.com", "Teacher"));
    Course course = courseService.createCourse("Transactional Behavior", "desc", Term.AUTUMN,
        2026, "transactional-test-teacher@example.com");

    Module week1 = moduleService.createModule(course.getId(), "Week 1",
        "transactional-test-teacher@example.com");
    Module week2 = moduleService.createModule(course.getId(), "Week 2",
        "transactional-test-teacher@example.com");

    assertDoesNotThrow(() -> moduleService.addDependency(week2.getId(), week1.getId(),
        "transactional-test-teacher@example.com"));

    Module reloaded = moduleService.getModuleById(week2.getId(),
        "transactional-test-teacher@example.com");
    assertDoesNotThrow(reloaded::getDependencies,
        "reading a lazy collection outside a transaction should not throw");
    assertTrue(reloaded.getDependencies().contains(week1));

    List<Module> byCourse = moduleService.getModulesByCourse(course.getId(),
        "transactional-test-teacher@example.com");
    assertEquals(2, byCourse.size());
  }
}
