package com.kova.aiticketinghub.ticket.ai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class AiSecretVaultTests {
    @TempDir Path temp;
    @Test void generatedKeySurvivesAServiceRestartAndCiphertextIsRandomized() {
        var config = new AiConfigurationProperties();
        config.setEncryptionKeyFile(temp.resolve("private.key").toString());
        var first = new AiSecretVault(config);
        String encrypted = first.encrypt("provider-credential");
        assertThat(encrypted).isNotEqualTo(first.encrypt("provider-credential"));
        assertThat(new AiSecretVault(config).decrypt(encrypted)).isEqualTo("provider-credential");
    }
    @Test void tamperingAndWrongKeysFailClosed() {
        var config = new AiConfigurationProperties();
        config.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        String encrypted = new AiSecretVault(config).encrypt("secret");
        byte[] modified = Base64.getDecoder().decode(encrypted); modified[modified.length - 1] ^= 1;
        assertThatThrownBy(() -> new AiSecretVault(config).decrypt(Base64.getEncoder().encodeToString(modified)))
                .isInstanceOf(AiSecretVault.AiStorageException.class).hasMessageNotContaining("secret");
        config.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(() -> new AiSecretVault(config).decrypt(encrypted)).isInstanceOf(AiSecretVault.AiStorageException.class);
    }
    @Test void missingKeyIsNotRegeneratedDuringDecryption() {
        var config = new AiConfigurationProperties();
        config.setEncryptionKeyFile(temp.resolve("missing.key").toString());
        assertThatThrownBy(() -> new AiSecretVault(config).decrypt(Base64.getEncoder().encodeToString(new byte[30])))
                .isInstanceOf(AiSecretVault.AiStorageException.class);
        assertThat(Files.exists(Path.of(config.getEncryptionKeyFile()))).isFalse();
    }
}
