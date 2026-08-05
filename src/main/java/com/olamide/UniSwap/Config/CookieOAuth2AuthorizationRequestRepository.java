package com.olamide.UniSwap.Config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

// Stores the OAuth2 authorization request in a cookie instead of the HTTP
// session, so the app can stay fully STATELESS (no JSESSIONID anywhere) while
// still running Google login. Mirrors the repository Spring Security ships in
// newer versions.
//
// The cookie holds a Java-serialized OAuth2AuthorizationRequest. It is
// HttpOnly, expires in 5 minutes, and the value is length/size capped before
// deserialization to blunt cookie-forgery attempts.
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int MAX_COOKIE_CHARS = 4096;
    private static final int COOKIE_EXPIRE_SECONDS = 5 * 60;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Assert.notNull(request, "request cannot be null");
        return deserialize(cookieValue(request));
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Assert.notNull(request, "request cannot be null");
        Assert.notNull(response, "response cannot be null");
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String serialized = serialize(authorizationRequest);
        Cookie cookie = new Cookie(COOKIE_NAME, serialized);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Assert.notNull(request, "request cannot be null");
        Assert.notNull(response, "response cannot be null");
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            Cookie cookie = new Cookie(COOKIE_NAME, null);
            cookie.setPath("/");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }
        return authorizationRequest;
    }

    private String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.length() > MAX_COOKIE_CHARS ? null : value;
            }
        }
        return null;
    }

    private static String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                out.writeObject(authorizationRequest);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to serialize authorization request", ex);
        }
    }

    private static OAuth2AuthorizationRequest deserialize(String cookieValue) {
        if (cookieValue == null) return null;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cookieValue);
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object value = in.readObject();
                return value instanceof OAuth2AuthorizationRequest ? (OAuth2AuthorizationRequest) value : null;
            }
        } catch (IOException | ClassNotFoundException ex) {
            return null;
        }
    }
}
