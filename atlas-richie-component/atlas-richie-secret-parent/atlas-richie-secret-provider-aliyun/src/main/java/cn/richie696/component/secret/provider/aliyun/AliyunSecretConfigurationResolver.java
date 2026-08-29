/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

final class AliyunSecretConfigurationResolver {
    ResolvedAliyunConfiguration resolve(Environment environment, BootstrapSecretProperties bootstrap) {
        return resolve(environment, bootstrap, null);
    }

    ResolvedAliyunConfiguration resolve(
            Environment environment,
            BootstrapSecretProperties bootstrap,
            SecretBootstrapContext context) {
        String providerId = "aliyun";
        String prefix = AliyunSecretProperties.PREFIX;
        if (context != null && context.providerId() != null && !context.providerId().isBlank()) {
            providerId = context.providerId();
            if (context.configurationPrefix() != null && !context.configurationPrefix().isBlank()) {
                prefix = context.configurationPrefix();
            }
        } else {
        String active = bootstrap.getActiveProvider();
        if (active != null && !active.isBlank() && bootstrap.getProviders().containsKey(active)) {
            providerId = active;
            prefix = BootstrapSecretProperties.PREFIX + ".providers." + active;
        }
        }
        AliyunSecretProperties properties = Binder.get(environment)
                .bind(prefix, AliyunSecretProperties.class)
                .orElseGet(AliyunSecretProperties::new);
        validate(properties);
        return new ResolvedAliyunConfiguration(providerId, properties, hash(providerId, properties));
    }

    private void validate(AliyunSecretProperties properties) {
        required(properties.getRegion(), "Alibaba Cloud region");
        validateEndpoint(properties.getEndpoint());
        if (properties.getEndpoint() != null
                && properties.getEndpoint().getHost().contains(".cryptoservice.kms.aliyuncs.com")
                && properties.getCaFile() == null) {
            invalid("Alibaba Cloud dedicated KMS endpoint requires ca-file");
        }
        if (properties.getSecretsManager().getPathPrefix() != null) {
            safePath(properties.getSecretsManager().getPathPrefix(), "Alibaba Cloud Secret prefix");
        }
        for (Map.Entry<String, String> binding : properties.getKms().getKeyBindings().entrySet()) {
            safeLogical(binding.getKey(), "logical KMS key");
            required(binding.getValue(), "Alibaba Cloud KMS key binding");
        }
        for (Map.Entry<String, AliyunSecretProperties.SecretMapping> binding : properties.getSecrets().entrySet()) {
            safeLogical(binding.getKey(), "logical Secret");
            if (binding.getValue() == null) invalid("Alibaba Cloud Secret mapping must not be null");
            required(binding.getValue().getSecretName(), "Alibaba Cloud Secret name binding");
            if (binding.getValue().getField() != null) safeLogical(binding.getValue().getField(), "Secret field");
        }
    }

    private void validateEndpoint(URI endpoint) {
        if (endpoint == null) return;
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || (!endpoint.getPath().isEmpty() && !"/".equals(endpoint.getPath()))) {
            invalid("Alibaba Cloud KMS endpoint is invalid");
        }
        boolean loopback = "localhost".equalsIgnoreCase(endpoint.getHost())
                || "127.0.0.1".equals(endpoint.getHost()) || "::1".equals(endpoint.getHost());
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                && !("http".equalsIgnoreCase(endpoint.getScheme()) && loopback)) {
            invalid("Alibaba Cloud KMS endpoint must use HTTPS; HTTP is allowed only for loopback development");
        }
    }

    private String hash(String providerId, AliyunSecretProperties properties) {
        String canonical = providerId + "\n" + properties.getRegion() + "\n" + properties.getEndpoint()
                + "\n" + properties.getCaFile() + "\n" + properties.getSecretsManager().getPathPrefix()
                + "\n" + properties.getKms().getKeyBindings() + "\n" + properties.getSecrets().keySet();
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
        safeLogical(value, label);
        if (value.endsWith("/")) invalid(label + " is invalid");
    }
    private void required(String value, String label) {
        if (value == null || value.isBlank()) invalid(label + " is required");
    }
    private void invalid(String message) { throw new SecretConfigurationException("SEC-BOOT-003", message); }

    record ResolvedAliyunConfiguration(
            String providerId, AliyunSecretProperties properties, String configurationHash) { }
}
