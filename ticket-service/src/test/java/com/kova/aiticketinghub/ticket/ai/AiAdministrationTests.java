package com.kova.aiticketinghub.ticket.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kova.aiticketinghub.ticket.auth.*;
import com.kova.aiticketinghub.ticketing.contracts.ai.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.ai.internal-token=internal-test-token-at-least-32-characters",
    "app.ai.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.ai.provider=OLLAMA", "app.ai.model=", "app.ai.api-key="
})
@AutoConfigureMockMvc
class AiAdministrationTests {
    private static final String TOKEN = "internal-test-token-at-least-32-characters";
    private final ObjectMapper json = new ObjectMapper();
    @Autowired MockMvc mvc;
    @Autowired AiConfigurationRepository repository;
    @Autowired AiSettingsService service;
    @Autowired AiSecretVault vault;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean AiAnalysisGateway gateway;

    @BeforeEach void clear() { repository.deleteAll(); users.deleteAll(); reset(gateway); }
    private Map<String,Object> draft(String provider) {
        var values = new LinkedHashMap<String,Object>();
        values.put("enabled", true); values.put("provider", provider); values.put("model", "test-model"); values.put("baseUrl", "");
        values.put("apiKey", provider.equals("OLLAMA") ? "" : "test-provider-secret"); values.put("clearApiKey", false);
        values.put("outputFormat", "SCHEMA"); values.put("timeoutMillis", 30000); values.put("connectTimeoutMillis", 5000);
        values.put("maxTokens", 4096); values.put("maxAttempts", 2); values.put("version", 0);
        return values;
    }
    private org.springframework.test.web.servlet.ResultActions save(Map<String,Object> values) throws Exception {
        return mvc.perform(put("/admin/ai/configuration").with(user("admin@example.com").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(values)));
    }

    @Test void onlyAdministratorsCanReadChangeOrTestSettings() throws Exception {
        mvc.perform(get("/admin/ai/configuration")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/ai/configuration").with(user("normal@example.com"))).andExpect(status().isForbidden());
        for (String path : List.of("models", "test-analysis", "test-connection"))
            mvc.perform(post("/admin/ai/" + path).with(user("normal@example.com")).with(csrf())
                    .contentType("application/json").content(json.writeValueAsString(draft("OLLAMA")))).andExpect(status().isForbidden());
        mvc.perform(put("/admin/ai/configuration").with(user("normal@example.com")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(draft("OLLAMA")))).andExpect(status().isForbidden());
        mvc.perform(put("/admin/ai/configuration").with(user("admin@example.com").roles("ADMIN"))
                .contentType("application/json").content(json.writeValueAsString(draft("OLLAMA")))).andExpect(status().isForbidden());
        verifyNoInteractions(gateway);
    }
    @Test void saveEncryptsCredentialsAndPublicResponsesNeverReturnThem() throws Exception {
        save(draft("OPENAI")).andExpect(status().isOk()).andExpect(jsonPath("version").value(1))
                .andExpect(jsonPath("apiKeyConfigured").value(true)).andExpect(jsonPath("apiKey").doesNotExist())
                .andExpect(jsonPath("encryptedApiKey").doesNotExist()).andExpect(jsonPath("updatedBy").value("admin@example.com"));
        var stored = repository.findById(1L).orElseThrow();
        assertThat(stored.getEncryptedApiKey()).doesNotContain("test-provider-secret");
        assertThat(vault.decrypt(stored.getEncryptedApiKey())).isEqualTo("test-provider-secret");
        mvc.perform(get("/admin/ai/configuration").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-provider-secret"))));
        assertThat(service.runtime().apiKey()).isEqualTo("test-provider-secret");
    }
    @Test void blankCredentialKeepsExistingKeyOnlyForTheSameProviderAndServer() throws Exception {
        var values = draft("OPENAI");
        save(values).andExpect(status().isOk());
        values.put("version", 1); values.put("apiKey", ""); values.put("model", "another-model");
        save(values).andExpect(status().isOk()).andExpect(jsonPath("version").value(2));
        assertThat(service.runtime().apiKey()).isEqualTo("test-provider-secret");
        values.put("version", 2); values.put("baseUrl", "http://another-server:8000/v1");
        save(values).andExpect(status().isBadRequest());
        assertThat(service.runtime().baseUrl()).isEmpty();
        values.put("apiKey", "explicit-new-key");
        save(values).andExpect(status().isOk());
        assertThat(service.runtime().apiKey()).isEqualTo("explicit-new-key");
    }
    @Test void switchingToLocalProviderClearsThePreviousCloudCredential() throws Exception {
        save(draft("OPENAI")).andExpect(status().isOk());
        var values = draft("OLLAMA"); values.put("version", 1);
        save(values).andExpect(status().isOk()).andExpect(jsonPath("apiKeyConfigured").value(false));
        assertThat(service.runtime().apiKey()).isEmpty();
    }
    @Test void credentialsCanBeExplicitlyRemovedWhileDisablingAnalysis() throws Exception {
        save(draft("OPENAI")).andExpect(status().isOk());
        var values = draft("OPENAI"); values.put("version", 1); values.put("apiKey", "");
        values.put("clearApiKey", true); values.put("enabled", false); values.put("model", "");
        save(values).andExpect(status().isOk()).andExpect(jsonPath("enabled").value(false))
                .andExpect(jsonPath("apiKeyConfigured").value(false));
        assertThat(service.runtime().apiKey()).isEmpty();
    }
    @Test void staleSavesAreRejectedWithoutOverwritingSettings() throws Exception {
        var values = draft("OLLAMA"); save(values).andExpect(status().isOk());
        values.put("model", "stale-write");
        save(values).andExpect(status().isConflict()).andExpect(jsonPath("message").value(org.hamcrest.Matchers.containsString("Rechargez")));
        assertThat(service.runtime().model()).isEqualTo("test-model");
    }
    @Test void internalConfigurationRequiresItsOwnTokenEvenForAnAdministrator() throws Exception {
        save(draft("OPENAI")).andExpect(status().isOk());
        mvc.perform(get("/internal/ai/configuration")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/ai/configuration").with(user("admin@example.com").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/internal/ai/configuration").header("Authorization", "Bearer incorrect")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/ai/configuration").header("Authorization", "Bearer " + TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("apiKey").value("test-provider-secret"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
    @Test void testsUseTheDraftAndExistingKeyWithoutSavingChanges() throws Exception {
        save(draft("OPENAI")).andExpect(status().isOk());
        when(gateway.test(any(), eq(false))).thenReturn(new AiDiagnosticResult(true, "Connexion réussie", null));
        var values = draft("OPENAI"); values.put("model", "unsaved-model"); values.put("apiKey", ""); values.put("version", 1);
        mvc.perform(post("/admin/ai/test-connection").with(user("admin@example.com").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(values)))
                .andExpect(status().isOk()).andExpect(jsonPath("success").value(true));
        verify(gateway).test(argThat(config -> config.model().equals("unsaved-model") && config.apiKey().equals("test-provider-secret")), eq(false));
        assertThat(service.runtime().model()).isEqualTo("test-model");
        assertThat(service.runtime().version()).isEqualTo(1);
    }
    @Test void invalidInputsDoNotStoreOrEchoSecrets() throws Exception {
        var values = draft("OPENAI");
        for (String url : List.of("file:///tmp/secret", "https://user:password@server/v1", "https://server/v1?key=secret", "invalid-url")) {
            values.put("baseUrl", url);
            save(values).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-provider-secret"))));
        }
        values.put("baseUrl", ""); values.put("apiKey", "a".repeat(4097));
        save(values).andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }
    @Test void registrationCannotGrantAdministratorRights() throws Exception {
        mvc.perform(post("/auth/register").with(csrf()).contentType("application/json")
                .content("{\"email\":\"normal@example.com\",\"password\":\"long-password-123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("role").value("USER"));
        assertThat(users.findByEmail("normal@example.com").orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }
    @Test void bootstrapCreatesAnAdministratorWithAWorkingLogin() throws Exception {
        new AdminBootstrap(users, passwords, "Admin@Example.com", "admin-password-1234").run(null);
        assertThat(users.findByEmail("admin@example.com").orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
        var result = mvc.perform(post("/auth/login").with(csrf()).param("email", "admin@example.com").param("password", "admin-password-1234"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("role").value("ADMIN"));
        mvc.perform(get("/admin/ai/configuration").session(session)).andExpect(status().isOk());
    }
    @Test void bootstrapDoesNotPromoteAnAccountWithMismatchedCredentials() {
        users.saveAndFlush(new AppUser("admin@example.com", passwords.encode("existing-password-123")));
        assertThatThrownBy(() -> new AdminBootstrap(users, passwords, "admin@example.com", "different-password-123").run(null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmail("admin@example.com").orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }
}
