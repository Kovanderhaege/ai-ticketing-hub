package com.kova.aiticketinghub.ticket.auth;

import com.kova.aiticketinghub.ticket.messaging.TicketCreatedProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean TicketCreatedProducer producer;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void anonymousRequestsCannotReadOrCreateTickets() throws Exception {
        mvc.perform(get("/ticket")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ticket/00000000-0000-0000-0000-000000000001")).andExpect(status().isUnauthorized());
        mvc.perform(post("/ticket").contentType("application/json").content("{\"title\":\"private\",\"description\":\"private\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/ticket").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(producer);
    }

    @Test
    void registrationValidatesAndHashesPasswordsAndRejectsDuplicateEmails() throws Exception {
        register("Alice@Example.com", "long-password-123").andExpect(status().isCreated())
                .andExpect(jsonPath("email").value("alice@example.com"))
                .andExpect(jsonPath("passwordHash").doesNotExist());
        AppUser user = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("long-password-123");
        assertThat(passwords.matches("long-password-123", user.getPasswordHash())).isTrue();
        register("ALICE@example.com", "long-password-456").andExpect(status().isConflict());
        register("invalid-email", "long-password-123").andExpect(status().isBadRequest());
        register("bob@example.com", "short").andExpect(status().isBadRequest());
        register("bob@example.com", "é".repeat(40)).andExpect(status().isBadRequest());
    }

    @Test
    void loginRotatesSessionAndLogoutRevokesAccess() throws Exception {
        register("alice@example.com", "long-password-123").andExpect(status().isCreated());
        mvc.perform(post("/auth/login").with(csrf()).param("email", "alice@example.com").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").param("email", "alice@example.com").param("password", "long-password-123"))
                .andExpect(status().isUnauthorized());

        var csrfResult = mvc.perform(get("/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("headerName").value("X-CSRF-TOKEN")).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        String oldId = session.getId();
        String token = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/auth/login").session(session).header("X-CSRF-TOKEN", token)
                        .param("email", "ALICE@example.com").param("password", "long-password-123"))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(oldId);
        mvc.perform(get("/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("email").value("alice@example.com"));
        mvc.perform(get("/ticket").session(session)).andExpect(status().isOk());
        mvc.perform(post("/ticket").session(session).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        var created = mvc.perform(post("/ticket").session(session).with(csrf()).contentType("application/json")
                        .content("{\"title\":\"Authenticated ticket\",\"description\":\"A description\"}"))
                .andExpect(status().isOk()).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/ticket/" + id).session(session)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/ticket")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfAndCorsPreventCrossOriginWrites() throws Exception {
        mvc.perform(post("/auth/register").contentType("application/json")
                        .content("{\"email\":\"eve@example.com\",\"password\":\"long-password-123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(options("/ticket").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        mvc.perform(options("/ticket").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        assertThat(users.count()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/auth/register").with(csrf()).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
