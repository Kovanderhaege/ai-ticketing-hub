package com.kova.aiticketinghub.notification.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TicketSessionClientTests {
    @Test
    void forwardsOnlyTheSessionCookieAndReadsIdentity() {
        var builder = RestClient.builder().baseUrl("http://ticket-service");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ticket-service/auth/me"))
                .andExpect(header("Cookie", "TICKETING_SESSION=abc123"))
                .andRespond(withSuccess("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"email\":\"alice@example.com\"}", MediaType.APPLICATION_JSON));
        assertThat(new TicketSessionClient(builder.build()).authenticate("abc123").email()).isEqualTo("alice@example.com");
        server.verify();
    }

    @Test
    void rejectsAnEmptySuccessfulResponse() {
        var builder = RestClient.builder().baseUrl("http://ticket-service");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ticket-service/auth/me")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new TicketSessionClient(builder.build()).authenticate("abc123"))
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
        server.verify();
    }
}
