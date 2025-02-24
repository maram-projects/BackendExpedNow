package com.example.ExpedNow.security;

import com.example.ExpedNow.models.User;
import com.example.ExpedNow.services.UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OAuth2UserService extends DefaultOAuth2UserService {
    private final UserService userService;

    public OAuth2UserService(UserService userService) {
        this.userService = userService;
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
            // Process OAuth2 user data
            User user = userService.processOAuth2User(email, name);
        }

        return oauth2User;
    }
}