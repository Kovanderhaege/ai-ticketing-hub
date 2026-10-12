package com.kova.aiticketinghub.notification.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final TicketSessionClient sessions;

    SessionAuthenticationFilter(TicketSessionClient sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sessionId = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(cookie -> "TICKETING_SESSION".equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
        if (sessionId != null) {
            // Only forward a servlet session identifier, never arbitrary cookie headers.
            if (!sessionId.matches("[A-Za-z0-9._-]{1,256}")) {
                response.setStatus(401);
                return;
            }
            try {
                var user = sessions.authenticate(sessionId);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        user.email(), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
                SecurityContextHolder.setContext(context);
            } catch (HttpClientErrorException exception) {
                response.setStatus(exception.getStatusCode().value() == 401 ? 401 : 503);
                return;
            } catch (RestClientException exception) {
                response.setStatus(503);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
