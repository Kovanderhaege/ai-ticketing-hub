package com.kova.aiticketinghub.notification.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

public class TicketSessionClient {
    private final RestClient client;

    public TicketSessionClient(RestClient client) {
        this.client = client;
    }

    public SessionUser authenticate(String sessionId) {
        SessionUser user = client.get().uri("/auth/me")
                .header(HttpHeaders.COOKIE, "TICKETING_SESSION=" + sessionId)
                .retrieve().body(SessionUser.class);
        if (user == null || user.id() == null || user.email() == null || user.email().isBlank()) {
            throw new RestClientException("Invalid authentication response");
        }
        return user;
    }

    public record SessionUser(UUID id, String email) {}
}
