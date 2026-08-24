/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** AWS Provider 的外部变量配置。 */
@ConfigurationProperties(prefix = AwsSecretProperties.PREFIX)
public class AwsSecretProperties {
    public static final String PREFIX = "platform.component.secret.aws";

    private String region;
    private Authentication authentication = new Authentication();
    private SecretsManager secretsManager = new SecretsManager();
    private Kms kms = new Kms();
    private Endpoints endpoints = new Endpoints();
    private Map<String, SecretMapping> secrets = new LinkedHashMap<>();

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Authentication getAuthentication() { return authentication; }
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication == null ? new Authentication() : authentication;
    }
    public SecretsManager getSecretsManager() { return secretsManager; }
    public void setSecretsManager(SecretsManager secretsManager) {
        this.secretsManager = secretsManager == null ? new SecretsManager() : secretsManager;
    }
    public Kms getKms() { return kms; }
    public void setKms(Kms kms) { this.kms = kms == null ? new Kms() : kms; }
    public Endpoints getEndpoints() { return endpoints; }
    public void setEndpoints(Endpoints endpoints) {
        this.endpoints = endpoints == null ? new Endpoints() : endpoints;
    }
    public Map<String, SecretMapping> getSecrets() { return Map.copyOf(secrets); }
    public void setSecrets(Map<String, SecretMapping> secrets) {
        this.secrets = secrets == null ? new LinkedHashMap<>() : new LinkedHashMap<>(secrets);
    }

    public static class Authentication {
        private AuthenticationType type = AuthenticationType.DEFAULT_CHAIN;
        private String profileName;
        public AuthenticationType getType() { return type; }
        public void setType(AuthenticationType type) {
            this.type = type == null ? AuthenticationType.DEFAULT_CHAIN : type;
        }
        public String getProfileName() { return profileName; }
        public void setProfileName(String profileName) { this.profileName = profileName; }
    }

    public enum AuthenticationType { DEFAULT_CHAIN, PROFILE }

    public static class SecretsManager {
        private String pathPrefix;
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
    }

    public static class Kms {
        private Map<String, String> keyBindings = new LinkedHashMap<>();
        private String signingAlgorithm = "RSASSA_PSS_SHA_256";
        public Map<String, String> getKeyBindings() { return Map.copyOf(keyBindings); }
        public void setKeyBindings(Map<String, String> keyBindings) {
            this.keyBindings = keyBindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keyBindings);
        }
        public String getSigningAlgorithm() { return signingAlgorithm; }
        public void setSigningAlgorithm(String signingAlgorithm) {
            this.signingAlgorithm = signingAlgorithm == null || signingAlgorithm.isBlank()
                    ? "RSASSA_PSS_SHA_256" : signingAlgorithm;
        }
    }

    public static class Endpoints {
        private URI secretsManager;
        private URI kms;
        public URI getSecretsManager() { return secretsManager; }
        public void setSecretsManager(URI secretsManager) { this.secretsManager = secretsManager; }
        public URI getKms() { return kms; }
        public void setKms(URI kms) { this.kms = kms; }
    }

    public static class SecretMapping {
        private String secretId;
        private String field;
        public String getSecretId() { return secretId; }
        public void setSecretId(String secretId) { this.secretId = secretId; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
    }
}
