package com._gods.simplelms.aitutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com._gods.simplelms.aitutor.TutorGroundingService;
import com._gods.simplelms.aitutor.TutorPageType;
import com._gods.simplelms.aitutor.VerifiedTutorContext;
import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Enrollment;
import com._gods.simplelms.model.Module;
import com._gods.simplelms.model.Role;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;
import com._gods.simplelms.repository.CourseRepository;
import com._gods.simplelms.repository.EnrollmentRepository;
import com._gods.simplelms.repository.ModuleRepository;
import com._gods.simplelms.repository.UserRepository;
import com._gods.simplelms.service.ContentService;
import com._gods.simplelms.service.EnrollmentService;
import com._gods.simplelms.service.ModuleService;
import com._gods.simplelms.service.StudentInteractionService;
import com._gods.simplelms.service.UserService;

@DataJpaTest
@Import({TutorGroundingService.class, ContentService.class, ModuleService.class,
    EnrollmentService.class, UserService.class, StudentInteractionService.class})
class TutorGroundingServiceTests {

  @Autowired private TutorGroundingService grounding;
  @Autowired private ContentService contentService;
  @Autowired private ModuleService moduleService;
  @Autowired private CourseRepository courseRepository;
  @Autowired private ModuleRepository moduleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EnrollmentRepository enrollmentRepository;

  private static int counter = 0;

  private Course course(String title, String description) {
    return courseRepository.save(new Course(title, description, Term.AUTUMN, 2026));
  }

  private String member(Course course, Role role) {
    String email = role.name().toLowerCase() + (counter++) + "@example.com";
    enrollmentRepository.save(new Enrollment(userRepository.save(new User(email, "N")), course, role));
    return email;
  }

  private Module module(Course course, String title) {
    return moduleRepository.save(new Module(course, title));
  }

  private static VerifiedTutorContext courseContext(Course course) {
    return new VerifiedTutorContext(TutorPageType.COURSE, course.getId(), course.getTitle());
  }

  private static String all(List<TutorGroundingService.Material> material) {
    return material.stream().map(m -> m.title() + "|" + m.text()).reduce("", (a, b) -> a + "\n" + b);
  }

  @Test
  void includesTheCourseDescriptionAndItsPages() {
    Course course = course("DAT250", "Advanced software technologies");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Module week1 = module(course, "Week 1");
    contentService.createStaticPage(week1.getId(), "Syllabus", 0, "Weekly topics: JPA, REST", null, null, teacher);

    String text = all(grounding.gather(student, courseContext(course)));

    assertTrue(text.contains("Advanced software technologies"));
    assertTrue(text.contains("Weekly topics: JPA, REST"));
  }

  @Test
  void neverIncludesAssignmentsOrCodingExercises() {
    Course course = course("DAT250", "desc");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Module week1 = module(course, "Week 1");
    contentService.createStaticPage(week1.getId(), "Syllabus", 0, "Visible page text", null, null, teacher);
    contentService.createAssignment(week1.getId(), "Secret Lab", 1, "Instructions: build a REST API", null, false, false, teacher);
    contentService.createCodingExercise(week1.getId(), "Secret Kata", 2, "https://git.example/kata", null, false, false, teacher);

    List<TutorGroundingService.Material> material = grounding.gather(student, courseContext(course));

    String text = all(material);
    assertTrue(text.contains("Visible page text"));
    assertFalse(text.contains("Secret Lab"));
    assertFalse(text.contains("Instructions: build a REST API"));
    assertFalse(text.contains("Secret Kata"));
    assertFalse(text.contains("git.example"));
    assertTrue(material.stream().allMatch(m -> m.kind() != null
        && (m.kind() == TutorGroundingService.Material.Kind.COURSE || m.kind() == TutorGroundingService.Material.Kind.PAGE)));
  }

  @Test
  void leavesOutWhatTheStudentCannotSee() {
    Course course = course("DAT250", "desc");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Module week1 = module(course, "Week 1");
    var later = contentService.createStaticPage(week1.getId(), "Next week", 0, "Spoilers from next week", null, null, teacher);
    contentService.updateContentMetadata(later.getId(), "Next week", 0, LocalDateTime.now().plusDays(7), null, teacher);
    contentService.createStaticPage(week1.getId(), "Visible", 1, "Visible text", null, null, teacher);

    Module week2 = module(course, "Week 2");
    contentService.createStaticPage(week2.getId(), "Locked page", 0, "Locked module secret", null, null, teacher);
    moduleService.addDependency(week2.getId(), week1.getId(), teacher);

    String text = all(grounding.gather(student, courseContext(course)));

    assertTrue(text.contains("Visible text"));
    assertFalse(text.contains("Spoilers from next week"));
    assertFalse(text.contains("Locked module secret"));
  }

  @Test
  void neverIncludesQuizzes() {
    Course course = course("DAT250", "desc");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Module week1 = module(course, "Week 1");
    contentService.createQuiz(week1.getId(), "Midterm quiz with answers", 0, 1, null, teacher);

    String text = all(grounding.gather(student, courseContext(course)));

    assertFalse(text.contains("Midterm quiz"));
  }

  @Test
  void neverIncludesAnotherCoursesMaterial() {
    Course mine = course("DAT250", "my course");
    Course other = course("SECRET101", "secret course description");
    String otherTeacher = member(other, Role.TEACHER);
    String student = member(mine, Role.STUDENT);
    Module secretModule = module(other, "Secret week");
    contentService.createStaticPage(secretModule.getId(), "Secret page", 0, "Other course secrets", null, null, otherTeacher);

    String text = all(grounding.gather(student, courseContext(mine)));

    assertFalse(text.contains("Other course secrets"));
    assertFalse(text.contains("secret course description"));
  }

  @Test
  void keepsTheTotalSizeBounded() {
    Course course = course("DAT250", "desc");
    String teacher = member(course, Role.TEACHER);
    String student = member(course, Role.STUDENT);
    Module week1 = module(course, "Week 1");
    for (int i = 0; i < 10; i++) {
      contentService.createStaticPage(week1.getId(), "Page " + i, i, "x".repeat(10_000), null, null, teacher);
    }

    List<TutorGroundingService.Material> material = grounding.gather(student, courseContext(course));

    int total = material.stream().mapToInt(m -> m.text().length()).sum();
    assertTrue(total <= TutorGroundingService.MAX_TOTAL_CHARS + material.size() * 20,
        "total was " + total);
    assertTrue(material.stream().allMatch(m -> m.text().length() <= TutorGroundingService.MAX_ITEM_CHARS + 20));
    assertTrue(material.stream().anyMatch(m -> m.text().endsWith("[…truncated]")));
  }

  @Test
  void hasNothingToGroundTheDashboardIn() {
    Course course = course("DAT250", "desc");
    String student = member(course, Role.STUDENT);

    assertTrue(grounding.gather(student,
        new VerifiedTutorContext(TutorPageType.DASHBOARD, null, null)).isEmpty());
  }
}
