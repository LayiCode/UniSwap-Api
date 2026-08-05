package com.olamide.UniSwap.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String ISSUER = "uniswap-api";
    private static final String AUDIENCE = "uniswap-app";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        // HS256 requires a key of at least 256 bits (32 bytes). Fail fast at
        // startup instead of minting trivially guessable tokens at runtime.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 bytes long for HS256. Set the JWT_SECRET environment variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername()) // we use email as the username
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // Parses AND verifies the signature in one call. Throws a JwtException
    // subclass for expired/malformed/tampered tokens — the caller decides how
    // to treat that (the auth filter treats it as "no valid login").
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Takes the already-parsed claims (the filter parses ONCE per request and
    // reuses them here) rather than re-parsing the token a second time.
    public boolean isTokenValid(Claims claims, UserDetails userDetails) {
        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date())
                && ISSUER.equals(claims.getIssuer())
                && claims.getAudience() != null
                && claims.getAudience().contains(AUDIENCE);
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }
}
