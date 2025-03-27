package com.example.application.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter
 * <p>
 * This filter intercepts all incoming HTTP requests and validates JWT tokens.
 * It extracts the token from the Authorization header, validates it, and sets
 * the authentication in the security context if the token is valid.
 * <p>
 * This replaces the session-based authentication used in the original Oracle Forms
 * application with a stateless token-based approach suitable for RESTful APIs.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Constructor for JwtAuthenticationFilter.  Uses constructor injection for better testability
     * and to ensure dependencies are provided at instantiation.
     *
     * @param jwtTokenProvider The JWT token provider.
     * @param userDetailsService The user details service.
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Filters each request to check for and validate JWT tokens.
     *
     * @param request     The HTTP request
     * @param response    The HTTP response
     * @param filterChain The filter chain
     * @throws ServletException If a servlet exception occurs
     * @throws IOException      If an I/O exception occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            // Get JWT token from request
            String token = getJwtFromRequest(request);

            // Validate token and set authentication
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);

                // Load user details and create authentication token
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                // Set details and context
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (token != null) {
                // Token is present but invalid.  Log a warning.  Do NOT expose the reason for invalidity in the logs.
                logger.warn("JWT token is invalid but present.  Authentication not set.");
            }
        } catch (ExpiredJwtException ex) {
            logger.error("JWT token has expired", ex);
            request.setAttribute("expired", ex.getMessage());
            //Optionally, return an error immediately:
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("JWT token has expired");
            return; // Stop the filter chain.
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token", ex);
            request.setAttribute("invalid", ex.getMessage());
            //Optionally, return an error immediately:
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Invalid JWT token");
            return; // Stop the filter chain.
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
            //Optionally, return an error immediately:
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.getWriter().write("Authentication error");
            return; // Stop the filter chain.
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT token from the Authorization header.
     * <p>
     * The Authorization header is expected to follow the format: "Bearer <token>".
     *
     * @param request The HTTP request
     * @return The JWT token or null if not found
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX_LENGTH);
        }
        return null;
    }
}