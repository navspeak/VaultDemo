package com.example.vaultdemo.service;

import com.example.vaultdemo.config.VaultProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class VaultTransitService {

    private final VaultProperties props;
    private final RestTemplate restTemplate;
    private final VaultAuthService authService;

    public VaultTransitService(VaultProperties props,
                               RestTemplate restTemplate,
                               VaultAuthService authService) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.authService = authService;
    }

    public String wrapDek(String base64Dek) {
        String appToken = authService.loginWithAppRole();
        String url = transitUrl("/encrypt/" + props.transit().keyName());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("plaintext", base64Dek),
                headers(appToken)
        );

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map<?, ?> body = requireBody(response);
        Map<?, ?> data = castMap(body.get("data"), "data");
        Object ciphertext = data.get("ciphertext");

        if (ciphertext == null) {
            throw new IllegalStateException("Vault did not return ciphertext");
        }
        return ciphertext.toString();
    }

    public String unwrapDek(String wrappedDek) {
        String appToken = authService.loginWithAppRole();
        String url = transitUrl("/decrypt/" + props.transit().keyName());
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("ciphertext", wrappedDek),
                headers(appToken)
        );

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map<?, ?> body = requireBody(response);
        Map<?, ?> data = castMap(body.get("data"), "data");
        Object plaintext = data.get("plaintext");

        if (plaintext == null) {
            throw new IllegalStateException("Vault did not return plaintext");
        }
        return plaintext.toString();
    }

    public void rotateKey() {
        String rootToken = authService.requireRootToken();
        String url = transitUrl("/keys/" + props.transit().keyName() + "/rotate");
        HttpEntity<String> entity = new HttpEntity<>(null, headers(rootToken));
        restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
    }

    public Map<?, ?> keyMetadata() {
        String rootToken = authService.requireRootToken();
        String url = transitUrl("/keys/" + props.transit().keyName());
        HttpEntity<String> entity = new HttpEntity<>(null, headers(rootToken));

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<?, ?> body = requireBody(response);
        return castMap(body.get("data"), "data");
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String transitUrl(String suffix) {
        return props.uri() + "/v1/" + props.transit().mountPath() + suffix;
    }

    private Map<?, ?> requireBody(ResponseEntity<Map> response) {
        if (response.getBody() == null) {
            throw new IllegalStateException("Vault returned empty response body");
        }
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> castMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected map field: " + field);
        }
        return map;
    }
}
