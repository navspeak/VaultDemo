package com.example.vaultdemo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EnvelopeCryptoService {

    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final VaultTransitService vaultTransitService;
    private final ObjectMapper objectMapper;

    public EnvelopeCryptoService(VaultTransitService vaultTransitService,
                                 ObjectMapper objectMapper) {
        this.vaultTransitService = vaultTransitService;
        this.objectMapper = objectMapper;
    }

    public String generateDekBase64() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_BITS);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate DEK", e);
        }
    }

    public RotationDemoResult demoRotation() {
        String dekBase64 = generateDekBase64();

        String wrappedV1 = vaultTransitService.wrapDek(dekBase64);

        vaultTransitService.rotateKey();

        String wrappedV2 = vaultTransitService.wrapDek(dekBase64);

        String unwrappedV1 = vaultTransitService.unwrapDek(wrappedV1);
        String unwrappedV2 = vaultTransitService.unwrapDek(wrappedV2);

        return new RotationDemoResult(dekBase64, wrappedV1, wrappedV2, unwrappedV1, unwrappedV2);
    }

    public Path encryptFile(Path inputFile, Path outputEnvelopeJson) {
        try {
            byte[] plainFileBytes = Files.readAllBytes(inputFile);

            String dekBase64 = generateDekBase64();
            byte[] dekBytes = Base64.getDecoder().decode(dekBase64);

            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec dek = new SecretKeySpec(dekBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encryptedFileBytes = cipher.doFinal(plainFileBytes);

            String wrappedDek = vaultTransitService.wrapDek(dekBase64);

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("originalFileName", inputFile.getFileName().toString());
            envelope.put("algorithm", "AES/GCM/NoPadding");
            envelope.put("ivBase64", Base64.getEncoder().encodeToString(iv));
            envelope.put("wrappedDek", wrappedDek);
            envelope.put("ciphertextBase64", Base64.getEncoder().encodeToString(encryptedFileBytes));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputEnvelopeJson.toFile(), envelope);
            return outputEnvelopeJson;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt file", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Path decryptFile(Path inputEnvelopeJson, Path outputFile) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(inputEnvelopeJson.toFile(), Map.class);

            String wrappedDek = requiredString(envelope, "wrappedDek");
            String ivBase64 = requiredString(envelope, "ivBase64");
            String ciphertextBase64 = requiredString(envelope, "ciphertextBase64");

            String dekBase64 = vaultTransitService.unwrapDek(wrappedDek);
            byte[] dekBytes = Base64.getDecoder().decode(dekBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encryptedFileBytes = Base64.getDecoder().decode(ciphertextBase64);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey dek = new SecretKeySpec(dekBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] plainBytes = cipher.doFinal(encryptedFileBytes);
            Files.write(outputFile, plainBytes);
            return outputFile;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt file", e);
        }
    }

    private String requiredString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value.toString();
    }

    public record RotationDemoResult(
            String dekBase64,
            String wrappedDekV1,
            String wrappedDekV2,
            String unwrappedDekV1,
            String unwrappedDekV2
    ) {}
}
