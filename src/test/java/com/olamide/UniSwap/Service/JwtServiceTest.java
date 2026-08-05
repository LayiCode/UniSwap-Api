package com.olamide.UniSwap.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-longer-than-thirty-two-bytes-000";
    private static final String EMAIL = "olamide@student.lautech.edu.ng";

    private JwtService newService(long expirationMs) {
        return new JwtService(SECRET, expirationMs);
    }

    private UserDetails mockUser() {
        UserDetails user = mock(UserDetails.class);
        when(user.getUsername()).thenReturn(EMAIL);
        return user;
    }

    @Test
    void generateToken_thenExtractAllClaims_roundTripsSubjectAndIssuer() {
        JwtService service = newService(3600000);
        String token = service.generateToken(mockUser());

        Claims claims = service.extractAllClaims(token);

        assertThat(claims.getSubject()).isEqualTo(EMAIL);
        assertThat(claims.getIssuer()).isEqualTo("uniswap-api");
        assertThat(claims.getAudience()).contains("uniswap-app");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void isTokenValid_acceptsAFreshTokenForTheSameUser() {
        JwtService service = newService(3600000);
        UserDetails user = mockUser();
        String token = service.generateToken(user);

        Claims claims = service.extractAllClaims(token);

        assertThat(service.isTokenValid(claims, user)).isTrue();
    }

    @Test
    void isTokenValid_rejectsATokenMintedForADifferentUser() {
        JwtService service = newService(3600000);
        String token = service.generateToken(mockUser());

        UserDetails otherUser = mock(UserDetails.class);
        when(otherUser.getUsername()).thenReturn("someone-else@example.com");

        Claims claims = service.extractAllClaims(token);

        assertThat(service.isTokenValid(claims, otherUser)).isFalse();
    }

    @Test
    void expiredToken_isRejectedDuringParsing() {
        // Negative expiration ⇒ the token is already dead on arrival. jjwt
        // refuses to even parse an expired token, so rejection surfaces as a
        // JwtException at extractAllClaims — which the auth filter catches
        // and treats as "not logged in".
        JwtService service = newService(-1000);
        UserDetails user = mockUser();
        String token = service.generateToken(user);

        assertThatThrownBy(() -> service.extractAllClaims(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractAllClaims_throwsOnATamperedToken() {
        JwtService service = newService(3600000);
        String token = service.generateToken(mockUser());

        // Flip the last character of the signature — corrupts the token.
        String tampered = token.substring(0, token.length() - 1) +
                (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> service.extractAllClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractAllClaims_throwsOnGarbageToken() {
        assertThatThrownBy(() -> newService(3600000).extractAllClaims("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void constructor_rejectsASecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService("too-short", 3600000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
