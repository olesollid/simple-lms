package com._gods.canvas2.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com._gods.canvas2.model.StaticPage;
import com._gods.canvas2.model.Term;
import com._gods.canvas2.service.ContentNotFoundException;
import com._gods.canvas2.service.ContentService;
import com._gods.canvas2.service.ModuleNotFoundException;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContentControllerTests {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ContentService contentService;

  private Module module(Long id) {
    Course course = new Course("Intro to Testing", "desc", Term.AUTUMN, 2026);
    Module module = new Module(course, "Week 1");
    setField(module, "id", id);
    return module;
  }

  private StaticPage page(Long id, Module module, String title, String body) {
    StaticPage page = new StaticPage(module, title, 0, body);
    setField(page, "id", id);
    return page;
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

  @Test
  void createStaticPageReturns201() throws Exception {
    Module module = module(1L);
    StaticPage page = page(1L, module, "Syllabus", "Welcome!");
    when(contentService.createStaticPage(anyLong(), any(), anyInt(), any(), any(), any()))
        .thenReturn(page);

    mockMvc.perform(post("/api/modules/1/pages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Syllabus","position":0,"body":"Welcome!"}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Syllabus"))
        .andExpect(jsonPath("$.body").value("Welcome!"));
  }

  @Test
  void createStaticPageReturns404WhenModuleMissing() throws Exception {
    when(contentService.createStaticPage(anyLong(), any(), anyInt(), any(), any(), any()))
        .thenThrow(new ModuleNotFoundException(999L));

    mockMvc.perform(post("/api/modules/999/pages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Syllabus","position":0,"body":"Welcome!"}
                """))
        .andExpect(status().isNotFound());
  }

  @Test
  void getContentsByModuleReturnsExistingContents() throws Exception {
    Module module = module(1L);
    StaticPage page = page(1L, module, "Syllabus", "Welcome!");
    when(contentService.getContentsByModule(1L)).thenReturn(List.of(page));

    mockMvc.perform(get("/api/modules/1/contents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Syllabus"));
  }

  @Test
  void getContentByIdReturns404WhenMissing() throws Exception {
    when(contentService.getContentById(999L)).thenThrow(new ContentNotFoundException(999L));

    mockMvc.perform(get("/api/contents/999"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateContentReturnsUpdatedContent() throws Exception {
    Module module = module(1L);
    StaticPage updated = page(1L, module, "Updated", "Welcome!");
    when(contentService.updateContentMetadata(any(), any(), anyInt(), any(), any()))
        .thenReturn(updated);

    mockMvc.perform(put("/api/contents/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Updated","position":0}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated"));
  }

  @Test
  void updateStaticPageBodyReturnsUpdatedContent() throws Exception {
    Module module = module(1L);
    StaticPage updated = page(1L, module, "Syllabus", "Updated body");
    when(contentService.updateStaticPageBody(any(), any())).thenReturn(updated);

    mockMvc.perform(put("/api/contents/1/body")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"body":"Updated body"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("Updated body"));
  }

  @Test
  void deleteContentReturns204() throws Exception {
    mockMvc.perform(delete("/api/contents/1"))
        .andExpect(status().isNoContent());
  }
}
