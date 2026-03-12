package com.example.vaultdemo.service;

import com.example.vaultdemo.config.VaultProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class VaultAuthService {

    private final VaultProperties props;
    private final RestTemplate restTemplate;

    public VaultAuthService(VaultProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    public String loginWithAppRole() {
        if (isBlank(props.roleId()) || isBlank(props.secretId())) {
            throw new IllegalStateException("VAULT_ROLE_ID / VAULT_SECRET_ID not set");
        }

        String url = props.uri() + "/v1/auth/approle/login";
        Map<String, Object> body = Map.of(
                "role_id", props.roleId(),
                "secret_id", props.secretId()
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
        Map<?, ?> responseBody = requireBody(response);
        Map<?, ?> auth = castMap(responseBody.get("auth"), "auth");
        Object clientToken = auth.get("client_token");

        if (clientToken == null || clientToken.toString().isBlank()) {
            throw new IllegalStateException("Vault did not return client_token");
        }
        return clientToken.toString();
    }

    public String requireRootToken() {
        if (isBlank(props.rootToken())) {
            throw new IllegalStateException("VAULT_ROOT_TOKEN not set");
        }
        return props.rootToken();
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}