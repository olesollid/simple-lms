package com._gods.simplelms.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com._gods.simplelms.model.Course;
import com._gods.simplelms.model.Group;
import com._gods.simplelms.model.GroupMembership;
import com._gods.simplelms.model.Term;
import com._gods.simplelms.model.User;
import com._gods.simplelms.service.ConflictException;
import com._gods.simplelms.service.ForbiddenException;
import com._gods.simplelms.service.GroupService;
import com._gods.simplelms.web.GroupController;

@WebMvcTest(GroupController.class)
@AutoConfigureMockMvc
class GroupControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GroupService groupService;

  private Group group(Long id, String name) {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    setField(course, "id", 1L);
    Group group = new Group(course, name);
    setField(group, "id", id);
    return group;
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      Class<?> type = target.getClass();
      java.lang.reflect.Field field = null;
      while (type != null) {
        try {
          field = type.getDeclaredField(fieldName);
          break;
        } catch (NoSuchFieldException e) {
          type = type.getSuperclass();
        }
      }
      if (field == null) {
        throw new NoSuchFieldException(fieldName);
      }
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private RequestPostProcessor as(String email) {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("email", email), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  @Test
  void createGroupReturns201() throws Exception {
    Group group = group(1L, "Team A");
    when(groupService.createGroup(eq(1L), any(), anyString())).thenReturn(group);

    mockMvc.perform(post("/api/courses/1/groups")
            .with(as("teacher@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Team A"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Team A"));
  }

  @Test
  void createGroupReturns403ForStudent() throws Exception {
    when(groupService.createGroup(eq(1L), any(), anyString()))
        .thenThrow(new ForbiddenException("not a teacher"));

    mockMvc.perform(post("/api/courses/1/groups")
            .with(as("student@example.com"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Team A"}
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void getGroupsByCourseReturnsExistingGroups() throws Exception {
    Group group = group(1L, "Team A");
    when(groupService.getGroupsByCourse(eq(1L), anyString())).thenReturn(List.of(group));

    mockMvc.perform(get("/api/courses/1/groups").with(as("teacher@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Team A"));
  }

  @Test
  void getOwnGroupReturnsTheCallersGroup() throws Exception {
    Group group = group(1L, "Team A");
    when(groupService.getOwnGroup(eq(1L), anyString())).thenReturn(group);

    mockMvc.perform(get("/api/courses/1/groups/mine").with(as("student@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Team A"));
  }

  @Test
  void addMemberReturns201() throws Exception {
    Group group = group(1L, "Team A");
    User student = new User("student@example.com", "Student");
    GroupMembership membership = new GroupMembership(student, group);
    when(groupService.addMember(eq(1L), anyString(), anyString())).thenReturn(membership);

    mockMvc.perform(post("/api/groups/1/members/student@example.com")
            .with(as("teacher@example.com")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("student@example.com"));
  }

  @Test
  void addMemberReturns403ForStudent() throws Exception {
    when(groupService.addMember(anyLong(), anyString(), anyString()))
        .thenThrow(new ForbiddenException("not a teacher"));

    mockMvc.perform(post("/api/groups/1/members/student@example.com")
            .with(as("student@example.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  void addMemberReturns409WhenAlreadyInAGroup() throws Exception {
    when(groupService.addMember(anyLong(), anyString(), anyString()))
        .thenThrow(new ConflictException("already in a group"));

    mockMvc.perform(post("/api/groups/1/members/student@example.com")
            .with(as("teacher@example.com")))
        .andExpect(status().isConflict());
  }

  @Test
  void removeMemberReturns204() throws Exception {
    mockMvc.perform(delete("/api/groups/1/members/student@example.com")
            .with(as("teacher@example.com")))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteGroupReturns204() throws Exception {
    mockMvc.perform(delete("/api/groups/1").with(as("teacher@example.com")))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteGroupReturns403ForStudent() throws Exception {
    org.mockito.Mockito.doThrow(new ForbiddenException("teachers only"))
        .when(groupService).deleteGroup(anyLong(), anyString());

    mockMvc.perform(delete("/api/groups/1").with(as("student@example.com")))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteGroupReturns409WhenItHasSubmissions() throws Exception {
    org.mockito.Mockito.doThrow(new ConflictException("has submissions"))
        .when(groupService).deleteGroup(anyLong(), anyString());

    mockMvc.perform(delete("/api/groups/1").with(as("teacher@example.com")))
        .andExpect(status().isConflict());
  }
}
