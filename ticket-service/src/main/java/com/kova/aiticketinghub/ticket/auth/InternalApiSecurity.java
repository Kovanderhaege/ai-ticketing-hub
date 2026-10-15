package com.kova.aiticketinghub.ticket.auth;

import com.kova.aiticketinghub.ticket.ai.AiConfigurationProperties;
import com.kova.aiticketinghub.ticketing.contracts.ai.InternalTokenVerifier;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;

@Configuration
public class InternalApiSecurity {
    @Bean @Order(1)
    SecurityFilterChain internalApiChain(HttpSecurity http, AiConfigurationProperties config) throws Exception {
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                if (InternalTokenVerifier.matches(config.getInternalToken(), request.getHeader("Authorization"))) {
                    var auth = new UsernamePasswordAuthenticationToken("analysis-service", null,
                            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                chain.doFilter(request, response);
            }
        };
        return http.securityMatcher("/internal/ai/**").csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/internal/ai/configuration").hasRole("INTERNAL")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> response.setStatus(401)))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
