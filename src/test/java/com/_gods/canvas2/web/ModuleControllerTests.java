package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com._gods.canvas2.model.Module;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.service.ModuleHasDependentsException;
import com._gods.canvas2.service.ModuleNotFoundException;
import com._gods.canvas2.service.ModuleService;

@WebMvcTest(ModuleController.class)
@AutoConfigureMockMvc(addFilters = false)
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

  @Test
  void createModuleReturns201() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = module(1L, course, "Week 1");
    when(moduleService.createModule(anyLong(), any())).thenReturn(module);

    mockMvc.perform(post("/api/courses/1/modules")
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

    mockMvc.perform(get("/api/courses/1/modules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Week 1"));
  }

  @Test
  void getModuleByIdReturns404WhenMissing() throws Exception {
    when(moduleService.getModuleById(999L)).thenThrow(new ModuleNotFoundException(999L));

    mockMvc.perform(get("/api/modules/999"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateModuleReturnsUpdatedModule() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module updated = module(1L, course, "Week 1 - Updated");
    when(moduleService.updateModuleTitle(any(), any())).thenReturn(updated);

    mockMvc.perform(put("/api/modules/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(REQUEST_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Week 1 - Updated"));
  }

  @Test
  void deleteModuleReturns204() throws Exception {
    mockMvc.perform(delete("/api/modules/1"))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteModuleWithDependentsReturns409() throws Exception {
    org.mockito.Mockito.doThrow(new ModuleHasDependentsException(1L))
        .when(moduleService).deleteModule(1L);

    mockMvc.perform(delete("/api/modules/1"))
        .andExpect(status().isConflict());
  }

  @Test
  void addDependencyReturns200() throws Exception {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = module(2L, course, "Week 2");
    when(moduleService.addDependency(2L, 1L)).thenReturn(module);

    mockMvc.perform(post("/api/modules/2/dependencies/1"))
        .andExpect(status().isOk());
  }

  @Test
  void addDependencyWithCycleReturns400() throws Exception {
    when(moduleService.addDependency(1L, 1L))
        .thenThrow(new IllegalArgumentException("A module cannot depend on itself"));

    mockMvc.perform(post("/api/modules/1/dependencies/1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeDependencyReturns204() throws Exception {
    mockMvc.perform(delete("/api/modules/2/dependencies/1"))
        .andExpect(status().isNoContent());
  }
}
