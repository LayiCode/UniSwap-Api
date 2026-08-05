package com.olamide.UniSwap.Config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2FailureHandler;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    // Comma-separated list of frontend origins allowed to call the API.
    // Override with CORS_ALLOWED_ORIGINS for any real deployment.
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://127.0.0.1:3000}")
    private String corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            OAuth2AuthenticationSuccessHandler oauth2SuccessHandler,
            OAuth2AuthenticationFailureHandler oauth2FailureHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.oauth2FailureHandler = oauth2FailureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // stateless JWT API, no browser form/cookie sessions to protect
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Order matters: the seller-private my-listings path must be
                        // matched BEFORE the broad GET /api/products/** permitAll rule
                        // below — otherwise the /** wildcard would let anyone read
                        // another user's private inventory (and NPE trying to read a
                        // null principal).
                        .requestMatchers(HttpMethod.GET, "/api/products/my-listings").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // Health checks from load balancers / uptime monitors
                        // must be reachable without auth.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Google OAuth2 login is only wired when a client id/secret exist; the
        // frontend learns whether it's available from GET /api/auth/config.
        // Leaving it out entirely (rather than registering a no-op) keeps the
        // security filter chain from demanding a ClientRegistrationRepository
        // that doesn't exist in a no-Google dev setup.
        if (googleClientId != null && !googleClientId.isBlank()) {
            http.oauth2Login(oauth -> oauth
                    // The OAuth2 authorization request is normally parked in the
                    // HTTP session; we run stateless, so keep it in a cookie the
                    // callback can present back instead.
                    .authorizationEndpoint(authEndpoint -> authEndpoint
                            .authorizationRequestRepository(new CookieOAuth2AuthorizationRequestRepository()))
                    .successHandler(oauth2SuccessHandler)
                    .failureHandler(oauth2FailureHandler));
        }

        return http.build();
    }

    // Unauthenticated requests to protected endpoints get a clean 401 JSON
    // body (same ApiErrorResponse shape as everything else), not Spring's
    // default empty 403 or a whitelabel page. Built by hand to avoid coupling
    // this class to a specific JSON binder.
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            String body = "{\"timestamp\":\"" + LocalDateTime.now() + "\"," +
                    "\"status\":401,\"error\":\"Unauthorized\"," +
                    "\"message\":\"Authentication required\"}";

            response.getWriter().write(body);
        };
    }

    // Login is implemented manually in UserService (it never goes through an
    // AuthenticationManager), so we deliberately do NOT register a
    // DaoAuthenticationProvider bean — doing so would suppress Spring
    // Security's default UserDetailsService wiring and emit a confusing
    // startup warning about an unused provider.

    // CORS for the Next.js frontend. Allowed origins can be tightened for a
    // specific deployment via CORS_ALLOWED_ORIGINS.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsAllowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
