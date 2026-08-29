package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;

/** Keeps optional provider modules small while preserving one validation contract. */
public final class RemoteProviderModuleSupport {
    private RemoteProviderModuleSupport() { }

    public static <T extends RemoteProviderProperties> T bind(
            ConfigurableEnvironment environment, BootstrapSecretProperties bootstrap,
            String prefix, Class<T> type) {
        return bind(environment, bootstrap, prefix, type, null);
    }

    public static <T extends RemoteProviderProperties> T bind(
            ConfigurableEnvironment environment, BootstrapSecretProperties bootstrap,
            String prefix, Class<T> type, SecretBootstrapContext context) {
        String effective = prefix;
        if (context != null && context.configurationPrefix() != null
                && !context.configurationPrefix().isBlank()) {
            effective = context.configurationPrefix();
        } else {
            String active = bootstrap.getActiveProvider();
            if (active != null && !active.isBlank() && bootstrap.getProviders().containsKey(active)) {
                effective = BootstrapSecretProperties.PREFIX + ".providers." + active;
            }
        }
        return Binder.get(environment).bind(effective, type).orElseGet(() -> {
            try { return type.getConstructor().newInstance(); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException("Provider properties cannot be created", exception); }
        });
    }

    public static void validate(String type, RemoteProviderProperties properties) {
        validate(type, properties, Set.of(SecretCapability.SECRET_READ,
                SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP));
    }

    public static void validate(
            String type,
            RemoteProviderProperties properties,
            Set<SecretCapability> capabilities) {
        if (capabilities.contains(SecretCapability.SECRET_READ)) {
            validateEndpoint(type + " Secret", properties.getSecretEndpoint());
        }
        if (capabilities.contains(SecretCapability.KEY_WRAP)
                || capabilities.contains(SecretCapability.KEY_UNWRAP)) {
            validateEndpoint(type + " KMS", properties.getKmsEndpoint());
        }
        if (capabilities.isEmpty()) invalid(type + " capabilities must not be empty");
        URI endpoint = properties.getEndpoint();
        if (endpoint != null) validateEndpoint(type, endpoint);
        RemoteProviderProperties.Authentication authentication = properties.getAuthentication();
        if (authentication == null || authentication.getType() == null) invalid(type + " authentication type is required");
        if ("volcengine".equalsIgnoreCase(type)) {
            required(properties.getRegion(), type + " region");
            required(properties.getNamespace(), type + " keyring namespace");
        }
        switch (authentication.getType()) {
            case BEARER_TOKEN -> required(authentication.getToken().length == 0 ? null : "configured", type + " bearer token");
            case TOKEN_FILE -> required(authentication.getTokenFile(), type + " token file");
            case WORKLOAD_IDENTITY_TOKEN_FILE -> required(authentication.getWorkloadIdentityTokenFile(), type + " workload identity token file");
            case ACCESS_KEY -> {
                required(authentication.getAccessKeyId(), type + " access key id");
                required(authentication.getAccessKeySecret().length == 0 ? null : "configured", type + " access key secret");
            }
            case NONE -> { }
        }
        validateWire(type, properties.getWire());
        if (authentication.getSignature() != RemoteProviderProperties.RequestSignature.NONE) {
            required(authentication.getAccessKeyId(), type + " signing access key id");
            required(authentication.getAccessKeySecret().length == 0 ? null : "configured", type + " signing access key secret");
            if (authentication.getSignature() == RemoteProviderProperties.RequestSignature.TENCENT_TC3_HMAC_SHA256
                    || authentication.getSignature() == RemoteProviderProperties.RequestSignature.VOLCENGINE_HMAC_SHA256) {
                required(authentication.getSigningService(), type + " signing service");
            }
            if (authentication.getSignatureExpirationSeconds() < 1
                    || authentication.getSignatureExpirationSeconds() > 86400) {
                invalid(type + " signature expiration must be between 1 and 86400 seconds");
            }
        }
        validateTransportSecurity(type, properties);
        for (Map.Entry<String, String> entry : properties.getKeyBindings().entrySet()) {
            safe(entry.getKey(), type + " logical key"); required(entry.getValue(), type + " physical key");
        }
        for (Map.Entry<String, RemoteProviderProperties.SecretMapping> entry : properties.getSecrets().entrySet()) {
            safe(entry.getKey(), type + " logical Secret");
            if (entry.getValue() == null) invalid(type + " Secret mapping must not be null");
            required(entry.getValue().getPath(), type + " Secret path");
            if (entry.getValue().getField() != null) safe(entry.getValue().getField(), type + " Secret field");
        }
    }

    private static void validateEndpoint(String label, URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null) invalid(label + " endpoint is required");
        if (endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            invalid(label + " endpoint must not contain user-info, query, or fragment");
        }
        boolean loopback = "localhost".equalsIgnoreCase(endpoint.getHost())
                || "127.0.0.1".equals(endpoint.getHost()) || "::1".equals(endpoint.getHost());
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                && !("http".equalsIgnoreCase(endpoint.getScheme()) && loopback)) {
            invalid(label + " endpoint must use HTTPS; HTTP is allowed only for loopback development");
        }
    }

    private static void validateTransportSecurity(String type, RemoteProviderProperties properties) {
        RemoteProviderProperties.Tls tls = properties.getTls();
        if (tls.getTrustStore() != null && !tls.getTrustStore().isBlank()
                && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(tls.getTrustStore()))) {
            invalid(type + " TLS trust-store does not exist");
        }
        if (tls.getKeyStore() != null && !tls.getKeyStore().isBlank()
                && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(tls.getKeyStore()))) {
            invalid(type + " TLS key-store does not exist");
        }
        RemoteProviderProperties.Proxy proxy = properties.getProxy();
        if (proxy.getHost() != null && !proxy.getHost().isBlank()
                && (proxy.getPort() < 1 || proxy.getPort() > 65535)) {
            invalid(type + " proxy port must be between 1 and 65535");
        }
        if (proxy.getUsername() != null && !proxy.getUsername().isBlank() && proxy.getPassword().length == 0) {
            invalid(type + " proxy password is required when proxy username is configured");
        }
    }

    public static String hash(String type, RemoteProviderProperties properties) {
        Map<String, String> secretMappings = new TreeMap<>();
        properties.getSecrets().forEach((logical, mapping) -> secretMappings.put(
                logical, mapping == null ? "<null>" : mapping.getPath() + "#" + mapping.getField()));
        String canonical = type + "\n" + properties.getEndpoint() + "\n"
                + properties.getSecretEndpoint() + "\n" + properties.getKmsEndpoint() + "\n"
                + properties.getAuthentication().getType() + "\n"
                + properties.getAuthentication().getTokenFile() + "\n"
                + properties.getAuthentication().getWorkloadIdentityTokenFile() + "\n"
                + properties.getAuthentication().getAccessKeyId() + "\n"
                + fingerprint(properties.getAuthentication().getToken()) + "\n"
                + fingerprint(properties.getAuthentication().getAccessKeySecret()) + "\n"
                + properties.getAuthentication().getSignature() + "\n"
                + properties.getAuthentication().getSigningService() + "\n"
                + properties.getAuthentication().getSigningRegion() + "\n"
                + properties.getAuthentication().getApiAction() + "\n"
                + properties.getAuthentication().getApiVersion() + "\n"
                + fingerprint(properties.getAuthentication().getSecurityToken()) + "\n"
                + properties.getAuthentication().getSignatureExpirationSeconds() + "\n"
                + properties.getTls().getTrustStore() + "\n" + fingerprint(properties.getTls().getTrustStorePassword()) + "\n"
                + properties.getTls().getKeyStore() + "\n" + fingerprint(properties.getTls().getKeyStorePassword()) + "\n"
                + properties.getTls().getKeyStoreType() + "\n"
                + properties.getProxy().getHost() + "\n" + properties.getProxy().getPort() + "\n"
                + properties.getProxy().getUsername() + "\n" + fingerprint(properties.getProxy().getPassword()) + "\n"
                + properties.getRegion() + "\n" + properties.getProjectId() + "\n"
                + properties.getTenantId() + "\n" + properties.getNamespace() + "\n" + properties.getApiVersion() + "\n"
                + properties.getWire().getSecretPath() + "\n" + properties.getWire().getWrapPath() + "\n"
                + properties.getWire().getUnwrapPath() + "\n" + properties.getWire().getSecretValueField() + "\n"
                + properties.getWire().getSecretVersionField() + "\n" + properties.getWire().getSecretCreatedAtField() + "\n"
                + properties.getWire().getWrappedKeyField() + "\n" + properties.getWire().getPlaintextField() + "\n"
                + properties.getWire().getSecretValueEncoding() + "\n"
                + properties.getWire().getRequestValueField() + "\n"
                + properties.getWire().getRequestWrapValueField() + "\n"
                + properties.getWire().getRequestUnwrapValueField() + "\n"
                + properties.getWire().getRequestAadField() + "\n"
                + properties.getWire().getRequestAadEncoding() + "\n"
                + properties.getWire().getRequestKeyField() + "\n"
                + properties.getWire().getSecretLatestPath() + "\n"
                + properties.getWire().getSecretMethod() + "\n"
                + properties.getWire().getSecretRequestNameField() + "\n"
                + properties.getWire().getSecretRequestVersionField() + "\n"
                + properties.getWire().getLatestVersionValue() + "\n"
                + properties.getWire().getRequestValueEncoding() + "\n"
                + properties.getWire().getResponseValueEncoding() + "\n"
                + properties.getWire().getRequestAlgorithmField() + "\n"
                + properties.getWire().getRequestAlgorithm() + "\n"
                + properties.getWire().getSecretAction() + "\n"
                + properties.getWire().getSecretApiVersion() + "\n"
                + properties.getWire().getSecretSigningService() + "\n"
                + properties.getWire().getWrapAction() + "\n"
                + properties.getWire().getUnwrapAction() + "\n"
                + properties.getWire().getKmsApiVersion() + "\n"
                + properties.getWire().getKmsSigningService() + "\n"
                + new TreeMap<>(properties.getSecrets()).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (entry.getValue() == null ? "<null>" : entry.getValue().getPath() + "#" + entry.getValue().getField()))
                .toList()
                + "\n" + secretMappings + "\n" + new TreeMap<>(properties.getKeyBindings());
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public static SecretBootstrapClient client(String type, String providerId, String hash,
                                               RemoteProviderProperties provider, BootstrapSecretProperties bootstrap,
                                               Set<SecretCapability> capabilities) {
        URI secretEndpoint = capabilities.contains(SecretCapability.SECRET_READ)
                ? provider.getSecretEndpoint() : provider.getKmsEndpoint();
        URI kmsEndpoint = capabilities.contains(SecretCapability.KEY_WRAP)
                || capabilities.contains(SecretCapability.KEY_UNWRAP)
                ? provider.getKmsEndpoint() : provider.getSecretEndpoint();
        return new RemoteSecretProviderClient(type, providerId, hash, provider, bootstrap, capabilities,
                new JsonHttpSecretTransport(secretEndpoint, kmsEndpoint,
                        provider.getAuthentication(), provider.getWire(),
                        variables(provider), provider.getTls(), provider.getProxy(),
                        bootstrap.getResilience().getConnectTimeout(), bootstrap.getResilience().getReadTimeout(),
                        bootstrap.getResilience().getMaxAttempts()));
    }

    public static RemoteSecretProviderClient reuseOrCreate(
            String type, ConfigurableEnvironment environment, BootstrapSecretProperties bootstrap,
            ObjectProvider<SecretBootstrapState> stateProvider, String prefix,
            Set<SecretCapability> capabilities) {
        RemoteProviderProperties properties = bind(environment, bootstrap, prefix, RemoteProviderProperties.class);
        OfficialWireProfiles.apply(type, properties);
        validate(type, properties, capabilities);
        SecretBootstrapState state = stateProvider.getIfAvailable();
        if (state != null && state.client() instanceof RemoteSecretProviderClient existing
                && type.equals(existing.descriptor().providerType())) {
            if (!hash(type, properties).equals(existing.configurationHash())) {
                throw new IllegalStateException(type + " Provider configuration changed after bootstrap");
            }
            return existing;
        }
        return (RemoteSecretProviderClient) client(type, type, hash(type, properties), properties, bootstrap, capabilities);
    }

    private static void safe(String value, String label) {
        required(value, label);
        if (value.contains("..") || value.contains("://") || value.startsWith("/")) invalid(label + " is invalid");
    }
    private static void validateWire(String type, RemoteProviderProperties.Wire wire) {
        if (wire == null) invalid(type + " wire profile is required");
        for (String template : new String[]{wire.getSecretPath(), wire.getWrapPath(), wire.getUnwrapPath()}) {
            required(template, type + " wire path");
            if (!template.startsWith("/") || template.contains("..") || template.contains("\\")) {
                invalid(type + " wire path is invalid");
            }
        }
        if (!wire.getSecretLatestPath().isBlank()
                && (!wire.getSecretLatestPath().startsWith("/")
                || wire.getSecretLatestPath().contains("..")
                || wire.getSecretLatestPath().contains("\\"))) {
            invalid(type + " wire latest Secret path is invalid");
        }
        for (String field : new String[]{wire.getSecretValueField(), wire.getSecretVersionField(),
                wire.getSecretCreatedAtField(), wire.getWrappedKeyField(), wire.getPlaintextField()}) {
            safe(field, type + " wire field");
        }
        if (!"PLAIN".equalsIgnoreCase(wire.getSecretValueEncoding())
                && !"BASE64".equalsIgnoreCase(wire.getSecretValueEncoding())) {
            invalid(type + " wire secret-value-encoding must be PLAIN or BASE64");
        }
        safe(wire.getRequestValueField(), type + " wire request value field");
        if (wire.getRequestWrapValueField() != null && !wire.getRequestWrapValueField().isBlank()) {
            safe(wire.getRequestWrapValueField(), type + " wire request wrap value field");
        }
        if (wire.getRequestUnwrapValueField() != null && !wire.getRequestUnwrapValueField().isBlank()) {
            safe(wire.getRequestUnwrapValueField(), type + " wire request unwrap value field");
        }
        if (!wire.getRequestAadField().isBlank()) {
            safe(wire.getRequestAadField(), type + " wire request AAD field");
        }
        if (wire.getRequestAadEncoding() == null) invalid(type + " wire request AAD encoding is required");
        if (wire.getSecretMethod() == null) invalid(type + " wire Secret method is required");
        if (wire.getRequestValueEncoding() == null || wire.getResponseValueEncoding() == null) {
            invalid(type + " wire value encodings are required");
        }
        if (wire.getRequestKeyField() != null && !wire.getRequestKeyField().isBlank()) {
            safe(wire.getRequestKeyField(), type + " wire request key field");
        }
        for (String field : new String[]{wire.getSecretRequestNameField(),
                wire.getSecretRequestVersionField(), wire.getRequestAlgorithmField()}) {
            if (field != null && !field.isBlank()) safe(field, type + " wire request field");
        }
    }
    private static Map<String, String> variables(RemoteProviderProperties properties) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("region", value(properties.getRegion()));
        result.put("projectId", value(properties.getProjectId()));
        result.put("tenantId", value(properties.getTenantId()));
        result.put("namespace", value(properties.getNamespace()));
        result.put("apiVersion", value(properties.getApiVersion()));
        return result;
    }
    private static String value(String value) { return value == null ? "" : value; }
    private static String fingerprint(char[] value) {
        if (value == null || value.length == 0) return "<empty>";
        java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
            if (encoded.hasArray()) java.util.Arrays.fill(encoded.array(), (byte) 0);
            java.util.Arrays.fill(value, '\0');
        }
    }
    private static String fingerprint(String value) {
        if (value == null || value.isBlank()) return "<empty>";
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
    private static void required(String value, String label) { if (value == null || value.isBlank()) invalid(label + " is required"); }
    private static void invalid(String message) { throw new SecretConfigurationException("SEC-BOOT-003", message); }
}
