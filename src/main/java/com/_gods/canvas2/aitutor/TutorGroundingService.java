package com._gods.canvas2.aitutor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com._gods.canvas2.model.Assignment;
import com._gods.canvas2.model.CodingExercise;
import com._gods.canvas2.model.Content;
import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.repository.CourseRepository;
import com._gods.canvas2.service.ContentService;
import com._gods.canvas2.service.ModuleService;

/**
 * Collects the published course material the tutor may ground its answers in. It reads only what
 * this student can already see in the app: contents come through {@link ContentService}, which
 * hides locked modules and content outside its visibility window. Quizzes are left out entirely
 * so questions and correct answers can never reach the model.
 */
@Service
@Transactional(readOnly = true)
public class TutorGroundingService {

  /** A piece of published course material the tutor may draw on. Always data, never instructions. */
  public record Material(Kind kind, String title, String text) {
    public enum Kind { COURSE, ASSIGNMENT, PAGE }
  }

  static final int MAX_TOTAL_CHARS = 12_000;
  static final int MAX_ITEM_CHARS = 4_000;
  static final int MAX_CURRENT_ASSIGNMENT_CHARS = 6_000;

  private final CourseRepository courseRepository;
  private final ModuleService moduleService;
  private final ContentService contentService;

  public TutorGroundingService(CourseRepository courseRepository, ModuleService moduleService,
      ContentService contentService) {
    this.courseRepository = courseRepository;
    this.moduleService = moduleService;
    this.contentService = contentService;
  }

  /** Material for an already verified context, most relevant first, within a fixed size budget. */
  public List<Material> gather(String email, VerifiedTutorContext context) {
    if (context.courseId() == null) {
      return List.of();
    }
    Budget budget = new Budget(MAX_TOTAL_CHARS);
    List<Material> material = new ArrayList<>();

    if (context.assignmentId() != null) {
      Content current = contentService.getContentById(context.assignmentId(), email);
      budget.add(material, Material.Kind.ASSIGNMENT, current.getTitle(),
          describe(current), MAX_CURRENT_ASSIGNMENT_CHARS);
    }

    Course course = courseRepository.findById(context.courseId()).orElse(null);
    if (course != null && !course.getDescription().isBlank()) {
      budget.add(material, Material.Kind.COURSE, course.getTitle(), course.getDescription(),
          MAX_ITEM_CHARS);
    }

    for (Module module : moduleService.getModulesByCourse(context.courseId(), email)) {
      for (Content content : contentService.getContentsByModule(module.getId(), email)) {
        if (budget.exhausted()) {
          return material;
        }
        if (content.getId().equals(context.assignmentId())) {
          continue;
        }
        if (content instanceof StaticPage page) {
          budget.add(material, Material.Kind.PAGE, page.getTitle(), page.getBody(), MAX_ITEM_CHARS);
        } else if (content instanceof Assignment || content instanceof CodingExercise) {
          budget.add(material, Material.Kind.ASSIGNMENT, content.getTitle(),
              describe(content), MAX_ITEM_CHARS);
        }
        // Quizzes are skipped on purpose.
      }
    }
    return material;
  }

  private static String describe(Content content) {
    if (content instanceof Assignment assignment) {
      return assignment.getInstructions();
    }
    if (content instanceof CodingExercise exercise) {
      return "Coding exercise. Starter repository: " + exercise.getRepositoryUrl();
    }
    return "";
  }

  /** Keeps the total prompt material bounded however much the course contains. */
  private static final class Budget {
    private int remaining;

    Budget(int total) {
      this.remaining = total;
    }

    boolean exhausted() {
      return remaining <= 0;
    }

    void add(List<Material> into, Material.Kind kind, String title, String text, int itemMax) {
      if (exhausted() || text == null || text.isBlank()) {
        return;
      }
      String trimmed = text.strip();
      int limit = Math.min(itemMax, remaining);
      if (trimmed.length() > limit) {
        trimmed = trimmed.substring(0, limit) + " […truncated]";
      }
      remaining -= Math.min(text.strip().length(), limit);
      into.add(new Material(kind, title, trimmed));
    }
  }
}
