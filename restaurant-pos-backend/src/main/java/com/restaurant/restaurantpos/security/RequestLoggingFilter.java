package com.restaurant.restaurantpos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/api/") && log.isDebugEnabled()) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String username = (auth != null && auth.getName() != null) ? auth.getName() : "ANONYMOUS";
                String authorities = (auth != null && auth.getAuthorities() != null) ? auth.getAuthorities().toString() : "NONE";

                // Routine request-debug log: strictly restricted to DEBUG level.
                // Excludes Authorization headers, tokens, passwords, and sensitive request payloads.
                log.debug("[BACKEND REQUEST DEBUG] {} {} -> Status: {} | User: {} | Authorities: {}",
                        request.getMethod(), path, response.getStatus(), username, authorities);
            }
        }
    }
}
