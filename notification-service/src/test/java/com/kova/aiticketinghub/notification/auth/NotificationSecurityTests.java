package com.kova.aiticketinghub.notification.auth;

import com.kova.aiticketinghub.notification.controller.NotificationController;
import com.kova.aiticketinghub.notification.service.NotificationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean TicketSessionClient sessions;
    @MockitoBean NotificationService notifications;

    @Test
    void anonymousCannotReadNotifications() throws Exception {
        mvc.perform(get("/notification")).andExpect(status().isUnauthorized());
        verifyNoInteractions(sessions, notifications);
    }

    @Test
    void validSessionCanReadNotifications() throws Exception {
        when(sessions.authenticate("valid-session")).thenReturn(new TicketSessionClient.SessionUser(UUID.randomUUID(), "alice@example.com"));
        when(notifications.getAll()).thenReturn(List.of());
        mvc.perform(get("/notification").cookie(new Cookie("TICKETING_SESSION", "valid-session"))).andExpect(status().isOk());
        verify(notifications).getAll();
    }

    @Test
    void expiredOrRevokedSessionsCannotReadNotifications() throws Exception {
        when(sessions.authenticate("expired")).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/notification").cookie(new Cookie("TICKETING_SESSION", "expired"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test
    void authenticationOutageNeverExposesNotifications() throws Exception {
        when(sessions.authenticate("session")).thenThrow(new ResourceAccessException("unavailable"));
        mvc.perform(get("/notification").cookie(new Cookie("TICKETING_SESSION", "session"))).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(notifications);
    }
}
