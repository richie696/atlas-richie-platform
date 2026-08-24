/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 阿里云 Provider 的可变部署配置；业务代码不依赖本类型。 */
@ConfigurationProperties(prefix = AliyunSecretProperties.PREFIX)
public class AliyunSecretProperties {
    public static final String PREFIX = "platform.component.secret.aliyun";

    private String region;
    private URI endpoint;
    private Path caFile;
    private SecretsManager secretsManager = new SecretsManager();
    private Kms kms = new Kms();
    private Map<String, SecretMapping> secrets = new LinkedHashMap<>();

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public Path getCaFile() { return caFile; }
    public void setCaFile(Path caFile) { this.caFile = caFile; }
    public SecretsManager getSecretsManager() { return secretsManager; }
    public void setSecretsManager(SecretsManager secretsManager) {
        this.secretsManager = secretsManager == null ? new SecretsManager() : secretsManager;
    }
    public Kms getKms() { return kms; }
    public void setKms(Kms kms) { this.kms = kms == null ? new Kms() : kms; }
    public Map<String, SecretMapping> getSecrets() { return Map.copyOf(secrets); }
    public void setSecrets(Map<String, SecretMapping> secrets) {
        this.secrets = secrets == null ? new LinkedHashMap<>() : new LinkedHashMap<>(secrets);
    }

    public static class SecretsManager {
        private String pathPrefix;
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
    }

    public static class Kms {
        private Map<String, String> keyBindings = new LinkedHashMap<>();
        public Map<String, String> getKeyBindings() { return Map.copyOf(keyBindings); }
        public void setKeyBindings(Map<String, String> keyBindings) {
            this.keyBindings = keyBindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keyBindings);
        }
    }

    public static class SecretMapping {
        private String secretName;
        private String field;
        public String getSecretName() { return secretName; }
        public void setSecretName(String secretName) { this.secretName = secretName; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
    }
}
