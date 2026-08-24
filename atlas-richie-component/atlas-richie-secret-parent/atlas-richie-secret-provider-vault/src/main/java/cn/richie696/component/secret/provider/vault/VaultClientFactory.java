/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.JwtAuthentication;
import org.springframework.vault.authentication.JwtAuthenticationOptions;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Vault SDK 对象和认证生命周期的唯一创建点。
 */
final class VaultClientFactory {

    VaultSecretClient create(
            VaultSecretConfigurationResolver.ResolvedVaultConfiguration resolved,
            BootstrapSecretProperties bootstrapProperties) {
        VaultSecretProperties properties = resolved.properties();
        VaultEndpoint endpoint = VaultEndpoint.from(properties.getEndpoint());
        ClientOptions clientOptions = new ClientOptions(
                bootstrapProperties.getResilience().getConnectTimeout(),
                bootstrapProperties.getResilience().getReadTimeout());
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryFactory.create(
                clientOptions,
                sslConfiguration(properties));
        RestTemplateBuilder templateBuilder = RestTemplateBuilder.builder()
                .endpoint(endpoint)
                .requestFactory(requestFactory);
        VaultRequestIdCapture requestIdCapture = new VaultRequestIdCapture();
        templateBuilder.customizers(template ->
                template.getInterceptors().add(requestIdCapture.interceptor()));
        if (properties.getNamespace() != null && !properties.getNamespace().isBlank()) {
            templateBuilder.defaultHeader("X-Vault-Namespace", properties.getNamespace());
        }

        ThreadPoolTaskScheduler scheduler = null;
        VaultTemplate vaultTemplate = null;
        try {
            if (properties.getAuthentication().getType() == VaultSecretProperties.AuthenticationType.AGENT) {
                vaultTemplate = new VaultTemplate(templateBuilder);
            } else {
                RestTemplate authenticationTemplate = templateBuilder.build();
                ClientAuthentication authentication = authentication(
                        properties.getAuthentication(),
                        authenticationTemplate);
                SessionManager sessionManager;
                if (properties.getAuthentication().getType()
                        == VaultSecretProperties.AuthenticationType.KUBERNETES) {
                    scheduler = new ThreadPoolTaskScheduler();
                    scheduler.setPoolSize(1);
                    scheduler.setDaemon(true);
                    scheduler.setThreadNamePrefix("atlas-secret-vault-session-");
                    scheduler.initialize();
                    sessionManager = new LifecycleAwareSessionManager(
                            authentication,
                            scheduler,
                            authenticationTemplate);
                } else {
                    sessionManager = new SimpleSessionManager(authentication);
                }
                vaultTemplate = new VaultTemplate(templateBuilder, sessionManager);
            }
            VaultTemplate ownedTemplate = vaultTemplate;
            ThreadPoolTaskScheduler ownedScheduler = scheduler;
            return new VaultSecretClient(
                    resolved,
                    bootstrapProperties,
                    ownedTemplate,
                    requestIdCapture,
                    () -> close(ownedTemplate, ownedScheduler));
        } catch (RuntimeException failure) {
            if (vaultTemplate != null) {
                close(vaultTemplate, scheduler);
            } else if (scheduler != null) {
                scheduler.destroy();
            }
            throw failure;
        }
    }

    private ClientAuthentication authentication(
            VaultSecretProperties.Authentication properties,
            RestTemplate authenticationTemplate) {
        return switch (properties.getType()) {
            case KUBERNETES -> kubernetesAuthentication(properties, authenticationTemplate);
            case TOKEN -> () -> {
                char[] token = properties.getToken();
                try {
                    return VaultToken.of(token);
                } finally {
                    Arrays.fill(token, '\0');
                }
            };
            case TOKEN_FILE -> () -> VaultToken.of(readCredential(properties.getTokenFile(), "Vault token file"));
            case APPROLE -> appRoleAuthentication(properties, authenticationTemplate);
            case JWT -> jwtAuthentication(properties, authenticationTemplate);
            case AGENT -> throw new IllegalStateException("Agent authentication does not create a session manager");
        };
    }

    private ClientAuthentication jwtAuthentication(
            VaultSecretProperties.Authentication properties,
            RestTemplate authenticationTemplate) {
        String jwt = credential(properties.getJwt(), properties.getJwtFile(), "Vault JWT");
        JwtAuthenticationOptions options = JwtAuthenticationOptions.builder()
                .path(properties.getJwtPath())
                .role(properties.getJwtRole())
                .jwt(jwt)
                .build();
        return new JwtAuthentication(options, authenticationTemplate);
    }

    private ClientAuthentication appRoleAuthentication(
            VaultSecretProperties.Authentication properties,
            RestTemplate authenticationTemplate) {
        String roleId = credential(properties.getRoleId(), properties.getRoleIdFile(), "Vault AppRole role-id");
        String secretId = credential(properties.getSecretId(), properties.getSecretIdFile(), "Vault AppRole secret-id");
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .path(properties.getAppRolePath())
                .roleId(AppRoleAuthenticationOptions.RoleId.provided(roleId))
                .secretId(AppRoleAuthenticationOptions.SecretId.provided(secretId))
                .build();
        return new AppRoleAuthentication(options, authenticationTemplate);
    }

    private ClientAuthentication kubernetesAuthentication(
            VaultSecretProperties.Authentication properties,
            RestTemplate authenticationTemplate) {
        KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions.builder()
                .path(properties.getKubernetesPath())
                .role(properties.getRole())
                .jwtSupplier(() -> readCredential(
                        properties.getServiceAccountTokenFile(),
                        "Kubernetes service account token file"))
                .build();
        return new KubernetesAuthentication(options, authenticationTemplate);
    }

    private SslConfiguration sslConfiguration(VaultSecretProperties properties) {
        String trustStore = properties.getTls().getTrustStore();
        if (trustStore == null || trustStore.isBlank()) {
            return SslConfiguration.unconfigured();
        }
        char[] password = properties.getTls().getTrustStorePassword();
        try {
            return SslConfiguration.forTrustStore(new FileSystemResource(trustStore), password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private String readCredential(String file, String label) {
        try {
            String credential = Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
            if (credential.isEmpty()) {
                throw new SecretConfigurationException("SEC-AUTH-001", label + " is empty");
            }
            return credential;
        } catch (IOException exception) {
            throw new SecretConfigurationException("SEC-AUTH-001", label + " cannot be read", exception);
        }
    }

    private String credential(char[] inline, String file, String label) {
        if (file != null && !file.isBlank()) {
            return readCredential(file, label + " file");
        }
        try {
            if (inline == null || inline.length == 0) {
                throw new SecretConfigurationException("SEC-AUTH-001", label + " is empty");
            }
            return new String(inline);
        } finally {
            if (inline != null) {
                Arrays.fill(inline, '\0');
            }
        }
    }

    private void close(VaultTemplate template, ThreadPoolTaskScheduler scheduler) {
        try {
            template.destroy();
        } catch (Exception ignored) {
            // Context shutdown must not expose provider details or block remaining cleanup.
        } finally {
            if (scheduler != null) {
                scheduler.destroy();
            }
        }
    }
}
