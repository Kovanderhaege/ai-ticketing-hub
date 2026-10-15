package com.kova.aiticketinghub.ticket.ai;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AiSecretVault {
    private final AiConfigurationProperties properties;
    private final SecureRandom random = new SecureRandom();
    public AiSecretVault(AiConfigurationProperties properties) { this.properties = properties; }

    private synchronized byte[] key(boolean create) throws Exception {
        byte[] bytes;
        if (!properties.getEncryptionKey().isBlank())
            bytes = Base64.getDecoder().decode(properties.getEncryptionKey());
        else {
            Path path = Path.of(properties.getEncryptionKeyFile()).toAbsolutePath();
            if (!Files.exists(path)) {
                if (!create) throw new IllegalStateException("Missing encryption key file");
                Files.createDirectories(path.getParent());
                byte[] generated = new byte[32]; random.nextBytes(generated);
                try {
                    if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
                        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                    else Files.createFile(path);
                    Files.writeString(path, Base64.getEncoder().encodeToString(generated), StandardCharsets.UTF_8);
                } catch (FileAlreadyExistsException race) { /* another application instance created the key */ }
            }
            bytes = Base64.getDecoder().decode(Files.readString(path).strip());
        }
        if (bytes.length != 32) throw new IllegalStateException("Invalid encryption key");
        return bytes;
    }

    public String encrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) { throw new AiStorageException(); }
    }
    public String decrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length < 28) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"), new GCMParameterSpec(128, payload, 0, 12));
            return new String(cipher.doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new AiStorageException(); }
    }
    public static class AiStorageException extends RuntimeException {
        public AiStorageException() { super("Le serveur ne peut pas accéder aux identifiants protégés de l’IA."); }
    }
}
