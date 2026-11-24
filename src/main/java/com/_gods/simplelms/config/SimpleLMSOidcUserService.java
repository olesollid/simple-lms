package com._gods.simplelms.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com._gods.simplelms.model.User;

/**
 * Resolves Google's OIDC login (requests the openid scope) into a provisioned {@link User} and a
 * principal whose name attribute is the account's normalized, verified email.
 */
@Service
public class SimpleLMSOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private static final Set<GrantedAuthority> AUTHORITIES = Set.of(new SimpleGrantedAuthority("ROLE_USER"));

  private final OidcUserService delegate = new OidcUserService();
  private final UserProvisioning userProvisioning;

  public SimpleLMSOidcUserService(UserProvisioning userProvisioning) {
    this.userProvisioning = userProvisioning;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) {
    OidcUser oidcUser = delegate.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    String email = resolveVerifiedEmail(oidcUser, registrationId);
    String name = oidcUser.getAttribute("name");

    User user = userProvisioning.provision(email, name);

    Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
    claims.put("email", user.getEmail());
    OidcUserInfo userInfo = new OidcUserInfo(claims);
    return new DefaultOidcUser(AUTHORITIES, oidcUser.getIdToken(), userInfo, "email");
  }

  private String resolveVerifiedEmail(OidcUser oidcUser, String registrationId) {
    String email = oidcUser.getEmail();
    if (email == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("no_email"), registrationId + " login did not provide an email");
    }

    if ("google".equals(registrationId) && !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("unverified_email"), "Google account email is not verified");
    }

    return email;
  }
}
