package com.olamide.UniSwap.Config;

import com.olamide.UniSwap.Service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Runs once per request. Looks for "Authorization: Bearer <token>", validates
// it, and if valid, tells Spring Security who's making this request so
// downstream @AuthenticationPrincipal / SecurityContext lookups work.
// A missing, expired, malformed, or tampered token is treated as "not logged
// in" — it never crashes the request, and protected endpoints respond 401 via
// the AuthenticationEntryPoint (see SecurityConfig).
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER);

        if (authHeader != null && authHeader.startsWith(PREFIX) && !authHeader.equals(PREFIX)) {
            String token = authHeader.substring(PREFIX.length());
            try {
                // Parse AND verify the signature exactly once per request;
                // the resulting claims are reused for the validity check.
                Claims claims = jwtService.extractAllClaims(token);

                boolean notAlreadyAuthenticated = SecurityContextHolder.getContext().getAuthentication() == null;
                if (notAlreadyAuthenticated) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());

                    if (jwtService.isTokenValid(claims, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                // Expired/malformed/tampered token, or the account was deleted.
                // Continue unauthenticated; the entry point turns this into a
                // 401 for protected endpoints instead of a 500.
                log.debug("Rejected invalid JWT: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
