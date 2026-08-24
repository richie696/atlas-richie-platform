package cn.richie696.component.secret.provider.barbican;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import cn.richie696.component.secret.provider.common.RemoteProviderProperties;

@ConfigurationProperties(prefix = BarbicanSecretProperties.PREFIX)
public class BarbicanSecretProperties {
    public static final String PREFIX = "platform.component.secret.barbican";
    private URI endpoint;
    private Authentication authentication = new Authentication();
    private String projectId;
    private RemoteProviderProperties.Tls tls = new RemoteProviderProperties.Tls();
    private RemoteProviderProperties.Proxy proxy = new RemoteProviderProperties.Proxy();
    private Map<String, SecretMapping> secrets = new LinkedHashMap<>();
    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI value) { endpoint = value; }
    public Authentication getAuthentication() { return authentication; }
    public void setAuthentication(Authentication value) { authentication = value == null ? new Authentication() : value; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String value) { projectId = value; }
    public RemoteProviderProperties.Tls getTls() { return tls; }
    public void setTls(RemoteProviderProperties.Tls value) { tls = value == null ? new RemoteProviderProperties.Tls() : value; }
    public RemoteProviderProperties.Proxy getProxy() { return proxy; }
    public void setProxy(RemoteProviderProperties.Proxy value) { proxy = value == null ? new RemoteProviderProperties.Proxy() : value; }
    public Map<String, SecretMapping> getSecrets() { return Map.copyOf(secrets); }
    public void setSecrets(Map<String, SecretMapping> value) { secrets = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public static class Authentication {
        private AuthenticationType type = AuthenticationType.BEARER_TOKEN;
        private char[] token = new char[0];
        private String tokenFile;
        public AuthenticationType getType() { return type; }
        public void setType(AuthenticationType value) { type = value == null ? AuthenticationType.BEARER_TOKEN : value; }
        public char[] getToken() { return token.clone(); }
        public void setToken(char[] value) { token = value == null ? new char[0] : value.clone(); }
        public String getTokenFile() { return tokenFile; }
        public void setTokenFile(String value) { tokenFile = value; }
    }
    public enum AuthenticationType { BEARER_TOKEN, TOKEN_FILE }
    public static class SecretMapping {
        private String id;
        public String getId() { return id; }
        public void setId(String value) { id = value; }
    }
}
