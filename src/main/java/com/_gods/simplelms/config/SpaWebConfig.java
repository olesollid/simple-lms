package com._gods.simplelms.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled React app from classpath:/static and falls back to index.html for
 * client-side routes (e.g. /courses/1), so deep links and page refreshes work.
 * Backend paths are never rewritten.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new PathResourceResolver() {
          @Override
          protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
              return requested;
            }
            if (isBackendPath(resourcePath) || resourcePath.contains(".")) {
              return null;
            }
            Resource index = new ClassPathResource("static/index.html");
            return index.exists() ? index : null;
          }
        });
  }

  private static boolean isBackendPath(String path) {
    return path.startsWith("api/") || path.equals("api")
        || path.startsWith("oauth2/") || path.startsWith("login/")
        || path.startsWith("actuator");
  }
}
