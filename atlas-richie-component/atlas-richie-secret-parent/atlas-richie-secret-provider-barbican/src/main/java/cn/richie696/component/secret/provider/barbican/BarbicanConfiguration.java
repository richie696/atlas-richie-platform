package cn.richie696.component.secret.provider.barbican;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import java.net.URI; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.Map; import java.util.TreeMap;
final class BarbicanConfiguration {
    private BarbicanConfiguration() { }
    static void validate(BarbicanSecretProperties p) {
        URI endpoint = p.getEndpoint(); if (endpoint == null || endpoint.getHost() == null) invalid("Barbican endpoint is required");
        if (endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) invalid("Barbican endpoint must not contain user-info, query, or fragment");
        boolean loopback = "localhost".equalsIgnoreCase(endpoint.getHost()) || "127.0.0.1".equals(endpoint.getHost()) || "::1".equals(endpoint.getHost());
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !("http".equalsIgnoreCase(endpoint.getScheme()) && loopback)) invalid("Barbican endpoint must use HTTPS; HTTP is allowed only for loopback development");
        if (p.getAuthentication().getType() == BarbicanSecretProperties.AuthenticationType.TOKEN_FILE) required(p.getAuthentication().getTokenFile(), "Barbican token file"); else if (p.getAuthentication().getToken().length == 0) invalid("Barbican token is required");
        for (Map.Entry<String, BarbicanSecretProperties.SecretMapping> e : p.getSecrets().entrySet()) { safe(e.getKey(), "Barbican logical Secret"); if (e.getValue() == null) invalid("Barbican Secret mapping must not be null"); safe(e.getValue().getId(), "Barbican Secret id"); }
        validateTransport(p.getTls(), p.getProxy());
    }
    static String hash(String id, BarbicanSecretProperties p) { String canonical = id + "\n" + p.getEndpoint() + "\n" + p.getProjectId() + "\n" + p.getAuthentication().getType() + "\n" + p.getAuthentication().getTokenFile() + "\n" + p.getTls().getTrustStore() + "\n" + p.getTls().getKeyStore() + "\n" + p.getProxy().getHost() + "\n" + p.getProxy().getPort() + "\n" + new TreeMap<>(p.getSecrets()); try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static void safe(String value, String label) { required(value, label); if (value.contains("..") || value.contains("://") || value.contains("/")) invalid(label + " is invalid"); }
    private static void required(String value, String label) { if (value == null || value.isBlank()) invalid(label + " is required"); }
    private static void invalid(String message) { throw new SecretConfigurationException("SEC-BOOT-003", message); }
    private static void validateTransport(cn.richie696.component.secret.provider.common.RemoteProviderProperties.Tls tls, cn.richie696.component.secret.provider.common.RemoteProviderProperties.Proxy proxy) {
        if (tls.getTrustStore() != null && !tls.getTrustStore().isBlank() && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(tls.getTrustStore()))) invalid("Barbican TLS trust-store does not exist");
        if (tls.getKeyStore() != null && !tls.getKeyStore().isBlank() && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(tls.getKeyStore()))) invalid("Barbican TLS key-store does not exist");
        if (proxy.getHost() != null && !proxy.getHost().isBlank() && (proxy.getPort() < 1 || proxy.getPort() > 65535)) invalid("Barbican proxy port is invalid");
    }
}
