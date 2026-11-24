package com._gods.simplelms.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Enrollment;
import com._gods.simplelms.model.Group;
import com._gods.simplelms.model.GroupMembership;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;
import com._gods.simplelms.repository.CourseRepository;
import com._gods.simplelms.repository.EnrollmentRepository;
import com._gods.simplelms.repository.GroupMembershipRepository;
import com._gods.simplelms.repository.GroupRepository;
import com._gods.simplelms.repository.ModuleRepository;
import com._gods.simplelms.repository.UserRepository;

/**
 * Basic repository operations. Content inheritance and interactions are covered
 * by H2DatabaseTests against the application's configured datasource.
 */
@DataJpaTest
class RepositoryTests {

  @Autowired
  private CourseRepository courseRepository;

  @Autowired
  private ModuleRepository moduleRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EnrollmentRepository enrollmentRepository;

  @Autowired
  private GroupRepository groupRepository;

  @Autowired
  private GroupMembershipRepository groupMembershipRepository;

  @Test
  void findsUserByEmail() {
    userRepository.save(new User("student@example.com", "Student Name"));

    Optional<User> found = userRepository.findById("student@example.com");

    assertTrue(found.isPresent());
    assertEquals("Student Name", found.get().getName());
  }

  @Test
  void courseCrudOperationsWork() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));

    Optional<Course> found = courseRepository.findById(course.getId());
    assertTrue(found.isPresent());
    assertEquals("Intro to Testing", found.get().getTitle());

    courseRepository.deleteById(course.getId());
    assertFalse(courseRepository.findById(course.getId()).isPresent());
  }

  @Test
  void savesAndFindsEnrollmentByUserAndCourse() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    User user = userRepository.save(new User("student@example.com", "Student Name"));
    enrollmentRepository.save(new Enrollment(user, course, Role.STUDENT));

    Optional<Enrollment> found = enrollmentRepository.findByUserAndCourse(user, course);

    assertTrue(found.isPresent());
    assertEquals(Role.STUDENT, found.get().getRole());
  }

  @Test
  void savesAndFindsGroupMembershipsByGroup() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    Group group = groupRepository.save(new Group(course, "Group 1"));
    User userOne = userRepository.save(new User("student1@example.com", "Student One"));
    User userTwo = userRepository.save(new User("student2@example.com", "Student Two"));
    groupMembershipRepository.save(new GroupMembership(userOne, group));
    groupMembershipRepository.save(new GroupMembership(userTwo, group));

    List<GroupMembership> members = groupMembershipRepository.findByGroup(group);

    assertEquals(2, members.size());
  }

  @Test
  void savesAndFindsModulesByCourse() {
    Course course = courseRepository.save(new Course("Intro to Testing", "desc", Term.AUTUMN, 2026));
    moduleRepository.save(new Module(course, "Week 1"));
    moduleRepository.save(new Module(course, "Week 2"));

    List<Module> modules = moduleRepository.findByCourse(course);

    assertEquals(2, modules.size());
  }
}
