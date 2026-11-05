package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Enrollment;
import com._gods.canvas2.model.Role;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.model.User;
import com._gods.canvas2.service.ConflictException;
import com._gods.canvas2.service.EnrollmentService;
import com._gods.canvas2.service.NotFoundException;
import com._gods.canvas2.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private EnrollmentService enrollmentService;

  private static final String REQUEST_BODY = """
      {"email":"student@example.com","name":"Student Name"}
      """;

  private User user() {
    return new User("student@example.com", "Student Name");
  }

  @Test
  void createUserReturns201() throws Exception {
    when(userService.createUser(any(), any())).thenReturn(user());

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("student@example.com"));
  }

  @Test
  void createDuplicateUserReturns409() throws Exception {
    when(userService.createUser(any(), any())).thenThrow(new ConflictException("exists"));

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void getAllUsersReturnsExistingUsers() throws Exception {
    when(userService.getAllUsers()).thenReturn(List.of(user()));

    mockMvc.perform(get("/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Student Name"));
  }

  @Test
  void getUserReturns404WhenMissing() throws Exception {
    when(userService.getUserByEmail("missing@example.com"))
        .thenThrow(new NotFoundException("User not found"));

    mockMvc.perform(get("/api/users/missing@example.com"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateUserReturnsUpdatedUser() throws Exception {
    when(userService.updateUserName(any(), any()))
        .thenReturn(new User("student@example.com", "New Name"));

    mockMvc.perform(put("/api/users/student@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"New Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("New Name"));
  }

  @Test
  void deleteUserReturns204() throws Exception {
    mockMvc.perform(delete("/api/users/student@example.com"))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteUserWithDependentsReturns409() throws Exception {
    doThrow(new ConflictException("has dependents"))
        .when(userService).deleteUser("student@example.com");

    mockMvc.perform(delete("/api/users/student@example.com"))
        .andExpect(status().isConflict());
  }

  @Test
  void getEnrollmentsByUserReturnsEnrollments() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    when(enrollmentService.getEnrollmentsByUser("student@example.com"))
        .thenReturn(List.of(new Enrollment(user(), course, Role.STUDENT)));

    mockMvc.perform(get("/api/users/student@example.com/enrollments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].courseTitle").value("Intro to Testing"))
        .andExpect(jsonPath("$[0].role").value("STUDENT"));
  }
}
