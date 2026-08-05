package com.olamide.UniSwap.Config;

import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Service.JwtService;
import com.olamide.UniSwap.Service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// After a successful Google OAuth2 login the browser is sitting on the
// backend's /login/oauth2/code/google URL. This handler provisions the local
// account, mints our own JWT for it, and bounces the browser back to the
// Next.js frontend with the token in the query string for it to store.
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        User user = userService.findOrCreateOAuthUser(email, name);
        String token = jwtService.generateToken(new UserPrincipal(user));

        response.sendRedirect(frontendUrl + "/auth/callback?token=" + token);
    }
}
