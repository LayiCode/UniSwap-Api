package com.olamide.UniSwap.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

// Registers the Google OAuth2 client ONLY when GOOGLE_CLIENT_ID is present in
// the environment. Declaring it in application.yaml unconditionally would make
// Spring Boot attempt to build a registration with an empty client id and fail
// to start; this bean is skipped entirely until real credentials are supplied.
// The same env var gates the oauth2Login wiring in SecurityConfig.
@Configuration
public class OAuth2ClientConfig {

    @Bean
    @ConditionalOnProperty(name = "GOOGLE_CLIENT_ID")
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret,
            @Value("${OAUTH_REDIRECT_URI:http://localhost:8080/login/oauth2/code/google}") String redirectUri
    ) {
        if (clientId == null || clientId.isBlank()) {
            return new InMemoryClientRegistrationRepository();
        }
        return new InMemoryClientRegistrationRepository(
                ClientRegistration.withRegistrationId("google")
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri(redirectUri)
                        .scope("openid", "profile", "email")
                        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                        .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                        .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                        .userNameAttributeName("sub")
                        .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                        .clientName("Google")
                        .build());
    }
}
