package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

final class OpenBaoSecretConfiguration {
    private OpenBaoSecretConfiguration() { }
    static void validate(OpenBaoSecretProperties properties) {
        URI endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.getHost() == null) invalid("OpenBao endpoint is required");
        if (endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) invalid("OpenBao endpoint must not contain user-info, query, or fragment");
        boolean loopback = "localhost".equalsIgnoreCase(endpoint.getHost()) || "127.0.0.1".equals(endpoint.getHost()) || "::1".equals(endpoint.getHost());
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !("http".equalsIgnoreCase(endpoint.getScheme()) && loopback)) invalid("OpenBao endpoint must use HTTPS; HTTP is allowed only for loopback development");
        safe(properties.getKv().getMount(), "OpenBao KV mount"); safe(properties.getKv().getRuntimePrefix(), "OpenBao runtime prefix"); safe(properties.getTransit().getMount(), "OpenBao Transit mount");
        if (properties.getAuthentication().getType() == OpenBaoSecretProperties.AuthenticationType.TOKEN_FILE) required(properties.getAuthentication().getTokenFile(), "OpenBao token file"); else if (properties.getAuthentication().getToken().length == 0) invalid("OpenBao token is required");
        for (Map.Entry<String, String> entry : properties.getTransit().getKeyBindings().entrySet()) { safe(entry.getKey(), "OpenBao logical key"); safe(entry.getValue(), "OpenBao transit key"); }
        for (Map.Entry<String, OpenBaoSecretProperties.SecretMapping> entry : properties.getSecrets().entrySet()) { safe(entry.getKey(), "OpenBao logical Secret"); if (entry.getValue() == null) invalid("OpenBao Secret mapping must not be null"); safe(entry.getValue().getPath(), "OpenBao Secret path"); if (entry.getValue().getField() != null) safe(entry.getValue().getField(), "OpenBao Secret field"); }
        validateTransport(properties.getTls(), properties.getProxy());
    }
    static String hash(String providerId, OpenBaoSecretProperties p) {
        String canonical = providerId + "\n" + p.getEndpoint() + "\n" + p.getNamespace() + "\n" + p.getAuthentication().getType() + "\n" + p.getAuthentication().getTokenFile() + "\n" + p.getTls().getTrustStore() + "\n" + p.getTls().getKeyStore() + "\n" + p.getProxy().getHost() + "\n" + p.getProxy().getPort() + "\n" + p.getKv().getMount() + "\n" + p.getKv().getRuntimePrefix() + "\n" + p.getTransit().getMount() + "\n" + new TreeMap<>(p.getTransit().getKeyBindings()) + "\n" + new TreeMap<>(p.getSecrets());
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static void safe(String value, String label) { required(value, label); if (value.contains("..") || value.contains("://") || value.startsWith("/")) invalid(label + " is invalid"); }
    private static void required(String value, String label) { if (value == null || value.isBlank()) invalid(label + " is required"); }
    private static void invalid(String message) { throw new SecretConfigurationException("SEC-BOOT-003", message); }
    private static void validateTransport(cn.richie696.component.secret.provider.common.RemoteProviderProperties.Tls tls, cn.richie696.component.secret.provider.common.RemoteProviderProperties.Proxy proxy) {
        if (tls.getTrustStore() != null && !tls.getTrustStore().isBlank() && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(tls.getTrustStore()))) invalid("OpenBao TLS trust-store does not exist");
        if (tls.getKeyStore() != null && !tls.getKeyStore().isBlank() && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(tls.getKeyStore()))) invalid("OpenBao TLS key-store does not exist");
        if (proxy.getHost() != null && !proxy.getHost().isBlank() && (proxy.getPort() < 1 || proxy.getPort() > 65535)) invalid("OpenBao proxy port is invalid");
    }
}
