package com.example.vaultdemo.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(VaultProperties.class)
public class VaultConfig {

    @Bean
    public VaultTemplate vaultTemplate(VaultProperties props) {

        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(props.uri()));

        ClientAuthentication authentication = clientAuthentication(props, endpoint);

        return new VaultTemplate(endpoint, authentication);
    }

    private ClientAuthentication clientAuthentication(VaultProperties props, VaultEndpoint endpoint) {

        AppRoleAuthenticationOptions options =
                AppRoleAuthenticationOptions.builder()
                        .roleId(AppRoleAuthenticationOptions.RoleId.provided(props.roleId()))
                        .secretId(AppRoleAuthenticationOptions.SecretId.provided(props.secretId()))
                        .build();

        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());

        restTemplate.setUriTemplateHandler(
                new org.springframework.web.util.DefaultUriBuilderFactory(props.uri())
        );

        return new AppRoleAuthentication(options, restTemplate);
    }
}
