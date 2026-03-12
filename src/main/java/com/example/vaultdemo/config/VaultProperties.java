package com.example.vaultdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vault")
public record VaultProperties(
        String uri,
        String roleId,
        String secretId,
        String rootToken,
        Transit transit
) {
    public record Transit(
            String mountPath,
            String keyName
    ) {}
}
