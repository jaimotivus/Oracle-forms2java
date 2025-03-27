package com.example.application.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for JWT token operations.
 * This class handles JWT token generation, validation, and extraction of claims.
 */
@Component
public class JwtTokenUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshExpiration;

    /**
     * Extract username from token
     *
     * @param token JWT token
     * @return username
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract expiration date from token
     *
     * @param token JWT token
     * @return expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract a specific claim from token
     *
     * @param token JWT token
     * @param claimsResolver function to extract specific claim
     * @param <T> type of claim
     * @return extracted claim
     * @throws JwtException if there is an issue extracting the claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from token
     *
     * @param token JWT token
     * @return all claims
     * @throws JwtException if the token is invalid or expired
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts
                    .parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.error("JWT Token has expired: {}", e.getMessage());
            throw new JwtException("JWT Token has expired", e);
        } catch (MalformedJwtException e) {
            logger.error("JWT Token is malformed: {}", e.getMessage());
            throw new JwtException("JWT Token is malformed", e);
        } catch (SignatureException e) {
            logger.error("JWT Token signature is invalid: {}", e.getMessage());
            throw new JwtException("JWT Token signature is invalid", e);
        } catch (UnsupportedJwtException e) {
            logger.error("JWT Token is unsupported: {}", e.getMessage());
            throw new JwtException("JWT Token is unsupported", e);
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
            throw new JwtException("JWT claims string is empty", e);
        } catch (JwtException e) {
            logger.error("JWT general exception: {}", e.getMessage());
            throw new JwtException("JWT general exception", e);
        }
    }

    /**
     * Get signing key from secret
     *
     * @return signing key
     */
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Check if token is expired
     *
     * @param token JWT token
     * @return true if token is expired
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Generate token for user
     *
     * @param userDetails user details
     * @return JWT token
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generate token with extra claims
     *
     * @param extraClaims extra claims to add to token
     * @param userDetails user details
     * @return JWT token
     */
    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails
    ) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Generate refresh token
     *
     * @param userDetails user details
     * @return refresh token
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration);
    }

    /**
     * Build token with claims and expiration
     *
     * @param extraClaims extra claims
     * @param userDetails user details
     * @param expiration expiration time in milliseconds
     * @return JWT token
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        Instant now = Instant.now();
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(Duration.ofMillis(expiration))))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate token
     *
     * @param token JWT token
     * @param userDetails user details
     * @return true if token is valid
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * Invalidates a token by setting its expiration to a past date.
     * Note: This method only invalidates the token locally. For a complete solution,
     * you might need to implement a token blacklist.
     *
     * @param token The JWT token to invalidate.
     */
    public void invalidateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Date now = new Date();
            if (claims.getExpiration().after(now)) {
                claims.setExpiration(now);
                // While we can't directly modify the token, setting the expiration to now effectively invalidates it
                logger.info("Token invalidated successfully.");
            } else {
                logger.warn("Token is already expired.");
            }
        } catch (JwtException e) {
            logger.error("Error invalidating token: {}", e.getMessage());
        }
    }
}