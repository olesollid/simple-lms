package com._gods.canvas2.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  private final CanvasOAuth2UserService canvasOAuth2UserService;
  private final CanvasOidcUserService canvasOidcUserService;

  @Value("${app.frontend-url:http://localhost:5173}")
  private String frontendUrl;

  public SecurityConfig(CanvasOAuth2UserService canvasOAuth2UserService,
      CanvasOidcUserService canvasOidcUserService) {
    this.canvasOAuth2UserService = canvasOAuth2UserService;
    this.canvasOidcUserService = canvasOidcUserService;
  }

  @Bean
  SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(requests -> requests
            .requestMatchers("/error").permitAll()
            // Data and operational endpoints require login. Everything else is the bundled
            // frontend (static files and client-side routes), which must load before login so
            // it can show the sign-in links. OAuth2 login/logout endpoints are filters.
            .requestMatchers("/api/**", "/actuator/**").authenticated()
            .anyRequest().permitAll())
        .oauth2Login(oauth2 -> oauth2
            .defaultSuccessUrl(frontendUrl, true)
            .userInfoEndpoint(info -> info
                .userService(canvasOAuth2UserService)
                .oidcUserService(canvasOidcUserService)))
        .logout(logout -> logout
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
        .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
            PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
        .csrf(csrf -> csrf.spa())
        .cors(Customizer.withDefaults())
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(frontendUrl));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
