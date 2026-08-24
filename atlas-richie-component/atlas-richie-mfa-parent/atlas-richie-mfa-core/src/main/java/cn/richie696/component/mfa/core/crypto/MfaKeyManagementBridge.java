/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.mfa.core.crypto;

import cn.richie696.component.secret.api.SecretOperations;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * 将 MFA 旧密钥管理契约桥接到统一的最小知识 Secret 外观。
 *
 * <p>桥接层只知道稳定的业务用途；Provider、物理 Key、算法、AAD、DEK 与信封格式均由
 * Secret 组件内聚。此类仅服务于 MFA 兼容期，不是面向业务系统的新调用 API。</p>
 */
public final class MfaKeyManagementBridge implements KeyManagementProvider {
    private static final String MFA_DATA_KEY = "mfa.totp.data-key";
    private static final String ENVELOPE_PREFIX = "arse:v1:";

    private final SecretOperations secrets;

    public MfaKeyManagementBridge(SecretOperations secrets) {
        this.secrets = Objects.requireNonNull(secrets, "secrets must not be null");
    }

    @Override
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            return secrets.encrypt(MFA_DATA_KEY, bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        requireEnvelope(ciphertext);
        return secrets.decrypt(ciphertext, bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String storeSecret(String tenantId, String userId, String plainSecret) {
        return encrypt(plainSecret);
    }

    @Override
    public String retrieveSecret(String secretReference) {
        return decrypt(secretReference);
    }

    @Override
    public void deleteSecret(String secretReference) {
        requireEnvelope(secretReference);
        // 信封密文随 MFA 业务记录删除；Provider 中的 KEK 不属于单个用户，不能在此删除。
    }

    private void requireEnvelope(String value) {
        if (value == null || !value.startsWith(ENVELOPE_PREFIX)) {
            throw new IllegalArgumentException("MFA Secret reference is not an arse:v1 envelope");
        }
    }
}
