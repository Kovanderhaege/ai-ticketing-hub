package com.kova.aiticketinghub.ticket.ai;

import com.kova.aiticketinghub.ticketing.contracts.ai.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Objects;

@Service @Transactional(readOnly = true)
public class AiSettingsService {
    private final AiConfigurationRepository repository;
    private final AiConfigurationProperties properties;
    private final AiSecretVault vault;
    public AiSettingsService(AiConfigurationRepository repository, AiConfigurationProperties properties, AiSecretVault vault) {
        this.repository = repository; this.properties = properties; this.vault = vault;
    }
    public record View(boolean enabled, String provider, String model, String baseUrl, boolean apiKeyConfigured,
            String outputFormat, long timeoutMillis, long connectTimeoutMillis, int maxTokens, int maxAttempts,
            long version, Instant updatedAt, String updatedBy, boolean serviceLinkConfigured) {}

    public View view() {
        var entity = repository.findById(1L).orElse(null);
        var config = runtime(entity, false);
        boolean hasKey = entity == null ? !properties.getApiKey().isBlank()
                : entity.getEncryptedApiKey() != null && !entity.getEncryptedApiKey().isEmpty();
        return new View(config.enabled(), config.provider(), config.model(), config.baseUrl(), hasKey,
                config.outputFormat(), config.timeoutMillis(), config.connectTimeoutMillis(), config.maxTokens(), config.maxAttempts(),
                config.version(), entity == null ? null : entity.getUpdatedAt(), entity == null ? null : entity.getUpdatedBy(),
                InternalTokenVerifier.configured(properties.getInternalToken()));
    }
    public AiRuntimeConfiguration runtime() { return runtime(repository.findById(1L).orElse(null), true); }
    private AiRuntimeConfiguration runtime(AiConfigurationEntity entity, boolean includeSecret) {
        if (entity == null) {
            var defaults = properties.defaults();
            return new AiRuntimeConfiguration(defaults.enabled(), defaults.provider(), defaults.model(), defaults.baseUrl(),
                    includeSecret ? defaults.apiKey() : "", defaults.outputFormat(), defaults.timeoutMillis(),
                    defaults.connectTimeoutMillis(), defaults.maxTokens(), defaults.maxAttempts(), 0);
        }
        return new AiRuntimeConfiguration(entity.isEnabled(), entity.getProvider(), entity.getModel(), entity.getBaseUrl(),
                includeSecret ? vault.decrypt(entity.getEncryptedApiKey()) : "", entity.getOutputFormat(), entity.getTimeoutMillis(),
                entity.getConnectTimeoutMillis(), entity.getMaxTokens(), entity.getMaxAttempts(), entity.getRevision());
    }
    public void requireServiceLink() {
        if (!InternalTokenVerifier.configured(properties.getInternalToken()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La liaison entre les services doit être configurée par l’administrateur du serveur.");
    }
    public AiRuntimeConfiguration prepare(AiSettingsDraft draft, boolean requireModel) {
        requireServiceLink();
        return prepare(draft, repository.findById(1L).orElse(null), requireModel);
    }
    private AiRuntimeConfiguration prepare(AiSettingsDraft draft, AiConfigurationEntity entity, boolean requireModel) {
        var current = runtime(entity, false);
        String key = draft.apiKey() == null ? "" : draft.apiKey().strip();
        var proposed = new AiRuntimeConfiguration(draft.enabled(), draft.provider(), draft.model().strip(), draft.baseUrl().strip(),
                key, draft.outputFormat(), draft.timeoutMillis(), draft.connectTimeoutMillis(), draft.maxTokens(), draft.maxAttempts(), current.version());
        if (!draft.clearApiKey() && key.isEmpty() && sameEndpoint(current, proposed))
            key = runtime(entity, true).apiKey();
        if (draft.clearApiKey()) key = "";
        return new AiRuntimeConfiguration(proposed.enabled(), proposed.provider(), proposed.model(), proposed.baseUrl(), key,
                proposed.outputFormat(), proposed.timeoutMillis(), proposed.connectTimeoutMillis(),
                proposed.maxTokens(), proposed.maxAttempts(), proposed.version()).validate(requireModel);
    }
    private boolean sameEndpoint(AiRuntimeConfiguration before, AiRuntimeConfiguration after) {
        try { return Objects.equals(before.provider(), after.provider())
                && Objects.equals(before.effectiveBaseUrl(), after.effectiveBaseUrl()); }
        catch (IllegalArgumentException exception) { return false; }
    }
    @Transactional
    public View save(AiSettingsDraft draft, String editor) {
        requireServiceLink();
        var entity = repository.findLocked().orElseGet(AiConfigurationEntity::new);
        if (draft.version() != entity.getRevision())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La configuration a été modifiée. Rechargez-la avant d’enregistrer.");
        var config = prepare(draft, entity.getRowVersion() == null ? null : entity, draft.enabled());
        entity.setEnabled(config.enabled()); entity.setProvider(config.provider()); entity.setModel(config.model());
        entity.setBaseUrl(config.baseUrl()); entity.setEncryptedApiKey(vault.encrypt(config.apiKey()));
        entity.setOutputFormat(config.outputFormat()); entity.setTimeoutMillis(config.timeoutMillis());
        entity.setConnectTimeoutMillis(config.connectTimeoutMillis()); entity.setMaxTokens(config.maxTokens());
        entity.setMaxAttempts(config.maxAttempts()); entity.setRevision(entity.getRevision() + 1);
        entity.setUpdatedAt(Instant.now()); entity.setUpdatedBy(editor);
        repository.saveAndFlush(entity);
        return view();
    }
}
