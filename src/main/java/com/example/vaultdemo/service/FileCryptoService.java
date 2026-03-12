package com.example.vaultdemo.service;

import com.example.vaultdemo.config.VaultProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FileCryptoService {

    private final VaultTemplate vaultTemplate;
    private final VaultProperties vaultProperties;
    private final ObjectMapper objectMapper;

    public FileCryptoService(VaultTemplate vaultTemplate,
                             VaultProperties vaultProperties,
                             ObjectMapper objectMapper) {
        this.vaultTemplate = vaultTemplate;
        this.vaultProperties = vaultProperties;
        this.objectMapper = objectMapper;
    }

    public Path encryptFile(Path inputFile, Path outputEnvelopeFile) throws IOException {
        byte[] fileBytes = Files.readAllBytes(inputFile);
        String base64Plaintext = Base64.getEncoder().encodeToString(fileBytes);

        String path = transitPath("encrypt/" + vaultProperties.transit().keyName());

        Map<String, Object> request = Map.of("plaintext", base64Plaintext);
        var response = vaultTemplate.write(path, request);

        if (response == null || response.getData() == null || response.getData().get("ciphertext") == null) {
            throw new IllegalStateException("Vault did not return ciphertext");
        }

        String ciphertext = response.getData().get("ciphertext").toString();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("originalFileName", inputFile.getFileName().toString());
        envelope.put("keyName", vaultProperties.transit().keyName());
        envelope.put("ciphertext", ciphertext);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputEnvelopeFile.toFile(), envelope);
        return outputEnvelopeFile;
    }

    public Path decryptFile(Path inputEnvelopeFile, Path outputFile) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope =
                objectMapper.readValue(inputEnvelopeFile.toFile(), Map.class);

        String ciphertext = (String) envelope.get("ciphertext");
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("Envelope file does not contain ciphertext");
        }

        String path = transitPath("decrypt/" + vaultProperties.transit().keyName());

        Map<String, Object> request = Map.of("ciphertext", ciphertext);
        var response = vaultTemplate.write(path, request);

        if (response == null || response.getData() == null || response.getData().get("plaintext") == null) {
            throw new IllegalStateException("Vault did not return plaintext");
        }

        String base64Plaintext = response.getData().get("plaintext").toString();
        byte[] fileBytes = Base64.getDecoder().decode(base64Plaintext);

        Files.write(outputFile, fileBytes);
        return outputFile;
    }

    private String transitPath(String suffix) {
        return vaultProperties.transit().mountPath() + "/" + suffix;
    }
}
