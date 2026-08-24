package cn.richie696.component.secret.provider.openbao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;

/** OpenBao 专属配置；调用方只需提供连接、认证和逻辑名称映射。 */
@ConfigurationProperties(prefix = OpenBaoSecretProperties.PREFIX)
public class OpenBaoSecretProperties {
    public static final String PREFIX = "platform.component.secret.openbao";
    private URI endpoint;
    private String namespace;
    private Authentication authentication = new Authentication();
    private Kv kv = new Kv();
    private Transit transit = new Transit();
    private RemoteProviderProperties.Tls tls = new RemoteProviderProperties.Tls();
    private RemoteProviderProperties.Proxy proxy = new RemoteProviderProperties.Proxy();
    private Map<String, SecretMapping> secrets = new LinkedHashMap<>();
    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public Authentication getAuthentication() { return authentication; }
    public void setAuthentication(Authentication value) { authentication = value == null ? new Authentication() : value; }
    public Kv getKv() { return kv; }
    public void setKv(Kv value) { kv = value == null ? new Kv() : value; }
    public Transit getTransit() { return transit; }
    public void setTransit(Transit value) { transit = value == null ? new Transit() : value; }
    public RemoteProviderProperties.Tls getTls() { return tls; }
    public void setTls(RemoteProviderProperties.Tls value) { tls = value == null ? new RemoteProviderProperties.Tls() : value; }
    public RemoteProviderProperties.Proxy getProxy() { return proxy; }
    public void setProxy(RemoteProviderProperties.Proxy value) { proxy = value == null ? new RemoteProviderProperties.Proxy() : value; }
    public Map<String, SecretMapping> getSecrets() { return Map.copyOf(secrets); }
    public void setSecrets(Map<String, SecretMapping> value) { secrets = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public static class Authentication {
        private AuthenticationType type = AuthenticationType.TOKEN;
        private char[] token = new char[0];
        private String tokenFile;
        public AuthenticationType getType() { return type; }
        public void setType(AuthenticationType value) { type = value == null ? AuthenticationType.TOKEN : value; }
        public char[] getToken() { return token.clone(); }
        public void setToken(char[] value) { token = value == null ? new char[0] : value.clone(); }
        public String getTokenFile() { return tokenFile; }
        public void setTokenFile(String value) { tokenFile = value; }
    }
    public enum AuthenticationType { TOKEN, TOKEN_FILE }
    public static class Kv {
        private String mount = "secret";
        private String runtimePrefix = "runtime";
        public String getMount() { return mount; }
        public void setMount(String value) { mount = value; }
        public String getRuntimePrefix() { return runtimePrefix; }
        public void setRuntimePrefix(String value) { runtimePrefix = value; }
    }
    public static class Transit {
        private String mount = "transit";
        private Map<String, String> keyBindings = new LinkedHashMap<>();
        public String getMount() { return mount; }
        public void setMount(String value) { mount = value; }
        public Map<String, String> getKeyBindings() { return Map.copyOf(keyBindings); }
        public void setKeyBindings(Map<String, String> value) { keyBindings = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    }
    public static class SecretMapping {
        private String path;
        private String field;
        public String getPath() { return path; }
        public void setPath(String value) { path = value; }
        public String getField() { return field; }
        public void setField(String value) { field = value; }
    }
}
