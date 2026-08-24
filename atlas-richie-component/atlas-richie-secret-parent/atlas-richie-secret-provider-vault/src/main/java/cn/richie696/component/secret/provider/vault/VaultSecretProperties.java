/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vault Provider 的外部变量配置。业务调用方不需要了解这些类型。
 */
@ConfigurationProperties(prefix = VaultSecretProperties.PREFIX)
public class VaultSecretProperties {
    public static final String PREFIX = "platform.component.secret.vault";

    private URI endpoint;
    private String namespace;
    private Authentication authentication = new Authentication();
    private Kv kv = new Kv();
    private Transit transit = new Transit();
    private Tls tls = new Tls();
    private Map<String, SecretMapping> secrets = new LinkedHashMap<>();

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = endpoint;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication == null ? new Authentication() : authentication;
    }

    public Kv getKv() {
        return kv;
    }

    public void setKv(Kv kv) {
        this.kv = kv == null ? new Kv() : kv;
    }

    public Transit getTransit() {
        return transit;
    }

    public void setTransit(Transit transit) {
        this.transit = transit == null ? new Transit() : transit;
    }

    public Tls getTls() {
        return tls;
    }

    public void setTls(Tls tls) {
        this.tls = tls == null ? new Tls() : tls;
    }

    public Map<String, SecretMapping> getSecrets() {
        return Map.copyOf(secrets);
    }

    public void setSecrets(Map<String, SecretMapping> secrets) {
        this.secrets = secrets == null ? new LinkedHashMap<>() : new LinkedHashMap<>(secrets);
    }

    public static class Authentication {
        private AuthenticationType type = AuthenticationType.KUBERNETES;
        private char[] token = new char[0];
        private String tokenFile;
        private String role;
        private String kubernetesPath = "kubernetes";
        private String serviceAccountTokenFile =
                "/var/run/secrets/kubernetes.io/serviceaccount/token";
        private String appRolePath = "approle";
        private char[] roleId = new char[0];
        private char[] secretId = new char[0];
        private String roleIdFile;
        private String secretIdFile;
        private String jwtPath = "jwt";
        private String jwtRole;
        private char[] jwt = new char[0];
        private String jwtFile;

        public AuthenticationType getType() {
            return type;
        }

        public void setType(AuthenticationType type) {
            this.type = type == null ? AuthenticationType.KUBERNETES : type;
        }

        public char[] getToken() {
            return token.clone();
        }

        public void setToken(char[] token) {
            this.token = replaceSecret(this.token, token);
        }

        public String getTokenFile() {
            return tokenFile;
        }

        public void setTokenFile(String tokenFile) {
            this.tokenFile = tokenFile;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getKubernetesPath() {
            return kubernetesPath;
        }

        public void setKubernetesPath(String kubernetesPath) {
            this.kubernetesPath = kubernetesPath;
        }

        public String getServiceAccountTokenFile() {
            return serviceAccountTokenFile;
        }

        public void setServiceAccountTokenFile(String serviceAccountTokenFile) {
            this.serviceAccountTokenFile = serviceAccountTokenFile;
        }

        public String getAppRolePath() {
            return appRolePath;
        }

        public void setAppRolePath(String appRolePath) {
            this.appRolePath = appRolePath;
        }

        public char[] getRoleId() {
            return roleId.clone();
        }

        public void setRoleId(char[] roleId) {
            this.roleId = replaceSecret(this.roleId, roleId);
        }

        public char[] getSecretId() {
            return secretId.clone();
        }

        public void setSecretId(char[] secretId) {
            this.secretId = replaceSecret(this.secretId, secretId);
        }

        public String getRoleIdFile() {
            return roleIdFile;
        }

        public void setRoleIdFile(String roleIdFile) {
            this.roleIdFile = roleIdFile;
        }

        public String getSecretIdFile() {
            return secretIdFile;
        }

        public void setSecretIdFile(String secretIdFile) {
            this.secretIdFile = secretIdFile;
        }

        public String getJwtPath() {
            return jwtPath;
        }

        public void setJwtPath(String jwtPath) {
            this.jwtPath = jwtPath;
        }

        public String getJwtRole() {
            return jwtRole;
        }

        public void setJwtRole(String jwtRole) {
            this.jwtRole = jwtRole;
        }

        public char[] getJwt() {
            return jwt.clone();
        }

        public void setJwt(char[] jwt) {
            this.jwt = replaceSecret(this.jwt, jwt);
        }

        public String getJwtFile() {
            return jwtFile;
        }

        public void setJwtFile(String jwtFile) {
            this.jwtFile = jwtFile;
        }

        private static char[] replaceSecret(char[] previous, char[] next) {
            if (previous != null) {
                java.util.Arrays.fill(previous, '\0');
            }
            return next == null ? new char[0] : next.clone();
        }
    }

    public enum AuthenticationType {
        KUBERNETES,
        TOKEN_FILE,
        TOKEN,
        APPROLE,
        JWT,
        AGENT
    }

    public static class Kv {
        private String mount = "secret";
        private String runtimePrefix = "runtime";

        public String getMount() {
            return mount;
        }

        public void setMount(String mount) {
            this.mount = mount;
        }

        public String getRuntimePrefix() {
            return runtimePrefix;
        }

        public void setRuntimePrefix(String runtimePrefix) {
            this.runtimePrefix = runtimePrefix;
        }
    }

    public static class Transit {
        private String mount = "transit";
        private Map<String, String> keyBindings = new LinkedHashMap<>();

        public String getMount() {
            return mount;
        }

        public void setMount(String mount) {
            this.mount = mount;
        }

        public Map<String, String> getKeyBindings() {
            return Map.copyOf(keyBindings);
        }

        public void setKeyBindings(Map<String, String> keyBindings) {
            this.keyBindings = keyBindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keyBindings);
        }
    }

    public static class Tls {
        private String trustStore;
        private char[] trustStorePassword = new char[0];

        public String getTrustStore() {
            return trustStore;
        }

        public void setTrustStore(String trustStore) {
            this.trustStore = trustStore;
        }

        public char[] getTrustStorePassword() {
            return trustStorePassword.clone();
        }

        public void setTrustStorePassword(char[] trustStorePassword) {
            char[] previous = this.trustStorePassword;
            this.trustStorePassword = trustStorePassword == null ? new char[0] : trustStorePassword.clone();
            if (previous != null) {
                java.util.Arrays.fill(previous, '\0');
            }
        }
    }

    public static class SecretMapping {
        private String path;
        private String field;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }
    }
}
