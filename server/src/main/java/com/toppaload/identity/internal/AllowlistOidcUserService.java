package com.toppaload.identity.internal;

import com.toppaload.audit.AuditRecorder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
class AllowlistOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    AllowlistOidcUserService(UserRepository userRepository, AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        return processOidcUser(userRequest, oidcUser);
    }

    OidcUser processOidcUser(OidcUserRequest userRequest, OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            Object emailAttr = oidcUser.getAttribute("email");
            if (emailAttr != null) {
                email = emailAttr.toString();
            }
        }
        if (email == null || email.isBlank()) {
            Object preferredUsername = oidcUser.getAttribute("preferred_username");
            if (preferredUsername != null) {
                email = preferredUsername.toString();
            }
        }
        if (email == null || email.isBlank()) {
            email = oidcUser.getName();
        }

        var userOpt = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            auditRecorder.record(
                    null,
                    email,
                    "LOGIN_DENIED",
                    "USER",
                    email,
                    null,
                    Map.of("reason", "NOT_IN_ALLOWLIST_OR_INACTIVE")
            );
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied", "User is not in allowlist or inactive", null));
        }

        UserEntity user = userOpt.get();
        String userNameAttributeName = userRequest != null && userRequest.getClientRegistration() != null
                && userRequest.getClientRegistration().getProviderDetails() != null
                && userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint() != null
                ? userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()
                : null;

        if (userNameAttributeName == null || userNameAttributeName.isBlank()) {
            userNameAttributeName = IdTokenClaimNames.SUB;
        }

        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                userNameAttributeName
        );
    }
}
