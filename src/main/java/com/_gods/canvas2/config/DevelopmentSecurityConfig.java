package com._gods.canvas2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class DevelopmentSecurityConfig {

  @Bean
  @Order(1)
  SecurityFilterChain h2ConsoleSecurity(HttpSecurity http) throws Exception {
    return http.securityMatcher("/h2-console/**")
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
        .build();
  }

  @Bean
  SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}
