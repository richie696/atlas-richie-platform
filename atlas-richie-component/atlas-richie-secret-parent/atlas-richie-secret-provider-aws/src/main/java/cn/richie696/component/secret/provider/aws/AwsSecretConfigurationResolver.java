/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

final class AwsSecretConfigurationResolver {

    ResolvedAwsConfiguration resolve(Environment environment, BootstrapSecretProperties bootstrapProperties) {
        return resolve(environment, bootstrapProperties, null);
    }

    ResolvedAwsConfiguration resolve(
            Environment environment,
            BootstrapSecretProperties bootstrapProperties,
            SecretBootstrapContext context) {
        String providerId = "aws";
        String prefix = AwsSecretProperties.PREFIX;
        if (context != null && context.providerId() != null && !context.providerId().isBlank()) {
            providerId = context.providerId();
            if (context.configurationPrefix() != null && !context.configurationPrefix().isBlank()) {
                prefix = context.configurationPrefix();
            }
        } else {
        String active = bootstrapProperties.getActiveProvider();
        if (active != null && !active.isBlank() && bootstrapProperties.getProviders().containsKey(active)) {
            providerId = active;
            prefix = BootstrapSecretProperties.PREFIX + ".providers." + active;
        }
        }
        AwsSecretProperties properties = Binder.get(environment)
                .bind(prefix, AwsSecretProperties.class)
                .orElseGet(AwsSecretProperties::new);
        validate(properties);
        return new ResolvedAwsConfiguration(providerId, properties, hash(providerId, properties));
    }

    private void validate(AwsSecretProperties properties) {
        required(properties.getRegion(), "AWS region");
        if (properties.getAuthentication().getType() == AwsSecretProperties.AuthenticationType.PROFILE) {
            required(properties.getAuthentication().getProfileName(), "AWS profile name");
        }
        validateEndpoint(properties.getEndpoints().getSecretsManager(), "AWS Secrets Manager endpoint");
        validateEndpoint(properties.getEndpoints().getKms(), "AWS KMS endpoint");
        try {
            SigningAlgorithmSpec.fromValue(properties.getKms().getSigningAlgorithm());
        } catch (IllegalArgumentException exception) {
            invalid("AWS KMS signing algorithm is invalid");
        }
        if (properties.getSecretsManager().getPathPrefix() != null) {
            safePath(properties.getSecretsManager().getPathPrefix(), "AWS Secret path prefix");
        }
        for (Map.Entry<String, String> binding : properties.getKms().getKeyBindings().entrySet()) {
            safeLogical(binding.getKey(), "logical KMS key");
            required(binding.getValue(), "AWS KMS key binding");
        }
        properties.getKms().getVerificationKeyBindings().forEach((logical, keys) -> {
            safeLogical(logical, "logical KMS verification key");
            if (keys.isEmpty()) invalid("AWS KMS verification key history must not be empty");
            keys.forEach(key -> required(key, "AWS KMS historical verification key"));
        });
        for (Map.Entry<String, AwsSecretProperties.SecretMapping> binding : properties.getSecrets().entrySet()) {
            safeLogical(binding.getKey(), "logical Secret");
            if (binding.getValue() == null) {
                invalid("AWS Secret mapping must not be null");
            }
            required(binding.getValue().getSecretId(), "AWS Secret ID binding");
            if (binding.getValue().getField() != null) {
                safeLogical(binding.getValue().getField(), "AWS Secret field binding");
            }
        }
    }

    private void validateEndpoint(URI endpoint, String label) {
        if (endpoint == null) return;
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            invalid(label + " is invalid");
        }
        boolean loopback = "localhost".equalsIgnoreCase(endpoint.getHost())
                || "127.0.0.1".equals(endpoint.getHost())
                || "::1".equals(endpoint.getHost());
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                && !("http".equalsIgnoreCase(endpoint.getScheme()) && loopback)) {
            invalid(label + " must use HTTPS; HTTP is allowed only for loopback development");
        }
    }

    private String hash(String providerId, AwsSecretProperties properties) {
        String canonical = providerId + "\n" + properties.getRegion() + "\n"
                + properties.getAuthentication().getType() + "\n"
                + properties.getAuthentication().getProfileName() + "\n"
                + properties.getEndpoints().getSecretsManager() + "\n"
                + properties.getEndpoints().getKms() + "\n"
                + properties.getSecretsManager().getPathPrefix() + "\n"
                + properties.getKms().getKeyBindings() + "\n"
                + properties.getKms().getVerificationKeyBindings() + "\n"
                + properties.getKms().getSigningAlgorithm() + "\n" + properties.getSecrets().keySet();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private void safeLogical(String value, String label) {
        required(value, label);
        if (value.contains("..") || value.contains("://") || value.startsWith("/")) invalid(label + " is invalid");
    }
    private void safePath(String value, String label) {
        required(value, label);
        if (value.contains("..") || value.contains("://") || value.startsWith("/") || value.endsWith("/")) {
            invalid(label + " is invalid");
        }
    }
    private void required(String value, String label) {
        if (value == null || value.isBlank()) invalid(label + " is required");
    }
    private void invalid(String message) { throw new SecretConfigurationException("SEC-BOOT-003", message); }

    record ResolvedAwsConfiguration(String providerId, AwsSecretProperties properties, String configurationHash) { }
}
