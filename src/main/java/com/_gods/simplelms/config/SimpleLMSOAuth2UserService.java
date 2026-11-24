package com._gods.simplelms.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com._gods.simplelms.model.User;

/**
 * Resolves GitHub's plain OAuth2 login (no openid scope) into a provisioned {@link User} and a
 * principal whose name attribute is the account's normalized, verified email.
 */
@Service
class SimpleLMSOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private static final Set<GrantedAuthority> AUTHORITIES = Set.of(new SimpleGrantedAuthority("ROLE_USER"));

  private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
  private final UserProvisioning userProvisioning;
  private final RestClient restClient = RestClient.create();

  public SimpleLMSOAuth2UserService(UserProvisioning userProvisioning) {
    this.userProvisioning = userProvisioning;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) {
    OAuth2User githubUser = delegate.loadUser(userRequest);

    String email = resolveVerifiedEmail(githubUser, userRequest.getAccessToken().getTokenValue());
    String name = githubUser.getAttribute("name");
    if (name == null) {
      name = githubUser.getAttribute("login");
    }

    User user = userProvisioning.provision(email, name);

    Map<String, Object> attributes = new HashMap<>(githubUser.getAttributes());
    attributes.put("email", user.getEmail());
    return new DefaultOAuth2User(AUTHORITIES, attributes, "email");
  }

  private String resolveVerifiedEmail(OAuth2User githubUser, String accessToken) {
    String email = githubUser.getAttribute("email");
    if (email != null) {
      return email;
    }

    List<Map<String, Object>> emails = restClient.get()
        .uri("https://api.github.com/user/emails")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .retrieve()
        .body(List.class);

    if (emails != null) {
      for (Map<String, Object> entry : emails) {
        if (Boolean.TRUE.equals(entry.get("primary")) && Boolean.TRUE.equals(entry.get("verified"))) {
          return (String) entry.get("email");
        }
      }
    }

    throw new OAuth2AuthenticationException(
        new OAuth2Error("no_verified_email"),
        "GitHub account has no verified, accessible email address");
  }
}
