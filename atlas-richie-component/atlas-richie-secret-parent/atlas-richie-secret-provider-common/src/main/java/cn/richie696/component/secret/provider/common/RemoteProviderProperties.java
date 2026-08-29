package cn.richie696.component.secret.provider.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Common external configuration used by the optional provider artifacts. */
public class RemoteProviderProperties {
    private URI endpoint;
    private URI secretEndpoint;
    private URI kmsEndpoint;
    private Authentication authentication = new Authentication();
    private String region;
    private String projectId;
    private String tenantId;
    private String namespace;
    private String apiVersion;
    private Wire wire = new Wire();
    private Tls tls = new Tls();
    private Proxy proxy = new Proxy();
    private Map<String, SecretMapping> secrets = new LinkedHashMap<>();
    private Map<String, String> keyBindings = new LinkedHashMap<>();

    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public URI getSecretEndpoint() { return secretEndpoint == null ? endpoint : secretEndpoint; }
    public void setSecretEndpoint(URI value) { this.secretEndpoint = value; }
    public URI getKmsEndpoint() { return kmsEndpoint == null ? endpoint : kmsEndpoint; }
    public void setKmsEndpoint(URI value) { this.kmsEndpoint = value; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    public Wire getWire() { return wire; }
    public void setWire(Wire wire) { this.wire = wire == null ? new Wire() : wire; }
    public Tls getTls() { return tls; }
    public void setTls(Tls value) { this.tls = value == null ? new Tls() : value; }
    public Proxy getProxy() { return proxy; }
    public void setProxy(Proxy value) { this.proxy = value == null ? new Proxy() : value; }
    public Authentication getAuthentication() { return authentication; }
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication == null ? new Authentication() : authentication;
    }
    public Map<String, SecretMapping> getSecrets() { return Map.copyOf(secrets); }
    public void setSecrets(Map<String, SecretMapping> secrets) {
        this.secrets = secrets == null ? new LinkedHashMap<>() : new LinkedHashMap<>(secrets);
    }
    public Map<String, String> getKeyBindings() { return Map.copyOf(keyBindings); }
    public void setKeyBindings(Map<String, String> keyBindings) {
        this.keyBindings = keyBindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keyBindings);
    }

    public static class Authentication {
        private AuthenticationType type = AuthenticationType.BEARER_TOKEN;
        private char[] token = new char[0];
        private String tokenFile;
        private String accessKeyId;
        private char[] accessKeySecret = new char[0];
        private String securityToken;
        private String signingService;
        private String signingRegion;
        private String apiAction;
        private String apiVersion;
        private RequestSignature signature = RequestSignature.NONE;
        private int signatureExpirationSeconds = 1800;
        private String workloadIdentityTokenFile;
        public AuthenticationType getType() { return type; }
        public void setType(AuthenticationType type) {
            this.type = type == null ? AuthenticationType.BEARER_TOKEN : type;
        }
        public char[] getToken() { return token.clone(); }
        public void setToken(char[] token) {
            char[] previous = this.token;
            this.token = token == null ? new char[0] : token.clone();
            if (previous != null) java.util.Arrays.fill(previous, '\0');
        }
        public String getTokenFile() { return tokenFile; }
        public void setTokenFile(String tokenFile) { this.tokenFile = tokenFile; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public char[] getAccessKeySecret() { return accessKeySecret.clone(); }
        public void setAccessKeySecret(char[] value) {
            char[] previous = this.accessKeySecret;
            this.accessKeySecret = value == null ? new char[0] : value.clone();
            if (previous != null) java.util.Arrays.fill(previous, '\0');
        }
        public String getSecurityToken() { return securityToken; }
        public void setSecurityToken(String value) { this.securityToken = value; }
        public String getSigningService() { return signingService; }
        public void setSigningService(String value) { this.signingService = value; }
        public String getSigningRegion() { return signingRegion; }
        public void setSigningRegion(String value) { this.signingRegion = value; }
        public String getApiAction() { return apiAction; }
        public void setApiAction(String value) { this.apiAction = value; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String value) { this.apiVersion = value; }
        public RequestSignature getSignature() { return signature; }
        public void setSignature(RequestSignature value) { this.signature = value == null ? RequestSignature.NONE : value; }
        public int getSignatureExpirationSeconds() { return signatureExpirationSeconds; }
        public void setSignatureExpirationSeconds(int value) { this.signatureExpirationSeconds = value; }
        public String getWorkloadIdentityTokenFile() { return workloadIdentityTokenFile; }
        public void setWorkloadIdentityTokenFile(String value) { this.workloadIdentityTokenFile = value; }
    }

    public enum AuthenticationType { NONE, BEARER_TOKEN, TOKEN_FILE, WORKLOAD_IDENTITY_TOKEN_FILE, ACCESS_KEY }
    public enum RequestSignature { NONE, HUAWEI_SDK_HMAC_SHA256, TENCENT_TC3_HMAC_SHA256,
        VOLCENGINE_HMAC_SHA256, BAIDU_BCE_V2 }

    /** Vendor REST shape kept inside the provider; never exposed by Secret API. */
    public static class Wire {
        private String secretPath = "/secrets/{path}";
        private String secretLatestPath = "";
        private HttpMethod secretMethod = HttpMethod.GET;
        private String secretRequestNameField = "";
        private String secretRequestVersionField = "";
        private String latestVersionValue = "latest";
        private String wrapPath = "/keys/{key}/wrap";
        private String unwrapPath = "/keys/{key}/unwrap";
        private String secretValueField = "value";
        private String secretVersionField = "version";
        private String secretCreatedAtField = "createdAt";
        private String wrappedKeyField = "wrappedKey";
        private String plaintextField = "plaintext";
        private String secretValueEncoding = "PLAIN";
        private String requestValueField = "value";
        private String requestWrapValueField = "";
        private String requestUnwrapValueField = "";
        private String requestAadField = "aad";
        private RequestAadEncoding requestAadEncoding = RequestAadEncoding.BASE64;
        private boolean requestAadEncodingConfigured;
        private String requestKeyField = "";
        private ValueEncoding requestValueEncoding = ValueEncoding.BASE64;
        private ValueEncoding responseValueEncoding = ValueEncoding.BASE64;
        private String requestAlgorithmField = "";
        private String requestAlgorithm = "";
        private String secretAction = "";
        private String secretApiVersion = "";
        private String secretSigningService = "";
        private String wrapAction = "";
        private String unwrapAction = "";
        private String kmsApiVersion = "";
        private String kmsSigningService = "";
        public String getSecretPath() { return secretPath; }
        public void setSecretPath(String value) { this.secretPath = value == null ? "" : value; }
        public String getSecretLatestPath() { return secretLatestPath; }
        public void setSecretLatestPath(String value) { this.secretLatestPath = value == null ? "" : value; }
        public HttpMethod getSecretMethod() { return secretMethod; }
        public void setSecretMethod(HttpMethod value) { this.secretMethod = value == null ? HttpMethod.GET : value; }
        public String getSecretRequestNameField() { return secretRequestNameField; }
        public void setSecretRequestNameField(String value) { this.secretRequestNameField = value == null ? "" : value; }
        public String getSecretRequestVersionField() { return secretRequestVersionField; }
        public void setSecretRequestVersionField(String value) { this.secretRequestVersionField = value == null ? "" : value; }
        public String getLatestVersionValue() { return latestVersionValue; }
        public void setLatestVersionValue(String value) { this.latestVersionValue = value == null ? "latest" : value; }
        public String getWrapPath() { return wrapPath; }
        public void setWrapPath(String value) { this.wrapPath = value == null ? "" : value; }
        public String getUnwrapPath() { return unwrapPath; }
        public void setUnwrapPath(String value) { this.unwrapPath = value == null ? "" : value; }
        public String getSecretValueField() { return secretValueField; }
        public void setSecretValueField(String value) { this.secretValueField = value == null ? "" : value; }
        public String getSecretVersionField() { return secretVersionField; }
        public void setSecretVersionField(String value) { this.secretVersionField = value == null ? "" : value; }
        public String getSecretCreatedAtField() { return secretCreatedAtField; }
        public void setSecretCreatedAtField(String value) { this.secretCreatedAtField = value == null ? "" : value; }
        public String getWrappedKeyField() { return wrappedKeyField; }
        public void setWrappedKeyField(String value) { this.wrappedKeyField = value == null ? "" : value; }
        public String getPlaintextField() { return plaintextField; }
        public void setPlaintextField(String value) { this.plaintextField = value == null ? "" : value; }
        public String getSecretValueEncoding() { return secretValueEncoding; }
        public void setSecretValueEncoding(String value) { this.secretValueEncoding = value == null ? "" : value; }
        public String getRequestValueField() { return requestValueField; }
        public void setRequestValueField(String value) { this.requestValueField = value == null ? "" : value; }
        public String getRequestWrapValueField() { return requestWrapValueField; }
        public void setRequestWrapValueField(String value) { this.requestWrapValueField = value == null ? "" : value; }
        public String getRequestUnwrapValueField() { return requestUnwrapValueField; }
        public void setRequestUnwrapValueField(String value) { this.requestUnwrapValueField = value == null ? "" : value; }
        public String getRequestAadField() { return requestAadField; }
        public void setRequestAadField(String value) { this.requestAadField = value == null ? "" : value; }
        public RequestAadEncoding getRequestAadEncoding() { return requestAadEncoding; }
        public void setRequestAadEncoding(RequestAadEncoding value) {
            this.requestAadEncoding = value == null ? RequestAadEncoding.BASE64 : value;
            this.requestAadEncodingConfigured = value != null;
        }
        boolean isRequestAadEncodingConfigured() { return requestAadEncodingConfigured; }
        public String getRequestKeyField() { return requestKeyField; }
        public void setRequestKeyField(String value) { this.requestKeyField = value == null ? "" : value; }
        public ValueEncoding getRequestValueEncoding() { return requestValueEncoding; }
        public void setRequestValueEncoding(ValueEncoding value) {
            this.requestValueEncoding = value == null ? ValueEncoding.BASE64 : value;
        }
        public ValueEncoding getResponseValueEncoding() { return responseValueEncoding; }
        public void setResponseValueEncoding(ValueEncoding value) {
            this.responseValueEncoding = value == null ? ValueEncoding.BASE64 : value;
        }
        public String getRequestAlgorithmField() { return requestAlgorithmField; }
        public void setRequestAlgorithmField(String value) { this.requestAlgorithmField = value == null ? "" : value; }
        public String getRequestAlgorithm() { return requestAlgorithm; }
        public void setRequestAlgorithm(String value) { this.requestAlgorithm = value == null ? "" : value; }
        public String getSecretAction() { return secretAction; }
        public void setSecretAction(String value) { this.secretAction = value == null ? "" : value; }
        public String getSecretApiVersion() { return secretApiVersion; }
        public void setSecretApiVersion(String value) { this.secretApiVersion = value == null ? "" : value; }
        public String getSecretSigningService() { return secretSigningService; }
        public void setSecretSigningService(String value) { this.secretSigningService = value == null ? "" : value; }
        public String getWrapAction() { return wrapAction; }
        public void setWrapAction(String value) { this.wrapAction = value == null ? "" : value; }
        public String getUnwrapAction() { return unwrapAction; }
        public void setUnwrapAction(String value) { this.unwrapAction = value == null ? "" : value; }
        public String getKmsApiVersion() { return kmsApiVersion; }
        public void setKmsApiVersion(String value) { this.kmsApiVersion = value == null ? "" : value; }
        public String getKmsSigningService() { return kmsSigningService; }
        public void setKmsSigningService(String value) { this.kmsSigningService = value == null ? "" : value; }
    }

    public enum RequestAadEncoding { BASE64, ATTRIBUTES }
    public enum ValueEncoding { BASE64, BASE64_URL }
    public enum HttpMethod { GET, POST }

    public static class SecretMapping {
        private String path;
        private String field;
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
    }

    public static class Tls {
        private String trustStore;
        private char[] trustStorePassword = new char[0];
        private String keyStore;
        private char[] keyStorePassword = new char[0];
        private String keyStoreType = "PKCS12";
        public String getTrustStore() { return trustStore; }
        public void setTrustStore(String value) { trustStore = value; }
        public char[] getTrustStorePassword() { return trustStorePassword.clone(); }
        public void setTrustStorePassword(char[] value) { java.util.Arrays.fill(trustStorePassword, '\0'); trustStorePassword = value == null ? new char[0] : value.clone(); }
        public String getKeyStore() { return keyStore; }
        public void setKeyStore(String value) { keyStore = value; }
        public char[] getKeyStorePassword() { return keyStorePassword.clone(); }
        public void setKeyStorePassword(char[] value) { java.util.Arrays.fill(keyStorePassword, '\0'); keyStorePassword = value == null ? new char[0] : value.clone(); }
        public String getKeyStoreType() { return keyStoreType; }
        public void setKeyStoreType(String value) { keyStoreType = value == null || value.isBlank() ? "PKCS12" : value; }
    }

    public static class Proxy {
        private String host;
        private int port;
        private String username;
        private char[] password = new char[0];
        public String getHost() { return host; }
        public void setHost(String value) { host = value; }
        public int getPort() { return port; }
        public void setPort(int value) { port = value; }
        public String getUsername() { return username; }
        public void setUsername(String value) { username = value; }
        public char[] getPassword() { return password.clone(); }
        public void setPassword(char[] value) { java.util.Arrays.fill(password, '\0'); password = value == null ? new char[0] : value.clone(); }
    }
}
