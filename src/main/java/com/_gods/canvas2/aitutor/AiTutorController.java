package com._gods.canvas2.aitutor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-tutor")
public class AiTutorController {

  private final TutorAccessService accessService;

  public AiTutorController(TutorAccessService accessService) {
    this.accessService = accessService;
  }

  /** Tells the widget whether to show itself. Off unless the server says otherwise. */
  @GetMapping("/config")
  public TutorConfigResponse getConfig(
      @RequestParam(name = "course_id", required = false) Long courseId,
      Authentication authentication) {
    return new TutorConfigResponse(accessService.isEnabledFor(authentication.getName(), courseId));
  }
}
