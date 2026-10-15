package com.kova.aiticketinghub.analysis.config;

import com.kova.aiticketinghub.ticketing.contracts.ai.InternalTokenVerifier;
import org.springframework.context.annotation.*;
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
public class AnalysisInternalSecurity {
    @Bean
    SecurityFilterChain analysisSecurityChain(HttpSecurity http, AnalysisProperties config) throws Exception {
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                if (InternalTokenVerifier.matches(config.getInternalToken(), request.getHeader("Authorization")))
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            "ticket-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
                chain.doFilter(request, response);
            }
        };
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/internal/ai/**").hasRole("INTERNAL").anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> response.setStatus(401)))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
