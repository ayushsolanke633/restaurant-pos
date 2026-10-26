package com.restaurant.restaurantpos.security;

import com.restaurant.restaurantpos.service.CustomUserDetailsService;
import com.restaurant.restaurantpos.service.JwtService;
import com.restaurant.restaurantpos.service.TokenRevocationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final WaiterSessionManager waiterSessionManager;
    private final TokenRevocationService tokenRevocationService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this(jwtService, userDetailsService, null, null);
    }

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            WaiterSessionManager waiterSessionManager) {
        this(jwtService, userDetailsService, waiterSessionManager, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            WaiterSessionManager waiterSessionManager,
            TokenRevocationService tokenRevocationService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.waiterSessionManager = waiterSessionManager;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            String jti = jwtService.extractJti(jwt);
            if (tokenRevocationService != null && tokenRevocationService.isRevoked(jti)) {
                // Token revoked or check failed closed: do not authenticate
                filterChain.doFilter(request, response);
                return;
            }

            final String userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails) && isSessionValid(userDetails, jwt)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (org.springframework.security.authentication.DisabledException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"" + ex.getMessage() + "\"}");
            return;
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | com.restaurant.restaurantpos.exception.RevocationStorageException ex) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Service Unavailable\",\"message\":\"Authentication service temporarily unavailable\"}");
            return;
        } catch (Exception ex) {
            // Token invalid or expired - do not authenticate, allow request to proceed to SecurityEntryPoint (401)
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSessionValid(UserDetails userDetails, String jwt) {
        if (waiterSessionManager != null && userDetails instanceof CustomUserDetails cud) {
            if ("WAITER".equalsIgnoreCase(cud.getRoleName())) {
                return waiterSessionManager.isSessionActive(cud.getUserId(), jwt);
            }
        }
        return true;
    }
}
