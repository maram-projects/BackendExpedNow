// 1. First, modify OAuth2UserService to break the circular dependency
package com.example.ExpedNow.services.auth2;

import com.example.ExpedNow.services.core.impl.UserServiceImpl;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationContext;

@Service
public class OAuth2UserService extends DefaultOAuth2UserService {
    // Use ApplicationContext instead of direct UserService dependency
    private final ApplicationContext applicationContext;

    public OAuth2UserService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        // Extract email and name from OAuth2User
        String email = null;
        String name = null;

        if (userRequest.getClientRegistration().getRegistrationId().equals("google")) {
            email = oauth2User.getAttribute("email");
            name = oauth2User.getAttribute("name");
        } else if (userRequest.getClientRegistration().getRegistrationId().equals("facebook")) {
            email = oauth2User.getAttribute("email");
            name = oauth2User.getAttribute("name");
        }

        if (email != null) {
            // Get UserService from application context when needed (lazy loading)
            UserServiceImpl userService = applicationContext.getBean(UserServiceImpl.class);
            userService.processOAuth2User(email, name);
        }

        return oauth2User;
    }
}