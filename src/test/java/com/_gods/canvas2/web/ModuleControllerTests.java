package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com._gods.canvas2.model.Course;
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.service.ModuleNotFoundException;
import com._gods.canvas2.service.ModuleService;

@WebMvcTest(ModuleController.class)
@AutoConfigureMockMvc
class ModuleControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ModuleService moduleService;

  private static final String REQUEST_BODY = """
      {"title":"Week 1"}
      """;

  private Module module(Long id, Course course, String title) {
    Module module = new Module(course, title);
    setId(module, id);
    return module;
  }

  private void setId(Module module, Long id) {
    try {
      java.lang.reflect.Field field = Module.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(module, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private RequestPostProcessor asTeacher() {
    RequestPostProcessor login = oauth2Login().oauth2User(new DefaultOAuth2User(
        Set.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of("email", "teacher@example.com"), "email"));
    return request -> csrf().postProcessRequest(login.postProcessRequest(request));
  }

  @Test
  void createModuleReturns201() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = module(1L, course, "Week 1");
    when(moduleService.createModule(anyLong(), any(), anyString())).thenReturn(module);

    mockMvc.perform(post("/api/courses/1/modules")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Week 1"));
  }

  @Test
  void getModulesByCourseReturnsExistingModules() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = module(1L, course, "Week 1");
    when(moduleService.getModulesByCourse(1L)).thenReturn(List.of(module));

    mockMvc.perform(get("/api/courses/1/modules").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Week 1"));
  }

  @Test
  void getModuleByIdReturns404WhenMissing() throws Exception {
    when(moduleService.getModuleById(999L)).thenThrow(new ModuleNotFoundException(999L));

    mockMvc.perform(get("/api/modules/999").with(asTeacher()))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateModuleReturnsUpdatedModule() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module updated = module(1L, course, "Week 1 - Updated");
    when(moduleService.updateModuleTitle(any(), any(), anyString())).thenReturn(updated);

    mockMvc.perform(put("/api/modules/1")
            .with(asTeacher())
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Week 1 - Updated"));
  }

  @Test
  void deleteModuleWithNoDependentsReturns200WithEmptyAffectedList() throws Exception {
    when(moduleService.deleteModule(any(), anyString())).thenReturn(List.of());

    mockMvc.perform(delete("/api/modules/1").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedModuleId").value(1))
        .andExpect(jsonPath("$.affectedModuleIds").isEmpty());
  }

  @Test
  void deleteModuleWithDependentsReturns200WithAffectedModules() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module dependent = module(2L, course, "B");
    when(moduleService.deleteModule(any(), anyString())).thenReturn(List.of(dependent));

    mockMvc.perform(delete("/api/modules/1").with(asTeacher()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedModuleId").value(1))
        .andExpect(jsonPath("$.affectedModuleIds[0]").value(2));
  }

  @Test
  void addDependencyReturns200() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = module(2L, course, "Week 2");
    when(moduleService.addDependency(any(), any(), anyString())).thenReturn(module);

    mockMvc.perform(post("/api/modules/2/dependencies/1").with(asTeacher()))
        .andExpect(status().isOk());
  }

  @Test
  void addDependencyWithCycleReturns400() throws Exception {
    when(moduleService.addDependency(any(), any(), anyString()))
        .thenThrow(new IllegalArgumentException("A module cannot depend on itself"));

    mockMvc.perform(post("/api/modules/1/dependencies/1").with(asTeacher()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeDependencyReturns204() throws Exception {
    mockMvc.perform(delete("/api/modules/2/dependencies/1").with(asTeacher()))
        .andExpect(status().isNoContent());
  }
}
